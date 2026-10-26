package dev.nangua.miniagent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionEvaluatorTest {
    @Test
    void respectsPrecedenceAndParentheses() {
        assertEquals(14, ExpressionEvaluator.evaluate("2 + 3 * 4"));
        assertEquals(20, ExpressionEvaluator.evaluate("(2 + 3) * 4"));
        assertEquals(512, ExpressionEvaluator.evaluate("2^3^2"));
        assertEquals(-4, ExpressionEvaluator.evaluate("-2^2"));
        assertEquals(2.5, ExpressionEvaluator.evaluate("5 / 2"));
    }

    @Test
    void supportsFunctionsAndConstants() {
        assertEquals(3, ExpressionEvaluator.evaluate("sqrt(9)"));
        assertEquals(7, ExpressionEvaluator.evaluate("max(3, 7, 5)"));
        assertEquals(3, ExpressionEvaluator.evaluate("min(3, 7, 5)"));
        assertEquals(4, ExpressionEvaluator.evaluate("abs(-4)"));
        assertEquals(3, ExpressionEvaluator.evaluate("round(pi)"));
        assertTrue(ExpressionEvaluator.evaluate("e") > 2.7);
    }

    @Test
    void rejectsDivisionByZeroAndUnknownInput() {
        assertThrows(ToolException.class, () -> ExpressionEvaluator.evaluate("1 / 0"));
        assertThrows(ToolException.class, () -> ExpressionEvaluator.evaluate("1 % 0"));
        assertThrows(ToolException.class, () -> ExpressionEvaluator.evaluate("unknown(1)"));
        assertThrows(ToolException.class, () -> ExpressionEvaluator.evaluate("Runtime.getRuntime()"));
        assertThrows(ToolException.class, () -> ExpressionEvaluator.evaluate("1 +"));
    }

    @Test
    void rejectsOversizedAndDeeplyNestedExpressions() {
        assertThrows(ToolException.class, () -> ExpressionEvaluator.evaluate("1".repeat(600)));
        String deep = "(".repeat(80) + "1" + ")".repeat(80);
        assertThrows(ToolException.class, () -> ExpressionEvaluator.evaluate(deep));
    }
}
