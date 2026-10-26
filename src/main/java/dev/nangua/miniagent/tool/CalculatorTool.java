package dev.nangua.miniagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nangua.miniagent.util.Json;

import java.util.List;
import java.util.Locale;

/**
 * 算术计算工具：四则运算、取模、乘方、括号，以及 sqrt/abs/round/floor/ceil/
 * min/max 和 pi/e。真正的解析与求值在 {@link ExpressionEvaluator}——手写递归
 * 下降，不借助任何脚本引擎，所以模型生成的表达式不可能执行任意代码。
 */
public final class CalculatorTool implements Tool {
    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public String description() {
        return "Evaluate a arithmetic expression safely. Supports + - * / % ^, parentheses, "
                + "sqrt, abs, round, floor, ceil, min, max, pi and e.";
    }

    @Override
    public ObjectNode parameterSchema() {
        return Schemas.object(
                "Arithmetic expression to evaluate.",
                List.of("expression"),
                Schemas.properties(
                        "expression", Schemas.string("e.g. (12.5 + 3) * 4 / 2")
                )
        );
    }

    @Override
    public ToolResult execute(ToolContext context, ObjectNode arguments) {
        JsonNode expressionNode = arguments.get("expression");
        if (expressionNode == null || !expressionNode.isTextual()) {
            throw new ToolException("'expression' must be a string");
        }
        String expression = expressionNode.asText();
        double value = ExpressionEvaluator.evaluate(expression);
        String formatted = format(value);
        ObjectNode result = Json.mapper().createObjectNode();
        result.put("expression", expression);
        result.put("result", formatted);
        return ToolResult.success(Json.write(result));
    }

    static String format(double value) {
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 1e-10 && Math.abs(value) < 9e15) {
            return Long.toString((long) rounded);
        }
        String text = String.format(Locale.ROOT, "%.10f", value);
        text = text.replaceAll("0+$", "");
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
