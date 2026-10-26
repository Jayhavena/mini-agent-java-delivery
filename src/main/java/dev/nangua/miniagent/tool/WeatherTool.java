package dev.nangua.miniagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nangua.miniagent.util.Json;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic mock weather provider with a real-tool-shaped schema.
 *
 * <p>确定式的 mock 天气：内置 15 个城市的数据，输出刻意带 isMock=true 和
 * mock-weather 来源标记，避免模型把演示数据当真实数据。查不到的城市抛
 * ToolException 并附上支持列表，模型可以据此向用户解释或换城市。
 */
public final class WeatherTool implements Tool {
    private static final Map<String, Weather> CITIES = new LinkedHashMap<>();

    static {
        CITIES.put("北京", new Weather("晴转多云", 21, 34, "东北风 2 级"));
        CITIES.put("上海", new Weather("阵雨", 24, 78, "东南风 3 级"));
        CITIES.put("杭州", new Weather("多云", 23, 66, "东风 2 级"));
        CITIES.put("深圳", new Weather("雷阵雨", 29, 81, "南风 3 级"));
        CITIES.put("广州", new Weather("多云", 30, 72, "南风 2 级"));
        CITIES.put("成都", new Weather("阴", 19, 70, "微风"));
        CITIES.put("南京", new Weather("晴", 22, 48, "东风 2 级"));
        CITIES.put("武汉", new Weather("多云", 25, 60, "北风 2 级"));
        CITIES.put("西安", new Weather("晴", 20, 38, "西风 2 级"));
        CITIES.put("重庆", new Weather("小雨", 21, 84, "微风"));
        CITIES.put("苏州", new Weather("多云", 23, 64, "东南风 2 级"));
        CITIES.put("厦门", new Weather("晴间多云", 28, 70, "海风 3 级"));
        CITIES.put("青岛", new Weather("晴", 19, 55, "北风 3 级"));
        CITIES.put("长沙", new Weather("多云", 24, 68, "北风 2 级"));
        CITIES.put("天津", new Weather("晴", 20, 40, "东北风 2 级"));
    }

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String description() {
        return "Get mock current weather for a Chinese city. Returns condition, temperature, "
                + "humidity and wind. Data is simulated for this offline demo.";
    }

    @Override
    public ObjectNode parameterSchema() {
        return Schemas.object(
                "City weather lookup (mock data).",
                List.of("city"),
                Schemas.properties(
                        "city", Schemas.string("Chinese city name, e.g. 杭州"),
                        "unit", Schemas.enumeration("Temperature unit (default celsius)", "celsius", "fahrenheit")
                )
        );
    }

    @Override
    public ToolResult execute(ToolContext context, ObjectNode arguments) {
        JsonNode cityNode = arguments.get("city");
        if (cityNode == null || !cityNode.isTextual() || cityNode.asText().isBlank()) {
            throw new ToolException("'city' must be a non-blank string");
        }
        String city = cityNode.asText().trim().replace("市", "");
        Weather weather = CITIES.get(city);
        if (weather == null) {
            throw new ToolException("No mock weather data for '" + city + "'. Supported cities: "
                    + String.join("、", CITIES.keySet()));
        }
        String unit = arguments.path("unit").asText("celsius").toLowerCase(Locale.ROOT);
        if (!unit.equals("celsius") && !unit.equals("fahrenheit")) {
            throw new ToolException("'unit' must be celsius or fahrenheit");
        }
        double temperature = unit.equals("fahrenheit")
                ? weather.temperatureC() * 9.0 / 5.0 + 32
                : weather.temperatureC();

        ObjectNode payload = Json.mapper().createObjectNode();
        payload.put("city", city);
        payload.put("condition", weather.condition());
        payload.put("temperature", Math.round(temperature * 10.0) / 10.0);
        payload.put("unit", unit.equals("fahrenheit") ? "°F" : "°C");
        payload.put("humidityPercent", weather.humidity());
        payload.put("wind", weather.wind());
        payload.put("date", LocalDate.now().toString());
        payload.put("source", "mock-weather");
        payload.put("isMock", true);
        return ToolResult.success(Json.write(payload));
    }

    private record Weather(String condition, int temperatureC, int humidity, String wind) {
    }
}
