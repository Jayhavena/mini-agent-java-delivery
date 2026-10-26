package dev.nangua.miniagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import dev.nangua.miniagent.context.ContextManager;
import dev.nangua.miniagent.context.SystemPromptBuilder;
import dev.nangua.miniagent.llm.ChatMessage;
import dev.nangua.miniagent.llm.LlmConfig;
import dev.nangua.miniagent.llm.LlmRequest;
import dev.nangua.miniagent.session.JsonSessionRepository;
import dev.nangua.miniagent.session.MessageKind;
import dev.nangua.miniagent.session.Session;
import dev.nangua.miniagent.session.SessionMessage;
import dev.nangua.miniagent.tool.TodoTool;
import dev.nangua.miniagent.tool.ToolRegistry;
import dev.nangua.miniagent.tool.WeatherTool;
import dev.nangua.miniagent.trace.TraceEvent;
import dev.nangua.miniagent.trace.TraceType;
import dev.nangua.miniagent.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline conversation flows through the real runtime, context manager, tools and JSON store.
 * Scripted answers drive the protocol, but assertions inspect the requests actually sent to the
 * model and the state actually written to disk; canned final text alone is not a memory oracle.
 */
class AgentConversationFlowTest {
    private static final LlmConfig LLM_CONFIG = new LlmConfig(
            "https://example.invalid", "offline-test-key", "offline-scripted-model", 0.0, 256,
            Duration.ofSeconds(5), 0, true);

    @TempDir
    Path temp;

    @Test
    void remembersProjectCodeInTheNextPlainConversationRequest() {
        String firstQuestion = "请记住：项目代号是 ORION-FLOW-42。";
        String firstAnswer = "已记住项目代号 ORION-FLOW-42。";
        String followUp = "我刚才说的项目代号是什么？";
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .finalAnswer(firstAnswer)
                .finalAnswer("项目代号是 ORION-FLOW-42。");
        Harness harness = harness(llm, AgentConfig.defaults());
        Session session = harness.repo().create("alice", "项目讨论");

        AgentReply first = harness.runtime().chat(session, firstQuestion);
        AgentReply second = harness.runtime().chat(session, followUp);

        assertFinal(first, session, 1);
        assertFinal(second, session, 1);
        assertEquals(2, llm.requests().size());
        assertEquals(0, llm.remaining());
        assertEquals(2, llm.requests().get(0).messages().size());
        assertEquals(List.of(ChatMessage.user(firstQuestion)), llm.requests().get(0).messages().subList(1, 2));
        LlmRequest secondRequest = llm.requests().get(1);
        assertEquals("system", secondRequest.messages().get(0).role());
        assertEquals(List.of(ChatMessage.user(firstQuestion), ChatMessage.assistant(firstAnswer),
                        ChatMessage.user(followUp)),
                secondRequest.messages().subList(1, secondRequest.messages().size()));
        assertTrue(secondRequest.jsonMode());
        assertKinds(session, MessageKind.USER, MessageKind.ASSISTANT_FINAL,
                MessageKind.USER, MessageKind.ASSISTANT_FINAL);
        assertEquals(0, session.getCompactedMessages());
        assertEquals(2, harness.trace().count(TraceType.TURN_END));
        assertEquals(0, harness.trace().count(TraceType.TOOL_CALL));
        assertPersisted(session);
    }

