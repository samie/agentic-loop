package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.ui.LoopDiagram;
import com.example.agentdemo.ui.LoopStage;
import com.example.agentdemo.ui.MainView;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.markdown.Markdown;

public class StageOverview extends Composite<Div> {

    public StageOverview(LoopStage stage, Runnable onLeave) {
        LoopDiagram diagram = new LoopDiagram();
        diagram.highlight(stage);

        Button readMore = new Button("Read more in the Architecture tab →", e -> {
            onLeave.run();
            MainView.showArchitecture(stage);
        });
        readMore.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);

        Div header = new Div(new H4(stage.badge() + " " + stage.title()), readMore);
        header.addClassName("stage-overview-header");

        Div overview = getContent();
        overview.addClassName("stage-overview");
        overview.add(header, new Markdown(stage.explanationMarkdown()), diagram);
    }
}
