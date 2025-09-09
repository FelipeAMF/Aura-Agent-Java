package com.auraagent.services;

import com.auraagent.models.FirebaseAuthResponse;
import com.auraagent.models.ReportModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class FirebaseService {

    private static final String FIREBASE_API_KEY = "AIzaSyCLYUO-M55o4rE1wJ3uLyc8pRkviJpYiYo";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void initialize() {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                InputStream serviceAccount = FirebaseService.class.getResourceAsStream("/firebase_key.json");
                if (serviceAccount == null) {
                    throw new IOException("Ficheiro 'firebase_key.json' não encontrado na pasta resources.");
                }
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setDatabaseUrl("https://claraia-default-rtdb.firebaseio.com/")
                        .build();
                FirebaseApp.initializeApp(options);
                System.out.println("Conexão com o Firebase estabelecida com sucesso.");
            } catch (IOException e) {
                System.err.println("ERRO CRÍTICO AO INICIALIZAR O FIREBASE: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    // --- AUTENTICAÇÃO ---
    public static CompletableFuture<FirebaseAuthResponse> signInAsync(String email, String password) {
        String url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + FIREBASE_API_KEY;
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("returnSecureToken", true);

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    try {
                        return objectMapper.readValue(response.body(), FirebaseAuthResponse.class);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(null);
        }
    }

    // --- MÉTODOS GENÉRICOS DE LEITURA/ESCRITA ---
    private static <T> CompletableFuture<T> getDataAsync(String path, GenericTypeIndicator<T> typeIndicator) {
        CompletableFuture<T> future = new CompletableFuture<>();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            public void onDataChange(DataSnapshot dataSnapshot) {
                future.complete(dataSnapshot.exists() ? dataSnapshot.getValue(typeIndicator) : null);
            }

            public void onCancelled(DatabaseError error) {
                future.completeExceptionally(error.toException());
            }
        });
        return future;
    }

    private static CompletableFuture<Boolean> removeDataAsync(String path) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path);
        ref.removeValue((error, ref1) -> future.complete(error == null));
        return future;
    }

    private static CompletableFuture<Boolean> updateChildrenAsync(String path, Map<String, Object> data) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path);
        ref.updateChildren(data, (error, ref1) -> future.complete(error == null));
        return future;
    }

    // --- RELATÓRIOS ---
    public static CompletableFuture<Map<String, ReportModel>> getReportsAsync(String userId) {
        return getDataAsync("users/" + userId + "/reports", new GenericTypeIndicator<Map<String, ReportModel>>() {
        });
    }

    // --- LISTAS DE CONTATOS ---
    public static CompletableFuture<Map<String, Object>> getContactListsAsync(String userId, String token) {
        return getDataAsync("users/" + userId + "/contact_lists", new GenericTypeIndicator<Map<String, Object>>() {
        });
    }

    public static CompletableFuture<Map<String, Object>> getContactsFromListAsync(String userId, String token,
            String listName) {
        return getDataAsync("users/" + userId + "/contact_lists/" + listName,
                new GenericTypeIndicator<Map<String, Object>>() {
                });
    }

    public static CompletableFuture<Boolean> createContactList(String userId, String token, String listName) {
        return updateChildrenAsync("users/" + userId + "/contact_lists/" + listName, Map.of("_placeholder", true));
    }

    public static CompletableFuture<Boolean> deleteContactList(String userId, String token, String listName) {
        return removeDataAsync("users/" + userId + "/contact_lists/" + listName);
    }

    public static CompletableFuture<Boolean> addContactsToList(String userId, String listName, List<String> contacts) {
        Map<String, Object> updates = new HashMap<>();
        for (String contact : contacts) {
            updates.put(contact, true);
        }
        String path = "users/" + userId + "/contact_lists/" + listName;
        return updateChildrenAsync(path, updates);
    }

    // --- BLACKLIST ---
    public static CompletableFuture<Map<String, Boolean>> getBlacklist(String userId, String token) {
        return getDataAsync("users/" + userId + "/blacklist", new GenericTypeIndicator<Map<String, Boolean>>() {
        });
    }

    public static CompletableFuture<Boolean> addToBlacklist(String userId, String token, List<String> numbers) {
        Map<String, Object> updates = numbers.stream().collect(Collectors.toMap(number -> number, number -> true));
        return updateChildrenAsync("users/" + userId + "/blacklist", updates);
    }

    public static CompletableFuture<Boolean> removeFromBlacklist(String userId, String token, List<String> numbers) {
        Map<String, Object> updates = numbers.stream().collect(Collectors.toMap(number -> number, number -> null));
        return updateChildrenAsync("users/" + userId + "/blacklist", updates);
    }

    public static CompletableFuture<Boolean> clearBlacklist(String userId, String token) {
        return removeDataAsync("users/" + userId + "/blacklist");
    }

    // --- TEMPLATES ---
    public static CompletableFuture<Map<String, Object>> getCampaignTemplates(String userId, String token) {
        return getDataAsync("users/" + userId + "/campaign_templates", new GenericTypeIndicator<Map<String, Object>>() {
        });
    }

    public static CompletableFuture<Map<String, Object>> getTemplateData(String userId, String token,
            String templateName) {
        return getDataAsync("users/" + userId + "/campaign_templates/" + templateName,
                new GenericTypeIndicator<Map<String, Object>>() {
                });
    }

    public static CompletableFuture<Boolean> saveTemplate(String userId, String token, String templateName,
            String spintaxMessage, String delay) {
        String path = "users/" + userId + "/campaign_templates/" + templateName + "/settings";
        String delayValue = delay.replaceAll("[^0-9]", "");
        Map<String, Object> settings = new HashMap<>();
        settings.put("spintax_template", spintaxMessage);
        settings.put("delay", delayValue);
        return updateChildrenAsync(path, settings);
    }

    public static CompletableFuture<Boolean> deleteTemplate(String userId, String token, String templateName) {
        String path = "users/" + userId + "/campaign_templates/" + templateName;
        return removeDataAsync(path);
    }

    // --- AI SETTINGS ---
    public static CompletableFuture<Boolean> saveAIPersonality(String userId, String accountName, String prompt) {
        String path = "users/" + userId + "/ai_settings/" + accountName;
        Map<String, Object> personality = Map.of("personality_prompt", prompt);
        return updateChildrenAsync(path, personality);
    }

    public static CompletableFuture<String> getAIPersonality(String userId, String accountName) {
        CompletableFuture<String> future = new CompletableFuture<>();
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("users/" + userId + "/ai_settings/" + accountName + "/personality_prompt");
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            public void onDataChange(DataSnapshot dataSnapshot) {
                future.complete(dataSnapshot.exists() ? dataSnapshot.getValue(String.class) : "");
            }

            public void onCancelled(DatabaseError error) {
                future.completeExceptionally(error.toException());
            }
        });
        return future;
    }

    /**
     * Salva um novo relatório de campanha na base de dados.
     * 
     * @param userId ID do utilizador.
     * @param report O objeto ReportModel contendo os dados da campanha.
     * @return Um CompletableFuture indicando o sucesso da operação.
     */
    public static CompletableFuture<Boolean> saveReport(String userId, ReportModel report) {
        // Gera uma chave única para o novo relatório
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users/" + userId + "/reports").push();

        // Define o ID da campanha como a chave gerada
        report.setCampaignId(ref.getKey());

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ref.setValue(report, (error, ref1) -> future.complete(error == null));
        return future;
    }
}