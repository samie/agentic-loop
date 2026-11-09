package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.ui.LoopRole;

public class ErrorCard extends LoopCard {

    public ErrorCard(RuntimeException error) {
        super(LoopRole.ERROR, "The loop stopped", "");
        explain("The agent raises errors it can't hand back to the model.");
        addContent(dataBlock(String.valueOf(error.getMessage())));
    }
}
