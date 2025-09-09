package com.auraagent.services;

import com.auraagent.models.WhatsappAccount;
import com.auraagent.models.WhatsappMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Files;

public class WhatsappService {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String API_URL = getNodeApiUrl();

    private static String getNodeApiUrl() {
        int port = ProcessManager.getCurrentPort();
        return "http://localhost:" + port;
    }

    public CompletableFuture<List<WhatsappAccount>> getStatusAsync() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/status"))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        if (response.statusCode() != 200) {
                            System.err.println("Erro HTTP ao obter status: " + response.statusCode());
                            return new ArrayList<WhatsappAccount>();
                        }
                        return objectMapper.readValue(response.body(), new TypeReference<List<WhatsappAccount>>() {
                        });
                    } catch (Exception e) {
                        System.err.println("Erro ao processar a resposta do status do WhatsApp: " + e.getMessage());
                        return new ArrayList<WhatsappAccount>();
                    }
                })
                .exceptionally(e -> {
                    System.err.println("Erro de comunicação com o servidor de WhatsApp: " + e.getMessage());
                    return new ArrayList<WhatsappAccount>();
                });
    }

    public CompletableFuture<Boolean> connectAsync(String sessionId) {
        return sendSessionRequest(sessionId, "/connect");
    }

    public CompletableFuture<Boolean> disconnectAsync(String sessionId) {
        return sendSessionRequest(sessionId, "/disconnect");
    }

    public CompletableFuture<List<WhatsappMessage>> getNewMessagesAsync(String sessionId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/get-messages/" + sessionId))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        if (response.statusCode() != 200) {
                            System.err.println("Erro ao buscar mensagens: " + response.statusCode());
                            return new ArrayList<>();
                        }
                        JsonNode root = objectMapper.readTree(response.body());
                        return objectMapper.convertValue(
                                root.get("messages"),
                                new TypeReference<List<WhatsappMessage>>() {
                                });
                    } catch (Exception e) {
                        System.err.println("Erro ao processar mensagens recebidas: " + e.getMessage());
                        return new ArrayList<>();
                    }
                });
    }

    private CompletableFuture<Boolean> sendSessionRequest(String sessionId, String endpoint) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> response.statusCode() == 200)
                    .exceptionally(e -> {
                        System.err.println("Erro de comunicação com o servidor de WhatsApp: " + e.getMessage());
                        return false;
                    });
        } catch (IOException e) {
            System.err.println("Erro ao construir a requisição para " + endpoint + ": " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    public CompletableFuture<Boolean> sendMessageAsync(String sessionId, String number, String message) {
        return sendMessageAsync(sessionId, number, null, message);
    }

    public CompletableFuture<Boolean> sendMessageAsync(String sessionId, String number, Path imagePath,
            String message) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            payload.put("to", number);
            payload.put("message", message);

            if (imagePath != null) {
                byte[] imageBytes = Files.readAllBytes(imagePath);
                String imageData = Base64.getEncoder().encodeToString(imageBytes);
                String mimeType = Files.probeContentType(imagePath);
                String fileName = imagePath.getFileName().toString();

                payload.put("imageData", imageData);
                payload.put("mimeType", mimeType);
                payload.put("fileName", fileName);
            }

            String jsonPayload = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/send-proactive-message"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> response.statusCode() == 200);

        } catch (Exception e) {
            System.err.println("Erro ao construir a requisição de envio de mensagem: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    // MÉTODO CORRIGIDO
    public CompletableFuture<Map<String, Boolean>> validateNumbersAsync(String sessionId, List<String> numbers) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            payload.put("numbers", numbers);
            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/validate-numbers"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                System.out.println("[Validador] Resposta do Servidor: " + response.body());
                                return objectMapper.readValue(response.body(),
                                        new TypeReference<Map<String, Boolean>>() {
                                        });
                            } catch (IOException e) {
                                System.err.println("[Validador] Erro ao processar JSON da resposta: " + e.getMessage());
                                return new HashMap<String, Boolean>();
                            }
                        } else {
                            System.err.println(
                                    "[Validador] Erro na resposta do servidor. Código: " + response.statusCode());
                            System.err.println("[Validador] Corpo da Resposta: " + response.body());
                            return new HashMap<String, Boolean>();
                        }
                    })
                    .exceptionally(ex -> {
                        System.err.println("[Validador] Exceção ao comunicar com o servidor: " + ex.getMessage());
                        return new HashMap<String, Boolean>();
                    });
        } catch (IOException e) {
            System.err.println("Erro ao construir a requisição de validação de números: " + e.getMessage());
            return CompletableFuture.completedFuture(new HashMap<>());
        }
    }
}