package com.example.agentdemo.ui.demo.timeline;

import com.example.agentdemo.harness.Agent;
import com.example.agentdemo.harness.ChatOptions;
import com.example.agentdemo.harness.ChatResponse;
import com.example.agentdemo.harness.Tool;
import com.example.agentdemo.ui.demo.timeline.cards.DecisionCard;
import com.example.agentdemo.ui.demo.timeline.cards.ErrorCard;
import com.example.agentdemo.ui.demo.timeline.cards.FinalAnswerCard;
import com.example.agentdemo.ui.demo.timeline.cards.LoopCard;
import com.example.agentdemo.ui.demo.timeline.cards.LoopSummaryCard;
import com.example.agentdemo.ui.demo.timeline.cards.ObservationCard;
import com.example.agentdemo.ui.demo.timeline.cards.QuestionCard;
import com.example.agentdemo.ui.demo.timeline.cards.RequestCard;
import com.example.agentdemo.ui.demo.timeline.cards.StepStatsCard;
import com.example.agentdemo.ui.demo.timeline.cards.ToolCallCard;
import com.qxotic.jinfer.chat.Content;
import com.qxotic.jinfer.chat.Message;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import java.util.List;

public class QuestionRun extends Composite<Div> implements Agent.Listener {

    private final UI ui;
    private final ModelInUse model;
    private final RequestHistory history;
    private final LoopStatistics statistics = new LoopStatistics();
    private final long questionStart = System.nanoTime();

    private LoopStepSection step;
    private RequestCard request;
    private List<Message> requestConversation = List.of();
    private int openStep;
    private long stepStart;
    private ChatResponse stepResponse;

    QuestionRun(UI ui, int number, String question, ModelInUse model, RequestHistory history) {
        this.ui = ui;
        this.model = model;
        this.history = history;
        getContent().addClassName("question-run");
        getContent().add(Timeline.heading("Question " + number));
        showInQuestion(new QuestionCard(question));
    }

    @Override
    public void onRequest(int stepNumber, List<Message> conversation, List<Tool> tools, ChatOptions options) {
        long now = System.nanoTime();
        ui.access(() -> {
            closeStep(now);
            openStep(stepNumber, now, conversation, tools, options);
        });
    }

    @Override
    public void onReasoning(String delta) {
        ui.access(() -> step.appendThinking(delta));
    }

    @Override
    public void onContent(String delta) {
        ui.access(() -> step.appendAnswer(delta));
    }

    @Override
    public void onResponse(int stepNumber, ChatResponse response) {
        ui.access(() -> {
            stepResponse = response;
            step.stopWaiting();
            if (!step.streaming()) {
                step.appendThinking(response.reasoning());
                step.appendAnswer(response.content());
            }
            if (response.nativePrompt() != null) {
                request.showNativePrompt(response.nativePrompt(), history.previousNativePrompt());
                history.promptRead(response.nativePrompt());
            }
            step.response().complete(response, requestConversation);
            step.show(new DecisionCard(response.finishReason()));
        });
    }

    @Override
    public void onToolCall(Content.ToolCall call, String result) {
        ui.access(() -> {
            statistics.toolCalled(call.name());
            step.show(new ToolCallCard(call, result));
            step.show(new ObservationCard());
        });
    }

    public void finish(String answer) {
        long now = System.nanoTime();
        ui.access(() -> {
            closeStep(now);
            showInQuestion(new FinalAnswerCard(answer));
            summarize(now);
        });
    }

    public void fail(RuntimeException error) {
        long now = System.nanoTime();
        ui.access(() -> {
            if (step != null) step.stopWaiting();
            closeStep(now);
            showInQuestion(new ErrorCard(error));
            summarize(now);
        });
    }

    private void openStep(int stepNumber, long now, List<Message> conversation, List<Tool> tools, ChatOptions options) {
        openStep = stepNumber;
        stepStart = now;
        requestConversation = conversation;
        step = new LoopStepSection(stepNumber, options.stream());
        getContent().add(step);
        request = new RequestCard(stepNumber, conversation, tools, options, model, history);
        history.requestSent(conversation.size());
        step.show(request);
        step.waitForTheModel();
    }

    private void closeStep(long now) {
        if (openStep == 0) return;
        long stepNanos = now - stepStart;
        ChatResponse.Usage usage = stepResponse == null ? null : stepResponse.usage();
        String finishReason = stepResponse == null ? "–" : stepResponse.finishReason();
        step.show(new StepStatsCard(openStep, stepNanos, now - questionStart, usage));
        statistics.stepFinished(new LoopStatistics.Step(openStep, finishReason, usage, stepNanos));
        openStep = 0;
        stepResponse = null;
    }

    private void summarize(long now) {
        if (statistics.modelCalls() == 0) return;
        showInQuestion(new LoopSummaryCard(statistics, now - questionStart));
    }

    private void showInQuestion(LoopCard card) {
        getContent().add(card);
        card.scrollIntoView();
    }
}
