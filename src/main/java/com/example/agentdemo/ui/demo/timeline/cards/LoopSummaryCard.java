package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.harness.ChatResponse;
import com.example.agentdemo.ui.LoopRole;
import com.example.agentdemo.ui.demo.timeline.LoopStatistics;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Table;
import com.vaadin.flow.component.html.UnorderedList;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class LoopSummaryCard extends LoopCard {

    public LoopSummaryCard(LoopStatistics statistics, long elapsedNanos) {
        super(LoopRole.LLM, "Loop summary: the LLM statistics of this question",
                plural(statistics.modelCalls(), "model call") + " · " + plural(statistics.toolCalls(), "tool call"));
        long modelNanos = statistics.modelTime().toNanos();
        explain("The whole question took " + duration(elapsedNanos) + ": the model worked for "
                + duration(modelNanos) + ", the agent and the tools took "
                + duration(Math.max(0, elapsedNanos - modelNanos)) + ".");
        if (!statistics.modelReportedUsage()) {
            addContent(dataLabel("The model reported no token counts or timings."));
            return;
        }
        addContent(perStepTable(statistics), highlights(statistics));
    }

    private static Div perStepTable(LoopStatistics statistics) {
        Table table = new Table();
        table.addClassName("summary-table");
        table.addHeaderRow("Step", "finish_reason", "Tokens in", "Reused", "Tokens out", "Thinking",
                "Read time", "Write time", "Out tokens/s", "Step time");
        for (LoopStatistics.Step step : statistics.steps()) table.addRow(stepCells(step));
        table.addFooterRow("Total", "",
                tokens(statistics.promptTokens()), tokens(statistics.cachedTokens()),
                tokens(statistics.outputTokens()), tokens(statistics.thinkingTokens()),
                time(statistics.promptTime()), time(statistics.outputTime()),
                rate(statistics.outputTokensPerSecond()), duration(statistics.stepNanos()));
        Div scrollable = new Div(table);
        scrollable.addClassName("summary-table-scroller");
        return scrollable;
    }

    private static List<String> stepCells(LoopStatistics.Step step) {
        ChatResponse.Usage usage = step.usage();
        List<String> cells = new ArrayList<>(List.of(String.valueOf(step.number()), step.finishReason()));
        if (usage == null) {
            cells.addAll(List.of("–", "–", "–", "–", "–", "–", "–"));
        } else {
            cells.addAll(List.of(tokens(usage.promptTokens()), tokens(usage.cachedTokens()),
                    tokens(usage.outputTokens()), tokens(usage.thinkingTokens()),
                    time(usage.promptTime()), time(usage.outputTime()), rate(usage.tokensPerSecond())));
        }
        cells.add(duration(step.nanos()));
        return cells;
    }

    private static UnorderedList highlights(LoopStatistics statistics) {
        UnorderedList list = new UnorderedList(
                new ListItem("Prompt tokens: " + tokens(statistics.promptTokens()) + " sent in total, "
                        + tokens(statistics.cachedTokens()) + " of them ("
                        + String.format("%.0f%%", statistics.cachedShare() * 100)
                        + ") reused from the previous call. The other " + tokens(statistics.readTokens())
                        + " were read at " + rate(statistics.readTokensPerSecond()) + " tokens/s."),
                new ListItem("Largest prompt: " + tokens(statistics.largestPrompt()) + " tokens, the conversation "
                        + "at its longest. Every request repeats the whole conversation, so the totals grow "
                        + "with each step."),
                new ListItem("Generated: " + tokens(statistics.outputTokens()) + " tokens"
                        + (statistics.thinkingTokens() > 0 ? ", " + tokens(statistics.thinkingTokens()) + " of them thinking," : "")
                        + " at " + rate(statistics.outputTokensPerSecond()) + " tokens/s on average."));
        if (statistics.delegations() > 0) {
            list.add(new ListItem(plural(statistics.delegations(), "delegate call") + " ran a sub-agent with its "
                    + "own model calls. Those run in their own loop and are not counted here."));
        }
        list.addClassName("summary-highlights");
        return list;
    }

    private static String tokens(int count) {
        return String.format("%,d", count);
    }

    private static String time(Duration time) {
        return duration(time.toNanos());
    }

    private static String rate(double perSecond) {
        return String.format("%.1f", perSecond);
    }
}
