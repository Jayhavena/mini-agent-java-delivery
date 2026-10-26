package dev.nangua.miniagent.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nangua.miniagent.util.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TodoAndRegistryTest {
    private final TodoTool todo = new TodoTool();

    @Test
    void addListCompleteDelete() throws Exception {
        ObjectNode state = Json.mapper().createObjectNode();
        ToolContext context = new ToolContext("alice", "s_1", state);

        ToolResult added = todo.execute(context, args("action", "add", "text", "明天带伞"));
        assertTrue(added.ok());
        assertEquals(1, Json.readTreeUnchecked(added.content()).path("todo").path("id").asInt());

        todo.execute(context, args("action", "add", "text", "周五发周报"));
        ToolResult list = todo.execute(context, args("action", "list"));
        assertEquals(2, Json.readTreeUnchecked(list.content()).path("total").asInt());
        assertEquals("明天带伞",
                Json.readTreeUnchecked(list.content()).path("todos").get(0).path("text").asText());

        ToolResult completed = todo.execute(context, args("action", "complete", "id", 1));
        assertTrue(Json.readTreeUnchecked(completed.content()).path("todo").path("done").asBoolean());

        ToolResult openOnly = todo.execute(context,
                Json.mapper().createObjectNode().put("action", "list").put("includeDone", false));
        var openJson = Json.readTreeUnchecked(openOnly.content());
        assertEquals(1, openJson.path("todos").size());
        assertEquals(2, openJson.path("total").asInt());

        todo.execute(context, args("action", "delete", "id", 1));
        assertEquals(1, state.path("todo").path("items").size());
    }

    @Test
    void twoSessionsHaveIndependentLists() {
        ObjectNode stateA = Json.mapper().createObjectNode();
        ObjectNode stateB = Json.mapper().createObjectNode();
        todo.execute(new ToolContext("alice", "s_a", stateA), args("action", "add", "text", "A 的待办"));
        todo.execute(new ToolContext("alice", "s_b", stateB), args("action", "add", "text", "B 的待办"));

        assertEquals("A 的待办", stateA.path("todo").path("items").get(0).path("text").asText());
        assertEquals("B 的待办", stateB.path("todo").path("items").get(0).path("text").asText());
        assertEquals(1, stateA.path("todo").path("items").size());
        assertEquals(1, stateB.path("todo").path("items").size());
    }

    @Test
    void rejectsInvalidTodoArguments() {
        ToolContext context = new ToolContext("alice", "s", Json.mapper().createObjectNode());
        assertThrows(ToolException.class, () -> todo.execute(context, args("action", "add")));
        assertThrows(ToolException.class, () -> todo.execute(context, args("action", "complete", "id", 99)));
        assertThrows(ToolException.class, () -> todo.execute(context, args("action", "nope")));
    }

    @Test
    void registryRejectsDuplicateAndUnknownTools() {
        ToolRegistry registry = new ToolRegistry().register(new CalculatorTool());
        assertThrows(IllegalArgumentException.class, () -> registry.register(new CalculatorTool()));
        assertEquals(1, registry.size());
        assertTrue(registry.specs().get(0).parameters().has("properties"));
        ToolException unknown = assertThrows(ToolException.class,
                () -> registry.execute("nope", new ToolContext("u", "s", Json.mapper().createObjectNode()),
                        Json.mapper().createObjectNode()));
        assertTrue(unknown.getMessage().contains("calculator"));
    }

    @Test
    void searchAndWeatherAreMockButStructured() throws Exception {
        SearchTool search = new SearchTool();
        ToolResult result = search.execute(new ToolContext("u", "s", Json.mapper().createObjectNode()),
                args("query", "杭州天气", "limit", 2));
        assertTrue(result.ok());
        var json = Json.readTreeUnchecked(result.content());
        assertEquals("mock-search", json.path("source").asText());
        assertFalse(json.path("results").isEmpty());
        assertTrue(json.path("results").get(0).path("url").asText().startsWith("mock://"));

        WeatherTool weather = new WeatherTool();
        ToolResult weatherResult = weather.execute(
                new ToolContext("u", "s", Json.mapper().createObjectNode()),
                args("city", "杭州"));
        var weatherJson = Json.readTreeUnchecked(weatherResult.content());
        assertEquals("杭州", weatherJson.path("city").asText());
        assertTrue(weatherJson.path("isMock").asBoolean());
        assertEquals("°C", weatherJson.path("unit").asText());
    }

    @Test
    void readDocsBlocksTraversalAndMissingFiles() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("mini-agent-docs");
        java.nio.file.Files.writeString(dir.resolve("ok.md"), "# hello");
        ReadDocsTool tool = new ReadDocsTool(dir);
        ToolContext context = new ToolContext("u", "s", Json.mapper().createObjectNode());

        ToolResult ok = tool.execute(context, args("path", "ok.md"));
        assertEquals("# hello", Json.readTreeUnchecked(ok.content()).path("content").asText());
        assertThrows(ToolException.class, () -> tool.execute(context, args("path", "../secret.md")));
        assertThrows(ToolException.class, () -> tool.execute(context, args("path", "missing.md")));
    }

    private static ObjectNode args(Object... keyValues) {
        ObjectNode node = Json.mapper().createObjectNode();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = String.valueOf(keyValues[i]);
            Object value = keyValues[i + 1];
            if (value instanceof Integer integer) {
                node.put(key, integer);
            } else if (value instanceof Boolean bool) {
                node.put(key, bool);
            } else {
                node.put(key, String.valueOf(value));
            }
        }
        return node;
    }
}
