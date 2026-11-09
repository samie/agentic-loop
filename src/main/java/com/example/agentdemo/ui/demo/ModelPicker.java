package com.example.agentdemo.ui.demo;

import com.example.agentdemo.ModelService;
import com.example.agentdemo.ModelService.ModelOption;
import com.example.agentdemo.ModelService.ModelState;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.signals.Signal;

public class ModelPicker extends Composite<Div> {

    public ModelPicker(ModelService models, Signal<Boolean> locked) {
        Select<ModelOption> select = new Select<>();
        select.setLabel("Model");
        select.setItems(models.options());
        select.setItemLabelGenerator(ModelOption::label);
        select.setRenderer(new ComponentRenderer<>(ModelPicker::nameAndDescription));
        select.addClassName("model-select");
        select.bindValue(models.state().map(ModelState::option), option -> models.select(option.ref()));
        select.bindEnabled(Signal.not(locked));

        Paragraph status = new Paragraph();
        status.addClassName("status");
        status.bindText(models.state().map(ModelState::status));

        getContent().addClassName("model-picker");
        getContent().add(select, status);
    }

    private static Div nameAndDescription(ModelOption option) {
        Span description = new Span(option.description());
        description.addClassName("model-option-description");
        Div row = new Div(new Span(option.label()), description);
        row.addClassName("model-option");
        return row;
    }
}
