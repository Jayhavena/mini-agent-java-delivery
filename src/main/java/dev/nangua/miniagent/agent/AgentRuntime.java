package dev.nangua.miniagent.agent;

import dev.nangua.miniagent.context.ContextManager;
import dev.nangua.miniagent.context.ContextWindow;
import dev.nangua.miniagent.llm.LlmClient;
import dev.nangua.miniagent.llm.LlmConfig;
import dev.nangua.miniagent.llm.LlmException;
import dev.nangua.miniagent.llm.LlmRequest;
import dev.nangua.miniagent.llm.LlmResponse;
import dev.nangua.miniagent.parse.AgentOutput;
import dev.nangua.miniagent.parse.AgentOutputParser;
import dev.nangua.miniagent.parse.ParseException;
import dev.nangua.miniagent.session.Session;
import dev.nangua.miniagent.session.SessionMessage;
import dev.nangua.miniagent.session.SessionRepository;
import dev.nangua.miniagent.session.SessionStoreException;
import dev.nangua.miniagent.tool.ToolContext;
import dev.nangua.miniagent.tool.ToolException;
import dev.nangua.miniagent.tool.ToolRegistry;
import dev.nangua.miniagent.tool.ToolResult;
import dev.nangua.miniagent.trace.TraceEvent;
import dev.nangua.miniagent.trace.TraceLogger;
import dev.nangua.miniagent.trace.TraceType;
import dev.nangua.miniagent.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The whole agent runtime. No framework: one explicit loop, one JSON protocol,
 * a tool registry, persisted session memory, hard limits and trace events.
 *
 * <p>整个 Agent 运行时的核心。一个 turn（一次 {@link #chat} 调用）内维护一个显式的
 * step 循环：每步重建上下文、请求一次模型、把响应解析成协议 JSON，结果只有两种——
 * final（最终答案，turn 结束）或 tool_call（执行工具、结果落进 session，进入下一个
 * step 让模型基于结果继续决策）。三个独立计数器（maxSteps / maxToolRounds /
 * maxParseRetries）保证任何异常行为都会终止并给出兜底回答。
 */
public final class AgentRuntime {
    private final LlmClient llm;
    private final LlmConfig llmConfig;
    private final ToolRegistry tools;
    private final SessionRepository sessions;
    private final ContextManager context;
    private final TraceLogger trace;
    private final AgentConfig config;
    private final AgentOutputParser parser = new AgentOutputParser();

    public AgentRuntime(LlmClient llm,
                        LlmConfig llmConfig,
                        ToolRegistry tools,
                        SessionRepository sessions,
                        ContextManager context,
                        TraceLogger trace,
                        AgentConfig config) {
        this.llm = Objects.requireNonNull(llm, "llm");
        this.llmConfig = Objects.requireNonNull(llmConfig, "llmConfig");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.context = Objects.requireNonNull(context, "context");
        this.trace = Objects.requireNonNull(trace, "trace");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * 处理一个 turn：从用户输入到最终 {@link AgentReply}。
     *
     * <p>一次调用内可能发起多次模型请求（step）：模型每返回一个 tool_call，就执行工具、
     * 把 TOOL_RESULT 写进 session，然后进入下一个 step，直到模型返回 final 或触发某个
     * 上限。所有出口（包括失败兜底）都会落盘一条 ASSISTANT_FINAL，保证会话总有终点、
     * 崩溃后可恢复。
     */
    public AgentReply chat(Session session, String userInput) {
        Objects.requireNonNull(session, "session");
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("userInput must not be blank");
        }
        long started = System.nanoTime();
        // 用户输入在任何模型调用之前先落盘：后续哪怕全部失败，也不会丢用户说了什么。
        session.append(SessionMessage.user(userInput.trim()));
        persist(session, "user message");
        trace.log(TraceEvent.of(session, TraceType.TURN_START, TraceEvent.data(
                "chars", userInput.length(),
                "messageCount", session.messageCount()
        )));

        if (context.maybeCompact(session, trace)) {
            persist(session, "compaction");
        }

        // 以下计数器都是 turn 内的局部状态：跨 turn 重置，也不落盘。
        List<AgentReply.ToolCallRecord> toolRecords = new ArrayList<>();
        int steps = 0;          // 已发起的 LLM 请求数（解析失败重试同样消耗 step）
        int toolRounds = 0;     // 已执行的工具次数，失败也计入配额
        int parseFailures = 0;  // 本轮累计解析失败次数；成功后不清零，语义是“累计”而非“连续”
        ContextWindow lastWindow = null;

        for (int step = 1; step <= config.maxSteps(); step++) {
            steps = step;
            // 模型没有跨请求记忆，每个 step 都必须从 session 重建完整上下文；
            // 上一步写入的 TOOL_RESULT / 纠错 note 正是在这里进入本次请求的。
            lastWindow = context.build(session);
            trace.log(TraceEvent.of(session, TraceType.CONTEXT_BUILT, TraceEvent.data(
                    "step", step,
                    "messages", lastWindow.messages().size(),
                    "estimatedTokens", lastWindow.estimatedTokens(),
                    "summaryChars", lastWindow.summaryChars()
            )));

            LlmResponse response;
            long llmStarted = System.nanoTime();
            trace.log(TraceEvent.of(session, TraceType.LLM_REQUEST, TraceEvent.data(
                    "step", step,
                    "model", llmConfig.model(),
                    "messages", lastWindow.messages().size(),
                    "estimatedTokens", lastWindow.estimatedTokens()
            )));
            try {
                // 第四个参数固定为 true（请求 JSON 模式），但真正是否下发
                // response_format 还取决于 LlmConfig.jsonMode，两者是与门。
                response = llm.complete(new LlmRequest(
                        lastWindow.messages(),
                        llmConfig.temperature(),
                        llmConfig.maxOutputTokens(),
                        true
                ));
            } catch (LlmException e) {
                trace.log(TraceEvent.of(session, TraceType.ERROR, TraceEvent.data(
                        "stage", "llm",
                        "status", e.httpStatus(),
                        "message", e.getMessage()
                )));
                return fallback(session, started, steps, toolRecords, AgentStopReason.LLM_ERROR,
                        "模型调用失败：" + Json.preview(e.getMessage(), 300)
                                + "。请检查 API Key、余额和网络，或稍后重试。");
            }
            long llmMs = (System.nanoTime() - llmStarted) / 1_000_000L;
            trace.log(TraceEvent.of(session, TraceType.LLM_RESPONSE, TraceEvent.data(
                    "step", step,
                    "latencyMs", llmMs,
                    "finishReason", response.finishReason(),
                    "promptTokens", response.promptTokens(),
                    "completionTokens", response.completionTokens(),
                    "contentPreview", Json.preview(response.content(), 220)
            )));

            AgentOutput output;
            try {
                output = parser.parse(response);
            } catch (ParseException e) {
                parseFailures++;
                trace.log(TraceEvent.of(session, TraceType.PARSE_ERROR, TraceEvent.data(
                        "step", step,
                        "failure", parseFailures,
                        "error", e.getMessage()
                )));
                // 用 > 而非 >=：maxParseRetries 的语义是“重试次数”，
                // 总失败上限是 maxParseRetries + 1（默认 2，即第 3 次失败触发兜底）。
                if (parseFailures > config.maxParseRetries()) {
                    return fallback(session, started, steps, toolRecords, AgentStopReason.PARSE_ERROR,
                            "模型连续返回了无法解析的工具协议，本轮已停止。请重试或更换模型。");
                }
                // 纠错提示作为 CONTEXT_NOTE 落盘，下一轮以 system 消息回灌给模型；
                // 它会一直留在 session 历史里，直到被上下文压缩合并掉。
                String correction = "[PROTOCOL_CORRECTION] Your previous response could not be parsed: "
                        + e.getMessage()
                        + "\nReply with exactly one JSON object using the tool_call/final protocol. "
                        + "No markdown fences, no prose.";
                session.append(SessionMessage.contextNote(correction));
                persist(session, "parse correction");
                continue;
            }

            if (output instanceof AgentOutput.FinalAnswer finalAnswer) {
                trace.log(TraceEvent.of(session, TraceType.PARSE_OK, TraceEvent.data(
                        "step", step, "type", "final", "thought", finalAnswer.thought()
                )));
                session.append(SessionMessage.assistantFinal(finalAnswer.thought(),
                        finalAnswer.answer(), step));
                persist(session, "final answer");
                AgentReply reply = new AgentReply(
                        finalAnswer.answer(), finalAnswer.thought(), toolRecords, steps,
                        AgentStopReason.FINAL, elapsedMs(started),
                        lastWindow.estimatedTokens(), session.messageCount(), session.getId()
                );
                trace.log(TraceEvent.of(session, TraceType.TURN_END, TraceEvent.data(
                        "step", step,
                        "stopReason", reply.stopReason(),
                        "toolCalls", toolRecords.size(),
                        "elapsedMs", reply.elapsedMs()
                )));
                if (context.maybeCompact(session, trace)) {
                    persist(session, "post-turn compaction");
                }
                return reply;
            }

            AgentOutput.ToolCall call = (AgentOutput.ToolCall) output;
            trace.log(TraceEvent.of(session, TraceType.PARSE_OK, TraceEvent.data(
                    "step", step, "type", "tool_call",
                    "tool", call.toolName(), "thought", call.thought()
            )));
            // 上限检查必须放在 append 之前：否则会落盘一条“有 tool_call、没有
            // tool_result”的悬空消息，下一轮的上下文就不完整了。
            if (toolRounds >= config.maxToolRounds()) {
                return fallback(session, started, steps, toolRecords, AgentStopReason.MAX_TOOL_ROUNDS,
                        "本轮已达到工具调用上限（" + config.maxToolRounds()
                                + " 次），我先停止继续调用工具。可以缩小问题范围后再试。");
            }

            session.append(SessionMessage.assistantToolCall(call.thought(), call.toolName(),
                    call.arguments(), step));
            persist(session, "tool call");
            trace.log(TraceEvent.of(session, TraceType.TOOL_CALL, TraceEvent.data(
                    "step", step,
                    "tool", call.toolName(),
                    "arguments", Json.write(call.arguments())
            )));

            long toolStarted = System.nanoTime();
            ToolResult result;
            try {
                result = tools.execute(call.toolName(),
                        new ToolContext(session.getUserId(), session.getId(), session.getToolState()),
                        call.arguments());
            } catch (ToolException e) {
                result = ToolResult.failure("Tool error: " + e.getMessage());
                trace.log(TraceEvent.of(session, TraceType.TOOL_ERROR, TraceEvent.data(
                        "step", step, "tool", call.toolName(), "message", e.getMessage()
                )));
            } catch (RuntimeException e) {
                // 只回灌异常类名：不把堆栈和内部消息带进模型上下文，避免实现细节泄漏。
                result = ToolResult.failure("Tool crashed: " + e.getClass().getSimpleName());
                trace.log(TraceEvent.of(session, TraceType.TOOL_ERROR, TraceEvent.data(
                        "step", step, "tool", call.toolName(),
                        "message", e.getClass().getSimpleName() + ": " + e.getMessage()
                )));
            }
            long toolMs = (System.nanoTime() - toolStarted) / 1_000_000L;
            // 在执行之后才计数：失败的调用同样消耗工具配额，防止反复失败形成死循环。
            toolRounds++;
            session.append(SessionMessage.toolResult(call.toolName(), result.ok(),
                    result.content(), step));
            persist(session, "tool result");
            toolRecords.add(new AgentReply.ToolCallRecord(
                    call.toolName(), call.arguments(), result.ok(),
                    Json.preview(result.content(), 300), toolMs
            ));
            trace.log(TraceEvent.of(session, TraceType.TOOL_RESULT, TraceEvent.data(
                    "step", step,
                    "tool", call.toolName(),
                    "ok", result.ok(),
                    "durationMs", toolMs,
                    "preview", Json.preview(result.content(), 300)
            )));
        }

        return fallback(session, started, steps, toolRecords, AgentStopReason.MAX_STEPS,
                "本轮已达到最大推理步数（" + config.maxSteps() + " 步），已停止。");
    }

    /**
     * 所有非正常终止（LLM 失败、解析超限、各类上限）的统一出口：补一条 ASSISTANT_FINAL
     * 落盘并返回兜底 AgentReply，保证会话始终有终点、用户总能看到一段可读文案，
     * 而不是异常或空回复。此时上下文未构建，estimatedContextTokens 记为 -1。
     */
    private AgentReply fallback(Session session, long started, int steps,
                                List<AgentReply.ToolCallRecord> toolRecords,
                                AgentStopReason reason, String answer) {
        String thought = "runtime fallback: " + reason;
        session.append(SessionMessage.assistantFinal(thought, answer, steps));
        persist(session, "fallback answer");
        trace.log(TraceEvent.of(session, reason == AgentStopReason.LLM_ERROR
                        ? TraceType.ERROR : TraceType.TURN_LIMIT, TraceEvent.data(
                "reason", reason, "steps", steps, "toolCalls", toolRecords.size()
        )));
        AgentReply reply = new AgentReply(answer, thought, toolRecords, steps, reason,
                elapsedMs(started), -1, session.messageCount(), session.getId());
        trace.log(TraceEvent.of(session, TraceType.TURN_END, TraceEvent.data(
                "stopReason", reason, "steps", steps, "toolCalls", toolRecords.size(),
                "elapsedMs", reply.elapsedMs()
        )));
        return reply;
    }

    private void persist(Session session, String reason) {
        try {
            sessions.save(session);
        } catch (SessionStoreException e) {
            throw new AgentException("Cannot persist session after " + reason, e);
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
