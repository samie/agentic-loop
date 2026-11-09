package com.example.agentdemo.ui.demo.timeline;

import com.example.agentdemo.harness.ChatOptions;

/** @param remote whether the model runs behind an HTTP API, which shows only JSON, never the native text */
public record ModelInUse(String ref, ChatOptions recommended, boolean remote) {}
