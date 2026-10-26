package dev.nangua.miniagent.agent;

import dev.nangua.miniagent.trace.TraceEvent;
import dev.nangua.miniagent.trace.TraceLogger;
import dev.nangua.miniagent.trace.TraceType;

import java.util.ArrayList;
import java.util.List;

final class CapturingTraceLogger implements TraceLogger {
    private final List<TraceEvent> events = new ArrayList<>();

    @Override
    public void log(TraceEvent event) {
        events.add(event);
    }

    @Override
    public List<TraceEvent> tail(String sessionId, int limit) {
        List<TraceEvent> filtered = events.stream()
                .filter(event -> event.sessionId().equals(sessionId))
                .toList();
        return filtered.subList(Math.max(0, filtered.size() - limit), filtered.size());
    }

    List<TraceEvent> events() {
        return List.copyOf(events);
    }

    boolean has(TraceType type) {
        return events.stream().anyMatch(event -> event.type() == type);
    }

    long count(TraceType type) {
        return events.stream().filter(event -> event.type() == type).count();
    }
}
