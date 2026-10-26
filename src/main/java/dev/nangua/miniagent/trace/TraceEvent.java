package dev.nangua.miniagent.trace;

import dev.nangua.miniagent.session.Session;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条 trace 审计事件：何时发生（timestamp）、归属哪个 session/user、什么类型
 * （TraceType），以及任意键值数据。事件构造后不再变化，data 会被防御性拷贝成
 * 不可变 Map。与 SessionMessage 的分工：trace 记录「运行时做了什么」，
 * session 消息记录「对话里说了什么」。
 */
public record TraceEvent(
        Instant timestamp,
        String sessionId,
        String userId,
        TraceType type,
        Map<String, Object> data
) {
    public TraceEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        // Map.copyOf 不接受 null value：事件数据里不要放 null，否则这里会直接抛 NPE。
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static TraceEvent of(Session session, TraceType type, Map<String, Object> data) {
        return new TraceEvent(Instant.now(), session.getId(), session.getUserId(), type, data);
    }

    public static Map<String, Object> data(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Expected key/value pairs");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return result;
    }
}
