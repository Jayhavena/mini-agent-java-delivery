package dev.nangua.miniagent.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nangua.miniagent.util.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorToolTest {
    private final CalculatorTool tool = new CalculatorTool();
    private final ToolContext context = new ToolContext("alice", "s_test", Json.mapper().createObjectNode());

    @Test
    void exposesNameDescriptionAndSchema() {
        assertEquals("calculator", tool.name());
        assertTrue(tool.description().contains("arithmetic"));
        assertEquals("object", tool.parameterSchema().path("type").asText());
        assertTrue(tool.parameterSchema().path("required").toString().contains("expression"));
    }

    @Test
    void evaluatesAndReturnsJson() {
        ObjectNode args = Json.mapper().createObjectNode().put("expression", "(12.5 + 3) * 4 / 2");
        ToolResult result = tool.execute(context, args);
        assertTrue(result.ok());
        assertEquals("31", Json.readTreeUnchecked(result.content()).path("result").asText());
    }

    @Test
    void rejectsBadArguments() {
        assertThrows(ToolException.class,
                () -> tool.execute(context, Json.mapper().createObjectNode()));
        assertThrows(ToolException.class,
                () -> tool.execute(context, Json.mapper().createObjectNode().put("expression", 42)));
        assertThrows(ToolException.class,
                () -> tool.execute(context, Json.mapper().createObjectNode().put("expression", "1/0")));
    }
}
