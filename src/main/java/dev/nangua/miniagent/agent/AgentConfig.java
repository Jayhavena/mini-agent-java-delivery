package dev.nangua.miniagent.agent;

import java.util.Map;

/**
 * Hard limits for one user turn. The defaults are intentionally small so the
 * context-compression and loop-limit behaviour can be demonstrated cheaply.
 *
 * <p>一个 turn 的全部硬限制。默认值（maxSteps=8 / maxToolRounds=6 / maxParseRetries=2）
 * 故意偏小，让上下文压缩和各类上限行为在演示与测试中容易触发；均可通过 AGENT_*
 * 环境变量覆盖，见 {@link #fromEnvironment()}。
 */
public record AgentConfig(
        int maxSteps,
        int maxToolRounds,
        int maxParseRetries,
        int contextTokenBudget,
        int keepRecentMessages,
        int maxSummaryChars
) {
    public AgentConfig {
        if (maxSteps < 2) {
            throw new IllegalArgumentException("maxSteps must be >= 2");
        }
        if (maxToolRounds < 1) {
            throw new IllegalArgumentException("maxToolRounds must be >= 1");
        }
        if (maxParseRetries < 0) {
            throw new IllegalArgumentException("maxParseRetries must be >= 0");
        }
        if (contextTokenBudget < 500) {
            throw new IllegalArgumentException("contextTokenBudget must be >= 500");
        }
        if (keepRecentMessages < 2) {
            throw new IllegalArgumentException("keepRecentMessages must be >= 2");
        }
        if (maxSummaryChars < 500) {
            throw new IllegalArgumentException("maxSummaryChars must be >= 500");
        }
    }

    public static AgentConfig defaults() {
        return new AgentConfig(8, 6, 2, 6000, 12, 4000);
    }

    public static AgentConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public static AgentConfig fromEnvironment(Map<String, String> env) {
        return new AgentConfig(
                parseInt(env.get("AGENT_MAX_STEPS"), 8),
                parseInt(env.get("AGENT_MAX_TOOL_ROUNDS"), 6),
                parseInt(env.get("AGENT_MAX_PARSE_RETRIES"), 2),
                parseInt(env.get("AGENT_CONTEXT_TOKEN_BUDGET"), 6000),
                parseInt(env.get("AGENT_KEEP_RECENT_MESSAGES"), 12),
                parseInt(env.get("AGENT_MAX_SUMMARY_CHARS"), 4000)
        );
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
