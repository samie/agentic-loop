package com.example.agentdemo.harness;

import com.qxotic.jinfer.chat.Content;
import com.qxotic.jinfer.chat.Message;
import java.time.Duration;
import java.util.List;

/**
 * One reply from the LLM, flattened for the loop.
 *
 * @param finishReason why the model stopped: "stop", "tool_calls", "length" or "other"
 * @param content      text for the user; may be empty when calling tools
 * @param reasoning    the model's private thinking, or empty
 * @param toolCalls    tools the model wants run; empty unless finishReason is "tool_calls"
 * @param reply        the reply exactly as the model produced it; this is what goes into the conversation
 * @param nativePrompt the exact text the model read for this step (chat template applied), or null
 * @param nativeOutput the exact text the model wrote, special tokens included, or null
 * @param usage        tokens in and out and the model's time, or null
 */
public record ChatResponse(String finishReason, String content, String reasoning,
                           List<Content.ToolCall> toolCalls, Message reply,
                           String nativePrompt, String nativeOutput, Usage usage) {

    /**
     * What one model call cost.
     *
     * @param promptTokens   tokens the model read (the whole conversation, template included)
     * @param cachedTokens   of those, how many were reused from the previous call instead of re-read
     * @param outputTokens   tokens the model wrote
     * @param thinkingTokens of those, how many were thinking
     * @param promptTime     time to read the prompt
     * @param outputTime     time to write the reply
     */
    public record Usage(int promptTokens, int cachedTokens, int outputTokens, int thinkingTokens,
                        Duration promptTime, Duration outputTime) {

        public Duration modelTime() {
            return promptTime.plus(outputTime);
        }

        public double tokensPerSecond() {
            double seconds = outputTime.toNanos() / 1e9;
            return seconds > 0 ? outputTokens / seconds : 0;
        }
    }

    /** The same response with the model's raw input and output text attached. */
    public ChatResponse withNativeText(String prompt, String output) {
        return new ChatResponse(finishReason, content, reasoning, toolCalls, reply, prompt, output, usage);
    }

    /** The same response with token counts and timings attached. */
    public ChatResponse withUsage(Usage usage) {
        return new ChatResponse(finishReason, content, reasoning, toolCalls, reply, nativePrompt, nativeOutput, usage);
    }
}
