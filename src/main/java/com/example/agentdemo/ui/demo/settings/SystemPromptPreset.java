package com.example.agentdemo.ui.demo.settings;

import com.example.agentdemo.ui.demo.Preset;
import java.util.List;

public record SystemPromptPreset(String name, String description, String systemPrompt) implements Preset {

    public static final SystemPromptPreset DEFAULT = new SystemPromptPreset("Default",
            "The default system prompt: use the tools whenever they help.",
            AgentConfig.DEFAULT_SYSTEM_PROMPT);

    public static final SystemPromptPreset CALCULATOR = new SystemPromptPreset("Calculator",
            "Forbids mental arithmetic, so every number goes through the calculator.",
            """
            You are a precise assistant who cannot do arithmetic in your head. For every \
            calculation, however small, call the calculator tool with the complete expression, \
            and use only its result. Never state a number you did not get from the calculator.""");

    public static final SystemPromptPreset CLOCK = new SystemPromptPreset("Clock",
            "The model is told it doesn't know the time, so it asks current_time once per place.",
            """
            You do not know the current date or time. Whenever a question involves the current \
            date or time anywhere, call current_time with the IANA time zone of that place \
            (for example Europe/Helsinki), once per place, and answer only from the results.""");

    public static final SystemPromptPreset TWO_TOOLS = new SystemPromptPreset("Two tools",
            "Plans every tool call first, then uses current_time and the calculator together.",
            """
            You solve tasks with tools. First list every tool call the task needs, then make \
            them: current_time for any current date or time, the calculator for all arithmetic. \
            You may call several tools at once. Never compute or guess yourself, and answer \
            only when you have every result.""");

    public static final SystemPromptPreset DELEGATE = new SystemPromptPreset("Delegate",
            "A coordinator that hands each side task to a sub-agent with its own loop.",
            """
            You are a coordinator. You never solve subtasks yourself: for each self-contained \
            subtask, call delegate once with complete instructions. The sub-agent has the \
            calculator and current_time tools. When all sub-agents have answered, combine their \
            results into one short answer.""");

    public static final List<SystemPromptPreset> ALL = List.of(DEFAULT, CALCULATOR, CLOCK, TWO_TOOLS, DELEGATE);

    @Override
    public String tooltip() {
        return description + " Replaces the system prompt and enables all tools.";
    }
}