    @Test
    void addsThePreviousWeatherResultToTodoOnAReferenceOnlyFollowUp() {
        String weatherQuestion = "查一下杭州天气。";
        String weatherAnswer = "杭州多云，23°C；这是离线模拟天气。";
        String todoText = "杭州：多云，23°C（离线 mock-weather）";
        String weatherArguments = "{\"city\":\"杭州\",\"unit\":\"celsius\"}";
        String todoArguments = Json.write(Json.mapper().createObjectNode()
                .put("action", "add").put("text", todoText));
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .tool("weather", weatherArguments)
                .finalAnswer(weatherAnswer)
                .tool("todo", todoArguments)
                .finalAnswer("已把刚才的天气结果加入本窗口待办。");
        Harness harness = harness(llm, AgentConfig.defaults());
        Session session = harness.repo().create("alice", "天气转待办");

        AgentReply weatherReply = harness.runtime().chat(session, weatherQuestion);
        assertFinal(weatherReply, session, 2, "weather");
        assertArguments(weatherReply, 0, weatherArguments);
        SessionMessage weatherResult = session.getMessages().get(2);
        JsonNode weather = Json.readTreeUnchecked(weatherResult.content());
        assertEquals(MessageKind.TOOL_RESULT, weatherResult.kind());
        assertEquals("杭州", weather.path("city").asText());
        assertEquals("多云", weather.path("condition").asText());
        assertEquals(23.0, weather.path("temperature").asDouble());
        assertEquals("°C", weather.path("unit").asText());
        assertEquals("mock-weather", weather.path("source").asText());
        assertTrue(weather.path("isMock").asBoolean());
        assertContainsMessage(llm.requests().get(1), "user", renderedToolResult(weatherResult));

        String followUp = "把刚才查到的结果加入待办。";
        AgentReply todoReply = harness.runtime().chat(session, followUp);

        assertFinal(todoReply, session, 2, "todo");
        assertArguments(todoReply, 0, todoArguments);
        assertEquals(4, llm.requests().size());
        assertEquals(0, llm.remaining());
        // The follow-up supplies neither the city nor the temperature. Its first request must
        // carry the previous observation, not just the previous assistant's paraphrase.
        LlmRequest followUpRequest = llm.requests().get(2);
        assertEquals(List.of(ChatMessage.user(weatherQuestion),
                        ChatMessage.assistant(session.getMessages().get(1).content()),
                        ChatMessage.user(renderedToolResult(weatherResult)),
                        ChatMessage.assistant(weatherAnswer), ChatMessage.user(followUp)),
                followUpRequest.messages().subList(1, followUpRequest.messages().size()));
        assertContainsMessage(llm.requests().get(3), "user",
                renderedToolResult(session.getMessages().get(6)));
        assertEquals(Json.readTreeUnchecked(todoArguments), session.getMessages().get(5).arguments());
        JsonNode added = Json.readTreeUnchecked(session.getMessages().get(6).content());
        assertEquals("add", added.path("action").asText());
        assertEquals(todoText, added.path("todo").path("text").asText());
        assertTodo(session, todoText, false);
        assertKinds(session, MessageKind.USER, MessageKind.ASSISTANT_TOOL_CALL,
                MessageKind.TOOL_RESULT, MessageKind.ASSISTANT_FINAL,
                MessageKind.USER, MessageKind.ASSISTANT_TOOL_CALL,
                MessageKind.TOOL_RESULT, MessageKind.ASSISTANT_FINAL);
        assertEquals(List.of("weather", "todo"), calledTools(harness));
        assertPersisted(session);
    }

