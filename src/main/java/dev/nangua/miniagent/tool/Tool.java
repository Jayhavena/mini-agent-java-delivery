package dev.nangua.miniagent.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A tool owns its name, natural-language description and JSON Schema. The runtime
 * never hard-codes which tool to call; the LLM chooses using the schema prompt.
 *
 * <p>工具接口：自己负责名称、自然语言描述和 JSON Schema。Runtime 从不硬编码
 * “该调哪个工具”——选择权完全在模型，依据是 system prompt 里公布的 schema。
 */
public interface Tool {
    String name();

    String description();

    ObjectNode parameterSchema();

    ToolResult execute(ToolContext context, ObjectNode arguments) throws ToolException;

    default ToolSpec spec() {
        return new ToolSpec(name(), description(), parameterSchema());
    }
}
