package com.example.agentdemo.ui.demo.timeline;

import com.example.agentdemo.ui.demo.timeline.cards.LoopCard;
import com.example.agentdemo.ui.demo.timeline.cards.ResponseCard;
import com.example.agentdemo.ui.demo.timeline.cards.ThinkingCard;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Div;

public class LoopStepSection extends Composite<Div> {

    private final boolean streaming;
    private final Div waiting = new Div();
    private ThinkingCard thinking;
    private ResponseCard response;

    LoopStepSection(int step, boolean streaming) {
        this.streaming = streaming;
        getContent().addClassName("loop-step");
        getContent().add(Timeline.heading("Loop step " + step));
        waiting.addClassName("waiting");
    }

    boolean streaming() {
        return streaming;
    }

    void show(LoopCard card) {
        Div section = getContent();
        if (waiting.getParent().isPresent()) {
            section.addComponentAtIndex(section.indexOf(waiting), card);
        } else {
            section.add(card);
        }
        card.scrollIntoView();
    }

    void waitForTheModel() {
        waiting.setText(streaming ? "⏳ The model is reading the prompt and generating its reply…"
                : "⏳ The model is reading the prompt and generating its reply. Streaming is off, "
                        + "so nothing appears until the reply is complete…");
        getContent().add(waiting);
    }

    void stopWaiting() {
        waiting.removeFromParent();
    }

    void appendThinking(String delta) {
        if (thinking == null) {
            if (delta == null || delta.isBlank()) return;
            thinking = new ThinkingCard();
            show(thinking);
        }
        thinking.append(delta);
    }

    void appendAnswer(String delta) {
        if (response == null && (delta == null || delta.isBlank())) return;
        response().appendAnswer(delta);
    }

    ResponseCard response() {
        if (response == null) {
            response = new ResponseCard(streaming);
            show(response);
        }
        return response;
    }
}
