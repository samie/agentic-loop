package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.ui.LoopRole;
import com.example.agentdemo.ui.LoopStage;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.TabSheet;

public abstract class LoopCard extends Composite<Card> {

    private final Div explanation = new Div();

    protected LoopCard(LoopRole role, LoopStage stage, String title, String flow) {
        Card card = getContent();
        card.addClassNames("loop-card", role.className());
        card.addThemeVariants(CardVariant.OUTLINED);
        card.setWidthFull();
        if (stage != null) card.setHeaderPrefix(new StageBadge(stage));
        card.setTitle(title);
        if (!flow.isEmpty()) card.setHeaderSuffix(flowLabel(flow));
        explanation.addClassName("loop-card-explanation");
        explanation.setVisible(false);
        card.add(explanation);
    }

    protected LoopCard(LoopRole role, String title, String flow) {
        this(role, null, title, flow);
    }

    @Override
    protected Card initContent() {
        return new Card();
    }

    protected void explain(String text) {
        explanation.setText(text);
        explanation.setVisible(true);
    }

    protected void addContent(Component... components) {
        getContent().add(components);
    }

    protected void compact() {
        getContent().addClassName("loop-card-compact");
    }

    protected static Pre dataBlock(String text) {
        Pre pre = new Pre(text == null ? "(not available)" : text);
        pre.addClassName("data");
        return pre;
    }

    protected static Div dataLabel(String text) {
        Div label = new Div(text);
        label.addClassName("data-label");
        return label;
    }

    protected static TabSheet formatTabs(String firstTitle, Component first, String secondTitle, Component second) {
        TabSheet tabs = new TabSheet();
        tabs.addThemeName("small");
        tabs.setWidthFull();
        tabs.add(firstTitle, first);
        tabs.add(secondTitle, second);
        return tabs;
    }

    protected static Div newPartWithFullToggle(String newLabel, String newPart, String fullLabel, String full) {
        Div label = dataLabel(newLabel);
        Pre text = dataBlock(newPart);
        Button toggle = new Button(fullLabel);
        toggle.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        toggle.addClickListener(e -> {
            boolean showFull = text.getText().equals(newPart);
            text.setText(showFull ? full : newPart);
            label.setText(showFull ? fullLabel : newLabel);
            toggle.setText(showFull ? "Show only the new part" : fullLabel);
        });
        return new Div(label, text, toggle);
    }

    protected static String duration(long nanos) {
        double seconds = nanos / 1e9;
        if (seconds < 10) return String.format("%.2f s", seconds);
        if (seconds < 60) return String.format("%.1f s", seconds);
        return String.format("%d min %d s", (long) seconds / 60, (long) seconds % 60);
    }

    protected static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    private static Span flowLabel(String flow) {
        Span label = new Span(flow);
        label.addClassName("loop-card-flow");
        return label;
    }
}
