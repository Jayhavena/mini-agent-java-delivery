package dev.nangua.miniagent.tool;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny helpers for building the JSON Schemas advertised to the model.
 *
 * <p>构建 JSON Schema 的小工具集，产物用于 system prompt 的 [TOOLS] 段落。
 * object() 固定 additionalProperties=false，让模型的参数更可预测。
 */
public final class Schemas {
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private Schemas() {
    }

    public static ObjectNode object(String description, List<String> required,
                                    Map<String, ObjectNode> properties) {
        ObjectNode schema = NODES.objectNode();
        schema.put("type", "object");
        if (description != null && !description.isBlank()) {
            schema.put("description", description);
        }
        ObjectNode props = schema.putObject("properties");
        properties.forEach(props::set);
        ArrayNode requiredNode = schema.putArray("required");
        required.forEach(requiredNode::add);
        schema.put("additionalProperties", false);
        return schema;
    }

    public static Map<String, ObjectNode> properties(Object... nameSchemaPairs) {
        if (nameSchemaPairs.length % 2 != 0) {
            throw new IllegalArgumentException("Expected name/schema pairs");
        }
        Map<String, ObjectNode> result = new LinkedHashMap<>();
        for (int i = 0; i < nameSchemaPairs.length; i += 2) {
            result.put((String) nameSchemaPairs[i], (ObjectNode) nameSchemaPairs[i + 1]);
        }
        return result;
    }

    public static ObjectNode string(String description) {
        ObjectNode node = NODES.objectNode();
        node.put("type", "string");
        node.put("description", description);
        return node;
    }

    public static ObjectNode enumeration(String description, String... values) {
        ObjectNode node = string(description);
        ArrayNode valuesNode = node.putArray("enum");
        for (String value : values) {
            valuesNode.add(value);
        }
        return node;
    }

    public static ObjectNode integer(String description, Integer minimum, Integer maximum) {
        ObjectNode node = NODES.objectNode();
        node.put("type", "integer");
        node.put("description", description);
        if (minimum != null) {
            node.put("minimum", minimum);
        }
        if (maximum != null) {
            node.put("maximum", maximum);
        }
        return node;
    }

    public static ObjectNode bool(String description) {
        ObjectNode node = NODES.objectNode();
        node.put("type", "boolean");
        node.put("description", description);
        return node;
    }
}
