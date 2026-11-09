package com.example.agentdemo.ui.demo.settings;

import com.example.agentdemo.harness.ChatOptions;
import com.example.agentdemo.harness.Llm;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.signals.Signal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LlmSettingsPanel extends Composite<Details> {

    private static final String AUTO = "auto";
    private static final String REQUIRED = "required";
    private static final String CAN_THINK_HELP = "Let the model reason before each step: slower, but shows its thoughts.";

    private final Checkbox think = new Checkbox("Think");
    private final IntegerField maxThinkingTokens = new IntegerField("Max thinking tokens");
    private final NumberField temperature = new NumberField("Temperature");
    private final NumberField topP = new NumberField("Top-p");
    private final IntegerField topK = new IntegerField("Top-k");
    private final NumberField minP = new NumberField("Min-p");
    private final IntegerField seed = new IntegerField("Seed");
    private final IntegerField maxOutputTokens = new IntegerField("Max output tokens");
    private final IntegerField timeoutSeconds = new IntegerField("Timeout (seconds)");
    private final Select<String> toolChoice = new Select<>();

    private Set<String> offeredTools = Set.copyOf(AgentConfig.TOOL_NAMES);
    private Llm llm;

    public LlmSettingsPanel() {
        think.setHelperText(CAN_THINK_HELP);
        think.addValueChangeListener(e -> enableWhatTheModelSupports());
        maxThinkingTokens.setHelperText("Caps the thinking part of each reply.");
        maxThinkingTokens.setMin(16);

        temperature.setHelperText("Randomness. Lower = more predictable; tool calls usually work best low.");
        range(temperature, 0, 2, 0.05);
        topP.setHelperText("Pick only from the likeliest tokens that together reach this probability.");
        range(topP, 0, 1, 0.05);
        topK.setHelperText("Pick only from this many likeliest tokens (0 = no limit).");
        topK.setMin(0);
        minP.setHelperText("Drop tokens less likely than this fraction of the likeliest one.");
        range(minP, 0, 1, 0.01);
        seed.setHelperText("Same seed + same settings = same output. Empty = random.");
        seed.setPlaceholder("random");

        maxOutputTokens.setHelperText("The reply stops after this many tokens (finish_reason \"length\").");
        maxOutputTokens.setMin(16);
        timeoutSeconds.setHelperText("Give up on a reply that takes longer.");
        timeoutSeconds.setMin(1);
        timeoutSeconds.setPlaceholder("none");
        toolChoice.setLabel("Tool choice");
        toolChoice.setHelperText("Forces a tool call in loop step 1 only; later steps are \"auto\", "
                + "or the model could never answer.");
        toolChoice.setItemLabelGenerator(LlmSettingsPanel::toolChoiceLabel);

        Button reset = new Button("Reset LLM settings", e -> reset());
        reset.addThemeVariants(ButtonVariant.TERTIARY);

        Details panel = getContent();
        panel.setSummaryText("LLM settings");
        panel.setWidthFull();
        panel.add(
                new SettingsSection("Thinking").withField(think, 1).withField(maxThinkingTokens, 1),
                new SettingsSection("Sampling").withField(temperature, 1).withField(topP, 1).withField(topK, 1)
                        .withField(minP, 1).withField(seed, 1),
                new SettingsSection("Limits and tool choice").withField(maxOutputTokens, 1)
                        .withField(timeoutSeconds, 1).withField(toolChoice, 1),
                reset);
        reset();
    }

    public ChatOptions options(boolean stream) {
        return new ChatOptions(
                think.getValue(),
                stream,
                toFloat(temperature.getValue()), toFloat(topP.getValue()), topK.getValue(), toFloat(minP.getValue()),
                seed.getValue() == null ? null : seed.getValue().longValue(),
                maxOutputTokens.getValue(),
                think.getValue() ? maxThinkingTokens.getValue() : null,
                timeoutSeconds.getValue() == null ? null : Duration.ofSeconds(timeoutSeconds.getValue()),
                selectedToolChoice());
    }

    public void modelChanged(Llm llm, String modelLabel) {
        this.llm = llm;
        if (llm == null) return;
        ChatOptions recommended = llm.recommended();
        temperature.setPlaceholder(modelDefault(recommended.temperature()));
        topP.setPlaceholder(modelDefault(recommended.topP()));
        topK.setPlaceholder(modelDefault(recommended.topK()));
        minP.setPlaceholder(modelDefault(recommended.minP()));
        maxOutputTokens.setPlaceholder(modelDefault(recommended.maxOutputTokens()));
        maxThinkingTokens.setPlaceholder("model default");
        think.setValue(llm.alwaysThinks());
        think.setHelperText(llm.alwaysThinks() ? modelLabel + " always thinks."
                : llm.canThink() ? CAN_THINK_HELP
                : modelLabel + " has no thinking mode.");
        enableWhatTheModelSupports();
    }

    public void offerTools(Set<String> enabledTools) {
        offeredTools = Set.copyOf(enabledTools);
        String previous = toolChoice.getValue();
        List<String> choices = new ArrayList<>(List.of(AUTO, REQUIRED));
        AgentConfig.TOOL_NAMES.stream().filter(offeredTools::contains).forEach(choices::add);
        toolChoice.setItems(choices);
        toolChoice.setValue(choices.contains(previous) ? previous : AUTO);
    }

    public void bindEditable(Signal<Boolean> editable) {
        getContent().bindEnabled(editable);
    }

    private void reset() {
        for (NumberField field : List.of(temperature, topP, minP)) field.clear();
        for (IntegerField field : List.of(topK, seed, maxOutputTokens, maxThinkingTokens, timeoutSeconds)) field.clear();
        think.setValue(llm != null && llm.alwaysThinks());
        offerTools(offeredTools);
        toolChoice.setValue(AUTO);
        enableWhatTheModelSupports();
    }

    private void enableWhatTheModelSupports() {
        boolean canThink = llm != null && llm.canThink();
        think.setEnabled(canThink && !llm.alwaysThinks());
        maxThinkingTokens.setEnabled(canThink && think.getValue());
    }

    private ChatOptions.ToolChoice selectedToolChoice() {
        String choice = toolChoice.getValue();
        if (choice == null || choice.equals(AUTO)) return ChatOptions.ToolChoice.AUTO;
        if (choice.equals(REQUIRED)) return ChatOptions.ToolChoice.REQUIRED;
        return new ChatOptions.ToolChoice.Named(choice);
    }

    private static String toolChoiceLabel(String choice) {
        return switch (choice) {
            case AUTO -> "auto: the model decides";
            case REQUIRED -> "required: must call a tool";
            default -> "must call " + choice;
        };
    }

    private static void range(NumberField field, double min, double max, double step) {
        field.setMin(min);
        field.setMax(max);
        field.setStep(step);
    }

    private static String modelDefault(Number value) {
        if (value == null) return "model default";
        String text = value instanceof Float || value instanceof Double
                ? String.format(Locale.ROOT, "%.2f", value.doubleValue()).replaceAll("0+$", "").replaceAll("\\.$", "")
                : value.toString();
        return "model default: " + text;
    }

    private static Float toFloat(Double value) {
        return value == null ? null : value.floatValue();
    }
}
