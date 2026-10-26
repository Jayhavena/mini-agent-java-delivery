package dev.nangua.miniagent.trace;

import dev.nangua.miniagent.session.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonlTraceLoggerTest {
    @TempDir
    Path temp;

    @Test
    void appendsAndReadsJsonlTrace() {
        JsonlTraceLogger logger = new JsonlTraceLogger(temp);
        Session session = new Session("s_trace", "alice", "trace");
        logger.log(TraceEvent.of(session, TraceType.TURN_START,
                TraceEvent.data("chars", 4, "messageCount", 1)));
        logger.log(TraceEvent.of(session, TraceType.TOOL_CALL,
                TraceEvent.data("tool", "calculator", "step", 1)));

        List<TraceEvent> tail = logger.tail("s_trace", 10);
        assertEquals(2, tail.size());
        assertEquals(TraceType.TURN_START, tail.get(0).type());
        assertEquals("calculator", tail.get(1).data().get("tool"));
        assertTrue(Files.isRegularFile(logger.fileFor("s_trace")));

        assertEquals(1, logger.tail("s_trace", 1).size());
        assertEquals(TraceType.TOOL_CALL, logger.tail("s_trace", 1).get(0).type());
    }
}
