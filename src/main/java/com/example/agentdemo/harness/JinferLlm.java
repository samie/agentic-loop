package com.example.agentdemo.harness;

import com.qxotic.jinfer.cache.PromptCache;
import com.qxotic.jinfer.chat.Channel;
import com.qxotic.jinfer.chat.ChatEngine;
import com.qxotic.jinfer.chat.ChatTemplate.ThinkingPolicy;
import com.qxotic.jinfer.Batch;
import com.qxotic.jinfer.chat.Content;
import com.qxotic.jinfer.chat.Conversation;
import com.qxotic.jinfer.chat.Message;
import com.qxotic.jinfer.hub.ModelStore;
import com.qxotic.jinfer.llm.Generator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The LLM, running inside this JVM with Quixotic jinfer. No server, no HTTP: one
 * {@link ChatEngine} holds the loaded GGUF model and generates the replies.
 */
public final class JinferLlm implements Llm, AutoCloseable {

    private static final System.Logger LOG = System.getLogger(JinferLlm.class.getName());

    private final ChatEngine engine;
    private final int maxOutputTokens;

    private JinferLlm(ChatEngine engine, int maxOutputTokens) {
        this.engine = engine;
        this.maxOutputTokens = maxOutputTokens;
    }

    /**
     * Loads a model, downloading it from Hugging Face on first use.
     *
     * @param modelRef "owner/repo:QUANT", e.g. "Qwen/Qwen3-1.7B-GGUF:Q8_0", or a local .gguf path
     */
    public static JinferLlm load(String modelRef, int contextCapacity, int maxOutputTokens) {
        Path path = ModelStore.standard().resolve(modelRef); // cached under ~/.cache/jinfer/models
        var cacheOptions = PromptCache.Options.DEFAULTS.withContextCapacity(contextCapacity);
        return new JinferLlm(new ChatEngine(path, Map.of(), cacheOptions), maxOutputTokens);
    }

    @Override
    public boolean canThink() {
        return engine.thinkingPolicy() != ThinkingPolicy.NONE;
    }

    @Override
    public ChatOptions recommended() {
        var defaults = engine.loaded().samplingDefaults();
        return new ChatOptions(false, true, defaults.effectiveTemperature(), defaults.effectiveTopP(),
                defaults.effectiveTopK(), defaults.effectiveMinP(), null, maxOutputTokens, null, null, null);
    }

    @Override
    public boolean alwaysThinks() {
        return engine.thinkingPolicy() == ThinkingPolicy.ALWAYS;
    }

    @Override
    public ChatResponse chat(List<Message> conversation, List<Tool> tools, ChatOptions options, Agent.Listener listener) {
        List<com.qxotic.jinfer.chat.Tool> jinferTools = tools.stream()
                .map(t -> com.qxotic.jinfer.chat.Tool.of(t.name(), t.description(), t.parameters()))
                .toList();
        // a model that always reasons cannot render a non-thinking turn
        boolean think = options.thinking() || engine.thinkingPolicy() == ThinkingPolicy.ALWAYS;
        // empty options fall back to the model's recommended sampling, then jinfer's baseline
        var sampling = engine.loaded().samplingDefaults().resolve(
                options.temperature(), options.topP(), options.topK(), options.minP(), options.seed());
        ChatEngine.Request request = ChatEngine.Request
                .builder(conversation, sampling)
                .tools(jinferTools)
                .thinking(think)
                .maxOutputTokens(options.maxOutputTokens() != null ? options.maxOutputTokens() : maxOutputTokens)
                .maxReasoningTokens(options.maxThinkingTokens())
                .timeout(options.timeout() != null ? options.timeout() : Duration.ZERO)
                .forcedTool(switch (options.toolChoice()) {
                    case ChatOptions.ToolChoice.Auto a -> ChatEngine.ForcedTool.NONE;
                    case ChatOptions.ToolChoice.Required r -> ChatEngine.ForcedTool.ANY;
                    case ChatOptions.ToolChoice.Named n -> new ChatEngine.ForcedTool.Named(n.tool());
                })
                .build();
        String nativePrompt = nativePrompt(new Conversation(conversation, jinferTools, think));

        // Generate; when streaming, each fragment goes to the listener as soon as it is decoded
        ChatEngine.Completion completion = engine.complete(request, !options.stream() ? ChatEngine.ReplySink.NONE
                : new ChatEngine.ReplySink() {
            @Override
            public void on(ChatEngine.Delta delta) {
                if (delta.channel().equals(Channel.REASONING)) listener.onReasoning(delta.text());
                else if (delta.channel().equals(Channel.CONTENT)) listener.onContent(delta.text());
            }
        });
        if (completion.cancelled()) {
            throw new IllegalStateException("Generation was cancelled");
        }
        String nativeOutput = engine.loaded().tokenizer().decode(completion.result().tokens());
        return toResponse(completion.reply(), completion.result().finishReason())
                .withNativeText(nativePrompt, nativeOutput)
                .withUsage(usage(completion));
    }

