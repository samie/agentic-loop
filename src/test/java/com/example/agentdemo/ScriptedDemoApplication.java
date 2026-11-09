package com.example.agentdemo;

import com.example.agentdemo.harness.Agent;
import com.example.agentdemo.harness.ChatOptions;
import com.example.agentdemo.harness.ChatResponse;
import com.example.agentdemo.harness.JinferLlm;
import com.example.agentdemo.harness.Llm;
import com.example.agentdemo.harness.Tool;
import com.qxotic.jinfer.chat.Content;
import com.qxotic.jinfer.chat.Message;
import com.qxotic.jinfer.chat.Role;
import com.qxotic.jinfer.llm.Generator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

/**
 * Runs the real UI with a scripted model, for looking at the UI without a model download or the
 * Vector API: {@code mvn spring-boot:test-run -Dspring-boot.run.main-class=com.example.agentdemo.ScriptedDemoApplication}.
 * The native text imitates LFM2.5's chat format; it is not produced by a real model.
 */
public class ScriptedDemoApplication {

    public static void main(String[] args) {
        SpringApplication.from(Application::main)
                .with(ScriptedModel.class)
                .run("--spring.main.allow-bean-definition-overriding=true");
    }

    @Configuration
    static class ScriptedModel {
        @Bean
        ModelService modelService() {
            return new ModelService(ModelService.OPTIONS.getFirst().ref(), List.of(), option -> {
                pause(1200); // pretend to load
                return new ScriptedLlm(option.ref());
            });
        }
    }

    /** First asks for the calculator, then answers with the tool's result. */
    static class ScriptedLlm implements Llm {

        private final boolean alwaysThinks;
        private final boolean canThink;
        private int previousPromptTokens;

        ScriptedLlm(String ref) {
            alwaysThinks = ref.contains("Thinking");
            canThink = alwaysThinks || ref.contains("Qwen") || ref.contains("MiniCPM");
        }

        @Override
        public boolean canThink() {
            return canThink;
        }

        @Override
        public ChatOptions recommended() {
            return alwaysThinks || canThink
                    ? new ChatOptions(false, true, 0.6f, 0.95f, 20, 0.0f, null, 1024, null, null, null)
                    : new ChatOptions(false, true, 0.1f, 0.95f, 50, 0.05f, null, 1024, null, null, null);
        }

        @Override
        public boolean alwaysThinks() {
            return alwaysThinks;
        }

        @Override
        public ChatResponse chat(List<Message> conversation, List<Tool> tools, ChatOptions options, Agent.Listener listener) {
            boolean thinking = options.thinking();
            pause(900);
            String thought = "The user wants seconds, so I should multiply with the calculator rather than guess.";
            boolean thinks = alwaysThinks || (canThink && thinking);
            if (thinks) {
                for (String word : thought.split("(?<= )")) {
                    if (options.stream()) listener.onReasoning(word);
                    pause(40);
                }
            }
            Message last = conversation.getLast();
            Message reply;
            String output;
            if (last.role().equals(Role.USER)) {
                reply = new Message(Role.ASSISTANT, List.of(new Content.ToolCall("", "calculator",
                        Map.of("expression", "3*86400 + 7*3600 + 12*60"))));
                output = "<|tool_call_start|>[calculator(expression=\"3*86400 + 7*3600 + 12*60\")]<|tool_call_end|><|im_end|>";
            } else {
                String result = ((Content.ToolResult) last.content().getFirst()).text();
                String answer = "3 days, 7 hours and 12 minutes is **" + result + " seconds**.";
                for (String word : answer.split("(?<= )")) {
                    if (options.stream()) listener.onContent(word);
                    pause(60);
                }
                reply = Message.assistant(answer);
                output = answer + "<|im_end|>";
            }
            if (thinks) { // the reply carries its reasoning, like a real thinking model's
                List<Content> parts = new java.util.ArrayList<>();
                parts.add(new Content.Reasoning(List.of(new Content.Text(thought)), null));
                parts.addAll(reply.content());
                reply = new Message(Role.ASSISTANT, parts);
                output = "<think>" + thought + "</think>" + output;
            }
            String prompt = nativePrompt(conversation, tools);
            int promptTokens = prompt.length() / 4; // roughly 4 characters per token
            ChatResponse.Usage usage = new ChatResponse.Usage(promptTokens, previousPromptTokens,
                    output.length() / 4, 0, Duration.ofMillis(120), Duration.ofMillis(780));
            previousPromptTokens = promptTokens;
            return JinferLlm.toResponse(reply, Generator.FinishReason.STOP)
                    .withNativeText(prompt, output)
                    .withUsage(usage);
        }

        private static String nativePrompt(List<Message> conversation, List<Tool> tools) {
            String toolList = tools.stream()
                    .map(t -> "{\"name\": \"" + t.name() + "\", \"description\": \"" + t.description() + "\", \"parameters\": …}")
                    .collect(Collectors.joining(", "));
            StringBuilder out = new StringBuilder("<|startoftext|>");
            for (Message m : conversation) {
                out.append("<|im_start|>").append(m.role()).append('\n');
                if (m.role().equals(Role.SYSTEM)) {
                    out.append(m.text()).append("\nList of tools: <|tool_list_start|>[").append(toolList).append("]<|tool_list_end|>");
                }
                for (Content part : m.content()) {
                    switch (part) {
                        case Content.Text t when !m.role().equals(Role.SYSTEM) -> out.append(t.text());
                        case Content.ToolCall c -> out.append("<|tool_call_start|>[").append(c.name()).append("(expression=\"")
                                .append(c.arguments().get("expression")).append("\")]<|tool_call_end|>");
                        case Content.ToolResult r -> out.append("<|tool_response_start|>").append(r.text()).append("<|tool_response_end|>");
                        default -> {}
                    }
                }
                out.append("<|im_end|>\n");
            }
            return out.append("<|im_start|>assistant\n").toString();
        }

    }

    static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
