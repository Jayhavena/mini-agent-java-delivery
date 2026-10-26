package dev.nangua.miniagent.parse;

import dev.nangua.miniagent.llm.LlmResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOutputParserTest {
    private final AgentOutputParser parser = new AgentOutputParser();

    @Test
    void parsesFinalAnswer() {
        AgentOutput output = parser.parse("""
                {"type":"final","thought":"用户只是打招呼","answer":"你好，我是 MiniAgent。"}
                """);

        AgentOutput.FinalAnswer answer = assertInstanceOf(AgentOutput.FinalAnswer.class, output);
        assertEquals("你好，我是 MiniAgent。", answer.answer());
        assertEquals("用户只是打招呼", answer.thought());
    }

    @Test
    void parsesToolCallWithObjectArguments() {
        AgentOutput output = parser.parse("""
                {"type":"tool_call","thought":"需要计算","tool":"calculator",
                 "arguments":{"expression":"(2+3)*4"}}
                """);

        AgentOutput.ToolCall call = assertInstanceOf(AgentOutput.ToolCall.class, output);
        assertEquals("calculator", call.toolName());
        assertEquals("(2+3)*4", call.arguments().path("expression").asText());
        assertEquals("需要计算", call.thought());
    }

    @Test
    void toleratesFencesPreambleAndStringArguments() {
        String raw = """
                下面是本次决定：
                ```json
                {"type":"tool_call","thought":"查天气","tool":"weather",
                 "arguments":"{\\"city\\":\\"杭州\\"}"}
                ```
                """;

        AgentOutput.ToolCall call = assertInstanceOf(AgentOutput.ToolCall.class, parser.parse(raw));
        assertEquals("weather", call.toolName());
        assertEquals("杭州", call.arguments().path("city").asText());
    }

    @Test
    void usesReasoningContentWhenThoughtMissing() {
        LlmResponse response = new LlmResponse(
                "{\"type\":\"final\",\"answer\":\"4\"}",
                "我检查了算术结果。", "stop", 10, 5, "deepseek-reasoner");

        AgentOutput.FinalAnswer answer = assertInstanceOf(AgentOutput.FinalAnswer.class,
                parser.parse(response));
        assertEquals("我检查了算术结果。", answer.thought());
        assertEquals("4", answer.answer());
    }

    @Test
    void infersTypeForCompatibleShapes() {
        assertInstanceOf(AgentOutput.FinalAnswer.class,
                parser.parse("{\"answer\":\"直接回答\"}"));
        assertInstanceOf(AgentOutput.ToolCall.class,
                parser.parse("{\"tool\":\"calculator\",\"arguments\":{\"expression\":\"1+1\"}}"));
    }

    @Test
    void rejectsInvalidJson() {
        ParseException error = assertThrows(ParseException.class,
                () -> parser.parse("I think the answer is 4."));
        assertTrue(error.getMessage().contains("No JSON object"));
    }

    @Test
    void rejectsUnknownTypeAndMissingFields() {
        assertThrows(ParseException.class,
                () -> parser.parse("{\"type\":\"plan\",\"answer\":\"x\"}"));
        assertThrows(ParseException.class,
                () -> parser.parse("{\"type\":\"final\",\"thought\":\"only thought\"}"));
        assertThrows(ParseException.class,
                () -> parser.parse("{\"type\":\"tool_call\",\"arguments\":{}}"));
    }

    @Test
    void rejectsObjectThatIsNotAnObject() {
        assertThrows(ParseException.class, () -> parser.parse("[1,2,3]"));
        assertThrows(ParseException.class, () -> parser.parse(""));
    }
}
