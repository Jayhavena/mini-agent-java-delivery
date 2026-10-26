package dev.nangua.miniagent.context;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nangua.miniagent.agent.AgentConfig;
import dev.nangua.miniagent.session.Session;
import dev.nangua.miniagent.session.SessionMessage;
import dev.nangua.miniagent.tool.Schemas;
import dev.nangua.miniagent.tool.Tool;
import dev.nangua.miniagent.tool.ToolContext;
import dev.nangua.miniagent.tool.ToolRegistry;
import dev.nangua.miniagent.tool.ToolResult;
import dev.nangua.miniagent.trace.NoopTraceLogger;
import dev.nangua.miniagent.util.Json;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextManagerTest {
    private final ToolRegistry registry = new ToolRegistry().register(new EchoTool());

    @Test
    void autoCompactsOldMessagesAndKeepsSummaryInPrompt() {
        AgentConfig config = new AgentConfig(8, 6, 2, 1_600, 4, 900);
        ContextManager manager = new ContextManager(config, new SystemPromptBuilder(registry));
        Session session = new Session("s_ctx", "alice", "context");
        session.append(SessionMessage.user("项目代号 ORION。请记住这个代号。"));
        session.append(SessionMessage.assistantFinal("已记住 ORION。", "ok", 1));
        for (int i = 0; i < 12; i++) {
            session.append(SessionMessage.user("这是第 " + i + " 条很长的填充消息，"
                    + "目的是让上下文超过预算。".repeat(8)));
            session.append(SessionMessage.assistantFinal("收到第 " + i + " 条。",
                    "reply", i + 2));
        }

        assertTrue(manager.estimateFullPrompt(session) > 1_600);
        assertTrue(manager.maybeCompact(session, NoopTraceLogger.INSTANCE));
        assertTrue(session.getCompactedMessages() > 0);
        assertTrue(session.getSummary().contains("ORION"));
        assertTrue(session.getSummary().contains("第 0 条"));

        ContextWindow window = manager.build(session);
        String system = window.messages().get(0).content();
        assertTrue(system.contains("[SESSION_MEMORY]"));
        assertTrue(system.contains("ORION"));
        assertTrue(window.estimatedTokens() < 1_600);
    }

    @Test
    void recentMessagesRemainVerbatimForFollowUps() {
        AgentConfig config = new AgentConfig(8, 6, 2, 1_200, 4, 900);
        ContextManager manager = new ContextManager(config, new SystemPromptBuilder(registry));
        Session session = new Session("s_recent", "alice", "followup");
        session.append(SessionMessage.user("杭州天气如何？"));
        session.append(SessionMessage.assistantToolCall("查天气", "echo",
                Json.mapper().createObjectNode().put("text", "杭州"), 1));
        session.append(SessionMessage.toolResult("echo", true, "杭州 23°C 多云", 1));
        session.append(SessionMessage.assistantFinal("杭州 23°C，多云。", "final", 2));
        session.append(SessionMessage.user("那明天呢？"));
        manager.forceCompact(session, NoopTraceLogger.INSTANCE);

        ContextWindow window = manager.build(session);
        String joined = String.join("\n", window.messages().stream().map(m -> m.content()).toList());
        assertTrue(joined.contains("那明天呢？"));
        assertTrue(joined.contains("杭州 23°C 多云"));
    }

    @Test
    void capsHugeToolResultInPromptButNotOnDisk() {
        AgentConfig config = new AgentConfig(8, 6, 2, 4_000, 6, 1000);
        ContextManager manager = new ContextManager(config, new SystemPromptBuilder(registry));
        Session session = new Session("s_cap", "alice", "cap");
        String huge = "X".repeat(20_000);
        session.append(SessionMessage.toolResult("echo", true, huge, 1));
        session.append(SessionMessage.user("总结一下"));

        ContextWindow window = manager.build(session);
        String toolMessage = window.messages().stream()
                .filter(message -> message.content().startsWith("[TOOL_RESULT name=echo"))
                .findFirst().orElseThrow().content();
        assertTrue(toolMessage.contains("[truncated in prompt]"));
        assertTrue(toolMessage.length() < 5_000);
        assertEquals(20_000, session.getMessages().get(0).content().length());
    }

    @Test
    void summaryMergeRespectsMaxChars() {
        SessionSummarizer summarizer = new SessionSummarizer();
        String merged = summarizer.merge("A".repeat(500), "B".repeat(500), 600);
        assertTrue(merged.length() <= 600);
        assertTrue(merged.startsWith("A"));
        assertTrue(merged.contains("B"));
    }

    @Test
    void tokenEstimatorCountsCjkSeparately() {
        TokenEstimator estimator = new TokenEstimator();
        assertTrue(estimator.estimate("你好世界") >= 4);
        assertTrue(estimator.estimate("hello world") < estimator.estimate("你好世界你好世界"));
        assertEquals(0, estimator.estimate(""));
    }

    @Test
    void systemPromptContainsToolsProtocolAndSessionState() {
        Session session = new Session("s_prompt", "alice", "prompt");
        session.getToolState().putObject("todo").put("nextId", 1).putArray("items");
        SystemPromptBuilder builder = new SystemPromptBuilder(registry);
        String prompt = builder.build(session);
        assertTrue(prompt.contains("\"name\" : \"echo\""));
        assertTrue(prompt.contains("[OUTPUT PROTOCOL]"));
        assertTrue(prompt.contains("Persistent todo state"));
        assertTrue(prompt.contains("alice"));
        assertNotNull(session.getId());
        assertFalse(prompt.isBlank());
    }

    static final class EchoTool implements Tool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "Echo text.";
        }

        @Override
        public ObjectNode parameterSchema() {
            return Schemas.object("Echo", List.of("text"),
                    Schemas.properties("text", Schemas.string("text")));
        }

        @Override
        public ToolResult execute(ToolContext context, ObjectNode arguments) {
            return ToolResult.success(arguments.path("text").asText(""));
        }
    }
}
