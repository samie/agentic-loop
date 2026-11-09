package com.example.agentdemo.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agentdemo.harness.ChatOptions;
import com.example.agentdemo.harness.ChatResponse;
import com.example.agentdemo.harness.Tools;
import com.qxotic.jinfer.chat.Content;
import com.qxotic.jinfer.chat.Message;
import com.qxotic.jinfer.chat.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OpenAiJsonTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Message CALL = new Message(Role.ASSISTANT, List.of(
            new Content.Reasoning(List.of(new Content.Text("use the calculator")), null),
            new Content.ToolCall("", "calculator", Map.of("expression", "17*23"))));
    private static final List<Message> CONVERSATION = List.of(
            Message.system("sys"), Message.user("What is 17 * 23?"), CALL,
            new Message(Role.TOOL, List.of(new Content.ToolResult("", "391"))));

    @Test
    void requestHasMessagesAndToolSchemas() {
        JsonNode body = JSON.readTree(OpenAiJson.request("lfm", CONVERSATION, List.of(Tools.CALCULATOR), ChatOptions.DEFAULTS));
        assertEquals("lfm", body.path("model").asString());
        assertEquals(4, body.path("messages").size());
        JsonNode function = body.path("tools").get(0).path("function");
        assertEquals("calculator", function.path("name").asString());
        assertEquals("object", function.path("parameters").path("type").asString());
    }

    @Test
    void toolCallsGetIdsAndArgumentsAsJsonString() {
        JsonNode messages = JSON.readTree(OpenAiJson.newMessages(CONVERSATION, 2, false));
        assertEquals(2, messages.size(), "only the messages after index 2");

        JsonNode assistant = messages.get(0);
        assertTrue(assistant.path("content").isNull());
        assertTrue(assistant.path("reasoning_content").isMissingNode(), "reasoning is never sent back");
        JsonNode call = assistant.path("tool_calls").get(0);
        assertEquals("call_0", call.path("id").asString());
        assertEquals("{\"expression\":\"17*23\"}", call.path("function").path("arguments").asString());

        JsonNode tool = messages.get(1);
        assertEquals("tool", tool.path("role").asString());
        assertEquals("call_0", tool.path("tool_call_id").asString(), "the result answers the minted id");
        assertEquals("391", tool.path("content").asString());
    }

    @Test
    void requestCarriesTheSettings() {
        ChatOptions options = new ChatOptions(true, true, 0.7f, 0.9f, 40, 0.05f, 42L, 256, null, null,
                ChatOptions.ToolChoice.REQUIRED);
        JsonNode body = JSON.readTree(OpenAiJson.request("lfm", CONVERSATION, List.of(Tools.CALCULATOR), options));
        assertEquals(0.7, body.path("temperature").asDouble(), 1e-6);
        assertEquals(0.9, body.path("top_p").asDouble(), 1e-6);
        assertEquals(40, body.path("top_k").asInt());
        assertEquals(42, body.path("seed").asLong());
        assertEquals(256, body.path("max_tokens").asInt());
        assertEquals("required", body.path("tool_choice").asString());
        assertTrue(body.path("stream").asBoolean());
        assertTrue(body.path("chat_template_kwargs").path("enable_thinking").asBoolean());

        JsonNode auto = JSON.readTree(OpenAiJson.request("lfm", CONVERSATION, List.of(Tools.CALCULATOR), ChatOptions.DEFAULTS));
        assertEquals("auto", auto.path("tool_choice").asString());
        assertTrue(auto.path("temperature").isMissingNode(), "unset values are left out");

        JsonNode noTools = JSON.readTree(OpenAiJson.request("lfm", CONVERSATION, List.of(), ChatOptions.DEFAULTS));
        assertTrue(noTools.path("tools").isMissingNode() && noTools.path("tool_choice").isMissingNode(),
                "no tools, no tool_choice");

        JsonNode named = JSON.readTree(OpenAiJson.request("lfm", CONVERSATION, List.of(Tools.CALCULATOR),
                ChatOptions.DEFAULTS.withToolChoice(new ChatOptions.ToolChoice.Named("calculator"))));
        assertEquals("calculator", named.path("tool_choice").path("function").path("name").asString());
    }

    @Test
    void editedSystemPromptCountsAsNew() {
        JsonNode messages = JSON.readTree(OpenAiJson.newMessages(CONVERSATION, 2, true));
        assertEquals(3, messages.size());
        assertEquals("system", messages.get(0).path("role").asString());
    }

    @Test
    void responseCarriesFinishReasonAndReasoning() {
        ChatResponse response = new ChatResponse("tool_calls", "", "use the calculator",
                List.of((Content.ToolCall) CALL.content().get(1)), CALL, null, null, null);
        JsonNode choice = JSON.readTree(OpenAiJson.response(CONVERSATION.subList(0, 2), response)).path("choices").get(0);
        assertEquals("tool_calls", choice.path("finish_reason").asString());
        assertEquals("use the calculator", choice.path("message").path("reasoning_content").asString());
        assertEquals("call_0", choice.path("message").path("tool_calls").get(0).path("id").asString());
    }
}
