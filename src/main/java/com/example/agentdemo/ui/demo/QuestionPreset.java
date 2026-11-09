package com.example.agentdemo.ui.demo;

import com.example.agentdemo.ui.demo.settings.SystemPromptPreset;
import java.util.List;

public record QuestionPreset(String name, String question, SystemPromptPreset worksBestWith) implements Preset {

    public static final List<QuestionPreset> ALL = List.of(
            new QuestionPreset("Seconds",
                    "How many seconds is 3 days, 7 hours and 12 minutes?",
                    SystemPromptPreset.DEFAULT),
            new QuestionPreset("Big numbers",
                    "What is 1234 * 5678 + 91011, and what is that result divided by 7?",
                    SystemPromptPreset.CALCULATOR),
            new QuestionPreset("Two time zones",
                    "What time is it right now in Helsinki and in Tokyo?",
                    SystemPromptPreset.CLOCK),
            new QuestionPreset("Time and math",
                    "Tell me the current UTC time, and how many seconds are in 3 days, 7 hours and 12 minutes.",
                    SystemPromptPreset.TWO_TOOLS),
            new QuestionPreset("Two side tasks",
                    "Do two side tasks and summarize both results: (1) find the current time in New York, "
                            + "(2) compute 2^20 - 1.",
                    SystemPromptPreset.DELEGATE));

    @Override
    public String tooltip() {
        return "\"" + question + "\" Works best with the " + worksBestWith.name() + " system prompt.";
    }
}
