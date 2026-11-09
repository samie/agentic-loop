package com.example.agentdemo.ui;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum LoopStage {
    QUESTION("①", "Question", """
            The user's message is appended to the conversation as a `user` message. \
            The conversation starts with a `system` prompt."""),
    REQUEST("②", "Request", """
            The agent sends the *whole* conversation plus a JSON Schema for every tool. \
            The model keeps no memory between calls, so every request repeats everything."""),
    THINKING("③", "Thinking", """
            Thinking models reason first, in a separate channel. Show it, but don't parse it, \
            and don't send it back."""),
    TOOL_CALL("④", "Tool calls", """
            The reply ends with `finish_reason: "tool_calls"`: a tool name plus arguments. \
            The agent, not the model, runs the function."""),
    OBSERVATION("⑤", "Observation", """
            Each result is appended as a `tool` message, linked to its call, and the agent asks \
            the model again (② once more), up to `max_steps` times."""),
    FINAL_ANSWER("⑥", "Final answer", """
            A reply with `finish_reason: "stop"` has no tool calls; its `content` is the answer.""");

    private final String badge;
    private final String title;
    private final String explanationMarkdown;

    LoopStage(String badge, String title, String explanationMarkdown) {
        this.badge = badge;
        this.title = title;
        this.explanationMarkdown = explanationMarkdown;
    }

    public String badge() {
        return badge;
    }

    public String title() {
        return title;
    }

    public String explanationMarkdown() {
        return explanationMarkdown;
    }

    public String diagramKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<LoopStage> fromDiagramKey(String key) {
        return Arrays.stream(values()).filter(stage -> stage.diagramKey().equals(key)).findFirst();
    }
}
