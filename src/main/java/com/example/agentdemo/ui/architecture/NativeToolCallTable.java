package com.example.agentdemo.ui.architecture;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Code;
import com.vaadin.flow.component.html.Table;

public class NativeToolCallTable extends Composite<Table> {

    public NativeToolCallTable() {
        Table table = getContent();
        table.addHeaderRow("Native style", "Example", "Used by");
        style("JSON in tags",
                "<tool_call>{\"name\":\"calculator\",\"arguments\":{\"expression\":\"17*23\"}}</tool_call>",
                "Granite, Qwen2.5/3, Hermes-style");
        style("XML function form",
                "<tool_call><function=calculator><parameter=expression>17*23</parameter></function></tool_call>",
                "Qwen3.5, Nemotron");
        style("Python-style call",
                "<|tool_call_start|>[calculator(expression=\"17*23\")]<|tool_call_end|>",
                "LFM2.5 (this demo's default), Llama 3.2");
        style("Channels (Harmony)",
                "<|channel|>commentary to=functions.calculator <|message|>{\"expression\":\"17*23\"}<|call|>",
                "gpt-oss");
        style("Special-token JSON list",
                "[TOOL_CALLS][{\"name\":\"calculator\",\"arguments\":{...}}]",
                "Mistral");
    }

    private void style(String name, String example, String usedBy) {
        var row = getContent().addRow();
        row.addDataCell(name);
        row.addDataCell(new Code(example));
        row.addDataCell(usedBy);
    }
}