    @Test
    void alternatingWindowsOfTheSameUserNeverMixContextOrTodoState() {
        String weatherMarker = "WX_WINDOW_ONLY_7B2";
        String reportMarker = "REPORT_WINDOW_ONLY_9C4";
        String weatherTodo = weatherMarker + " 杭州多云23°C";
        String reportTodo = reportMarker + " 整理本周周报";
        String weatherArguments = "{\"city\":\"杭州\",\"unit\":\"celsius\"}";
        String weatherAdd = addArguments(weatherTodo);
        String reportAdd = addArguments(reportTodo);
        String listArguments = "{\"action\":\"list\",\"includeDone\":true}";
        String completeArguments = "{\"action\":\"complete\",\"id\":1}";
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .tool("weather", weatherArguments).finalAnswer(weatherMarker + " 杭州多云23°C。")
                .tool("todo", reportAdd).finalAnswer(reportMarker + " 周报待办已保存。")
                .tool("todo", weatherAdd).finalAnswer(weatherMarker + " 天气待办已保存。")
                .tool("todo", listArguments).finalAnswer(reportMarker + " 本窗口只有周报待办。")
                .tool("todo", listArguments).finalAnswer(weatherMarker + " 本窗口只有天气待办。")
                .tool("todo", completeArguments).finalAnswer(reportMarker + " 周报待办已完成。");
        Harness harness = harness(llm, AgentConfig.defaults());
        Session weatherWindow = harness.repo().create("alice", "天气窗口");
        Session reportWindow = harness.repo().create("alice", "周报窗口");
        assertNotEquals(weatherWindow.getId(), reportWindow.getId());
        assertEquals(weatherWindow.getUserId(), reportWindow.getUserId());

        chatIsolated(harness, weatherWindow, reportWindow, weatherMarker, reportMarker,
                weatherMarker + " 查杭州天气。", "weather", weatherArguments);
        assertFalse(weatherWindow.getToolState().has("todo"));
        assertTrue(reportWindow.getToolState().isEmpty());
        SessionMessage weatherResult = weatherWindow.getMessages().get(2);

        chatIsolated(harness, reportWindow, weatherWindow, reportMarker, weatherMarker,
                reportMarker + " 把整理本周周报加入待办。", "todo", reportAdd);
        assertTodo(reportWindow, reportTodo, false);
        assertFalse(weatherWindow.getToolState().has("todo"));

        chatIsolated(harness, weatherWindow, reportWindow, weatherMarker, reportMarker,
                "把刚才查到的天气结果记成待办。", "todo", weatherAdd);
        assertContainsMessage(llm.requests().get(4), "user", renderedToolResult(weatherResult));
        assertTodo(weatherWindow, weatherTodo, false);
        assertTodo(reportWindow, reportTodo, false);

        AgentReply reportList = chatIsolated(harness, reportWindow, weatherWindow, reportMarker, weatherMarker,
                "列出这个窗口的周报待办。", "todo", listArguments);
        assertListedTodo(reportList, reportWindow, reportTodo, false);
        AgentReply weatherList = chatIsolated(harness, weatherWindow, reportWindow, weatherMarker, reportMarker,
                "再列出这个窗口的天气待办。", "todo", listArguments);
        assertListedTodo(weatherList, weatherWindow, weatherTodo, false);
        chatIsolated(harness, reportWindow, weatherWindow, reportMarker, weatherMarker,
                "将第一项周报待办标为完成。", "todo", completeArguments);

        // Both windows allocate id=1. Completing it in the report window must not touch id=1
        // in the weather window, even though both requests belong to the same user.
        assertTodo(weatherWindow, weatherTodo, false);
        assertTodo(reportWindow, reportTodo, true);
        assertEquals(12, weatherWindow.messageCount());
        assertEquals(12, reportWindow.messageCount());
        assertEquals(12, llm.requests().size());
        assertEquals(0, llm.remaining());
        assertEquals(List.of("weather", "todo", "todo", "todo", "todo", "todo"), calledTools(harness));
        assertEquals(6, harness.trace().count(TraceType.TURN_END));
        assertPersisted(weatherWindow);
        assertPersisted(reportWindow);
    }

    @Test
    void newRepositoryAndRuntimeResumeSavedMessagesSummaryAndToolState() {
        String projectFact = "重启前约定的项目代号是 PHOENIX-RESTART-18。";
        String todoText = "重启后继续检查 PHOENIX 发布清单";
        String addArguments = addArguments(todoText);
        AgentConfig config = new AgentConfig(8, 6, 2, 6_000, 4, 1_200);
        ScriptedLlmClient firstLlm = new ScriptedLlmClient()
                .finalAnswer("已记住 PHOENIX-RESTART-18。")
                .tool("todo", addArguments).finalAnswer("发布清单已保存。");
        Harness original = harness(firstLlm, config);
        Session originalSession = original.repo().create("alice", "重启恢复");
        assertFinal(original.runtime().chat(originalSession, projectFact), originalSession, 1);
        assertFinal(original.runtime().chat(originalSession, "把发布清单加入待办。"), originalSession, 2, "todo");
        assertEquals(0, firstLlm.remaining());

        // A deterministic saved-summary fixture isolates restart recovery from the separate
        // automatic-compaction flow below. Keep the four recent todo messages verbatim.
        assertTrue(original.context().forceCompact(originalSession, original.trace()));
        original.repo().save(originalSession);
        assertEquals(2, originalSession.getCompactedMessages());
        assertTrue(originalSession.getSummary().contains(projectFact));
        List<SessionMessage> savedMessages = List.copyOf(originalSession.getMessages());
        String savedSummary = originalSession.getSummary();
        JsonNode savedState = originalSession.getToolState().deepCopy();

        JsonSessionRepository restartedRepo = new JsonSessionRepository(temp);
        Session resumed = restartedRepo.find("alice", originalSession.getId()).orElseThrow();
        assertNotSame(original.repo(), restartedRepo);
        assertNotSame(originalSession, resumed);
        assertMessageSnapshot(savedMessages, resumed.getMessages());
        assertEquals(savedSummary, resumed.getSummary());
        assertEquals(2, resumed.getCompactedMessages());
        assertEquals(savedState, resumed.getToolState());
        ScriptedLlmClient restartedLlm = new ScriptedLlmClient()
                .tool("todo", "{\"action\":\"list\"}")
                .finalAnswer("项目代号 PHOENIX-RESTART-18，发布清单仍待处理。");
        Harness restarted = harness(restartedLlm, config, restartedRepo);
        assertNotSame(original.runtime(), restarted.runtime());

        String followUp = "恢复后告诉我之前的项目代号，并列出待办。";
        AgentReply reply = restarted.runtime().chat(resumed, followUp);

        assertFinal(reply, resumed, 2, "todo");
        assertArguments(reply, 0, "{\"action\":\"list\"}");
        assertEquals(2, restartedLlm.requests().size());
        assertEquals(0, restartedLlm.remaining());
        LlmRequest restoredRequest = restartedLlm.requests().get(0);
        String system = restoredRequest.messages().get(0).content();
        assertEquals("system", restoredRequest.messages().get(0).role());
        assertTrue(section(system, "SESSION_MEMORY").contains(projectFact));
        assertTrue(section(system, "SESSION_STATE").contains("total=1, open=1"));
        assertEquals(List.of(ChatMessage.user(savedMessages.get(2).content()),
                        ChatMessage.assistant(savedMessages.get(3).content()),
                        ChatMessage.user(renderedToolResult(savedMessages.get(4))),
                        ChatMessage.assistant(savedMessages.get(5).content()), ChatMessage.user(followUp)),
                restoredRequest.messages().subList(1, restoredRequest.messages().size()));
        assertListedTodo(reply, resumed, todoText, false);
        assertContainsMessage(restartedLlm.requests().get(1), "user", renderedToolResult(resumed.getMessages().get(8)));
        assertTodo(resumed, todoText, false);
        assertEquals(10, resumed.messageCount());
        assertEquals(6, originalSession.messageCount(), "resuming must not mutate the old in-memory object");
        assertEquals(0, restarted.trace().count(TraceType.COMPRESSION));
        assertPersisted(resumed);
    }

