package com.example.agentdemo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qxotic.jinfer.chat.Content;
import com.qxotic.jinfer.chat.Conversation;
import com.qxotic.jinfer.chat.Message;
import com.qxotic.jinfer.chat.Role;
import com.qxotic.jinfer.llm.Generator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Runs the real loop against a scripted fake LLM: no model, no download, no Vector API needed. */
class AgentTest {

    private static final Message TOOL_CALL = new Message(Role.ASSISTANT, List.of(
            new Content.Reasoning(List.of(new Content.Text("I should use the calculator.")), null),
            new Content.ToolCall("", "calculator", Map.of("expression", "17*23"))));
    private static final Message STOP = Message.assistant("17 × 23 = 391.");

    /** Replays scripted replies, streaming them like the real engine, and records each request. */
    static final class FakeLlm implements Llm {
        final Deque<Message> replies;
        final List<List<Message>> requests = new ArrayList<>();
        final List<Boolean> thinkingFlags = new ArrayList<>();
        final List<ChatOptions> options = new ArrayList<>();

        FakeLlm(Message... replies) {
            this.replies = new ArrayDeque<>(List.of(replies));
        }

        @Override
        public ChatResponse chat(List<Message> conversation, List<Tool> tools, ChatOptions options, Agent.Listener listener) {
            requests.add(List.copyOf(conversation));
            thinkingFlags.add(options.thinking());
            this.options.add(options);
            ChatResponse response = JinferLlm.toResponse(replies.removeFirst(), Generator.FinishReason.STOP)
                    .withNativeText("<prompt " + conversation.size() + ">", "<output>");
            if (!response.reasoning().isEmpty()) listener.onReasoning(response.reasoning());
            if (!response.content().isEmpty()) listener.onContent(response.content());
            return response;
        }
    }

    @Test
    void runsToolThenAnswers() {
        FakeLlm llm = new FakeLlm(TOOL_CALL, STOP);
        Agent agent = new Agent(llm, "You are helpful.", List.of(Tools.CALCULATOR), 5);
        List<String> events = new ArrayList<>();

        String answer = agent.run("What is 17 * 23?", ChatOptions.DEFAULTS, new Agent.Listener() {
            @Override public void onRequest(int step, List<Message> conversation, List<Tool> tools, ChatOptions options) {
                events.add("request " + step + ": " + conversation.size() + " messages, " + tools.size() + " tools");
            }
            @Override public void onResponse(int step, ChatResponse response) {
                events.add("response " + step + ": " + response.finishReason() + " " + response.nativePrompt());
            }
            @Override public void onReasoning(String delta) { events.add("reasoning: " + delta); }
            @Override public void onContent(String delta) { events.add("content: " + delta); }
            @Override public void onToolCall(Content.ToolCall call, String result) { events.add("tool: " + call.name() + " = " + result); }
        });

        assertEquals("17 × 23 = 391.", answer);
        assertEquals(List.of(
                "request 1: 2 messages, 1 tools", "reasoning: I should use the calculator.",
                "response 1: tool_calls <prompt 2>", "tool: calculator = 391",
                "request 2: 4 messages, 1 tools", "content: 17 × 23 = 391.",
                "response 2: stop <prompt 4>"), events);

        // Second request carries: system, user, assistant (the reply as-is), tool result
        List<Message> second = llm.requests.get(1);
        assertEquals(List.of(Role.SYSTEM, Role.USER, Role.ASSISTANT, Role.TOOL), second.stream().map(Message::role).toList());
        assertEquals(TOOL_CALL, second.get(2), "the reply must be appended verbatim");
        assertEquals(List.of(new Content.ToolResult("", "391")), second.get(3).content());

        // jinfer accepts the history: every tool result answers an earlier call
        new Conversation(agent.conversation(), List.of(), true);
    }

    @Test
    void toolErrorsAreFedBackAsObservations() {
        Message badCall = new Message(Role.ASSISTANT, List.of(new Content.ToolCall("call_7", "calculator", Map.of("expr", "1+"))));
        FakeLlm llm = new FakeLlm(badCall, STOP);
        new Agent(llm, "sys", List.of(Tools.CALCULATOR), 5).run("q", ChatOptions.DEFAULTS, new Agent.Listener() {});

        Content.ToolResult observation = (Content.ToolResult) llm.requests.get(1).get(3).content().getFirst();
        assertEquals("call_7", observation.callId());
        assertTrue(observation.text().startsWith("Error: missing required string parameter 'expression'"), observation.text());
    }

    @Test
    void unknownToolIsFedBack() {
        Message call = new Message(Role.ASSISTANT, List.of(new Content.ToolCall("", "weather", Map.of())));
        FakeLlm llm = new FakeLlm(call, STOP);
        new Agent(llm, "sys", List.of(Tools.CALCULATOR), 5).run("q", ChatOptions.DEFAULTS, new Agent.Listener() {});

        Content.ToolResult observation = (Content.ToolResult) llm.requests.get(1).get(3).content().getFirst();
        assertEquals("Error: unknown tool 'weather'. Available tools: [calculator]", observation.text());
    }

