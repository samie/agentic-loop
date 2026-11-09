package com.example.agentdemo.ui;

import java.util.List;
import java.util.Locale;

public enum LoopRole {
    USER("you"),
    AGENT("agent"),
    LLM("LLM: the model"),
    THINKING("thinking"),
    TOOL("tools"),
    FINAL("final answer"),
    ERROR("error");

    public static final List<LoopRole> LEGEND = List.of(USER, AGENT, LLM, THINKING, TOOL, FINAL);

    private final String legendLabel;

    LoopRole(String legendLabel) {
        this.legendLabel = legendLabel;
    }

    public String legendLabel() {
        return legendLabel;
    }

    public String className() {
        return "loop-role-" + name().toLowerCase(Locale.ROOT);
    }
}
