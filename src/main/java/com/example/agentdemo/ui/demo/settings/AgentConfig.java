package com.example.agentdemo.ui.demo.settings;

import com.example.agentdemo.harness.Llm;
import com.example.agentdemo.harness.Tool;
import com.example.agentdemo.harness.Tools;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record AgentConfig(String systemPrompt, int maxSteps, Set<String> toolNames, boolean stream) {

    public static final String DEFAULT_SYSTEM_PROMPT = """
            You are a helpful assistant. Use the available tools whenever they help:
            the calculator for any arithmetic, current_time for dates and times,
            and delegate for self-contained side tasks. Answer concisely.""";
    public static final int DEFAULT_MAX_STEPS = 10;
    public static final List<String> TOOL_NAMES = List.of("calculator", "current_time", "delegate");

    private static final String SUB_AGENT_PROMPT = """
            You are a helper agent working on one task. Use the calculator for any arithmetic and \
            current_time for dates and times. Answer concisely with the result.""";

    public List<Tool> tools(Llm llm) {
        List<Tool> enabled = new ArrayList<>();
        if (toolNames.contains("calculator")) enabled.add(Tools.CALCULATOR);
        if (toolNames.contains("current_time")) enabled.add(Tools.CURRENT_TIME);
        if (toolNames.contains("delegate")) {
            enabled.add(Tools.delegate(llm, SUB_AGENT_PROMPT, List.copyOf(enabled), maxSteps));
        }
        return enabled;
    }
}
