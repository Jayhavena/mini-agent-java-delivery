package dev.nangua.miniagent.trace;

import java.util.List;

/**
 * trace 日志接口：运行时在关键节点调用 log 追加审计事件。
 * 约定：实现应当保证「写 trace 失败不能打断 agent 的 turn」，推荐在实现内部
 * 捕获 IO 异常并告警。tail 供 CLI 的 /trace 之类回看最近事件，close 在进程
 * 退出前释放资源；两者都有默认空实现，实现类按需覆盖。
 */
public interface TraceLogger {
    void log(TraceEvent event);

    default List<TraceEvent> tail(String sessionId, int limit) {
        return List.of();
    }

    default void close() {
    }
}
