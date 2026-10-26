package dev.nangua.miniagent.agent;

import dev.nangua.miniagent.llm.LlmClient;
import dev.nangua.miniagent.llm.LlmException;
import dev.nangua.miniagent.llm.LlmRequest;
import dev.nangua.miniagent.llm.LlmResponse;
import dev.nangua.miniagent.util.Json;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Deterministic model double for loop, retry and memory tests. */
final class ScriptedLlmClient implements LlmClient {
    private final Deque<Object> script = new ArrayDeque<>();
    private final List<LlmRequest> requests = new ArrayList<>();

    ScriptedLlmClient then(String content) {
        script.addLast(new LlmResponse(content, "", "stop", 10, 10, "scripted"));
        return this;
    }

    ScriptedLlmClient then(RuntimeException error) {
        script.addLast(error);
        return this;
    }

    ScriptedLlmClient tool(String name, String argumentsJson) {
        var node = Json.mapper().createObjectNode();
        node.put("type", "tool_call");
        node.put("thought", "need " + name);
        node.put("tool", name);
        node.set("arguments", Json.readTreeUnchecked(argumentsJson));
        return then(Json.write(node));
    }

    ScriptedLlmClient finalAnswer(String answer) {
        var node = Json.mapper().createObjectNode();
        node.put("type", "final");
        node.put("thought", "done");
        node.put("answer", answer);
        return then(Json.write(node));
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        requests.add(request);
        if (script.isEmpty()) {
            throw new AssertionError("Scripted LLM has no response left");
        }
        Object next = script.removeFirst();
        if (next instanceof LlmResponse response) {
            return response;
        }
        if (next instanceof RuntimeException error) {
            throw error;
        }
        throw new AssertionError("Unsupported scripted response: " + next);
    }

    List<LlmRequest> requests() {
        return List.copyOf(requests);
    }

    int remaining() {
        return script.size();
    }

    static LlmException httpError() {
        return new LlmException("simulated provider failure", 500);
    }
}
