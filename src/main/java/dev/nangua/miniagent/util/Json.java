package dev.nangua.miniagent.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Single, consistently configured Jackson entry point.
 *
 * <p>全项目唯一的 Jackson ObjectMapper 配置入口：session 与 trace 的 JSON
 * 读写都经由同一个 MAPPER，保证落盘格式一致。异常约定：readTree 向调用方抛
 * 受检的 JsonProcessingException；readTreeUnchecked/write/pretty 分别包装为
 * IllegalArgumentException/IllegalStateException，调用方无需处理受检异常。
 */
public final class Json {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            // 时间不写成数值时间戳：落盘 JSON 里的 Instant 保持 ISO-8601 字符串，可读且跨平台稳定。
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    private Json() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static JsonNode readTree(String text) throws JsonProcessingException {
        return MAPPER.readTree(text);
    }

    /** Convenience for tool output that is expected to be valid JSON. */
    public static JsonNode readTreeUnchecked(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON: " + preview(text, 200), e);
        }
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise JSON", e);
        }
    }

    public static String pretty(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise pretty JSON", e);
        }
    }

    /** Compact one-line preview for logs and user-facing diagnostics. */
    public static String preview(String text, int maxChars) {
        if (text == null) {
            return "<null>";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= maxChars) {
            return oneLine;
        }
        return oneLine.substring(0, Math.max(0, maxChars)) + "...";
    }
}
