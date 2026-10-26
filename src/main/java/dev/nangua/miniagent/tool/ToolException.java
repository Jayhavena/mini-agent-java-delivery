package dev.nangua.miniagent.tool;

/**
 * A recoverable tool failure. The message is safe to show to the model.
 *
 * <p>可恢复的工具失败：消息内容默认会回灌给模型，所以构造时要保证对模型可见
 * 是安全的（不泄漏内部细节）。Runtime 会把它转成 ok=false 的 ToolResult，
 * 而不是让进程崩溃。
 */
public class ToolException extends RuntimeException {
    public ToolException(String message) {
        super(message);
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