    @Test
    void runtimeAutomaticallyCompactsLongHistoryAndSendsOldFactInSessionMemory() {
        String oldFact = "最早约定的项目代号是 ATLAS-MEMORY-73。";
        AgentConfig config = new AgentConfig(8, 6, 2, 6_000, 4, 1_200);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .finalAnswer("最近的执行进度已确认。")
                .finalAnswer("最早约定的项目代号是 ATLAS-MEMORY-73。");
        Harness harness = harness(llm, config);
        Session session = harness.repo().create("alice", "自动压缩回忆");
        // Tool schemas and the base system prompt must fit comfortably. An impossibly small
        // budget would test only fallback dropping, not successful summary-based recall.
        assertTrue(harness.context().estimateFullPrompt(session) < config.contextTokenBudget() / 2);
        session.append(SessionMessage.user(oldFact));
        session.append(SessionMessage.assistantFinal("记住代号", "已记住 ATLAS-MEMORY-73。", 1));
        for (int i = 0; i < 12; i++) {
            session.append(SessionMessage.user("进度第 " + i + " 轮："
                    + "这里只记录常规执行进度，不新增任何项目约定。".repeat(60)));
            session.append(SessionMessage.assistantFinal("确认进度", "收到第 " + i + " 轮执行记录。", 1));
        }
        int seededCount = session.messageCount();
        assertEquals(26, seededCount);
        assertEquals(0, session.getCompactedMessages());
        assertTrue(session.getSummary().isEmpty());
        assertTrue(harness.context().estimateFullPrompt(session) > config.contextTokenBudget());

        // Only chat is allowed to trigger compression here; no manual maybe/forceCompact.
        AgentReply confirmation = harness.runtime().chat(session, "先确认最近的执行进度。不要复述项目代号。");
        assertFinal(confirmation, session, 1);
        assertTrue(session.getCompactedMessages() > 2);
        assertTrue(session.getSummary().contains(oldFact));
        assertTrue(session.getSummary().length() <= config.maxSummaryChars());
        assertEquals(1, harness.trace().count(TraceType.COMPRESSION));
        TraceEvent compression = harness.trace().events().stream()
                .filter(event -> event.type() == TraceType.COMPRESSION).findFirst().orElseThrow();
        assertEquals(Boolean.FALSE, compression.data().get("forced"));
        assertEquals(0, ((Number) compression.data().get("compactedFrom")).intValue());
        assertEquals(session.getCompactedMessages(), ((Number) compression.data().get("compactedTo")).intValue());
        assertTrue(((Number) compression.data().get("beforeTokens")).intValue() > config.contextTokenBudget());
        assertTrue(((Number) compression.data().get("afterTokens")).intValue() < config.contextTokenBudget());
        assertPersisted(session);

        String question = "最早约定的项目代号是什么？";
        AgentReply recall = harness.runtime().chat(session, question);

        assertFinal(recall, session, 1);
        assertEquals(2, llm.requests().size());
        assertEquals(0, llm.remaining());
        LlmRequest recallRequest = llm.requests().get(1);
        assertEquals("system", recallRequest.messages().get(0).role());
        String memory = section(recallRequest.messages().get(0).content(), "SESSION_MEMORY");
        assertTrue(memory.contains(oldFact), "the old fact must reach the LLM inside system SESSION_MEMORY");
        assertContainsMessage(recallRequest, "user", question);
        assertContainsMessage(recallRequest, "assistant", confirmation.answer());
        assertTrue(recallRequest.messages().stream().skip(1)
                .noneMatch(message -> message.content().contains("ATLAS-MEMORY-73")),
                "recall must come from the summary, not an accidentally retained raw message");
        assertFalse(join(recallRequest).contains("[context omitted]"));
        assertTrue(recall.estimatedContextTokens() <= config.contextTokenBudget());
        assertEquals(1, harness.trace().count(TraceType.COMPRESSION));
        assertEquals(0, harness.trace().count(TraceType.TOOL_CALL));
        assertEquals(seededCount + 4, session.messageCount(), "compression must retain the full on-disk history");
        assertEquals(oldFact, session.getMessages().get(0).content());
        assertPersisted(session);
    }

