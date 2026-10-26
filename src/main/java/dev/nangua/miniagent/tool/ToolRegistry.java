package dev.nangua.miniagent.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Explicit registration point for tools; duplicate names fail fast.
 *
 * <p>工具注册表：装配发生在 AgentFactory.create，顺序即 [TOOLS] 段落里的展示
 * 顺序（LinkedHashMap）。名称必须匹配 [a-z][a-z0-9_]{1,31} 且不允许重复，
 * 都在注册期就快速失败。
 */
public final class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        String name = tool.name();
        if (name == null || !name.matches("[a-z][a-z0-9_]{1,31}")) {
            throw new IllegalArgumentException("Invalid tool name: " + name);
        }
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate tool name: " + name);
        }
        tools.put(name, tool);
        return this;
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Tool require(String name) {
        return find(name).orElseThrow(() ->
                new ToolException("Unknown tool '" + name + "'. Registered tools: " + names()));
    }

    public ToolResult execute(String name, ToolContext context, ObjectNode arguments) {
        return require(name).execute(context, arguments == null
                ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                : arguments);
    }

    public List<ToolSpec> specs() {
        List<ToolSpec> result = new ArrayList<>();
        tools.values().forEach(tool -> result.add(tool.spec()));
        return List.copyOf(result);
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }

    public int size() {
        return tools.size();
    }
}
