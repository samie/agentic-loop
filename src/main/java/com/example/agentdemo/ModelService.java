package com.example.agentdemo;

import com.example.agentdemo.harness.JinferLlm;
import com.example.agentdemo.harness.Llm;
import com.example.agentdemo.harness.OpenAiLlm;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.shared.SharedValueSignal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ModelService implements DisposableBean {

    /**
     * @param ref    a Hugging Face "owner/repo:QUANT" reference or .gguf path, or the API's model id
     * @param remote whether the model runs behind the OpenAI-compatible API instead of in this JVM
     */
    public record ModelOption(String ref, String label, String description, boolean remote) {
        public ModelOption(String ref, String label, String description) {
            this(ref, label, description, false);
        }
    }

    public enum Phase { LOADING, READY, FAILED }

    public record ModelState(ModelOption option, Phase phase, String status) {}

    public static final List<ModelOption> OPTIONS = List.of(
            new ModelOption("LiquidAI/LFM2.5-1.2B-Instruct-GGUF:Q4_K_M", "LFM2.5 1.2B Instruct",
                    "Fastest; built for on-device use (BFCL v3 49.1). 0.7 GB."),
            new ModelOption("openbmb/MiniCPM5-1B-GGUF:Q4_K_M", "MiniCPM5 1B",
                    "Thinking is optional (use Think); tool-calling scores not published. 0.7 GB."),
            new ModelOption("LiquidAI/LFM2.5-1.2B-Thinking-GGUF:Q4_K_M", "LFM2.5 1.2B Thinking",
                    "A thinking model: always reasons before it answers or calls a tool. 0.7 GB."),
            new ModelOption("unsloth/Qwen3.5-2B-GGUF:Q4_K_M", "Qwen3.5 2B",
                    "Thinking is optional (use Think). Largest and slowest of these. 1.3 GB."));

    private static final Logger log = LoggerFactory.getLogger(ModelService.class);

    private final Function<ModelOption, Llm> loader;
    private final List<ModelOption> options;
    private final SharedValueSignal<ModelState> state = new SharedValueSignal<>(ModelState.class);
    private final Signal<ModelState> readOnlyState = state.asReadonly();
    private volatile Llm llm;
    private int latestLoad;

    @Autowired
    public ModelService(@Value("${agent.model}") String defaultModel,
                        @Value("${agent.context-capacity}") int contextCapacity,
                        @Value("${agent.max-output-tokens}") int maxOutputTokens,
                        @Value("${agent.api.base-url}") String apiBaseUrl,
                        @Value("${agent.api.key}") String apiKey,
                        @Value("${agent.api.models}") String apiModels) {
        this(defaultModel, apiOptions(apiBaseUrl, apiKey, apiModels), option -> option.remote()
                ? new OpenAiLlm(apiBaseUrl, apiKey, option.ref())
                : JinferLlm.load(option.ref(), contextCapacity, maxOutputTokens));
    }

    /**
     * @param apiOptions models behind the OpenAI-compatible API, offered after the local ones
     * @param loader     loads (or connects to) the model of an option
     */
    public ModelService(String defaultModel, List<ModelOption> apiOptions, Function<ModelOption, Llm> loader) {
        this.loader = loader;
        List<ModelOption> all = new ArrayList<>(OPTIONS);
        all.addAll(apiOptions);
        if (all.stream().noneMatch(o -> o.ref().equals(defaultModel))) {
            all.addFirst(new ModelOption(defaultModel, defaultModel, "Set with agent.model / AGENT_MODEL."));
        }
        this.options = List.copyOf(all);
        select(defaultModel);
    }

    public Signal<ModelState> state() {
        return readOnlyState;
    }

    public Llm llm() {
        return llm;
    }

    public List<ModelOption> options() {
        return options;
    }

    public synchronized void select(String ref) {
        ModelOption option = options.stream().filter(o -> o.ref().equals(ref)).findFirst().orElseThrow();
        ModelState current = state.peek();
        if (current != null && current.option().equals(option) && current.phase() != Phase.FAILED) return;
        int load = ++latestLoad;
        Llm previous = llm;
        llm = null;
        state.set(new ModelState(option, Phase.LOADING, option.remote() ? "Connecting to " + option.label() + "…"
                : "Downloading / loading " + option.label()
                        + "… (the first use of a model downloads it, later loads take seconds)"));
        Thread.ofPlatform().daemon().name("model-loader").start(() -> load(option, load, previous));
    }

    @Override
    public synchronized void destroy() {
        close(llm);
    }

    private void load(ModelOption option, int load, Llm previous) {
        close(previous);
        long start = System.nanoTime();
        try {
            Llm loaded = loader.apply(option);
            synchronized (this) {
                if (load != latestLoad) {
                    close(loaded);
                    return;
                }
                log.info("Loaded {} in {} s", option.ref(), (System.nanoTime() - start) / 1_000_000_000);
                llm = loaded;
                state.set(new ModelState(option, Phase.READY, option.remote()
                        ? option.label() + ", " + option.description()
                        : option.label() + " (" + option.ref() + "), running in this JVM with Quixotic jinfer."));
            }
        } catch (Throwable e) {
            log.error("Failed to load {}", option.ref(), e);
            synchronized (this) {
                if (load == latestLoad) {
                    state.set(new ModelState(option, Phase.FAILED,
                            "Failed to load " + option.ref() + ": " + e + vectorApiHint(e)));
                }
            }
        }
    }

    /**
     * The models offered by the OpenAI-compatible API, or none without an API key. Without a
     * configured list, asks the API which models it has.
     */
    static List<ModelOption> apiOptions(String baseUrl, String apiKey, String models) {
        if (apiKey == null || apiKey.isBlank()) return List.of();
        List<String> ids = Arrays.stream(models.split(",")).map(String::strip).filter(id -> !id.isEmpty()).toList();
        if (ids.isEmpty()) {
            try {
                ids = OpenAiLlm.models(baseUrl, apiKey);
            } catch (RuntimeException e) {
                log.warn("An API key is set, but listing the models failed; set agent.api.models / OPENAI_MODELS", e);
                return List.of();
            }
        }
        String host = URI.create(baseUrl).getHost();
        return ids.stream()
                .map(id -> new ModelOption(id, id + " (" + host + ")",
                        "over the OpenAI-compatible API at " + baseUrl + ".", true))
                .toList();
    }

    private static void close(Llm llm) {
        if (llm instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("Failed to close the previous model", e);
            }
        }
    }

    private static String vectorApiHint(Throwable e) {
        String text = String.valueOf(e);
        return text.contains("incubator") || text.contains("Vector")
                ? " (jinfer needs a JDK 25 that ships jdk.incubator.vector, started with "
                        + "--add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED)"
                : "";
    }
}
