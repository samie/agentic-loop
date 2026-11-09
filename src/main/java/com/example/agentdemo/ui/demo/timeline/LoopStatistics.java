package com.example.agentdemo.ui.demo.timeline;

import com.example.agentdemo.harness.ChatResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

public final class LoopStatistics {

    public record Step(int number, String finishReason, ChatResponse.Usage usage, long nanos) {}

    private final List<Step> steps = new ArrayList<>();
    private int toolCalls;
    private int delegations;

    void stepFinished(Step step) {
        steps.add(step);
    }

    void toolCalled(String name) {
        toolCalls++;
        if (name.equals("delegate")) delegations++;
    }

    public List<Step> steps() {
        return List.copyOf(steps);
    }

    public int modelCalls() {
        return steps.size();
    }

    public int toolCalls() {
        return toolCalls;
    }

    public int delegations() {
        return delegations;
    }

    public boolean modelReportedUsage() {
        return steps.stream().anyMatch(step -> step.usage() != null);
    }

    public int promptTokens() {
        return sum(ChatResponse.Usage::promptTokens);
    }

    public int cachedTokens() {
        return sum(ChatResponse.Usage::cachedTokens);
    }

    public int readTokens() {
        return promptTokens() - cachedTokens();
    }

    public int outputTokens() {
        return sum(ChatResponse.Usage::outputTokens);
    }

    public int thinkingTokens() {
        return sum(ChatResponse.Usage::thinkingTokens);
    }

    public int largestPrompt() {
        return usages().mapToInt(ChatResponse.Usage::promptTokens).max().orElse(0);
    }

    public Duration promptTime() {
        return usages().map(ChatResponse.Usage::promptTime).reduce(Duration.ZERO, Duration::plus);
    }

    public Duration outputTime() {
        return usages().map(ChatResponse.Usage::outputTime).reduce(Duration.ZERO, Duration::plus);
    }

    public Duration modelTime() {
        return promptTime().plus(outputTime());
    }

    public long stepNanos() {
        return steps.stream().mapToLong(Step::nanos).sum();
    }

    public double readTokensPerSecond() {
        return perSecond(readTokens(), promptTime());
    }

    public double outputTokensPerSecond() {
        return perSecond(outputTokens(), outputTime());
    }

    public double cachedShare() {
        return promptTokens() == 0 ? 0 : (double) cachedTokens() / promptTokens();
    }

    private Stream<ChatResponse.Usage> usages() {
        return steps.stream().map(Step::usage).filter(Objects::nonNull);
    }

    private int sum(ToIntFunction<ChatResponse.Usage> tokens) {
        return usages().mapToInt(tokens).sum();
    }

    private static double perSecond(int tokens, Duration time) {
        double seconds = time.toNanos() / 1e9;
        return seconds > 0 ? tokens / seconds : 0;
    }
}
