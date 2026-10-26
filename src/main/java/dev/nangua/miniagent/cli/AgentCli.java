package dev.nangua.miniagent.cli;

import dev.nangua.miniagent.agent.AgentException;
import dev.nangua.miniagent.agent.AgentFactory;
import dev.nangua.miniagent.agent.AgentReply;
import dev.nangua.miniagent.session.MessageKind;
import dev.nangua.miniagent.session.Session;
import dev.nangua.miniagent.session.SessionMessage;
import dev.nangua.miniagent.trace.TraceEvent;
import dev.nangua.miniagent.util.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small REPL for manual testing and the two-window session demo.
 *
 * <p>交互式 REPL：普通输入触发一次完整的 turn（runtime().chat），斜杠命令负责
 * 窗口管理与观测（/new、/use、/sessions、/history、/trace、/state、/compact、
 * /rename）。本类只做分发与展示，不含 agent 逻辑；切换窗口只是替换 this.session
 * 引用，后续 turn 自然落到新窗口，无需通知 runtime。
 */
public final class AgentCli {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final AgentFactory.Wired wired;
    private final String userId;
    private final Path dataDir;
    private final BufferedReader in;
    private final PrintStream out;
    private Session session;

    public AgentCli(AgentFactory.Wired wired, Session session, String userId, Path dataDir) {
        this(wired, session, userId, dataDir,
                new BufferedReader(new InputStreamReader(System.in)), System.out);
    }

    AgentCli(AgentFactory.Wired wired, Session session, String userId, Path dataDir,
             BufferedReader in, PrintStream out) {
        this.wired = wired;
        this.session = session;
        this.userId = userId;
        this.dataDir = dataDir;
        this.in = in;
        this.out = out;
    }

    public void run() {
        banner();
        while (true) {
            out.print("\nYou> ");
            out.flush();
            String line;
            try {
                line = in.readLine();
            } catch (IOException e) {
                out.println("\n[error] 读取输入失败: " + e.getMessage());
                return;
            }
            if (line == null) {
                out.println("\nBye.");
                return;
            }
            String input = line.trim();
            if (input.isEmpty()) {
                continue;
            }
            if (input.startsWith("/")) {
                if (!handleCommand(input)) {
                    return;
                }
                continue;
            }
            handleChat(input);
        }
    }

    private void banner() {
        out.println("============================================================");
        out.println(" MiniAgent Java  |  from-scratch agent runtime");
        out.println("============================================================");
        out.println(" user     : " + userId);
        out.println(" session  : " + session.getId() + "  (" + session.getTitle() + ")");
        out.println(" model    : " + wired.llmConfig().model());
        out.println(" data dir : " + dataDir);
        out.println(" tools    : " + String.join(", ", wired.tools().names()));
        out.println(" commands : /new [title] /use <id> /sessions /history /trace /state /compact /help /quit");
        out.println("============================================================");
    }

