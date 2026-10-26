package dev.nangua.miniagent.tool;

/**
 * Result returned to the model. Content should be compact, valid JSON or plain text.
 *
 * <p>回灌给模型的工具结果。ok=false 不代表崩溃——失败原因同样写给模型，让它
 * 换参数、换工具或向用户解释；content 要精简，因为它会进入后续每一步的上下文。
 */
public record ToolResult(boolean ok, String content) {
    public ToolResult {
        content = content == null ? "" : content;
    }

    public static ToolResult success(String content) {
        return new ToolResult(true, content);
    }

    public static ToolResult failure(String content) {
        return new ToolResult(false, content);
    }
}
