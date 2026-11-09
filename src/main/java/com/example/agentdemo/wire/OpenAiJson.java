package com.example.agentdemo.wire;

import com.example.agentdemo.harness.ChatOptions;
import com.example.agentdemo.harness.ChatResponse;
import com.example.agentdemo.harness.Tool;
import com.qxotic.jinfer.chat.Content;
import com.qxotic.jinfer.chat.Message;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The loop's data in the OpenAI Chat Completions JSON shape, the format most hosted LLM APIs use.
 * {@link com.example.agentdemo.harness.OpenAiLlm} sends exactly this to an OpenAI-compatible API;
 * for a model running in this JVM it is the same data, written the way it would look on the wire.
 */
public final class OpenAiJson {

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    private static final JsonMapper COMPACT = JsonMapper.builder().build();

    private OpenAiJson() {}

    /**
     * The complete request body: model, every message so far, the tool schemas, and the settings
     * the model actually uses for this reply.
     */
    public static String request(String model, List<Message> conversation, List<Tool> tools, ChatOptions effective) {
        return JSON.writeValueAsString(requestBody(model, conversation, tools, effective));
    }

    /** The same request body as a JSON tree, ready to send. */
    public static ObjectNode requestBody(String model, List<Message> conversation, List<Tool> tools, ChatOptions options) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.putArray("messages").addAll(messages(conversation));
        settings(body, options);
        if (tools.isEmpty()) return body; // APIs reject tool_choice without tools
        toolChoice(body, options.toolChoice());
        ArrayNode toolsJson = body.putArray("tools");
        for (Tool tool : tools) {
            ObjectNode function = toolsJson.addObject().put("type", "function").putObject("function");
            function.put("name", tool.name()).put("description", tool.description());
            function.set("parameters", JSON.valueToTree(tool.parameters()));
        }
        return body;
    }

    /**
     * What is new since the previous request: the messages from index {@code from} on, plus the
     * system message when it was changed in between.
     */
    public static String newMessages(List<Message> conversation, int from, boolean systemChanged) {
        List<ObjectNode> all = messages(conversation);
        ArrayNode added = JSON.createArrayNode();
        if (systemChanged && from > 0) added.add(all.getFirst());
        added.addAll(all.subList(Math.min(from, all.size()), all.size()));
        return JSON.writeValueAsString(added);
    }

    // The generation settings, as OpenAI-compatible servers name them. top_k and min_p are
    // llama.cpp / vLLM extensions; chat_template_kwargs is how those servers switch thinking on.
    private static void settings(ObjectNode body, ChatOptions o) {
        body.put("stream", o.stream());
        if (o.stream()) body.putObject("stream_options").put("include_usage", true); // token counts in the last chunk
        if (o.temperature() != null) body.put("temperature", o.temperature());
        if (o.topP() != null) body.put("top_p", o.topP());
        if (o.topK() != null) body.put("top_k", o.topK());
        if (o.minP() != null) body.put("min_p", o.minP());
        if (o.seed() != null) body.put("seed", o.seed());
        if (o.maxOutputTokens() != null) body.put("max_tokens", o.maxOutputTokens());
        if (o.thinking()) body.putObject("chat_template_kwargs").put("enable_thinking", true);
    }

    private static void toolChoice(ObjectNode body, ChatOptions.ToolChoice choice) {
        switch (choice) {
            case ChatOptions.ToolChoice.Auto a -> body.put("tool_choice", "auto");
            case ChatOptions.ToolChoice.Required r -> body.put("tool_choice", "required");
            case ChatOptions.ToolChoice.Named n -> body.putObject("tool_choice")
                    .put("type", "function").putObject("function").put("name", n.tool());
        }
    }

    /** One message, e.g. the user's question as it enters the conversation. */
    public static String message(List<Message> conversation, int index) {
        return JSON.writeValueAsString(messages(conversation).get(index));
    }

    /** The response body for one step, given the conversation the model was answering. */
    public static String response(List<Message> conversation, ChatResponse response) {
        List<Message> withReply = new ArrayList<>(conversation);
        withReply.add(response.reply());
        ObjectNode message = messages(withReply).getLast();
        if (!response.reasoning().isEmpty()) {
            // returned by thinking models next to the answer; never sent back in a request
            message.put("reasoning_content", response.reasoning());
        }
        ObjectNode body = JSON.createObjectNode();
        ObjectNode choice = body.putArray("choices").addObject();
        choice.put("index", 0).put("finish_reason", response.finishReason());
        choice.set("message", message);
        return JSON.writeValueAsString(body);
    }

    /** A tool call's arguments as the JSON object the model produced. */
    public static String arguments(Content.ToolCall call) {
        return JSON.writeValueAsString(call.arguments());
    }

    /**
     * Converts every message, giving id-less tool calls positional ids ("call_0", "call_1", ...)
     * and matching each tool result to the call it answers, in order.
     */
    static List<ObjectNode> messages(List<Message> conversation) {
        List<ObjectNode> out = new ArrayList<>();
        Deque<String> unanswered = new ArrayDeque<>();
        int minted = 0;
        for (Message message : conversation) {
            ObjectNode json = JSON.createObjectNode().put("role", message.role().name());
            StringBuilder text = new StringBuilder();
            List<Content.ToolCall> calls = new ArrayList<>();
            String toolCallId = null;
            collect(message.content(), text, calls);
            for (Content part : message.content()) {
                if (part instanceof Content.ToolResult result) {
                    toolCallId = !result.callId().isEmpty() ? result.callId()
                            : unanswered.isEmpty() ? "" : unanswered.removeFirst();
                }
            }
            if (toolCallId != null) json.put("tool_call_id", toolCallId);

            String content = text.toString().strip();
            if (content.isEmpty() && !calls.isEmpty()) json.putNull("content");
            else json.put("content", content);

            if (!calls.isEmpty()) {
                ArrayNode toolCalls = json.putArray("tool_calls");
                for (Content.ToolCall call : calls) {
                    String id = call.id().isEmpty() ? "call_" + minted++ : call.id();
                    unanswered.addLast(id);
                    ObjectNode c = toolCalls.addObject().put("id", id).put("type", "function");
                    c.putObject("function")
                            .put("name", call.name())
                            .put("arguments", COMPACT.writeValueAsString(call.arguments())); // a JSON *string*
                }
            }
            out.add(json);
        }
        return out;
    }

    // Answer text and tool calls; reasoning text is left out (it is never sent back to the model).
    private static void collect(List<Content> parts, StringBuilder text, List<Content.ToolCall> calls) {
        for (Content part : parts) {
            switch (part) {
                case Content.Text t -> text.append(t.text());
                case Content.Reasoning r -> r.content().stream()
                        .filter(c -> c instanceof Content.ToolCall)
                        .forEach(c -> calls.add((Content.ToolCall) c));
                case Content.ToolCall call -> calls.add(call);
                case Content.ToolResult result -> text.append(result.text());
                default -> {}
            }
        }
    }
}
