package com.example.agentdemo.harness;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** A few example tools. Each one is just a {@link Tool} value: schema + function. */
public final class Tools {

    private Tools() {}

    public static final Tool CALCULATOR = new Tool(
            "calculator",
            "Evaluates an arithmetic expression with + - * / ^ and parentheses. "
                    + "Use it for any arithmetic instead of computing in your head.",
            Map.of("type", "object",
                    "properties", Map.of("expression", Map.of(
                            "type", "string",
                            "description", "e.g. \"155 / (58 * 1000 / 3600)\"")),
                    "required", List.of("expression")),
            args -> {
                double value = new Calculator(requireString(args, "expression")).evaluate();
                if (!Double.isFinite(value)) throw new IllegalArgumentException("result is not a finite number");
                return new BigDecimal(value, new MathContext(10)).stripTrailingZeros().toPlainString();
            });

    public static final Tool CURRENT_TIME = new Tool(
            "current_time",
            "Returns the current date and time in the given IANA time zone.",
            Map.of("type", "object",
                    "properties", Map.of("timezone", Map.of(
                            "type", "string",
                            "description", "IANA zone, e.g. \"Europe/Helsinki\". Defaults to UTC."))),
            args -> {
                String zone = args.get("timezone") instanceof String z && !z.isBlank() ? z : "UTC";
                return ZonedDateTime.now(ZoneId.of(zone)).format(DateTimeFormatter.RFC_1123_DATE_TIME);
            });

    /**
     * A sub-agent wrapped as a tool. It runs its own loop on a fresh conversation
     * and only its final answer comes back as the observation. It does not get the
     * delegate tool itself, so it cannot recurse.
     */
    public static Tool delegate(Llm llm, String systemPrompt, List<Tool> subAgentTools, int maxSteps) {
        return new Tool(
                "delegate",
                "Give a self-contained side task to a fresh sub-agent that has the same tools as you "
                        + "(except delegate). It cannot see this conversation, so include all needed context. "
                        + "Only its final answer comes back.",
                Map.of("type", "object",
                        "properties", Map.of("task", Map.of(
                                "type", "string",
                                "description", "Self-contained instructions for the sub-agent.")),
                        "required", List.of("task")),
                args -> new Agent(llm, systemPrompt, subAgentTools, maxSteps)
                        .run(requireString(args, "task"), ChatOptions.DEFAULTS, new Agent.Listener() {}));
    }

    /** Tool arguments come from the model, so check them and explain what's wrong. */
    static String requireString(Map<String, Object> args, String name) {
        if (!(args.get(name) instanceof String value)) {
            throw new IllegalArgumentException("missing required string parameter '" + name + "', got arguments " + args);
        }
        return value;
    }

    /** Recursive-descent parser: expr = term (('+'|'-') term)*, term = factor (('*'|'/') factor)*, ... */
    private static final class Calculator {
        private final String input;
        private int pos;

        Calculator(String input) {
            this.input = input.replace("**", "^");
        }

        double evaluate() {
            double value = expression();
            skipSpaces();
            if (pos < input.length()) throw error("unexpected '" + input.charAt(pos) + "'");
            return value;
        }

        private double expression() {
            double value = term();
            while (true) {
                if (eat('+')) value += term();
                else if (eat('-')) value -= term();
                else return value;
            }
        }

        private double term() {
            double value = power();
            while (true) {
                if (eat('*')) value *= power();
                else if (eat('/')) value /= power();
                else return value;
            }
        }

        private double power() {
            double base = unary();
            return eat('^') ? Math.pow(base, power()) : base; // right-associative
        }

        private double unary() {
            if (eat('-')) return -unary();
            if (eat('+')) return unary();
            if (eat('(')) {
                double value = expression();
                if (!eat(')')) throw error("missing ')'");
                return value;
            }
            return number();
        }

        private double number() {
            skipSpaces();
            int start = pos;
            while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) pos++;
            if (start == pos) throw error(pos < input.length() ? "unexpected '" + input.charAt(pos) + "'" : "unexpected end");
            return Double.parseDouble(input.substring(start, pos));
        }

        private boolean eat(char c) {
            skipSpaces();
            if (pos < input.length() && input.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipSpaces() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
        }

        private IllegalArgumentException error(String what) {
            return new IllegalArgumentException("cannot evaluate \"" + input + "\": " + what + " at position " + pos);
        }
    }
}
