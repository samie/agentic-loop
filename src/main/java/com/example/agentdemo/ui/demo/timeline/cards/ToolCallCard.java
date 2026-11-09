package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.ui.LoopRole;
import com.example.agentdemo.ui.LoopStage;
import com.example.agentdemo.wire.OpenAiJson;
import com.qxotic.jinfer.chat.Content;

public class ToolCallCard extends LoopCard {

    public ToolCallCard(Content.ToolCall call, String result) {
        super(LoopRole.TOOL, LoopStage.TOOL_CALL, "Tool call: " + call.name(), "agent runs it");
        explain("Your code runs the function the model asked for. The model never executes "
                + "anything; it only produced the name and the arguments.");
        addContent(dataLabel("Arguments, from the model"), dataBlock(OpenAiJson.arguments(call)),
                dataLabel("Result, from your code (always text)"), dataBlock(result));
    }
}
