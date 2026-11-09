package com.example.agentdemo.ui.demo;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.signals.Signal;
import java.util.function.Consumer;

public class QuestionBox extends Composite<Div> {

    private final TextArea question = new TextArea();

    public QuestionBox(Consumer<String> onAsk, Runnable onNewConversation, Signal<Boolean> ready, Signal<Boolean> running) {
        question.setPlaceholder("e.g. How many seconds is 3 days, 7 hours and 12 minutes?");
        question.setWidthFull();
        question.setMinRows(2);
        question.setMaxRows(6);
        question.setHelperText("Enter asks, Shift+Enter adds a new line.");

        Button ask = new Button();
        ask.addThemeVariants(ButtonVariant.PRIMARY);
        ask.bindText(running.map(isRunning -> isRunning ? "Working…" : "Ask"));
        ask.addClickListener(e -> submit(onAsk));
        askOnEnterWhileShiftEnterAddsALine(ask);

        Button newConversation = new Button("New conversation", e -> onNewConversation.run());

        question.bindEnabled(ready);
        ask.bindEnabled(ready);
        newConversation.bindEnabled(ready);
        focusTheQuestionWhenReadyAgain(ready);

        HorizontalLayout questionRow = new HorizontalLayout(question, ask, newConversation);
        questionRow.addClassName("chat-box");
        questionRow.setWidthFull();
        questionRow.setAlignItems(FlexComponent.Alignment.CENTER);

        PresetBar<QuestionPreset> examples =
                new PresetBar<>("Example questions:", QuestionPreset.ALL, preset -> setQuestion(preset.question()));
        examples.bindEnabled(ready);

        Div box = getContent();
        box.addClassName("question-box");
        box.add(questionRow, examples);
    }

    public void setQuestion(String text) {
        question.setValue(text);
        question.focus();
    }

    private void submit(Consumer<String> onAsk) {
        String text = question.getValue().trim();
        if (text.isEmpty()) return;
        question.clear();
        onAsk.accept(text);
    }

    private void askOnEnterWhileShiftEnterAddsALine(Button ask) {
        question.setValueChangeMode(ValueChangeMode.EAGER);
        ask.addClickShortcut(Key.ENTER).listenOn(question);
    }

    private void focusTheQuestionWhenReadyAgain(Signal<Boolean> ready) {
        Signal.effect(question, context -> {
            boolean isReady = ready.get();
            if (isReady && !context.isInitialRun()) question.focus();
        });
    }
}
