package dev.nangua.miniagent.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.nangua.miniagent.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Append-only JSONL trace, one file per session.
 *
 * <p>按「一个 session 一个 .jsonl 文件」追加写，同一 JVM 内用一把全局锁串行化
 * （锁不跨进程）。核心取舍：写失败只告警、绝不抛出——trace 永远不能打断
 * agent 的 turn。已知限制：内容不做脱敏、文件不做轮转，敏感参数会原样进日志。
 */
public final class JsonlTraceLogger implements TraceLogger {
    private static final int MAX_TAIL_BYTES = 1_000_000;
    private final Path root;
    private final ObjectMapper mapper;
    private final ReentrantLock lock = new ReentrantLock();

    public JsonlTraceLogger(Path dataRoot) {
        this(dataRoot.resolve("traces"), Json.mapper());
    }

    public JsonlTraceLogger(Path root, ObjectMapper mapper) {
        this.root = root.toAbsolutePath().normalize();
        this.mapper = mapper;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create trace directory " + this.root, e);
        }
    }

    @Override
    public void log(TraceEvent event) {
        Path file = fileFor(event.sessionId());
        lock.lock();
        try {
            Files.createDirectories(root);
            String line = mapper.writeValueAsString(event) + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Tracing must never break the agent turn.
            System.err.println("[warn] cannot write trace event: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 回看指定 session 最近的事件。
     * 边界：出于性能只读文件末尾最多 1 MB（MAX_TAIL_BYTES），limit 再大也拿不到
     * 窗口之外更早的事件；写到一半的残行（如进程中途被杀）会被静默跳过。
     * 文件不存在、limit 非正数或读取失败都返回空列表，不抛异常。
     */
    @Override
    public List<TraceEvent> tail(String sessionId, int limit) {
        Path file = fileFor(sessionId);
        if (!Files.isRegularFile(file) || limit <= 0) {
            return List.of();
        }
        lock.lock();
        try {
            List<String> lines = readTailLines(file);
            Deque<TraceEvent> events = new ArrayDeque<>();
            for (String line : lines) {
                try {
                    events.addLast(mapper.readValue(line, TraceEvent.class));
                    if (events.size() > limit) {
                        events.removeFirst();
                    }
                } catch (IOException ignored) {
                    // A partially written final line is not fatal.
                }
            }
            return new ArrayList<>(events);
        } catch (IOException e) {
            System.err.println("[warn] cannot read trace: " + e.getMessage());
            return List.of();
        } finally {
            lock.unlock();
        }
    }

    public Path fileFor(String sessionId) {
        String safe = sessionId == null ? "unknown" : sessionId.replaceAll("[^A-Za-z0-9_-]", "_");
        return root.resolve(safe + ".jsonl");
    }

    private static List<String> readTailLines(Path file) throws IOException {
        long size = Files.size(file);
        long start = Math.max(0, size - MAX_TAIL_BYTES);
        try (var channel = Files.newByteChannel(file)) {
            channel.position(start);
            byte[] bytes = new byte[(int) (size - start)];
            var buffer = java.nio.ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                // keep reading
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (start > 0) {
                // 从 1 MB 窗口中间起读时，首行几乎必然被截断，丢弃到第一个换行符，只保留完整行。
                int firstNewline = text.indexOf('\n');
                text = firstNewline >= 0 ? text.substring(firstNewline + 1) : "";
            }
            return text.lines().filter(line -> !line.isBlank()).toList();
        }
    }
}
