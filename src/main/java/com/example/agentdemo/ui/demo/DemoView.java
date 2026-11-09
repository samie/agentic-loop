package com.example.agentdemo.ui.demo;

import com.example.agentdemo.ModelService;
import com.example.agentdemo.ModelService.ModelState;
import com.example.agentdemo.harness.Agent;
import com.example.agentdemo.harness.ChatOptions;
import com.example.agentdemo.harness.Llm;
import com.example.agentdemo.ui.demo.settings.AgentConfig;
import com.example.agentdemo.ui.demo.settings.AgentSettingsPanel;
import com.example.agentdemo.ui.demo.settings.LlmSettingsPanel;
import com.example.agentdemo.ui.demo.timeline.ModelInUse;
import com.example.agentdemo.ui.demo.timeline.QuestionRun;
import com.example.agentdemo.ui.demo.timeline.Timeline;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;

public class DemoView extends Composite<VerticalLayout> {

    private final ModelService models;
    private final ValueSignal<Boolean> running = new ValueSignal<>(false);
    private final Timeline timeline = new Timeline();
    private final LlmSettingsPanel llmSettings = new LlmSettingsPanel();
    private final QuestionBox questionBox;
    private final AgentSettingsPanel agentSettings;

    private Llm llm;
    private Agent agent;
    private String systemPromptInUse;

    public DemoView(ModelService models) {
        this.models = models;
        Signal<Boolean> ready = Signal.computed(() ->
                models.state().get().phase() == ModelService.Phase.READY && !running.get());
        questionBox = new QuestionBox(this::ask, this::newConversation, ready, running);
        agentSettings = new AgentSettingsPanel(llmSettings::offerTools);
        llmSettings.bindEditable(Signal.not(running));
        agentSettings.bindEditable(Signal.not(running));

        VerticalLayout layout = getContent();
        layout.setPadding(false);
        layout.setWidthFull();
        layout.add(intro(), new RoleLegend(), new ModelPicker(models, running), questionBox,
                llmSettings, agentSettings, timeline);

        Signal.effect(layout, () -> useModel(models.state().get()));
    }

    private void useModel(ModelState state) {
        Llm loaded = state.phase() == ModelService.Phase.READY ? models.llm() : null;
        if (loaded == llm) return;
        llm = loaded;
        agent = null;
        llmSettings.modelChanged(llm, state.option().label());
        if (llm != null) newConversation();
    }

    private void newConversation() {
        AgentConfig config = agentSettings.config();
        systemPromptInUse = config.systemPrompt();
        agent = new Agent(llm, systemPromptInUse, config.tools(llm), config.maxSteps());
        timeline.clear();
    }

    private void ask(String question) {
        if (agent == null) return;
        AgentConfig config = agentSettings.config();
        agent.tools(config.tools(llm));
        agent.maxSteps(config.maxSteps());
        if (!config.systemPrompt().equals(systemPromptInUse)) {
            systemPromptInUse = config.systemPrompt();
            agent.systemPrompt(systemPromptInUse);
            timeline.systemPromptEdited();
        }
        ChatOptions options = llmSettings.options(config.stream());
        ModelService.ModelOption option = models.state().peek().option();
        ModelInUse model = new ModelInUse(option.ref(), llm.recommended(), option.remote());
        QuestionRun run = timeline.startQuestion(question, model);
        running.set(true);
        runTheLoopInTheBackground(agent, question, options, run);
    }

    private void runTheLoopInTheBackground(Agent loop, String question, ChatOptions options, QuestionRun run) {
        Thread.ofVirtual().start(() -> {
            try {
                run.finish(loop.run(question, options, run));
            } catch (RuntimeException e) {
                run.fail(e);
            } finally {
                running.set(false);
            }
        });
    }

    private static Paragraph intro() {
        Paragraph intro = new Paragraph("""
                Ask something that needs a tool, like arithmetic or the current time. The model \
                can't compute or look anything up itself: it can only ask the agent (your code) \
                to run a tool. Below, every step of the loop appears in order, with the data \
                that moves between the agent, the model and the tools, exactly as it is.""");
        intro.addClassName("intro");
        return intro;
    }
}
