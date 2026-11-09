package com.example.agentdemo.ui.demo.settings;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;

public class SettingsSection extends Composite<Div> {

    private static final int COLUMNS = 3;

    private final FormLayout form = new FormLayout();

    public SettingsSection(String title) {
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", COLUMNS));
        getContent().addClassName("settings-section");
        getContent().add(new H4(title), form);
    }

    public SettingsSection withField(Component field, int columns) {
        form.add(field, columns);
        return this;
    }

    public SettingsSection withFullWidthField(Component field) {
        return withField(field, COLUMNS);
    }

    public SettingsSection withFooter(Component footer) {
        getContent().add(footer);
        return this;
    }
}