    private Harness harness(ScriptedLlmClient llm, AgentConfig config) {
        return harness(llm, config, new JsonSessionRepository(temp));
    }

    private Harness harness(ScriptedLlmClient llm, AgentConfig config, JsonSessionRepository repo) {
        ToolRegistry tools = new ToolRegistry().register(new WeatherTool()).register(new TodoTool());
        ContextManager context = new ContextManager(config, new SystemPromptBuilder(tools));
        CapturingTraceLogger trace = new CapturingTraceLogger();
        // Do not use AgentFactory: its production wiring creates an HTTP-backed LLM client.
        AgentRuntime runtime = new AgentRuntime(llm, LLM_CONFIG, tools, repo, context, trace, config);
        return new Harness(runtime, repo, context, trace, llm);
    }

    private static AgentReply chatIsolated(Harness harness, Session own, Session other,
                                          String ownMarker, String otherMarker, String input,
                                          String tool, String arguments) {
        int firstRequest = harness.llm().requests().size();
        AgentReply reply = harness.runtime().chat(own, input);
        assertFinal(reply, own, 2, tool);
        assertArguments(reply, 0, arguments);
        List<LlmRequest> requests = harness.llm().requests();
        assertEquals(firstRequest + 2, requests.size());
        for (int i = firstRequest; i < requests.size(); i++) {
            String context = join(requests.get(i));
            assertTrue(context.contains(ownMarker), "request " + i + " must retain its window marker");
            assertFalse(context.contains(otherMarker), "request " + i + " leaked the other window marker");
            assertTrue(requests.get(i).messages().get(0).content().contains("Session: id=" + own.getId()));
            assertFalse(context.contains(other.getId()), "request " + i + " leaked the other session id");
        }
        SessionMessage result = own.getMessages().get(own.messageCount() - 2);
        assertContainsMessage(requests.get(firstRequest + 1), "user", renderedToolResult(result));
        return reply;
    }

    private static void assertFinal(AgentReply reply, Session session, int steps, String... toolNames) {
        assertEquals(AgentStopReason.FINAL, reply.stopReason());
        assertEquals(steps, reply.steps());
        assertEquals(session.getId(), reply.sessionId());
        assertTrue(reply.estimatedContextTokens() > 0);
        assertEquals(List.of(toolNames), reply.toolCalls().stream().map(AgentReply.ToolCallRecord::name).toList());
        assertTrue(reply.toolCalls().stream().allMatch(AgentReply.ToolCallRecord::ok));
        SessionMessage finalMessage = session.getMessages().get(reply.sessionMessageCount() - 1);
        assertEquals(MessageKind.ASSISTANT_FINAL, finalMessage.kind());
        assertEquals(steps, finalMessage.step());
        assertEquals(reply.answer(), finalMessage.content());
    }

    private static void assertArguments(AgentReply reply, int index, String arguments) {
        assertEquals(Json.readTreeUnchecked(arguments), reply.toolCalls().get(index).arguments());
    }

