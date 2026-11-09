package com.example.agentdemo.harness;

import java.util.Map;
import java.util.function.Function;

/**
 * A function the LLM may ask the host to call.
 *
 * @param name        function name advertised to the model
 * @param description tells the model <em>when</em> to use the tool; write it carefully
 * @param parameters  JSON Schema of the arguments, as nested maps and lists
 * @param function    the actual code: arguments in, text out
 */
public record Tool(String name, String description, Map<String, Object> parameters,
                   Function<Map<String, Object>, String> function) {

    public String call(Map<String, Object> arguments) {
        return function.apply(arguments);
    }
}