    @Test
    void stopsAfterMaxSteps() {
        Agent agent = new Agent(new FakeLlm(TOOL_CALL, TOOL_CALL), "sys", List.of(Tools.CALCULATOR), 2);
        var error = assertThrows(IllegalStateException.class, () -> agent.run("q", ChatOptions.DEFAULTS, new Agent.Listener() {}));
        assertEquals("Agent gave up after 2 steps", error.getMessage());
    }

    @Test
    void thinkingFlagReachesTheModelButNotSubAgents() {
        Message delegateCall = new Message(Role.ASSISTANT, List.of(
                new Content.ToolCall("", "delegate", Map.of("task", "What is 17 * 23?"))));
        // parent: delegate call, then (sub-agent: one answer), then parent's final answer
        FakeLlm llm = new FakeLlm(delegateCall, STOP, STOP);
        Tool delegate = Tools.delegate(llm, "sys", List.of(Tools.CALCULATOR), 5);

        new Agent(llm, "sys", List.of(Tools.CALCULATOR, delegate), 5).run("q", ChatOptions.DEFAULTS.withThinking(true), new Agent.Listener() {});

        assertEquals(List.of(true, false, true), llm.thinkingFlags);
        Content.ToolResult observation = (Content.ToolResult) llm.requests.get(2).get(3).content().getFirst();
        assertEquals("17 × 23 = 391.", observation.text(), "only the sub-agent's final answer comes back");
    }

    @Test
    void forcedToolChoiceAppliesToTheFirstStepOnly() {
        FakeLlm llm = new FakeLlm(TOOL_CALL, STOP);
        ChatOptions options = ChatOptions.DEFAULTS.withToolChoice(ChatOptions.ToolChoice.REQUIRED);
        new Agent(llm, "sys", List.of(Tools.CALCULATOR), 5).run("q", options, new Agent.Listener() {});

        assertEquals(ChatOptions.ToolChoice.REQUIRED, llm.options.get(0).toolChoice());
        assertEquals(ChatOptions.ToolChoice.AUTO, llm.options.get(1).toolChoice(), "or the model could never answer");
    }

    @Test
    void settingsCanChangeBetweenQuestions() {
        FakeLlm llm = new FakeLlm(STOP, TOOL_CALL, TOOL_CALL);
        Agent agent = new Agent(llm, "first prompt", List.of(Tools.CALCULATOR), 5);
        agent.run("q1", ChatOptions.DEFAULTS, new Agent.Listener() {});

        agent.systemPrompt("second prompt");
        agent.maxSteps(2);
        agent.tools(List.of());
        assertThrows(IllegalStateException.class, () -> agent.run("q2", ChatOptions.DEFAULTS, new Agent.Listener() {}));

        List<Message> second = llm.requests.get(1);
        assertEquals("second prompt", second.getFirst().text(), "the system prompt is just the first message");
        assertEquals(4, second.size(), "the rest of the conversation is kept");
        Content.ToolResult observation = (Content.ToolResult) llm.requests.get(2).getLast().content().getFirst();
        assertTrue(observation.text().startsWith("Error: unknown tool 'calculator'"), "disabled tools are gone");
        assertEquals(3, llm.requests.size(), "max steps 2 stops the second question after two calls");
    }

    @Test
    void flattensRepliesLikeTheOpenAiShape() {
        // A tool call nested inside the reasoning part still counts as a tool call
        Message nested = new Message(Role.ASSISTANT, List.of(new Content.Reasoning(List.of(
                new Content.Text("thinking…"), new Content.ToolCall("", "current_time", Map.of())), null)));
        ChatResponse response = JinferLlm.toResponse(nested, Generator.FinishReason.STOP);
        assertEquals("tool_calls", response.finishReason());
        assertEquals("thinking…", response.reasoning());
        assertEquals("current_time", response.toolCalls().getFirst().name());

        assertEquals("stop", JinferLlm.toResponse(STOP, Generator.FinishReason.STOP).finishReason());
        assertEquals("length", JinferLlm.toResponse(STOP, Generator.FinishReason.LENGTH).finishReason());
    }

    @Test
    void toolSchemasAreValidJinferTools() {
        Tool delegate = Tools.delegate(new FakeLlm(), "sys", List.of(), 1);
        for (Tool tool : List.of(Tools.CALCULATOR, Tools.CURRENT_TIME, delegate)) {
            var jinferTool = com.qxotic.jinfer.chat.Tool.of(tool.name(), tool.description(), tool.parameters());
            assertEquals("object", jinferTool.parameters().get("type"));
        }
    }

    @Test
    void calculator() {
        assertEquals("9.620689655", Tools.CALCULATOR.call(Map.of("expression", "155 / (58 * 1000 / 3600)")));
        assertEquals("512", Tools.CALCULATOR.call(Map.of("expression", "2 ** 3 ^ 2")));
        assertEquals("-7", Tools.CALCULATOR.call(Map.of("expression", "-(3 + 4)")));
        assertThrows(IllegalArgumentException.class, () -> Tools.CALCULATOR.call(Map.of("expression", "1/0")));
        assertThrows(IllegalArgumentException.class, () -> Tools.CALCULATOR.call(Map.of("expr", "1")));
    }
}
