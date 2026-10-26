package dev.nangua.miniagent.agent;

import dev.nangua.miniagent.context.ContextManager;
import dev.nangua.miniagent.context.SystemPromptBuilder;
import dev.nangua.miniagent.llm.LlmConfig;
import dev.nangua.miniagent.session.JsonSessionRepository;
import dev.nangua.miniagent.session.MessageKind;
import dev.nangua.miniagent.session.Session;
import dev.nangua.miniagent.tool.CalculatorTool;
import dev.nangua.miniagent.tool.TodoTool;
import dev.nangua.miniagent.tool.ToolRegistry;
import dev.nangua.miniagent.trace.TraceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeTest {
    private static final LlmConfig LLM_CONFIG = new LlmConfig(
            "https://example.invalid", "test-key", "test-model", 0.0, 256,
            Duration.ofSeconds(5), 0, true);

    @TempDir
    Path temp;

    @Test
    void directFinalAnswerWithoutTools() {
        ScriptedLlmClient llm = new ScriptedLlmClient().finalAnswer("你好，我是 MiniAgent。");
        Harness harness = harness(llm, AgentConfig.defaults());
        Session session = harness.session();

        AgentReply reply = harness.runtime.chat(session, "你好");

        assertEquals(AgentStopReason.FINAL, reply.stopReason());
        assertEquals("你好，我是 MiniAgent。", reply.answer());
        assertEquals(1, reply.steps());
        assertTrue(reply.toolCalls().isEmpty());
        assertEquals(2, session.messageCount());
        assertEquals(MessageKind.ASSISTANT_FINAL, session.getMessages().get(1).kind());
        assertTrue(harness.trace.has(TraceType.TURN_END));
        assertFalse(harness.trace.has(TraceType.TOOL_CALL));
    }

    @Test
    void callsCalculatorThenReturnsFinalAndFeedsResultBack() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .tool("calculator", "{\"expression\":\"(2+3)*4\"}")
                .finalAnswer("计算结果是 20。");
        Harness harness = harness(llm, AgentConfig.defaults());
        Session session = harness.session();

        AgentReply reply = harness.runtime.chat(session, "帮我算一下 (2+3)*4");

        assertEquals(AgentStopReason.FINAL, reply.stopReason());
        assertEquals(2, reply.steps());
        assertEquals(1, reply.toolCalls().size());
        assertTrue(reply.toolCalls().get(0).ok());
        assertTrue(reply.toolCalls().get(0).resultPreview().contains("20"));
        assertEquals("计算结果是 20。", reply.answer());
        assertEquals(4, session.messageCount());
        assertEquals(MessageKind.TOOL_RESULT, session.getMessages().get(2).kind());
        assertTrue(session.getMessages().get(2).content().contains("\"result\":\"20\""));
        assertTrue(harness.trace.has(TraceType.TOOL_CALL));
        assertTrue(harness.trace.has(TraceType.TOOL_RESULT));

        String secondPrompt = join(llm.requests().get(1));
        assertTrue(secondPrompt.contains("TOOL_RESULT name=calculator"));
        assertTrue(secondPrompt.contains("(2+3)*4"));
    }

    @Test
    void toolFailureIsReturnedToModelInsteadOfCrashing() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .tool("calculator", "{\"expression\":\"1/0\"}")
                .finalAnswer("除数不能为 0。");
        Harness harness = harness(llm, AgentConfig.defaults());

        AgentReply reply = harness.runtime.chat(harness.session(), "算 1/0");

        assertEquals(AgentStopReason.FINAL, reply.stopReason());
        assertEquals(1, reply.toolCalls().size());
        assertFalse(reply.toolCalls().get(0).ok());
        assertTrue(reply.toolCalls().get(0).resultPreview().contains("Division by zero"));
        assertTrue(harness.trace.has(TraceType.TOOL_ERROR));
    }

    @Test
    void stopsAtMaxToolRounds() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .tool("calculator", "{\"expression\":\"1+1\"}")
                .tool("calculator", "{\"expression\":\"2+2\"}")
                .finalAnswer("should not be requested");
        AgentConfig config = new AgentConfig(6, 1, 2, 5000, 10, 1000);
        Harness harness = harness(llm, config);

        AgentReply reply = harness.runtime.chat(harness.session(), "一直算");

        assertEquals(AgentStopReason.MAX_TOOL_ROUNDS, reply.stopReason());
        assertEquals(1, reply.toolCalls().size());
        assertEquals(2, reply.steps());
        assertEquals(1, llm.remaining());
        assertTrue(harness.trace.has(TraceType.TURN_LIMIT));
        assertTrue(reply.answer().contains("工具调用上限"));
    }

    @Test
    void stopsAtMaxSteps() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .tool("calculator", "{\"expression\":\"1+1\"}")
                .tool("calculator", "{\"expression\":\"2+2\"}")
                .tool("calculator", "{\"expression\":\"3+3\"}");
        AgentConfig config = new AgentConfig(2, 3, 2, 5000, 10, 1000);
        Harness harness = harness(llm, config);

        AgentReply reply = harness.runtime.chat(harness.session(), "继续");

        assertEquals(AgentStopReason.MAX_STEPS, reply.stopReason());
        assertEquals(2, reply.toolCalls().size());
        assertEquals(2, reply.steps());
        assertTrue(reply.answer().contains("最大推理步数"));
    }

    @Test
    void invalidJsonGetsCorrectionAndCanRecover() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then("这不是 JSON")
                .finalAnswer("已修正并回答。");
        Harness harness = harness(llm, AgentConfig.defaults());
        Session session = harness.session();

        AgentReply reply = harness.runtime.chat(session, "测试格式恢复");

        assertEquals(AgentStopReason.FINAL, reply.stopReason());
        assertEquals(2, reply.steps());
        assertTrue(harness.trace.has(TraceType.PARSE_ERROR));
        assertTrue(session.getMessages().stream()
                .anyMatch(message -> message.kind() == MessageKind.CONTEXT_NOTE));
        assertTrue(join(llm.requests().get(1)).contains("PROTOCOL_CORRECTION"));
    }

    @Test
    void repeatedInvalidJsonStops() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then("bad one")
                .then("bad two")
                .finalAnswer("unused");
        AgentConfig config = new AgentConfig(6, 4, 1, 5000, 10, 1000);
        Harness harness = harness(llm, config);

        AgentReply reply = harness.runtime.chat(harness.session(), "坏格式");

        assertEquals(AgentStopReason.PARSE_ERROR, reply.stopReason());
        assertEquals(2, reply.steps());
        assertTrue(reply.answer().contains("无法解析"));
    }

    @Test
    void llmFailureIsGraceful() {
        ScriptedLlmClient llm = new ScriptedLlmClient().then(ScriptedLlmClient.httpError());
        Harness harness = harness(llm, AgentConfig.defaults());
        Session session = harness.session();

        AgentReply reply = harness.runtime.chat(session, "会失败");

        assertEquals(AgentStopReason.LLM_ERROR, reply.stopReason());
        assertTrue(reply.answer().contains("模型调用失败"));
        assertTrue(harness.trace.has(TraceType.ERROR));
        assertEquals(MessageKind.ASSISTANT_FINAL,
                session.getMessages().get(session.messageCount() - 1).kind());
    }

    @Test
    void todoStateAndToolResultsSurviveFollowUpTurns() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .tool("todo", "{\"action\":\"add\",\"text\":\"买牛奶\"}")
                .finalAnswer("已加入待办。")
                .tool("todo", "{\"action\":\"list\"}")
                .finalAnswer("你的待办是买牛奶。");
        Harness harness = harness(llm, AgentConfig.defaults());
        Session session = harness.session();

        harness.runtime.chat(session, "把买牛奶加入待办");
        AgentReply second = harness.runtime.chat(session, "我刚才记了什么？");

        assertEquals(AgentStopReason.FINAL, second.stopReason());
        assertTrue(second.toolCalls().get(0).resultPreview().contains("买牛奶"));
        assertTrue(join(llm.requests().get(2)).contains("买牛奶"));
        assertEquals("买牛奶",
                session.getToolState().path("todo").path("items").get(0).path("text").asText());
        assertEquals(1, session.getToolState().path("todo").path("items").size());
    }

    @Test
    void twoSessionsOfSameUserDoNotShareConversationOrTodo() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .tool("todo", "{\"action\":\"add\",\"text\":\"窗口1待办\"}")
                .finalAnswer("窗口1完成")
                .tool("todo", "{\"action\":\"add\",\"text\":\"窗口2待办\"}")
                .finalAnswer("窗口2完成");
        Harness harness = harness(llm, AgentConfig.defaults());
        Session window1 = harness.session();
        Session window2 = harness.repo.create("alice", "窗口2");

        harness.runtime.chat(window1, "给窗口1记待办");
        harness.runtime.chat(window2, "给窗口2记待办");

        assertEquals("窗口1待办",
                window1.getToolState().path("todo").path("items").get(0).path("text").asText());
        assertEquals("窗口2待办",
                window2.getToolState().path("todo").path("items").get(0).path("text").asText());
        assertEquals(4, window1.messageCount());
        assertEquals(4, window2.messageCount());
        assertTrue(window1.getMessages().get(0).content().contains("窗口1"));
    }

    private Harness harness(ScriptedLlmClient llm, AgentConfig config) {
        JsonSessionRepository repo = new JsonSessionRepository(temp);
        CapturingTraceLogger trace = new CapturingTraceLogger();
        ToolRegistry registry = new ToolRegistry()
                .register(new CalculatorTool())
                .register(new TodoTool());
        ContextManager context = new ContextManager(config, new SystemPromptBuilder(registry));
        AgentRuntime runtime = new AgentRuntime(llm, LLM_CONFIG, registry, repo, context, trace, config);
        Session session = repo.create("alice", "test");
        return new Harness(runtime, repo, trace, session);
    }

    private static String join(dev.nangua.miniagent.llm.LlmRequest request) {
        StringBuilder sb = new StringBuilder();
        request.messages().forEach(message -> sb.append(message.role()).append(": ")
                .append(message.content()).append('\n'));
        return sb.toString();
    }

    private record Harness(AgentRuntime runtime, JsonSessionRepository repo,
                           CapturingTraceLogger trace, Session session) {
    }
}
