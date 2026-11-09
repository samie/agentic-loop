package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.ui.LoopRole;
import com.example.agentdemo.ui.LoopStage;
import com.vaadin.flow.component.html.Div;

public class ThinkingCard extends LoopCard {

    private final Div thoughts = new Div();

    public ThinkingCard() {
        super(LoopRole.THINKING, LoopStage.THINKING, "Thinking", "inside the LLM");
        explain("The model reasons before it answers. It is separate from the answer: "
                + "show it, but don't parse it. It is not sent back in later requests.");
        thoughts.addClassName("thinking-text");
        addContent(thoughts);
    }

    public void append(String delta) {
        thoughts.add(thoughts.getComponentCount() == 0 ? delta.stripLeading() : delta);
    }
}
