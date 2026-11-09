package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.harness.ChatResponse;
import com.example.agentdemo.ui.LoopRole;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

public class StepStatsCard extends LoopCard {

    public StepStatsCard(int step, long stepNanos, long elapsedNanos, ChatResponse.Usage usage) {
        super(LoopRole.AGENT, "⏱ Loop step " + step + " took " + duration(stepNanos),
                "🕒 Elapsed time " + duration(elapsedNanos));
        compact();
        if (usage == null) return;
        long modelNanos = usage.modelTime().toNanos();
        explain("Model " + duration(modelNanos) + " · agent and tools " + duration(Math.max(0, stepNanos - modelNanos)));
        addContent(
                statsLine("→", "In to the model: " + usage.promptTokens() + " tokens", usage.cachedTokens() > 0
                        ? usage.cachedTokens() + " reused from the previous call, the rest read in "
                                + duration(usage.promptTime().toNanos())
                        : "read in " + duration(usage.promptTime().toNanos())),
                statsLine("←", "Out of the model: " + usage.outputTokens() + " tokens",
                        (usage.thinkingTokens() > 0 ? usage.thinkingTokens() + " of them thinking, " : "")
                                + String.format("%.1f tokens/s", usage.tokensPerSecond())));
    }

    private static Div statsLine(String arrow, String what, String detail) {
        Span arrowSpan = new Span(arrow);
        arrowSpan.addClassName("stats-arrow");
        Span detailSpan = new Span(detail);
        detailSpan.addClassName("stats-detail");
        Div line = new Div(arrowSpan, new Span(what), detailSpan);
        line.addClassName("stats-line");
        return line;
    }
}
