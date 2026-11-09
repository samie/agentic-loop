package com.example.agentdemo.ui;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Svg;
import com.vaadin.flow.component.html.Div;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class LoopDiagram extends Composite<Div> {

    private static final String DIAGRAM_RESOURCE = "/META-INF/resources/agentic-loop.svg";
    private static final String DIAGRAM = readDiagram();

    public LoopDiagram() {
        Div diagram = getContent();
        diagram.addClassName("loop-diagram");
        diagram.getElement().setAttribute("role", "img");
        diagram.getElement().setAttribute("aria-label", "The agentic loop: user, agent, LLM, tools");
        diagram.add(new Svg(DIAGRAM));
    }

    public void highlight(LoopStage stage) {
        getContent().getElement().setAttribute("data-highlight", stage.diagramKey());
    }

    private static String readDiagram() {
        try (InputStream in = LoopDiagram.class.getResourceAsStream(DIAGRAM_RESOURCE)) {
            if (in == null) throw new IllegalStateException(DIAGRAM_RESOURCE + " is missing from the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
