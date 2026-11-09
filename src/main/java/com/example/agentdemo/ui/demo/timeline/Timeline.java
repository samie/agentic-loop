package com.example.agentdemo.ui.demo.timeline;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;

public class Timeline extends Composite<Div> {

    private final RequestHistory history = new RequestHistory();
    private int questions;

    public Timeline() {
        getContent().addClassName("timeline");
    }

    public QuestionRun startQuestion(String question, ModelInUse model) {
        QuestionRun run = new QuestionRun(UI.getCurrent(), ++questions, question, model, history);
        getContent().add(run);
        return run;
    }

    public void systemPromptEdited() {
        history.systemPromptEdited();
    }

    public void clear() {
        getContent().removeAll();
        questions = 0;
        history.forget();
    }

    static Div heading(String text) {
        Div heading = new Div(text);
        heading.addClassName("timeline-heading");
        return heading;
    }
}
