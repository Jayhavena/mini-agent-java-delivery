package dev.nangua.miniagent.trace;

/**
 * TraceLogger 的空实现（Null Object 模式）：丢弃所有事件，tail 恒返回空列表。
 * 用于显式关闭 trace 的场景，让调用方完全免于判空。无状态且线程安全，
 * 构造器私有，请直接使用单例 INSTANCE。
 */
public final class NoopTraceLogger implements TraceLogger {
    public static final NoopTraceLogger INSTANCE = new NoopTraceLogger();

    private NoopTraceLogger() {
    }

    @Override
    public void log(TraceEvent event) {
        // intentionally empty
    }
}