    /**
     * 执行一次 turn 并打印工具轨迹与统计。
     * 失败分两级提示：AgentException 视为业务侧失败，以 [fatal] 提示；其余
     * RuntimeException 以 [error] 提示并附类名便于排查。两者都只打印、不退出 REPL。
     */
    private void handleChat(String input) {
        out.println("\n[running] 正在调用模型...");
        long started = System.nanoTime();
        try {
            AgentReply reply = wired.runtime().chat(session, input);
            for (AgentReply.ToolCallRecord call : reply.toolCalls()) {
                out.printf("  [tool] %s %s (%d ms)%n",
                        call.name(),
                        call.ok() ? "ok" : "failed",
                        call.durationMs());
                out.println("         args: " + Json.preview(Json.write(call.arguments()), 180));
                if (!call.resultPreview().isBlank()) {
                    out.println("         result: " + call.resultPreview());
                }
            }
            out.println("\nAgent> " + reply.answer());
            out.printf("  [stats] steps=%d tools=%d stop=%s tokens~%d time=%dms%n",
                    reply.steps(), reply.toolCalls().size(), reply.stopReason(),
                    reply.estimatedContextTokens(), reply.elapsedMs());
            if (reply.stopReason() != dev.nangua.miniagent.agent.AgentStopReason.FINAL) {
                out.println("  [note] 本轮由 runtime 兜底结束，详情见 /trace");
            }
        } catch (AgentException e) {
            out.println("[fatal] " + e.getMessage());
        } catch (RuntimeException e) {
            out.println("[error] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        long elapsed = (System.nanoTime() - started) / 1_000_000L;
        out.printf("  [elapsed] %d ms%n", elapsed);
    }

    /** @return false when the REPL should exit. */
    private boolean handleCommand(String input) {
        String[] parts = input.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String argument = parts.length > 1 ? parts[1].trim() : "";
        switch (command) {
            case "/quit", "/exit", "/q" -> {
                out.println("Bye.");
                return false;
            }
            case "/help" -> printHelp();
            case "/new" -> newSession(argument);
            case "/use" -> useSession(argument);
            case "/sessions" -> listSessions();
            case "/history" -> printHistory(argument);
            case "/trace" -> printTrace(argument);
            case "/state" -> printState();
            case "/compact" -> compact();
            case "/rename" -> rename(argument);
            default -> out.println("[error] 未知命令 " + command + "，输入 /help 查看帮助。");
        }
        return true;
    }

    private void newSession(String title) {
        String finalTitle = title.isBlank() ? "窗口 " + TIME.format(java.time.LocalDateTime.now()) : title;
        session = wired.sessions().create(userId, finalTitle);
        out.println("[session] 已新建并切换: " + session.getId() + " (" + session.getTitle() + ")");
        wired.trace().log(TraceEvent.of(session, dev.nangua.miniagent.trace.TraceType.SESSION_CREATED,
                TraceEvent.data("source", "cli-command", "title", session.getTitle())));
    }

    /**
     * 切换会话：先按完整 sessionId 精确查找，找不到再按 id 前缀匹配。
     * 前缀恰好命中一个才切换；无命中或有多个候选时只提示错误，当前会话保持不变。
     */
    private void useSession(String token) {
        if (token.isBlank()) {
            out.println("[error] 用法: /use <session id 或前缀>");
            return;
        }
        var exact = wired.sessions().find(userId, token);
        if (exact.isPresent()) {
            session = exact.get();
            out.println("[session] 已切换到: " + session.getId() + " (" + session.getTitle() + ")");
            return;
        }
        List<Session> matches = new ArrayList<>();
        for (Session candidate : wired.sessions().list(userId)) {
            if (candidate.getId().startsWith(token)) {
                matches.add(candidate);
            }
        }
        if (matches.size() == 1) {
            session = matches.get(0);
            out.println("[session] 已切换到: " + session.getId() + " (" + session.getTitle() + ")");
        } else if (matches.isEmpty()) {
            out.println("[error] 没有匹配的 session。");
        } else {
            out.println("[error] 前缀不唯一: " + matches.stream().map(Session::getId).toList());
        }
    }

    private void listSessions() {
        List<Session> sessions = wired.sessions().list(userId);
        if (sessions.isEmpty()) {
            out.println("[session] 没有会话。");
            return;
        }
        out.println("Sessions for " + userId + ":");
        for (Session candidate : sessions) {
            out.printf("  %s %-22s msgs=%-3d updated=%s%n",
                    candidate.getId().equals(session.getId()) ? "*" : " ",
                    candidate.getId() + " " + candidate.getTitle(),
                    candidate.messageCount(),
                    TIME.format(java.time.LocalDateTime.ofInstant(
                            candidate.getUpdatedAt(), ZoneId.systemDefault())));
        }
    }

    private void printHistory(String argument) {
        int limit = parseLimit(argument, 20);
        List<SessionMessage> messages = session.getMessages();
        int from = Math.max(0, messages.size() - limit);
        out.println("History (" + from + ".." + (messages.size() - 1) + " of "
                + messages.size() + "), compacted=" + session.getCompactedMessages());
        for (int i = from; i < messages.size(); i++) {
            SessionMessage message = messages.get(i);
            out.printf("%s  %-20s %s%n",
                    TIME.format(java.time.LocalDateTime.ofInstant(message.createdAt(), ZoneId.systemDefault())),
                    message.kind() + (message.toolName().isBlank() ? "" : ":" + message.toolName()),
                    preview(message));
        }
    }

    private static String preview(SessionMessage message) {
        if (message.kind() == MessageKind.ASSISTANT_TOOL_CALL) {
            return "args=" + Json.preview(Json.write(message.arguments()), 160);
        }
        String content = message.content().replaceAll("\\s+", " ").trim();
        return content.length() <= 220 ? content : content.substring(0, 220) + "...";
    }

    private void printTrace(String argument) {
        int limit = parseLimit(argument, 20);
        List<TraceEvent> events = wired.trace().tail(session.getId(), limit);
        if (events.isEmpty()) {
            out.println("[trace] 暂无事件。");
            return;
        }
        for (TraceEvent event : events) {
            out.printf("%s  %-16s %s%n",
                    TIME.format(java.time.LocalDateTime.ofInstant(event.timestamp(), ZoneId.systemDefault())),
                    event.type(),
                    Json.preview(Json.write(event.data()), 260));
        }
        out.println("[trace] JSONL: " + dataDir.resolve("traces").resolve(session.getId() + ".jsonl"));
    }

    private void printState() {
        out.println("session: " + session.getId());
        out.println("title  : " + session.getTitle());
        out.println("summary: " + (session.getSummary().isBlank() ? "<empty>" : session.getSummary()));
        out.println("state  : " + Json.pretty(session.getToolState()));
        out.println("tokens : ~" + wired.context().estimateFullPrompt(session));
    }

    private void compact() {
        int before = wired.context().estimateFullPrompt(session);
        boolean changed = wired.context().forceCompact(session, wired.trace());
        if (changed) {
            wired.sessions().save(session);
            int after = wired.context().estimateFullPrompt(session);
            out.println("[compact] 已压缩，估算 tokens: " + before + " -> " + after
                    + "，保留最近消息，compacted=" + session.getCompactedMessages());
        } else {
            out.println("[compact] 没有可压缩的旧消息。");
        }
    }

    private void rename(String title) {
        if (title.isBlank()) {
            out.println("[error] 用法: /rename <新标题>");
            return;
        }
        session.setTitle(title);
        wired.sessions().save(session);
        out.println("[session] 标题已更新为: " + title);
    }

    private void printHelp() {
        out.println("""
                命令:
                  /new [title]     新建并切换一个独立窗口
                  /use <id|prefix> 切换到已有窗口
                  /sessions        列出当前用户的窗口
                  /history [n]     查看最近 n 条持久化消息
                  /trace [n]       查看最近 n 条执行事件
                  /state           查看摘要和工具状态
                  /compact         手动压缩早期 context
                  /rename <title>  修改窗口标题
                  /help            帮助
                  /quit            退出

                提示: 两个终端分别使用 --session 或 /new，即可模拟同一用户的两个独立窗口。
                """);
    }

    private static int parseLimit(String argument, int fallback) {
        if (argument == null || argument.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(1, Math.min(100, Integer.parseInt(argument.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
