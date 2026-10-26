package dev.nangua.miniagent.session;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nangua.miniagent.tool.TodoTool;
import dev.nangua.miniagent.tool.ToolContext;
import dev.nangua.miniagent.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSessionRepositoryTest {
    @TempDir
    Path temp;

    @Test
    void roundTripsMessagesSummaryAndToolState() {
        JsonSessionRepository repo = new JsonSessionRepository(temp);
        Session session = repo.create("alice", "窗口1");
        session.append(SessionMessage.user("查天气"));
        session.append(SessionMessage.assistantToolCall("需要天气", "weather",
                Json.mapper().createObjectNode().put("city", "杭州"), 1));
        session.append(SessionMessage.toolResult("weather", true, "{\"temp\":23}", 1));
        session.append(SessionMessage.assistantFinal("杭州 23 度", "查询完成", 2));
        session.setSummary("用户想查杭州天气");
        session.setCompactedMessages(1);
        new TodoTool().execute(
                new ToolContext("alice", session.getId(), session.getToolState()),
                Json.mapper().createObjectNode().put("action", "add").put("text", "带伞"));
        repo.save(session);

        Session loaded = repo.find("alice", session.getId()).orElseThrow();
        assertEquals("窗口1", loaded.getTitle());
        assertEquals(4, loaded.messageCount());
        assertEquals("用户想查杭州天气", loaded.getSummary());
        assertEquals(1, loaded.getCompactedMessages());
        assertEquals(MessageKind.TOOL_RESULT, loaded.getMessages().get(2).kind());
        assertEquals("带伞", loaded.getToolState().path("todo").path("items").get(0).path("text").asText());
        assertTrue(Files.isRegularFile(temp.resolve("sessions").resolve("alice")
                .resolve(session.getId() + ".json")));
    }

    @Test
    void sameUserTwoSessionsStayIndependentAcrossReload() {
        JsonSessionRepository repo = new JsonSessionRepository(temp);
        Session window1 = repo.create("alice", "天气窗口");
        Session window2 = repo.create("alice", "周报窗口");
        assertNotEquals(window1.getId(), window2.getId());

        TodoTool todo = new TodoTool();
        todo.execute(new ToolContext("alice", window1.getId(), window1.getToolState()),
                Json.mapper().createObjectNode().put("action", "add").put("text", "窗口1待办"));
        todo.execute(new ToolContext("alice", window2.getId(), window2.getToolState()),
                Json.mapper().createObjectNode().put("action", "add").put("text", "窗口2待办"));
        window1.append(SessionMessage.user("只属于窗口1"));
        repo.save(window1);
        repo.save(window2);

        Session loaded1 = repo.find("alice", window1.getId()).orElseThrow();
        Session loaded2 = repo.find("alice", window2.getId()).orElseThrow();
        assertEquals("窗口1待办", loaded1.getToolState().path("todo").path("items").get(0).path("text").asText());
        assertEquals("窗口2待办", loaded2.getToolState().path("todo").path("items").get(0).path("text").asText());
        assertEquals(1, loaded1.messageCount());
        assertEquals(0, loaded2.messageCount());
    }

    @Test
    void usersAreIsolatedAndIdsAreValidated() {
        JsonSessionRepository repo = new JsonSessionRepository(temp);
        Session alice = repo.create("alice", "A");
        assertTrue(repo.find("bob", alice.getId()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> repo.find("alice", "../escape"));
        assertThrows(IllegalArgumentException.class, () -> repo.find("../bob", alice.getId()));
        assertThrows(IllegalArgumentException.class, () -> repo.create("alice/evil", "x"));
    }

    @Test
    void listSkipsCorruptFileAndSortsByUpdatedAt() throws Exception {
        JsonSessionRepository repo = new JsonSessionRepository(temp);
        Session older = repo.create("alice", "older");
        Session newer = repo.create("alice", "newer");
        older.setUpdatedAt(java.time.Instant.now().minusSeconds(60));
        repo.save(older);
        newer.append(SessionMessage.user("new"));
        repo.save(newer);
        Files.writeString(temp.resolve("sessions").resolve("alice").resolve("bad.json"), "{not-json");

        List<Session> sessions = repo.list("alice");
        assertEquals(2, sessions.size());
        assertEquals(newer.getId(), sessions.get(0).getId());
        assertFalse(sessions.stream().anyMatch(session -> session.getTitle().equals("bad")));
    }

    @Test
    void deleteRemovesOnlyThatSession() {
        JsonSessionRepository repo = new JsonSessionRepository(temp);
        Session one = repo.create("alice", "one");
        Session two = repo.create("alice", "two");
        repo.delete("alice", one.getId());
        assertTrue(repo.find("alice", one.getId()).isEmpty());
        assertTrue(repo.find("alice", two.getId()).isPresent());
    }

    @Test
    void unsafeUserObjectNodeSerializationStillWorks() {
        Session session = new Session("s_x", "alice", "x");
        ObjectNode state = session.getToolState();
        state.put("anything", "value");
        assertEquals("value", Json.readTreeUnchecked(Json.write(session))
                .path("toolState").path("anything").asText());
    }
}
