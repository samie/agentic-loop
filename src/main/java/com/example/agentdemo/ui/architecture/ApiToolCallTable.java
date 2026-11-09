package com.example.agentdemo.ui.architecture;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Code;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Table;

public class ApiToolCallTable extends Composite<Table> {

    public ApiToolCallTable() {
        Table table = getContent();
        table.addHeaderRow("API", "Tool call", "Tool result");
        api(new Div(new Div("OpenAI Chat Completions"), new Div("(also llama.cpp, vLLM, Ollama, x.ai)")),
                "tool_calls: [{id, type: \"function\", function: {name, arguments: \"<JSON string>\"}}]",
                "{role: \"tool\", tool_call_id, content}");
        api(new Div("OpenAI Responses"),
                "{type: \"function_call\", call_id, name, arguments}",
                "{type: \"function_call_output\", call_id, output}");
        api(new Div("Anthropic Messages"),
                "{type: \"tool_use\", id, name, input: {...}}",
                "{type: \"tool_result\", tool_use_id, content}");
        api(new Div("Google Gemini"),
                "{functionCall: {name, args}}",
                "{functionResponse: {name, response}}");
    }

    private void api(Div name, String toolCall, String toolResult) {
        var row = getContent().addRow();
        row.addDataCell(name);
        row.addDataCell(new Code(toolCall));
        row.addDataCell(new Code(toolResult));
    }
}
