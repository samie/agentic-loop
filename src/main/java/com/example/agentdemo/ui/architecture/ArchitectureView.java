package com.example.agentdemo.ui.architecture;

import com.example.agentdemo.ui.LoopDiagram;
import com.example.agentdemo.ui.LoopStage;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class ArchitectureView extends Composite<VerticalLayout> {

    private final LoopStepsList steps = new LoopStepsList();

    public ArchitectureView() {
        VerticalLayout layout = getContent();
        layout.setPadding(false);
        layout.setWidthFull();
        layout.addClassName("architecture");
        layout.add(
                new LoopDiagram(),
                new H3("The loop, step by step"),
                new Markdown("""
                        The **agent** is your code. It owns the conversation and the tools, and it runs \
                        the loop. The **LLM** only turns text into text. The **tools** are plain functions \
                        that the agent runs when the model asks for them."""),
                steps,
                new Markdown("""
                        In this app the LLM is not an OpenAI-compatible server but a model running inside \
                        this JVM with Quixotic jinfer. The loop and the data are the same; the Demo tab shows \
                        each step both as OpenAI JSON and as the model's native text. The tools are \
                        `calculator`, `current_time` and `delegate`."""),
                new H3("How tool calls look"),
                new Markdown("""
                        A model has no special "tool" channel: a tool call is **text it learned to write**. \
                        The chat template lists the tool schemas in the prompt, and the model answers in its \
                        own syntax. The runtime (here jinfer, elsewhere llama.cpp or vLLM) recognizes that text \
                        and turns it into structured calls."""),
                new NativeToolCallTable(),
                new Markdown("""
                        Developer-facing APIs hide those differences behind JSON. The OpenAI shape is the \
                        most common one; the Demo tab uses it."""),
                new ApiToolCallTable());
    }

    public void showStep(LoopStage stage) {
        steps.showStep(stage);
    }
}
