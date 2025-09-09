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
    public static CompletableFuture<String> generateResponseAsync(List<Object> history, String systemPrompt,
            ExecutorService executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder promptBuilder = new StringBuilder();

                if (systemPrompt != null && !systemPrompt.isBlank()) {
                    promptBuilder.append("<start_of_turn>user\n");
                    promptBuilder.append("Instrução: A partir de agora, siga esta personalidade: ").append(systemPrompt)
                            .append("<end_of_turn>\n");
                    promptBuilder.append("<start_of_turn>model\n");
                    promptBuilder.append("Entendido. Vou seguir essa personalidade a partir de agora.<end_of_turn>\n");
                }

                for (Object item : history) {
                    Map<String, String> message = (Map<String, String>) item;
                    String role = "assistant".equals(message.get("role")) ? "model" : "user";
                    String content = message.get("content");

                    promptBuilder.append("<start_of_turn>").append(role).append("\n");
                    promptBuilder.append(content).append("<end_of_turn>\n");
                }

                promptBuilder.append("<start_of_turn>model\n");

                ObjectNode payload = MAPPER.createObjectNode();
                payload.put("prompt", promptBuilder.toString());
                payload.put("stream", false);
                payload.put("temperature", 0.6);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode responseJson = MAPPER.readTree(response.body());
                    String rawContent = responseJson.get("content").asText("").trim();

                    if (rawContent.contains("<start_of_turn>")) {
                        return rawContent.substring(0, rawContent.indexOf("<start_of_turn>")).trim();
                    }
                    return rawContent;
                } else {
                    System.err.println("Erro na API de IA: " + response.body());
                    return "Ocorreu um erro ao comunicar com a IA.";
                }

            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Erro ao conectar-se à API de IA: " + e.getMessage();
            }
        }, executor);
    }

    public static CompletableFuture<String> rephraseMessageAsync(String originalMessage, ExecutorService executor) {
        // ... (este método não precisa de alterações)
        return CompletableFuture.supplyAsync(() -> {
            try {
                String systemPrompt = "Reescreva a seguinte mensagem de forma ligeiramente diferente, mantendo o mesmo sentido, tom e intenção. A mensagem deve parecer natural e humana. Não adicione nenhuma explicação, apenas a mensagem reescrita.";

                StringBuilder promptBuilder = new StringBuilder();
                promptBuilder.append("<start_of_turn>user\n");
                promptBuilder.append(systemPrompt).append("\n\n").append("MENSAGEM ORIGINAL: \"")
                        .append(originalMessage).append("\"\n");
                promptBuilder.append("<end_of_turn>\n");
                promptBuilder.append("<start_of_turn>model\n");
                promptBuilder.append("MENSAGEM REESCRITA: \"");

                ObjectNode payload = MAPPER.createObjectNode();
                payload.put("prompt", promptBuilder.toString());
                payload.put("stream", false);
                payload.put("temperature", 0.7);
                payload.put("top_p", 0.9);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode responseJson = MAPPER.readTree(response.body());
                    String content = responseJson.get("content").asText(originalMessage);
                    return content.replace("\"", "").trim();
                } else {
                    return originalMessage;
                }

            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                return originalMessage;
            }
        }, executor);
    }
}