package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.ui.LoopRole;

public class DecisionCard extends LoopCard {

    public DecisionCard(String finishReason) {
        super(LoopRole.AGENT, "Agent decides: " + whatTheAgentDoes(finishReason), "");
        compact();
    }

    private static String whatTheAgentDoes(String finishReason) {
        return switch (finishReason) {
            case "tool_calls" -> "finish_reason = \"tool_calls\" → the agent runs each tool, adds the results "
                    + "to the conversation, and asks the model again.";
            case "stop" -> "finish_reason = \"stop\" → the loop ends; the answer goes to the user.";
            default -> "finish_reason = \"" + finishReason + "\" → not an answer; the agent stops with an error.";
        };
    }
}
