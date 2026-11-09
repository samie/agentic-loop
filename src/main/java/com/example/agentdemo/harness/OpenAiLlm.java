package com.example.agentdemo.harness;

import com.example.agentdemo.wire.OpenAiJson;
import com.qxotic.jinfer.chat.Content;
import com.qxotic.jinfer.chat.Message;
import com.qxotic.jinfer.chat.Role;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The LLM, behind an OpenAI-compatible HTTP API (OpenAI, x.ai, a llama.cpp server, …): every step
 * is one {@code POST /chat/completions} with the JSON the UI shows in its OpenAI JSON tab.
 */
public final class OpenAiLlm implements Llm, AutoCloseable {

    private static final System.Logger LOG = System.getLogger(OpenAiLlm.class.getName());
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    /**
     * @param baseUrl the API root, e.g. "https://api.openai.com/v1"
     * @param model   the model id the API knows, e.g. "gpt-5-mini"
     */
    public OpenAiLlm(String baseUrl, String apiKey, String model) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
    }

    /** The model ids the API offers ({@code GET /models}). */
    public static List<String> models(String baseUrl, String apiKey) {
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(stripTrailingSlash(baseUrl) + "/models"))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " from " + request.uri() + ": " + response.body());
            }
            List<String> ids = new ArrayList<>();
            JSON.readTree(response.body()).path("data").forEach(m -> ids.add(m.path("id").asString()));
            return ids.stream().sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not list the models at " + baseUrl + ": " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while listing the models at " + baseUrl, e);
        }
    }

    @Override
    public ChatResponse chat(List<Message> conversation, List<Tool> tools, ChatOptions options, Agent.Listener listener) {
        String body = JSON.writeValueAsString(OpenAiJson.requestBody(model, conversation, tools, options));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (options.timeout() != null) request.timeout(options.timeout());

        long start = System.nanoTime();
        Reply reply = new Reply();
        try {
            if (options.stream()) {
                HttpResponse<Stream<String>> response = http.send(request.build(), HttpResponse.BodyHandlers.ofLines());
                try (Stream<String> lines = response.body()) {
                    if (response.statusCode() != 200) throw httpError(response.statusCode(), lines.collect(Collectors.joining("\n")));
                    // Server-sent events: one "data: {chunk}" line per fragment, then "data: [DONE]"
                    for (String line : (Iterable<String>) lines::iterator) {
                        if (!line.startsWith("data:")) continue;
                        String data = line.substring(5).strip();
                        if (data.equals("[DONE]")) break;
                        reply.addChunk(JSON.readTree(data), start, listener);
                    }
                }
            } else {
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw httpError(response.statusCode(), response.body());
                reply.addComplete(JSON.readTree(response.body()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Request to " + baseUrl + " failed: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + baseUrl, e);
        }
        return reply.toResponse(System.nanoTime() - start, model);
    }

    private IllegalStateException httpError(int status, String body) {
        return new IllegalStateException("HTTP " + status + " from " + baseUrl + "/chat/completions: " + body);
    }

    @Override
    public void close() {
        http.close();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** One reply, put together from the complete response or from its streamed chunks. */
    private static final class Reply {
        final StringBuilder content = new StringBuilder();
        final StringBuilder reasoning = new StringBuilder();
        final Map<Integer, PartialCall> calls = new TreeMap<>(); // streamed calls arrive in pieces, by index
        String finishReason = "stop";
        JsonNode usage;   // OpenAI's token counts
        JsonNode timings; // llama.cpp's server also reports how long it took
        long firstTokenNanos = -1;

        void addComplete(JsonNode body) {
            JsonNode choice = body.path("choices").path(0);
            if (choice.isMissingNode()) throw new IllegalStateException("No choices in the response: " + body);
            JsonNode message = choice.path("message");
            content.append(message.path("content").asString(""));
            reasoning.append(reasoningOf(message));
            int index = 0;
            for (JsonNode call : message.path("tool_calls")) {
                calls.computeIfAbsent(index++, i -> new PartialCall()).add(call);
            }
            finish(body, choice);
        }

        void addChunk(JsonNode chunk, long start, Agent.Listener listener) {
            JsonNode choice = chunk.path("choices").path(0);
            if (!choice.isMissingNode()) {
                JsonNode delta = choice.path("delta");
                String thinking = reasoningOf(delta);
                String text = delta.path("content").asString("");
                boolean generated = !thinking.isEmpty() || !text.isEmpty() || delta.has("tool_calls");
                if (generated && firstTokenNanos < 0) firstTokenNanos = System.nanoTime() - start;
                if (!thinking.isEmpty()) {
                    reasoning.append(thinking);
                    listener.onReasoning(thinking);
                }
                if (!text.isEmpty()) {
                    content.append(text);
                    listener.onContent(text);
                }
                for (JsonNode call : delta.path("tool_calls")) {
                    calls.computeIfAbsent(call.path("index").asInt(0), i -> new PartialCall()).add(call);
                }
            }
            finish(chunk, choice);
        }

        // llama.cpp and DeepSeek call it reasoning_content, vLLM and OpenRouter reasoning
        private static String reasoningOf(JsonNode message) {
            return message.path("reasoning_content").asString(message.path("reasoning").asString(""));
        }

        private void finish(JsonNode body, JsonNode choice) {
            String reason = choice.path("finish_reason").asString("");
            if (!reason.isEmpty()) finishReason = reason;
            if (body.path("usage").isObject()) usage = body.path("usage");
            if (body.path("timings").isObject()) timings = body.path("timings");
        }

        ChatResponse toResponse(long totalNanos, String model) {
            List<Content> parts = new ArrayList<>();
            String thought = reasoning.toString().strip();
            String answer = content.toString().strip();
            if (!thought.isEmpty()) parts.add(new Content.Reasoning(List.of(new Content.Text(thought)), null));
            if (!answer.isEmpty()) parts.add(new Content.Text(answer));
            List<Content.ToolCall> toolCalls = calls.values().stream().map(PartialCall::toToolCall).toList();
            parts.addAll(toolCalls);

            // Some servers say "stop" even when the reply is a tool call; the calls are what count
            String reason = !toolCalls.isEmpty() ? "tool_calls" : finishReason;
            return new ChatResponse(reason, answer, thought, toolCalls, new Message(Role.ASSISTANT, parts),
                    null, null, usage(totalNanos, model));
        }

        /** Token counts from the API; times from llama.cpp's timings, else measured here (network included). */
        private ChatResponse.Usage usage(long totalNanos, String model) {
            if (usage == null) return null;
            Duration promptTime, outputTime;
            if (timings != null) {
                promptTime = Duration.ofNanos((long) (timings.path("prompt_ms").asDouble() * 1e6));
                outputTime = Duration.ofNanos((long) (timings.path("predicted_ms").asDouble() * 1e6));
            } else {
                long first = firstTokenNanos >= 0 ? firstTokenNanos : totalNanos;
                promptTime = Duration.ofNanos(first);
                outputTime = Duration.ofNanos(totalNanos - first);
            }
            var result = new ChatResponse.Usage(usage.path("prompt_tokens").asInt(),
                    usage.path("prompt_tokens_details").path("cached_tokens").asInt(timings != null ? timings.path("cache_n").asInt() : 0),
                    usage.path("completion_tokens").asInt(),
                    usage.path("completion_tokens_details").path("reasoning_tokens").asInt(),
                    promptTime, outputTime);
            LOG.log(System.Logger.Level.INFO, String.format(
                    "%s: prompt %d tok (%d cached) in %.2f s · generated %d tok (%d thinking) at %.1f tok/s",
                    model, result.promptTokens(), result.cachedTokens(), promptTime.toNanos() / 1e9,
                    result.outputTokens(), result.thinkingTokens(), result.tokensPerSecond()));
            return result;
        }
    }

    /** A tool call; when streaming, its id, name and argument text arrive in fragments. */
    private static final class PartialCall {
        String id = "";
        String name = "";
        final StringBuilder arguments = new StringBuilder();

        void add(JsonNode call) {
            if (call.hasNonNull("id")) id = call.path("id").asString();
            JsonNode function = call.path("function");
            if (function.hasNonNull("name")) name += function.path("name").asString();
            arguments.append(function.path("arguments").asString(""));
        }

        @SuppressWarnings("unchecked")
        Content.ToolCall toToolCall() {
            String json = arguments.toString().isBlank() ? "{}" : arguments.toString();
            try {
                // The API sends the arguments as a JSON *string*; the loop wants them as a Map
                return new Content.ToolCall(id, name, JSON.readValue(json, Map.class));
            } catch (JacksonException e) {
                throw new IllegalStateException("The model's arguments for " + name + " are not a JSON object: " + json, e);
            }
        }
    }
}
