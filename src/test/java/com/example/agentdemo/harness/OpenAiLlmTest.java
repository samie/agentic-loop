package com.example.agentdemo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qxotic.jinfer.chat.Message;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs OpenAiLlm against a tiny local HTTP server that answers like an OpenAI-compatible API. */
class OpenAiLlmTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<Message> CONVERSATION = List.of(Message.system("sys"), Message.user("What is 17 * 23?"));

    private HttpServer server;
    private final List<JsonNode> requests = new ArrayList<>();
    private final List<String> authorizations = new ArrayList<>();

    @AfterEach
    void stop() {
        server.stop(0);
    }

    /** Answers every request with the given status, content type and body. */
    private OpenAiLlm serve(int status, String contentType, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(JSON.readTree(exchange.getRequestBody().readAllBytes()));
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new OpenAiLlm("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/", "secret", "test-model");
    }

    @Test
    void streamedToolCallIsAssembledFromItsFragments() throws IOException {
        String sse = """
                data: {"choices":[{"index":0,"delta":{"role":"assistant","reasoning_content":"Use the "}}]}

                data: {"choices":[{"index":0,"delta":{"reasoning_content":"calculator."}}]}

                data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_abc","type":"function","function":{"name":"calculator","arguments":""}}]}}]}

                data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"expression\\":"}}]}}]}

                data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\\"17*23\\"}"}}]}}]}

                data: {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

                data: {"choices":[],"usage":{"prompt_tokens":120,"completion_tokens":30,"prompt_tokens_details":{"cached_tokens":100}}}

                data: [DONE]

                """;
        OpenAiLlm llm = serve(200, "text/event-stream", sse);
        StringBuilder streamedThinking = new StringBuilder();
        ChatResponse response = llm.chat(CONVERSATION, List.of(Tools.CALCULATOR), ChatOptions.DEFAULTS, new Agent.Listener() {
            @Override
            public void onReasoning(String delta) {
                streamedThinking.append(delta);
            }
        });

        assertEquals("tool_calls", response.finishReason());
        assertEquals("Use the calculator.", streamedThinking.toString());
        assertEquals("Use the calculator.", response.reasoning());
        assertEquals(1, response.toolCalls().size());
        assertEquals("call_abc", response.toolCalls().getFirst().id());
        assertEquals("calculator", response.toolCalls().getFirst().name());
        assertEquals(Map.of("expression", "17*23"), response.toolCalls().getFirst().arguments());
        assertEquals(120, response.usage().promptTokens());
        assertEquals(100, response.usage().cachedTokens());
        assertEquals(30, response.usage().outputTokens());

        JsonNode sent = requests.getFirst();
        assertEquals("test-model", sent.path("model").asString());
        assertTrue(sent.path("stream").asBoolean());
        assertEquals("calculator", sent.path("tools").get(0).path("function").path("name").asString());
        assertEquals("Bearer secret", authorizations.getFirst());
    }

    @Test
    void completeResponseWithoutStreaming() throws IOException {
        String body = """
                {"choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"17 × 23 = 391."}}],
                 "usage":{"prompt_tokens":150,"completion_tokens":9}}
                """;
        OpenAiLlm llm = serve(200, "application/json", body);
        List<String> streamed = new ArrayList<>();
        ChatOptions noStreaming = new ChatOptions(false, false, null, null, null, null, null, null, null, null, null);
        ChatResponse response = llm.chat(CONVERSATION, List.of(), noStreaming, new Agent.Listener() {
            @Override
            public void onContent(String delta) {
                streamed.add(delta);
            }
        });

        assertEquals("stop", response.finishReason());
        assertEquals("17 × 23 = 391.", response.content());
        assertTrue(response.toolCalls().isEmpty());
        assertTrue(streamed.isEmpty(), "nothing streams when streaming is off");
        assertFalse(requests.getFirst().has("tools"), "no tools, no tool schemas");
    }

    @Test
    void httpErrorsIncludeTheBody() throws IOException {
        OpenAiLlm llm = serve(401, "application/json", "{\"error\":{\"message\":\"Incorrect API key\"}}");
        var error = assertThrows(IllegalStateException.class,
                () -> llm.chat(CONVERSATION, List.of(), ChatOptions.DEFAULTS, new Agent.Listener() {}));
        assertTrue(error.getMessage().contains("401") && error.getMessage().contains("Incorrect API key"), error.getMessage());
    }
}
