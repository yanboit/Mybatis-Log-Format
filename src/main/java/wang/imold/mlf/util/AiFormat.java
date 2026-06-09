package wang.imold.mlf.util;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class AiFormat {

    private static final String API_URL = "https://dify.imold.wang/v1/workflows/run";
    private static final String API_KEY = "app-sgteTT2in0kmx26HvGeD83eN";
    private static final ObjectMapper mapper = new ObjectMapper();
    public String callDify(String logText) throws Exception {
        Map<String, Object> inputs = Map.of("logtext", logText);
        Map<String, Object> payload = Map.of(
                "inputs", inputs,
                "response_mode", "blocking",
                "user", "plugin-user"
        );

        String jsonBody = mapper.writeValueAsString(payload);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // 解析 JSON 逻辑（参考你之前的逻辑）
        return mapper.readTree(response.body())
                .path("data").path("outputs").path("formatSql").asText();
    }
}
