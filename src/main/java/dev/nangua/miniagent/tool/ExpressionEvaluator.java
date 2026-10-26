package dev.nangua.miniagent.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A deliberately small arithmetic parser. No script engine, reflection or
 * arbitrary code execution is involved.
 *
 * Supported: + - * / % ^, parentheses, unary signs, sqrt/abs/round/floor/ceil,
 * min/max with several arguments, and the constants pi/e.
 *
 * <p>刻意保持极小的手写递归下降算术解析器：不引入脚本引擎、反射或任何动态执行，
 * 模型生成的表达式不可能变成任意代码。两层上限防止资源耗尽：表达式最长 500
 * 字符、括号嵌套最深 64 层。所有错误都以 ToolException 抛出并带出错位置，
 * 便于回灌给模型自行修正。
 */
final class ExpressionEvaluator {
    // 两道资源上限：防超长输入与深嵌套括号把解析器变成资源黑洞。
    private static final int MAX_LENGTH = 500;
    private static final int MAX_DEPTH = 64;

    private final String input;
    private int pos;
    private int depth;

    private ExpressionEvaluator(String input) {
        this.input = input;
    }

    static double evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new ToolException("Expression is empty");
        }
        if (expression.length() > MAX_LENGTH) {
            throw new ToolException("Expression is too long (max " + MAX_LENGTH + " characters)");
        }
        ExpressionEvaluator parser = new ExpressionEvaluator(expression);
        double value = parser.parseExpression();
        parser.skipWhitespace();
        if (parser.pos != parser.input.length()) {
            throw parser.error("Unexpected character '" + parser.input.charAt(parser.pos) + "'");
        }
        if (!Double.isFinite(value)) {
            throw new ToolException("Expression result is not a finite number");
        }
        return value;
    }

    private double parseExpression() {
        double value = parseTerm();
        while (true) {
            skipWhitespace();
            if (consume('+')) {
                value += parseTerm();
            } else if (consume('-')) {
                value -= parseTerm();
            } else {
                return value;
            }
        }
    }

    private double parseTerm() {
        double value = parseUnary();
        while (true) {
            skipWhitespace();
            if (consume('*')) {
                value *= parseUnary();
            } else if (consume('/')) {
                double divisor = parseUnary();
                if (divisor == 0.0d) {
                    throw new ToolException("Division by zero");
                }
                value /= divisor;
            } else if (consume('%')) {
                double divisor = parseUnary();
                if (divisor == 0.0d) {
                    throw new ToolException("Modulo by zero");
                }
                value %= divisor;
            } else {
                return value;
            }
        }
    }

    private double parseUnary() {
        skipWhitespace();
        if (consume('+')) {
            return parseUnary();
        }
        if (consume('-')) {
            return -parseUnary();
        }
        return parsePower();
    }

    private double parsePower() {
        double base = parsePrimary();
        skipWhitespace();
        // ^ 右结合：2^3^2 = 2^(3^2) = 512，所以右侧递归回到 parseUnary。
        if (consume('^')) {
            return Math.pow(base, parseUnary());
        }
        return base;
    }

    private double parsePrimary() {
        enter();
        try {
            skipWhitespace();
            if (consume('(')) {
                double value = parseExpression();
                skipWhitespace();
                expect(')');
                return value;
            }
            if (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
                return parseNumber();
            }
            if (pos < input.length() && Character.isLetter(input.charAt(pos))) {
                return parseIdentifier();
            }
            throw error("Expected a number, constant, function or '('");
        } finally {
            depth--;
        }
    }

    private double parseIdentifier() {
        int start = pos;
        while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            pos++;
        }
        String name = input.substring(start, pos).toLowerCase(Locale.ROOT);
        skipWhitespace();
        if (!consume('(')) {
            return switch (name) {
                case "pi" -> Math.PI;
                case "e" -> Math.E;
                default -> throw error("Unknown constant or function '" + name + "'");
            };
        }
        List<Double> args = new ArrayList<>();
        skipWhitespace();
        if (!consume(')')) {
            while (true) {
                args.add(parseExpression());
                skipWhitespace();
                if (consume(')')) {
                    break;
                }
                expect(',');
            }
        }
        return applyFunction(name, args);
    }

    private double parseNumber() {
        int start = pos;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        if (pos < input.length() && input.charAt(pos) == '.') {
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
        }
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            int exponentStart = pos;
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                pos++;
            }
            int digitsStart = pos;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
            if (digitsStart == pos) {
                pos = exponentStart;
            }
        }
        try {
            return Double.parseDouble(input.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("Invalid number '" + input.substring(start, pos) + "'");
        }
    }

    private double applyFunction(String name, List<Double> args) {
        return switch (name) {
            case "sqrt" -> {
                requireArity(name, args, 1);
                if (args.get(0) < 0) {
                    throw new ToolException("sqrt() argument must be >= 0");
                }
                yield Math.sqrt(args.get(0));
            }
            case "abs" -> {
                requireArity(name, args, 1);
                yield Math.abs(args.get(0));
            }
            case "round" -> {
                requireArity(name, args, 1);
                yield Math.rint(args.get(0));
            }
            case "floor" -> {
                requireArity(name, args, 1);
                yield Math.floor(args.get(0));
            }
            case "ceil" -> {
                requireArity(name, args, 1);
                yield Math.ceil(args.get(0));
            }
            case "min" -> {
                requireAtLeast(name, args, 1);
                yield args.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
            }
            case "max" -> {
                requireAtLeast(name, args, 1);
                yield args.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            }
            default -> throw error("Unknown function '" + name + "'");
        };
    }

    private static void requireArity(String name, List<Double> args, int count) {
        if (args.size() != count) {
            throw new ToolException(name + "() expects " + count + " argument(s), got " + args.size());
        }
    }

    private static void requireAtLeast(String name, List<Double> args, int count) {
        if (args.size() < count) {
            throw new ToolException(name + "() expects at least " + count + " argument(s), got " + args.size());
        }
    }

    private void enter() {
        depth++;
        if (depth > MAX_DEPTH) {
            throw new ToolException("Expression is nested too deeply");
        }
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private boolean consume(char expected) {
        if (pos < input.length() && input.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!consume(expected)) {
            throw error("Expected '" + expected + "'");
        }
    }

    private ToolException error(String message) {
        return new ToolException(message + " at position " + pos);
    }
}
