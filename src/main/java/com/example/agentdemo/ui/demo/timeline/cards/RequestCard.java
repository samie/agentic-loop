package com.example.agentdemo.ui.demo.timeline.cards;

import com.example.agentdemo.harness.ChatOptions;
import com.example.agentdemo.harness.Tool;
import com.example.agentdemo.ui.LoopRole;
import com.example.agentdemo.ui.LoopStage;
import com.example.agentdemo.ui.demo.timeline.ModelInUse;
import com.example.agentdemo.ui.demo.timeline.RequestHistory;
import com.example.agentdemo.wire.OpenAiJson;
import com.qxotic.jinfer.chat.Message;
import com.vaadin.flow.component.html.Div;
import java.util.ArrayList;
import java.util.List;

public class RequestCard extends LoopCard {

    private final Div nativePrompt = new Div();

    public RequestCard(int step, List<Message> conversation, List<Tool> tools, ChatOptions options,
                       ModelInUse model, RequestHistory history) {
        super(LoopRole.AGENT, LoopStage.REQUEST, "Request", "agent → LLM");
        nativePrompt.add(dataBlock(model.remote()
                ? "The API applies the chat template on its server, so the native prompt isn't visible here: "
                        + "only the JSON goes over the wire."
                : "The exact prompt appears here once the model has read it."));
        int previousSize = history.previousRequestSize();
        boolean systemPromptEdited = history.systemPromptEditedSincePreviousRequest();
        int added = conversation.size() - previousSize + (systemPromptEdited ? 1 : 0);

        explain("The agent sends the whole conversation (" + conversation.size() + " messages) "
                + "and the " + tools.size() + " tool schemas. The model keeps no memory between "
                + "calls, so every request repeats everything. New this time: "
                + whatIsNew(step, previousSize, systemPromptEdited) + "."
                + settingsThatDifferFromTheModel(options, model.recommended()));
        Div openAiJson = newPartWithFullToggle(
                "New since the last call (" + plural(added, "message") + ")",
                OpenAiJson.newMessages(conversation, previousSize, systemPromptEdited),
                "Full request: all " + conversation.size() + " messages, " + tools.size() + " tool schemas and the settings",
                OpenAiJson.request(model.ref(), conversation, tools, options.orElse(model.recommended())));
        addContent(formatTabs("OpenAI JSON", openAiJson, "Native prompt", nativePrompt));
    }

    public void showNativePrompt(String prompt, String previousPrompt) {
        int unchanged = previousPrompt == null ? 0 : commonPrefixLength(previousPrompt, prompt);
        nativePrompt.removeAll();
        if (unchanged == 0) {
            nativePrompt.add(dataLabel("The whole prompt, as the model reads it (special tokens included)"),
                    dataBlock(prompt));
        } else {
            nativePrompt.add(newPartWithFullToggle(
                    "New since the last call, as the model reads it (special tokens included)",
                    prompt.substring(unchanged),
                    "Full prompt (" + prompt.length() + " characters)",
                    prompt));
        }
    }

    private static String whatIsNew(int step, int previousSize, boolean systemPromptEdited) {
        String what = previousSize == 0 ? "the system prompt and your question"
                : step == 1 ? "the earlier conversation plus your new question"
                : "the model's previous reply and the tool results";
        if (systemPromptEdited) what += ". The system prompt was edited, so the first message differs and "
                + "the model has to re-read everything (nothing can be reused from the cache)";
        return what;
    }

    private static String settingsThatDifferFromTheModel(ChatOptions o, ChatOptions defaults) {
        List<String> notes = new ArrayList<>();
        if (!o.stream()) notes.add("streaming off");
        if (o.thinking()) notes.add("thinking on");
        addIfSet(notes, "temperature", o.temperature(), defaults.temperature());
        addIfSet(notes, "top_p", o.topP(), defaults.topP());
        addIfSet(notes, "top_k", o.topK(), defaults.topK());
        addIfSet(notes, "min_p", o.minP(), defaults.minP());
        addIfSet(notes, "seed", o.seed(), null);
        addIfSet(notes, "max_tokens", o.maxOutputTokens(), defaults.maxOutputTokens());
        addIfSet(notes, "max thinking tokens", o.maxThinkingTokens(), null);
        if (o.timeout() != null) notes.add("timeout " + o.timeout().toSeconds() + " s");
        switch (o.toolChoice()) {
            case ChatOptions.ToolChoice.Required r -> notes.add("tool_choice \"required\" (step 1 only)");
            case ChatOptions.ToolChoice.Named n -> notes.add("tool_choice " + n.tool() + " (step 1 only)");
            default -> {}
        }
        return notes.isEmpty() ? "" : " Settings: " + String.join(", ", notes) + ".";
    }

    private static void addIfSet(List<String> notes, String name, Object value, Object modelDefault) {
        if (value == null) return;
        notes.add(name + " " + value + (modelDefault == null ? "" : " (model default " + modelDefault + ")"));
    }

    private static int commonPrefixLength(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }
}
