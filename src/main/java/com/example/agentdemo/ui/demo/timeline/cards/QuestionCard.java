package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.ui.LoopRole;
import com.example.agentdemo.ui.LoopStage;
import com.example.agentdemo.wire.OpenAiJson;
import com.qxotic.jinfer.chat.Message;
import java.util.List;

public class QuestionCard extends LoopCard {

    public QuestionCard(String question) {
        super(LoopRole.USER, LoopStage.QUESTION, "Your question", "user → agent");
        explain("The agent adds it to the conversation as a user message. The conversation "
                + "already starts with the system prompt, and keeps every earlier turn.");
        addContent(dataBlock(question),
                dataLabel("As a message (OpenAI Chat Completions JSON)"),
                dataBlock(OpenAiJson.message(List.of(Message.user(question)), 0)));
    }
}
