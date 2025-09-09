package com.auraagent.services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AIService {

    private static final String API_URL = "http://localhost:8080/completion";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public static CompletableFuture<String> generateResponseAsync(List<Object> history, String systemPrompt, ExecutorService executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Constrói o prompt no formato esperado pelo modelo Gemma
                StringBuilder promptBuilder = new StringBuilder();
                boolean firstUserMessage = true;

                for (Object item : history) {
                    Map<String, String> message = (Map<String, String>) item;
                    String role = "assistant".equals(message.get("role")) ? "model" : message.get("role");
                    String content = message.get("content");

                    promptBuilder.append("<start_of_turn>").append(role).append("\n");

                    if ("user".equals(role) && firstUserMessage) {
                        promptBuilder.append(systemPrompt).append("\n\n");
                        firstUserMessage = false;
                    }
                    promptBuilder.append(content).append("<end_of_turn>\n");
                }
                promptBuilder.append("<start_of_turn>model\n");

                // Monta o payload final
                ObjectNode payload = MAPPER.createObjectNode();
                payload.put("prompt", promptBuilder.toString());
                payload.put("stream", false);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode responseJson = MAPPER.readTree(response.body());
                    return responseJson.get("content").asText("Falha ao extrair o conteúdo da resposta.");
                } else {
                    return "Erro na API: " + response.statusCode() + " - " + response.body();
                }

            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt(); // Restaura o status de interrupção
                return "Erro ao se conectar à API: " + e.getMessage();
            }
        }, executor);
    }
}
