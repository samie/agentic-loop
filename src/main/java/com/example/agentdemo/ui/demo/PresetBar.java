package com.example.agentdemo.ui.demo;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.signals.Signal;
import java.util.List;
import java.util.function.Consumer;

public class PresetBar<T extends Preset> extends Composite<Div> {

    public PresetBar(String label, List<T> presets, Consumer<T> onApply) {
        Div bar = getContent();
        bar.addClassName("presets");
        bar.add(new Span(label));
        for (T preset : presets) {
            Button button = new Button(preset.name(), e -> onApply.accept(preset));
            button.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
            button.setTooltipText(preset.tooltip());
            bar.add(button);
        }
    }

    public void bindEnabled(Signal<Boolean> enabled) {
        getContent().bindEnabled(enabled);
    }
}
