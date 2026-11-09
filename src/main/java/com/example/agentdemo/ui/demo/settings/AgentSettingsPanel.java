package com.example.agentdemo.ui.demo.settings;

import com.example.agentdemo.ui.demo.PresetBar;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.signals.Signal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

public class AgentSettingsPanel extends Composite<Details> {

    private final TextArea systemPrompt = new TextArea();
    private final IntegerField maxSteps = new IntegerField("Max loop steps");
    private final CheckboxGroup<String> tools = new CheckboxGroup<>("Tools the model may call");
    private final Checkbox stream = new Checkbox("Stream the reply");

    public AgentSettingsPanel(Consumer<Set<String>> onToolsChanged) {
        systemPrompt.setHelperText("The first message of every conversation. Edits apply to the next request.");
        systemPrompt.setWidthFull();
        systemPrompt.setAriaLabel("System prompt");
        maxSteps.setHelperText("Model calls per question before the agent gives up.");
        maxSteps.setMin(1);
        maxSteps.setMax(20);
        maxSteps.setStepButtonsVisible(true);
        tools.setItems(AgentConfig.TOOL_NAMES);
        tools.setHelperText("Only these are offered to the model, as JSON schemas in every request.");
        tools.addValueChangeListener(e -> onToolsChanged.accept(e.getValue()));
        stream.setHelperText("Show the reply token by token while the model writes it (OpenAI \"stream\": true). "
                + "Off: nothing appears until the reply is complete.");

        PresetBar<SystemPromptPreset> presets =
                new PresetBar<>("System prompt presets:", SystemPromptPreset.ALL, this::apply);
        Button reset = new Button("Reset agent settings", e -> reset());
        reset.addThemeVariants(ButtonVariant.TERTIARY);

        Details panel = getContent();
        panel.setSummaryText("Agent settings");
        panel.setWidthFull();
        panel.add(
                new SettingsSection("System prompt").withFullWidthField(systemPrompt).withFooter(presets),
                new SettingsSection("Loop and tools").withField(maxSteps, 1).withField(tools, 2).withFullWidthField(stream),
                reset);
        reset();
    }

    public AgentConfig config() {
        return new AgentConfig(
                systemPrompt.getValue().isBlank() ? AgentConfig.DEFAULT_SYSTEM_PROMPT : systemPrompt.getValue(),
                maxSteps.getValue() == null ? AgentConfig.DEFAULT_MAX_STEPS : maxSteps.getValue(),
                Set.copyOf(tools.getValue()),
                stream.getValue());
    }

    public void bindEditable(Signal<Boolean> editable) {
        getContent().bindEnabled(editable);
    }

    private void apply(SystemPromptPreset preset) {
        systemPrompt.setValue(preset.systemPrompt());
        enableAllTools();
    }

    private void reset() {
        systemPrompt.setValue(AgentConfig.DEFAULT_SYSTEM_PROMPT);
        maxSteps.setValue(AgentConfig.DEFAULT_MAX_STEPS);
        enableAllTools();
        stream.setValue(true);
    }

    private void enableAllTools() {
        tools.setValue(new LinkedHashSet<>(AgentConfig.TOOL_NAMES));
    }
}
