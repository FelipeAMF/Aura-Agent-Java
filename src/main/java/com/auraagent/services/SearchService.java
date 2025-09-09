package com.auraagent.services;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SearchService {

    // IMPORTANTE: Substitua com as suas chaves
    private static final String API_KEY = "AIzaSyDSOUTtU5HG1i84VlSgQGbNYePaQT0kEJI";
    private static final String SEARCH_ENGINE_ID = "c1708db5bcd344057";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static CompletableFuture<String> searchInternet(String query) {
        // Lembre-se de obter e substituir os valores acima antes de usar.
        // A lógica de verificação foi removida para que o código compile.

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://www.googleapis.com/customsearch/v1?key=" + API_KEY + "&cx=" + SEARCH_ENGINE_ID + "&q="
                    + encodedQuery;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            if (response.statusCode() == 200) {
                                JsonNode root = MAPPER.readTree(response.body());
                                if (root.has("items")) {
                                    // Formata os 3 resultados mais relevantes num texto limpo
                                    return StreamSupport.stream(root.get("items").spliterator(), false)
                                            .limit(3)
                                            .map(item -> "Título: " + item.get("title").asText() + "\nTrecho: "
                                                    + item.get("snippet").asText())
                                            .collect(Collectors.joining("\n\n"));
                                }
                                return "Nenhum resultado encontrado para a pesquisa.";
                            } else {
                                return "Erro ao pesquisar na internet: " + response.body();
                            }
                        } catch (Exception e) {
                            return "Erro ao processar os resultados da pesquisa: " + e.getMessage();
                        }
                    });

        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture("Erro ao construir o pedido de pesquisa: " + e.getMessage());
        }
    }
}