    private static void assertContainsMessage(LlmRequest request, String role, String content) {
        assertTrue(request.messages().contains(new ChatMessage(role, content)),
                "missing expected " + role + " message in emitted LLM context");
    }

    private static void assertTodo(Session session, String text, boolean done) {
        JsonNode todo = session.getToolState().path("todo");
        assertEquals(2, todo.path("nextId").asInt());
        assertEquals(1, todo.path("items").size());
        JsonNode item = todo.path("items").get(0);
        assertEquals(1, item.path("id").asInt());
        assertEquals(text, item.path("text").asText());
        assertEquals(done, item.path("done").asBoolean());
        assertFalse(item.path("createdAt").asText().isBlank());
    }

    private static void assertListedTodo(AgentReply reply, Session session, String text, boolean done) {
        SessionMessage message = session.getMessages().get(reply.sessionMessageCount() - 2);
        assertEquals(MessageKind.TOOL_RESULT, message.kind());
        assertEquals("todo", message.toolName());
        assertTrue(message.toolOk());
        // Parse the full persisted payload, never the potentially truncated audit preview.
        JsonNode result = Json.readTreeUnchecked(message.content());
        assertEquals("list", result.path("action").asText());
        assertEquals(1, result.path("total").asInt());
        assertEquals(done ? 1 : 0, result.path("done").asInt());
        assertEquals(1, result.path("todos").size());
        assertEquals(1, result.path("todos").get(0).path("id").asInt());
        assertEquals(text, result.path("todos").get(0).path("text").asText());
        assertEquals(done, result.path("todos").get(0).path("done").asBoolean());
    }

    private void assertPersisted(Session expected) {
        // A new repository and object make this a disk assertion, not an in-memory alias check.
        Session saved = new JsonSessionRepository(temp).find(expected.getUserId(), expected.getId()).orElseThrow();
        assertNotSame(expected, saved);
        assertEquals(expected.getId(), saved.getId());
        assertEquals(expected.getUserId(), saved.getUserId());
        assertEquals(expected.getTitle(), saved.getTitle());
        assertEquals(expected.getCreatedAt(), saved.getCreatedAt());
        assertEquals(expected.getUpdatedAt(), saved.getUpdatedAt());
        assertMessageSnapshot(expected.getMessages(), saved.getMessages());
        assertEquals(expected.getSummary(), saved.getSummary());
        assertEquals(expected.getCompactedMessages(), saved.getCompactedMessages());
        assertEquals(expected.getToolState(), saved.getToolState());
    }

    private static void assertMessageSnapshot(List<SessionMessage> expected, List<SessionMessage> actual) {
        // Jackson reads a JSON null JsonNode as NullNode, whereas newly constructed messages
        // use Java null. Compare the complete JSON snapshot, including every message field,
        // rather than record equality that treats these equivalent persisted values differently.
        assertEquals(Json.readTreeUnchecked(Json.write(expected)), Json.readTreeUnchecked(Json.write(actual)));
    }

    private static void assertKinds(Session session, MessageKind... kinds) {
        assertEquals(List.of(kinds), session.getMessages().stream().map(SessionMessage::kind).toList());
    }

    private static List<String> calledTools(Harness harness) {
        return harness.trace().events().stream().filter(event -> event.type() == TraceType.TOOL_CALL)
                .map(event -> event.data().get("tool").toString()).toList();
    }

    private static String addArguments(String text) {
        return Json.write(Json.mapper().createObjectNode().put("action", "add").put("text", text));
    }

    private static String renderedToolResult(SessionMessage message) {
        assertEquals(MessageKind.TOOL_RESULT, message.kind());
        assertTrue(message.toolOk());
        return "[TOOL_RESULT name=" + message.toolName() + " ok=" + message.toolOk() + "]\n"
                + message.content() + "\n[/TOOL_RESULT]";
    }

    private static String section(String system, String name) {
        String opening = "[" + name + "]\n";
        String closing = "\n[/" + name + "]";
        int start = system.indexOf(opening);
        assertTrue(start >= 0, "missing system section " + name);
        int end = system.indexOf(closing, start + opening.length());
        assertTrue(end >= 0, "unclosed system section " + name);
        return system.substring(start + opening.length(), end);
    }

    private static String join(LlmRequest request) {
        return String.join("\n", request.messages().stream()
                .map(message -> message.role() + ": " + message.content()).toList());
    }

    private record Harness(AgentRuntime runtime, JsonSessionRepository repo, ContextManager context,
                           CapturingTraceLogger trace, ScriptedLlmClient llm) {
    }
}
