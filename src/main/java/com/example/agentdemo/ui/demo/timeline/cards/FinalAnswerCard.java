package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.ui.LoopRole;
import com.example.agentdemo.ui.LoopStage;
import com.vaadin.flow.component.markdown.Markdown;

public class FinalAnswerCard extends LoopCard {

    public FinalAnswerCard(String answer) {
        super(LoopRole.FINAL, LoopStage.FINAL_ANSWER, "Final answer", "agent → user");
        explain("The model's last reply had no tool calls, so the agent shows its content to the user.");
        addContent(new Markdown(answer));
    }
}
