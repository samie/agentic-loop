package com.example.agentdemo.harness;

import com.qxotic.jinfer.chat.Content;
import com.qxotic.jinfer.chat.Message;
import com.qxotic.jinfer.chat.Role;
import java.util.ArrayList;
import java.util.List;

/**
 * The agentic loop: Thought / Tool-call / Observation, repeated until the model
 * says {@code stop} or the step budget runs out.
 */
public class Agent {

    /** Lets a UI watch the loop as it happens, in this order per step. All methods are optional. */
    public interface Listener {
        /** The agent is about to send the whole conversation plus the tool schemas to the model. */
        default void onRequest(int step, List<Message> conversation, List<Tool> tools, ChatOptions options) {}
        /** A fragment of the model's thinking, while it generates. */
        default void onReasoning(String delta) {}
        /** A fragment of the model's answer text, while it generates. */
        default void onContent(String delta) {}
        /** The model's complete reply for this step. */
        default void onResponse(int step, ChatResponse response) {}
        /** The agent ran a tool the model asked for; the result goes back as a tool message. */
        default void onToolCall(Content.ToolCall call, String result) {}
    }

    private final Llm llm;
    private List<Tool> tools;
    private int maxSteps;
    private final List<Message> conversation = new ArrayList<>();

    public Agent(Llm llm, String systemPrompt, List<Tool> tools, int maxSteps) {
        this.llm = llm;
        this.tools = List.copyOf(tools);
        this.maxSteps = maxSteps;
        conversation.add(Message.system(systemPrompt));
    }

    /** Replaces the system prompt: it is simply the first message of the conversation. */
    public void systemPrompt(String systemPrompt) {
        conversation.set(0, Message.system(systemPrompt));
    }

    public void tools(List<Tool> tools) {
        this.tools = List.copyOf(tools);
    }

    public void maxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    /**
     * Runs the loop for one user question and returns the final answer.
     *
     * @param options sampling, limits, thinking and tool choice for the model's replies
     */
    public String run(String question, ChatOptions options, Listener listener) {
        conversation.add(Message.user(question));

        for (int step = 1; step <= maxSteps; step++) {
            // A forced tool choice applies to the first step only; otherwise the model could never answer
            ChatOptions stepOptions = step == 1 ? options : options.withToolChoice(ChatOptions.ToolChoice.AUTO);

            // THOUGHT: ask the model what to do next (thinking and text stream to the listener)
            listener.onRequest(step, List.copyOf(conversation), tools, stepOptions);
            ChatResponse response = llm.chat(conversation, tools, stepOptions, listener);
            listener.onResponse(step, response);

            // Always record what the model said, tool calls included
            conversation.add(response.reply());

            switch (response.finishReason()) {
                case "stop" -> {
                    return response.content(); // done
                }
                case "tool_calls" -> {
                    for (Content.ToolCall call : response.toolCalls()) {
                        // TOOL CALL: the host executes, not the model
                        String result = execute(call);
                        listener.onToolCall(call, result);
                        // OBSERVATION: feed the result back
                        conversation.add(new Message(Role.TOOL, List.of(new Content.ToolResult(call.id(), result))));
                    }
                }
                default -> throw new IllegalStateException("Unexpected finish_reason: " + response.finishReason());
            }
        }
        throw new IllegalStateException("Agent gave up after " + maxSteps + " steps");
    }

    /** Errors become observations, so the model can correct itself on the next step. */
    private String execute(Content.ToolCall call) {
        Tool tool = tools.stream().filter(t -> t.name().equals(call.name())).findFirst().orElse(null);
        if (tool == null) {
            return "Error: unknown tool '" + call.name() + "'. Available tools: "
                    + tools.stream().map(Tool::name).toList();
        }
        try {
            return tool.call(call.arguments());
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }

    /** Whether the model can think at all; when false, the thinking option has no effect. */
    public boolean canThink() {
        return llm.canThink();
    }

    public List<Message> conversation() {
        return List.copyOf(conversation);
    }
}
