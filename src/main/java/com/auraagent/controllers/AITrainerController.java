package com.auraagent.controllers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.auraagent.models.WhatsappAccount;
import com.auraagent.services.AIService;
import com.auraagent.services.FirebaseService;
import com.auraagent.services.ProcessManager;
import com.auraagent.services.SearchService; // <<-- IMPORT ADICIONADO
import com.auraagent.services.WhatsappService;
import com.auraagent.utils.JavaFxUtils;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class AITrainerController implements MainAppController.InitializableController {

    @FXML
    private VBox settingsPane, chatPane;
    @FXML
    private Button toggleServerButton, savePromptButton, sendTestMessageButton;
    @FXML
    private ComboBox<String> modelComboBox, accountComboBox;
    @FXML
    private TextArea personalityPromptArea, chatHistoryArea;
    @FXML
    private TextField userTestInput;
    @FXML
    private Label aiStatusLabel;

    private String userId;
    private String userToken;

    private final SimpleBooleanProperty isServerRunning = new SimpleBooleanProperty(false);
    private final StringProperty serverStatus = new SimpleStringProperty("Inativo");
    private final ObservableList<String> availableModels = FXCollections.observableArrayList();
    private final ObservableList<String> availableAccounts = FXCollections.observableArrayList();
    private final WhatsappService whatsappService = new WhatsappService();
    private List<Object> chatTestHistory = new ArrayList<>();
    private Process aiServerProcess;
    private static final ExecutorService AI_TASK_EXECUTOR = Executors.newFixedThreadPool(1);

    @Override
    public void initialize(String userId) {
        this.userId = userId;
        setupBindings();

        modelComboBox.setItems(availableModels);
        accountComboBox.setItems(availableAccounts);

        accountComboBox.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> onAccountSelected(newVal));

        loadAvailableModels();
        // A primeira carga de contas acontece aqui
        refreshData();
    }

    // MÉTODO ADICIONADO PARA CORRIGIR O PROBLEMA
    @Override
    public void onViewShown() {
        // Esta linha garante que os dados são atualizados sempre que a aba é exibida
        refreshData();
    }

    private void setupBindings() {
        modelComboBox.disableProperty().bind(isServerRunning);
        chatPane.disableProperty().bind(isServerRunning.not());

        aiStatusLabel.textProperty().bind(serverStatus);

        toggleServerButton.textProperty().bind(
                Bindings.when(isServerRunning).then("Desativar Servidor de IA").otherwise("Ativar Servidor de IA"));

        serverStatus.addListener((obs, oldVal, newVal) -> {
            aiStatusLabel.getStyleClass().removeAll("success-label", "danger-label", "warning-label");
            if ("Ativo".equals(newVal)) {
                aiStatusLabel.getStyleClass().add("success-label");
            } else if ("Inativo".equals(newVal)) {
                aiStatusLabel.getStyleClass().add("danger-label");
            } else {
                aiStatusLabel.getStyleClass().add("warning-label");
            }
        });
    }

    public void refreshData() {
        loadAccountsAsync();
    }

    private void loadAccountsAsync() {
        whatsappService.getStatusAsync().thenAcceptAsync(accountsData -> {
            Platform.runLater(() -> {
                String selected = accountComboBox.getSelectionModel().getSelectedItem();
                availableAccounts.clear();
                availableAccounts.add("Selecione uma conta...");
                for (WhatsappAccount acc : accountsData) {
                    // Apenas contas conectadas com número de telefone aparecem
                    if ("Conectado".equals(acc.getStatus()) && acc.getPhoneNumber() != null) {
                        availableAccounts.add(acc.getSessionId() + " (" + acc.getPhoneNumber() + ")");
                    }
                }

                // Tenta manter a seleção anterior, se ainda existir
                if (selected != null && availableAccounts.contains(selected)) {
                    accountComboBox.getSelectionModel().select(selected);
                } else {
                    accountComboBox.getSelectionModel().selectFirst();
                }
            });
        });
    }

    private void loadAvailableModels() {
        File modelDir = Paths.get(System.getProperty("user.dir"), "model").toFile();
        if (modelDir.exists() && modelDir.isDirectory()) {
            File[] ggufFiles = modelDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".gguf"));
            if (ggufFiles != null && ggufFiles.length > 0) {
                availableModels.setAll(Stream.of(ggufFiles)
                        .map(File::getName)
                        .sorted()
                        .collect(Collectors.toList()));
                modelComboBox.getSelectionModel().selectFirst();
            } else {
                availableModels.setAll("Nenhum modelo .gguf encontrado.");
            }
        } else {
            availableModels.setAll("Pasta 'model' não encontrada.");
        }
    }

    private void onAccountSelected(String accountName) {
        chatTestHistory.clear();
        if (accountName == null || accountName.equals("Selecione uma conta...")) {
            personalityPromptArea.clear();
            chatHistoryArea.clear();
            return;
        }

        String sessionId = accountName.split(" ")[0];

        FirebaseService.getAIPersonality(userId, sessionId).thenAccept(prompt -> {
            Platform.runLater(() -> {
                personalityPromptArea.setText(prompt != null ? prompt : "");
            });
        });

        String accountTitle = accountName.split(Pattern.quote(" ("))[0];
        chatHistoryArea.setText("--- Conversa de Teste com " + accountTitle + " ---\n");
    }

    @FXML
    private void handleToggleServer() {
        if (!isServerRunning.get()) {
            if (availableModels.isEmpty() || availableModels.get(0).contains("não encontrada")) {
                JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Erro",
                        "Nenhum modelo de IA (.gguf) encontrado na pasta 'model'.");
                return;
            }

            String selectedModel = modelComboBox.getSelectionModel().getSelectedItem();
            if (selectedModel == null || selectedModel.equals("Nenhum modelo .gguf encontrado.")) {
                JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Aviso", "Selecione um modelo de IA antes de ativar.");
                return;
            }

            toggleServerButton.setDisable(true);
            serverStatus.set("Iniciando...");

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    String llamaCppPath = "vendor/llama.cpp/server.exe";
                    String modelPath = Paths.get("model", selectedModel).toString();

                    ProcessBuilder builder = new ProcessBuilder(llamaCppPath, "-m", modelPath, "--port", "8080");
                    builder.redirectErrorStream(true);
                    aiServerProcess = builder.start();

                    // 👉 registra no ProcessManager
                    ProcessManager.setAiServerProcess(aiServerProcess);

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(aiServerProcess.getInputStream()))) {
                        String line;
                        boolean serverReady = false;
                        while ((line = reader.readLine()) != null) {
                            System.out.println("[AI Server]: " + line);
                            if (line.contains("listening on")) {
                                serverReady = true;
                                break;
                            }
                        }

                        if (serverReady) {
                            Platform.runLater(() -> {
                                isServerRunning.set(true);
                                serverStatus.set("Ativo");
                                JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso",
                                        "Servidor de IA ativado com sucesso!");
                                toggleServerButton.setDisable(false);
                            });
                        } else {
                            throw new IOException(
                                    "O servidor de IA foi encerrado inesperadamente antes de ficar pronto.");
                        }
                    }

                } catch (IOException e) {
                    ProcessManager.stopAiServer(); // 👉 mata se falhou
                    Platform.runLater(() -> {
                        isServerRunning.set(false);
                        serverStatus.set("Inativo");
                        JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro",
                                "Não foi possível iniciar o servidor de IA: " + e.getMessage());
                        toggleServerButton.setDisable(false);
                    });
                }
            });
        } else {
            ProcessManager.stopAiServer(); // 👉 encerra corretamente
            aiServerProcess = null;
            isServerRunning.set(false);
            serverStatus.set("Inativo");
            JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Servidor de IA desativado.");
        }
    }

    @FXML
    private void handleSavePrompt() {
        String account = accountComboBox.getSelectionModel().getSelectedItem();
        if (account == null || account.equals("Selecione uma conta...")) {
            JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Aviso", "Selecione uma conta antes de salvar.");
            return;
        }

        String sessionId = account.split(" ")[0];
        String prompt = personalityPromptArea.getText();

        FirebaseService.saveAIPersonality(userId, sessionId, prompt).thenAccept(success -> {
            Platform.runLater(() -> {
                if (success) {
                    JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso",
                            "Personalidade salva para " + sessionId + ".");
                } else {
                    JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro", "Erro ao salvar a personalidade no Firebase.");
                }
            });
        });
    }

    // =================================================================================
    // MÉTODO ATUALIZADO PARA INCLUIR A LÓGICA DE BUSCA
    // =================================================================================
    @FXML
    private void handleSendTestMessage() {
        String message = userTestInput.getText();
        if (message == null || message.isBlank()) {
            return;
        }

        String userMessage = message.trim();
        userTestInput.clear();
        chatHistoryArea.appendText("Você: " + userMessage + "\n");
        sendTestMessageButton.setDisable(true);

        // Verifica se é um comando de busca
        if (userMessage.toLowerCase().startsWith("/search ")) {
            String query = userMessage.substring(8).trim();
            handleSearchCommand(query);
        } else {
            handleStandardMessage(userMessage);
        }
    }

    private void handleSearchCommand(String query) {
        chatHistoryArea.appendText("IA: Buscando na internet por \"" + query + "\"...\n");

        SearchService.searchInternet(query).thenAcceptAsync(searchResults -> {
            Platform.runLater(() -> {
                // Remove a mensagem "Buscando..."
                int lastLineStart = chatHistoryArea.getText().lastIndexOf("IA: Buscando na internet");
                if (lastLineStart != -1) {
                    chatHistoryArea.deleteText(lastLineStart, chatHistoryArea.getLength());
                }

                // Verifica se a busca retornou um erro (ex: API Key não configurada)
                if (searchResults.startsWith("ERRO:")) {
                    chatHistoryArea.appendText("IA: " + searchResults + "\n\n");
                    sendTestMessageButton.setDisable(false);
                    return;
                }

                chatHistoryArea.appendText("IA a pensar...\n");

                // Constrói um prompt específico para a IA resumir os resultados
                String promptForAI = "Com base nos seguintes resultados de pesquisa, responda à pergunta original do usuário de forma clara e concisa.\n\n"
                        + "--- RESULTADOS DA PESQUISA ---\n"
                        + searchResults + "\n"
                        + "--- FIM DOS RESULTADOS ---\n\n"
                        + "Pergunta Original: " + query;

                // Cria um histórico temporário para esta única interação
                List<Object> searchContextHistory = new ArrayList<>();
                // Não adicionamos a mensagem do usuário ao histórico principal, pois ela era um
                // comando

                new Thread(() -> {
                    // Usamos o histórico temporário aqui
                    AIService.generateResponseAsync(searchContextHistory, promptForAI, AI_TASK_EXECUTOR)
                            .thenAcceptAsync(response -> {
                                Platform.runLater(() -> {
                                    int thinkingLineStart = chatHistoryArea.getText().lastIndexOf("IA a pensar...");
                                    if (thinkingLineStart != -1) {
                                        chatHistoryArea.deleteText(thinkingLineStart, chatHistoryArea.getLength());
                                    }

                                    chatHistoryArea.appendText("IA: " + response + "\n\n");

                                    // Adiciona a pergunta original e a resposta da IA ao histórico principal
                                    // para que a conversa tenha contexto.
                                    chatTestHistory.add(new HashMap<String, String>() {
                                        {
                                            put("role", "user");
                                            put("content", "Pesquise por: " + query);
                                        }
                                    });
                                    chatTestHistory.add(new HashMap<String, String>() {
                                        {
                                            put("role", "assistant");
                                            put("content", response);
                                        }
                                    });

                                    sendTestMessageButton.setDisable(false);
                                });
                            });
                }).start();
            });
        });
    }

    private void handleStandardMessage(String userMessage) {
        // Lógica original para mensagens normais
        if (chatTestHistory.isEmpty()) {
            chatTestHistory.add(new HashMap<String, String>() {
                {
                    put("role", "system");
                    put("content", personalityPromptArea.getText());
                }
            });
        }

        chatTestHistory.add(new HashMap<String, String>() {
            {
                put("role", "user");
                put("content", userMessage);
            }
        });

        chatHistoryArea.appendText("IA a pensar...\n");

        new Thread(() -> {
            AIService.generateResponseAsync(chatTestHistory, personalityPromptArea.getText(), AI_TASK_EXECUTOR)
                    .thenAcceptAsync(response -> {
                        Platform.runLater(() -> {
                            int lastLineStart = chatHistoryArea.getText().lastIndexOf("IA a pensar...");
                            if (lastLineStart != -1) {
                                chatHistoryArea.deleteText(lastLineStart, chatHistoryArea.getLength());
                            }

                            chatHistoryArea.appendText("IA: " + response + "\n\n");

                            chatTestHistory.add(new HashMap<String, String>() {
                                {
                                    put("role", "assistant");
                                    put("content", response);
                                }
                            });

                            sendTestMessageButton.setDisable(false);
                        });
                    });
        }).start();
    }
}