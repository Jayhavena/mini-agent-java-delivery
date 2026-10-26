package dev.nangua.miniagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nangua.miniagent.util.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateful todo list. State lives in the owning session's toolState node, so the
 * same user gets different todo lists in different windows.
 *
 * <p>有状态的待办工具：列表存在所属 session 的 toolState 节点里（key 为 todo），
 * 随 session 一起持久化——同一用户在不同聊天窗口的待办互不可见，重启也不丢。
 * id 由 nextId 递增分配，永不复用，避免 complete/delete 打到旧条目。
 */
public final class TodoTool implements Tool {
    private static final String STATE_KEY = "todo";
    private static final int MAX_TEXT = 500;

    @Override
    public String name() {
        return "todo";
    }

    @Override
    public String description() {
        return "Manage the todo list of the CURRENT chat session. Actions: add, list, complete, delete. "
                + "The list is isolated per session and persists across restarts.";
    }

    @Override
    public ObjectNode parameterSchema() {
        return Schemas.object(
                "Session-scoped todo operations.",
                List.of("action"),
                Schemas.properties(
                        "action", Schemas.enumeration("Operation to perform", "add", "list", "complete", "delete"),
                        "text", Schemas.string("Todo text; required for add"),
                        "id", Schemas.integer("Todo id; required for complete/delete", 1, null),
                        "includeDone", Schemas.bool("For list: include completed items (default true)")
                )
        );
    }

    @Override
    public ToolResult execute(ToolContext context, ObjectNode arguments) {
        String action = arguments.path("action").asText("");
        ObjectNode state = todoState(context.sessionState());
        return switch (action) {
            case "add" -> add(state, arguments);
            case "list" -> list(state, arguments);
            case "complete" -> complete(state, arguments);
            case "delete" -> delete(state, arguments);
            default -> throw new ToolException("'action' must be one of add, list, complete, delete");
        };
    }

    private ToolResult add(ObjectNode state, ObjectNode arguments) {
        String text = arguments.path("text").asText("").trim();
        if (text.isBlank()) {
            throw new ToolException("'text' is required for action=add");
        }
        if (text.length() > MAX_TEXT) {
            throw new ToolException("'text' is too long (max " + MAX_TEXT + " characters)");
        }
        ArrayNode items = items(state);
        int id = state.path("nextId").asInt(1);
        state.put("nextId", id + 1);
        ObjectNode item = items.addObject();
        item.put("id", id);
        item.put("text", text);
        item.put("done", false);
        item.put("createdAt", Instant.now().toString());

        ObjectNode payload = Json.mapper().createObjectNode();
        payload.put("action", "add");
        payload.set("todo", item);
        payload.put("total", items.size());
        return ToolResult.success(Json.write(payload));
    }

    private ToolResult list(ObjectNode state, ObjectNode arguments) {
        boolean includeDone = !arguments.has("includeDone") || arguments.path("includeDone").asBoolean(true);
        ArrayNode items = items(state);
        ArrayNode visible = Json.mapper().createArrayNode();
        int done = 0;
        for (JsonNode item : items) {
            if (item.path("done").asBoolean(false)) {
                done++;
            }
            if (includeDone || !item.path("done").asBoolean(false)) {
                visible.add(item);
            }
        }
        ObjectNode payload = Json.mapper().createObjectNode();
        payload.put("action", "list");
        payload.set("todos", visible);
        payload.put("total", items.size());
        payload.put("done", done);
        return ToolResult.success(Json.write(payload));
    }

    private ToolResult complete(ObjectNode state, ObjectNode arguments) {
        int id = requireId(arguments, "complete");
        for (JsonNode node : items(state)) {
            if (node.path("id").asInt() == id) {
                ((ObjectNode) node).put("done", true);
                ObjectNode payload = Json.mapper().createObjectNode();
                payload.put("action", "complete");
                payload.set("todo", node);
                return ToolResult.success(Json.write(payload));
            }
        }
        throw new ToolException("Todo id " + id + " not found in this session");
    }

    private ToolResult delete(ObjectNode state, ObjectNode arguments) {
        int id = requireId(arguments, "delete");
        ArrayNode items = items(state);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).path("id").asInt() == id) {
                JsonNode removed = items.remove(i);
                ObjectNode payload = Json.mapper().createObjectNode();
                payload.put("action", "delete");
                payload.put("deletedId", id);
                payload.set("todo", removed);
                payload.put("total", items.size());
                return ToolResult.success(Json.write(payload));
            }
        }
        throw new ToolException("Todo id " + id + " not found in this session");
    }

    private static int requireId(ObjectNode arguments, String action) {
        JsonNode idNode = arguments.get("id");
        if (idNode == null || !idNode.canConvertToInt()) {
            throw new ToolException("'id' is required for action=" + action);
        }
        return idNode.asInt();
    }

    private static ObjectNode todoState(ObjectNode sessionState) {
        JsonNode existing = sessionState.get(STATE_KEY);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = sessionState.putObject(STATE_KEY);
        created.put("nextId", 1);
        created.putArray("items");
        return created;
    }

    private static ArrayNode items(ObjectNode state) {
        JsonNode existing = state.get("items");
        if (existing instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode created = state.putArray("items");
        if (!state.has("nextId")) {
            state.put("nextId", 1);
        }
        return created;
    }

    static List<String> texts(ObjectNode sessionState) {
        JsonNode todo = sessionState.get(STATE_KEY);
        if (todo == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : todo.path("items")) {
            result.add(item.path("text").asText());
        }
        return result;
    }
}
