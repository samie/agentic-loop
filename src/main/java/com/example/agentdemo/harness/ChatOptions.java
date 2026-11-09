package com.example.agentdemo.harness;

import java.time.Duration;

/**
 * How the model should generate one reply. A null value means "the model's default".
 *
 * @param thinking          let the model reason before answering (ignored by models that cannot think)
 * @param stream            hand over the reply token by token as it is generated, or only when complete
 * @param temperature       randomness; lower is more predictable
 * @param topP              sample only from the most likely tokens covering this probability mass
 * @param topK              sample only from this many most likely tokens
 * @param minP              drop tokens less likely than this fraction of the most likely one
 * @param seed              same seed and settings give the same output
 * @param maxOutputTokens   stop the reply after this many tokens
 * @param maxThinkingTokens cap the thinking part of the reply
 * @param timeout           give up on the reply after this long
 * @param toolChoice        whether the reply must be a tool call
 */
public record ChatOptions(boolean thinking, boolean stream, Float temperature, Float topP, Integer topK, Float minP,
                          Long seed, Integer maxOutputTokens, Integer maxThinkingTokens,
                          Duration timeout, ToolChoice toolChoice) {

    /** Model defaults everywhere, streaming, no thinking, no forced tool. */
    public static final ChatOptions DEFAULTS =
            new ChatOptions(false, true, null, null, null, null, null, null, null, null, ToolChoice.AUTO);

    public ChatOptions {
        if (toolChoice == null) toolChoice = ToolChoice.AUTO;
    }

    /** These options, with every empty value taken from {@code defaults}: what the model actually uses. */
    public ChatOptions orElse(ChatOptions defaults) {
        return new ChatOptions(thinking, stream,
                temperature != null ? temperature : defaults.temperature,
                topP != null ? topP : defaults.topP,
                topK != null ? topK : defaults.topK,
                minP != null ? minP : defaults.minP,
                seed != null ? seed : defaults.seed,
                maxOutputTokens != null ? maxOutputTokens : defaults.maxOutputTokens,
                maxThinkingTokens != null ? maxThinkingTokens : defaults.maxThinkingTokens,
                timeout != null ? timeout : defaults.timeout,
                toolChoice);
    }

    public ChatOptions withThinking(boolean thinking) {
        return new ChatOptions(thinking, stream, temperature, topP, topK, minP, seed, maxOutputTokens,
                maxThinkingTokens, timeout, toolChoice);
    }

    public ChatOptions withToolChoice(ToolChoice choice) {
        return new ChatOptions(thinking, stream, temperature, topP, topK, minP, seed, maxOutputTokens,
                maxThinkingTokens, timeout, choice);
    }

    /** OpenAI's tool_choice: may the model answer in text, or must it call a tool? */
    public sealed interface ToolChoice {
        /** The model decides. */
        ToolChoice AUTO = new Auto();
        /** The reply must call one of the tools; the model picks which. */
        ToolChoice REQUIRED = new Required();

        record Auto() implements ToolChoice {}

        record Required() implements ToolChoice {}

        /** The reply must call this tool. */
        record Named(String tool) implements ToolChoice {}
    }
}
