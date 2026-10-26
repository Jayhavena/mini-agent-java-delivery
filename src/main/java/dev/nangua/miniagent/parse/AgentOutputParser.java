package dev.nangua.miniagent.parse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nangua.miniagent.llm.LlmResponse;
import dev.nangua.miniagent.util.Json;

import java.util.Locale;

/**
 * Parses the agent protocol. Real models sometimes wrap JSON in markdown fences or
 * add a sentence before it, so the parser intentionally tolerates those two cases
 * while still rejecting ambiguous payloads.
 *
 * <p>协议解析器。有意容忍两种常见跑偏：markdown 围栏和 JSON 前的几句前言
 * （见 extractJsonObject）；其余任何歧义都抛 ParseException，交给 Runtime
 * 计数、回灌纠错提示。
 */
public final class AgentOutputParser {
    private static final int MAX_RAW_CHARS = 100_000;
    private static final int MAX_THOUGHT_CHARS = 2_000;

    /** 解析响应。若 thought 为空且供应商返回了 reasoning_content，就用它充当
     *  thought，保证每次决策都有可审计的思考摘要。 */
    public AgentOutput parse(LlmResponse response) {
        AgentOutput output = parse(response.content());
        if (output.thought().isBlank() && !response.reasoningContent().isBlank()) {
            String reasoning = truncate(response.reasoningContent().trim(), MAX_THOUGHT_CHARS);
            if (output instanceof AgentOutput.ToolCall call) {
                return new AgentOutput.ToolCall(reasoning, call.toolName(), call.arguments());
            }
            AgentOutput.FinalAnswer answer = (AgentOutput.FinalAnswer) output;
            return new AgentOutput.FinalAnswer(reasoning, answer.answer());
        }
        return output;
    }

    public AgentOutput parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ParseException("Model returned empty content");
        }
        if (raw.length() > MAX_RAW_CHARS) {
            throw new ParseException("Model response is too large to parse");
        }
        String jsonText = extractJsonObject(raw);
        JsonNode root;
        try {
            root = Json.readTree(jsonText);
        } catch (JsonProcessingException e) {
            throw new ParseException("Model response is not valid JSON: "
                    + Json.preview(e.getOriginalMessage(), 300), e);
        }
        if (root == null || !root.isObject()) {
            throw new ParseException("Model response must be a JSON object");
        }

        // 模型漏写 type 时的兜底推断：有 tool 字段视为 tool_call，有 answer 视为 final。
        String type = text(root, "type");
        if (type.isBlank()) {
            if (root.has("tool") || root.has("tool_name")) {
                type = "tool_call";
            } else if (root.has("answer")) {
                type = "final";
            }
        }
        String thought = truncate(firstText(root, "thought", "reasoning"), MAX_THOUGHT_CHARS);

        return switch (type.toLowerCase(Locale.ROOT)) {
            case "tool_call", "tool", "call" -> parseToolCall(root, thought);
            case "final", "final_answer", "answer" -> parseFinal(root, thought);
            default -> throw new ParseException("Unknown response type '" + type
                    + "'. Expected tool_call or final.");
        };
    }

    private AgentOutput parseToolCall(JsonNode root, String thought) {
        String tool = firstText(root, "tool", "tool_name", "name");
        if (tool.isBlank()) {
            throw new ParseException("Tool call is missing the 'tool' field");
        }
        JsonNode argumentsNode = root.get("arguments");
        if (argumentsNode == null) {
            argumentsNode = root.get("args");
        }
        ObjectNode arguments;
        if (argumentsNode == null || argumentsNode.isNull()) {
            arguments = JsonNodeFactory.instance.objectNode();
        } else if (argumentsNode.isObject()) {
            arguments = (ObjectNode) argumentsNode;
        } else if (argumentsNode.isTextual()) {
            try {
                JsonNode parsed = Json.readTree(argumentsNode.asText());
                if (!parsed.isObject()) {
                    throw new ParseException("'arguments' string must decode to a JSON object");
                }
                arguments = (ObjectNode) parsed;
            } catch (JsonProcessingException e) {
                throw new ParseException("'arguments' is not valid JSON", e);
            }
        } else {
            throw new ParseException("'arguments' must be a JSON object");
        }
        return new AgentOutput.ToolCall(thought, tool.trim(), arguments);
    }

    private AgentOutput parseFinal(JsonNode root, String thought) {
        JsonNode answerNode = root.get("answer");
        if (answerNode == null) {
            answerNode = root.get("response");
        }
        if (answerNode == null) {
            answerNode = root.get("content");
        }
        if (answerNode == null || answerNode.isNull()) {
            throw new ParseException("Final answer is missing the 'answer' field");
        }
        String answer = answerNode.isTextual() ? answerNode.asText() : answerNode.toString();
        answer = answer.trim();
        if (answer.isBlank()) {
            throw new ParseException("Final answer must not be blank");
        }
        return new AgentOutput.FinalAnswer(thought, answer);
    }

    /** Accepts raw JSON, fenced JSON, or a short preamble followed by a JSON object. */
    static String extractJsonObject(String raw) {
        String text = raw.strip();
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1).strip();
        }
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline >= 0) {
                text = text.substring(firstNewline + 1);
            }
            int closingFence = text.lastIndexOf("```");
            if (closingFence >= 0) {
                text = text.substring(0, closingFence);
            }
            text = text.strip();
            if (text.startsWith("json")) {
                text = text.substring(4).strip();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ParseException("No JSON object found in model response: "
                    + Json.preview(raw, 300));
        }
        return text.substring(start, end + 1);
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isTextual() ? node.asText().trim() : "";
    }

    private static String firstText(JsonNode root, String... fields) {
        for (String field : fields) {
            String value = text(root, field);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
