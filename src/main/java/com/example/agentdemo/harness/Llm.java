package com.example.agentdemo.harness;

import com.qxotic.jinfer.chat.Message;
import java.util.List;

/** The LLM as the loop sees it: the whole conversation and the tools in, one reply out. */
public interface Llm {

    /**
     * Generates the next reply, streaming thinking and answer text to the listener as it goes.
     *
     * @param options sampling, limits, thinking and tool choice for this reply
     */
    ChatResponse chat(List<Message> conversation, List<Tool> tools, ChatOptions options, Agent.Listener listener);

    /** The values this model uses when an option is left empty: its recommended settings. */
    default ChatOptions recommended() {
        return ChatOptions.DEFAULTS;
    }

    /** Whether this model has a thinking mode at all. */
    default boolean canThink() {
        return false;
    }

    /** Whether this model always thinks, so thinking cannot be turned off. */
    default boolean alwaysThinks() {
        return false;
    }
}
