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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WhatsappService {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String API_URL = getNodeApiUrl();

    private static String getNodeApiUrl() {
        // Obtém a porta diretamente do ProcessManager
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
                        List<WhatsappMessage> messages = objectMapper.convertValue(
                            root.get("messages"), 
                            new TypeReference<List<WhatsappMessage>>(){}
                        );
                        return messages;
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
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            System.err.println("Erro na requisição para " + endpoint + ": " + response.statusCode());
                            return false;
                        }
                        return true;
                    })
                    .exceptionally(e -> {
                        System.err.println("Erro de comunicação com o servidor de WhatsApp: " + e.getMessage());
                        return false;
                    });
        } catch (IOException e) {
            System.err.println("Erro ao construir a requisição para " + endpoint + ": " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Sobrecarga do método para enviar apenas uma mensagem de texto.
     * Chama o método principal com o caminho da imagem como nulo.
     */
    public CompletableFuture<Boolean> sendMessageAsync(String sessionId, String number, String message) {
        return sendMessageAsync(sessionId, number, null, message);
    }

    /**
     * Envia uma mensagem. Se o caminho da imagem for fornecido, envia a imagem com
     * a
     * mensagem como legenda. Caso contrário, envia apenas o texto.
     */
    public CompletableFuture<Boolean> sendMessageAsync(String sessionId, String number, Path imagePath,
            String message) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            payload.put("to", number);
            payload.put("message", message); // Será a mensagem ou a legenda

            // Se um ficheiro de imagem foi anexado, adiciona os seus dados ao payload
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
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            System.err.println(
                                    "Erro ao enviar mensagem: " + response.statusCode() + " -> " + response.body());
                        }
                        return response.statusCode() == 200;
                    });

        } catch (Exception e) {
            System.err.println("Erro ao construir a requisição de envio de mensagem: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }
}
