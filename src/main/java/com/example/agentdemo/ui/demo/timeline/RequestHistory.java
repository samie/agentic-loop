package com.example.agentdemo.ui.demo.timeline;

public final class RequestHistory {

    private int previousRequestSize;
    private String previousNativePrompt;
    private boolean systemPromptEdited;

    public int previousRequestSize() {
        return previousRequestSize;
    }

    public String previousNativePrompt() {
        return previousNativePrompt;
    }

    public boolean systemPromptEditedSincePreviousRequest() {
        return systemPromptEdited && previousRequestSize > 0;
    }

    void systemPromptEdited() {
        systemPromptEdited = true;
    }

    void requestSent(int conversationSize) {
        previousRequestSize = conversationSize;
        systemPromptEdited = false;
    }

    void promptRead(String nativePrompt) {
        previousNativePrompt = nativePrompt;
    }

    void forget() {
        previousRequestSize = 0;
        previousNativePrompt = null;
        systemPromptEdited = false;
    }
}