    /**
     * The prompt exactly as the model reads it: the model's chat template renders the
     * conversation and tool schemas into tokens, which we decode back into text. Only for display.
     */
    private String nativePrompt(Conversation conversation) {
        try {
            StringBuilder text = new StringBuilder();
            for (Batch batch : engine.encode(conversation, null).prompt()) {
                if (batch.input() instanceof Batch.Input.Tokens tokens) {
                    text.append(engine.loaded().tokenizer().decode(tokens.ids()));
                }
            }
            return text.toString();
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not render the native prompt", e);
            return null;
        }
    }

    /** Token counts and timings of one model call; also logged, to compare models. */
    private ChatResponse.Usage usage(ChatEngine.Completion completion) {
        Generator.GenerationResult result = completion.result();
        var usage = new ChatResponse.Usage(completion.promptTokens(), completion.restoredTokens(),
                result.completionTokens(), completion.reasoningTokens(), result.promptTime(), result.decodeTime());
        LOG.log(System.Logger.Level.INFO, String.format(
                "%s: prompt %d tok (%d cached) in %.2f s · generated %d tok (%d thinking) at %.1f tok/s",
                engine.modelName(), usage.promptTokens(), usage.cachedTokens(), usage.promptTime().toNanos() / 1e9,
                usage.outputTokens(), usage.thinkingTokens(), usage.tokensPerSecond()));
        return usage;
    }

    /** Flattens a reply into answer text, thinking text and tool calls. */
    public static ChatResponse toResponse(Message reply, Generator.FinishReason reason) {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<Content.ToolCall> toolCalls = new ArrayList<>();
        collect(reply.content(), content, reasoning, toolCalls, false);

        String finishReason;
        if (!toolCalls.isEmpty()) finishReason = "tool_calls";
        else if (reason == Generator.FinishReason.STOP) finishReason = "stop";
        else if (reason == Generator.FinishReason.LENGTH) finishReason = "length";
        else finishReason = "other (" + reason + ")";

        return new ChatResponse(finishReason, content.toString().strip(), reasoning.toString().strip(),
                List.copyOf(toolCalls), reply, null, null, null);
    }

    // A tool call can also appear inside the reasoning part, so walk the parts recursively.
    private static void collect(List<Content> parts, StringBuilder content, StringBuilder reasoning,
                                List<Content.ToolCall> toolCalls, boolean inReasoning) {
        for (Content part : parts) {
            switch (part) {
                case Content.Text t -> (inReasoning ? reasoning : content).append(t.text());
                case Content.Reasoning r -> collect(r.content(), content, reasoning, toolCalls, true);
                case Content.ToolCall call -> toolCalls.add(call);
                default -> {} // tool results and media never appear in a generated reply
            }
        }
    }

    @Override
    public void close() {
        engine.close();
    }
}
