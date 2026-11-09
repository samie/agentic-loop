package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.harness.ChatResponse;
import com.example.agentdemo.ui.LoopRole;
import com.example.agentdemo.wire.OpenAiJson;
import com.qxotic.jinfer.chat.Message;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.markdown.Markdown;
import java.util.List;

public class ResponseCard extends LoopCard {

    private final boolean streamed;
    private Markdown answer;

    public ResponseCard(boolean streamed) {
        super(LoopRole.LLM, "Response", "LLM → agent");
        this.streamed = streamed;
    }

    public void appendAnswer(String delta) {
        if (answer == null) {
            if (delta == null || delta.isBlank()) return;
            answer = new Markdown();
            addContent(dataLabel(streamed ? "Answer text, as it streams"
                    : "Answer text (streaming off: it arrived all at once)"), answer);
            delta = delta.stripLeading();
        }
        answer.appendContent(delta);
    }

    public void complete(ChatResponse response, List<Message> requestConversation) {
        explain(whatTheModelDid(response));
        addContent(formatTabs(
                "OpenAI JSON", dataBlock(OpenAiJson.response(requestConversation, response)),
                "Native output", new Div(
                        dataLabel("Exactly what the model wrote, special tokens included"),
                        dataBlock(response.nativeOutput() != null ? response.nativeOutput()
                                : "Not visible: the API parses the model's output on its server and returns only JSON."))));
    }

    private static String whatTheModelDid(ChatResponse response) {
        return switch (response.finishReason()) {
            case "tool_calls" -> "The model asks the agent to run " + plural(response.toolCalls().size(), "tool")
                    + ". It can't run them itself: a tool call is just structured output. "
                    + "finish_reason is \"tool_calls\".";
            case "stop" -> "The model answers in plain text and asks for no tools. finish_reason is \"stop\".";
            default -> "The model stopped for another reason: finish_reason is \"" + response.finishReason() + "\".";
        };
    }
}
