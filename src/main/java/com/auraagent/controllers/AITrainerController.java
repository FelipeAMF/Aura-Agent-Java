package com.auraagent.controllers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.auraagent.models.WhatsappAccount;
import com.auraagent.services.AIService;
import com.auraagent.services.FirebaseService;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final StringProperty serverStatus = new SimpleStringProperty("Inativo"); // Nova propriedade para o status
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
        loadAccountsAsync();
    }

    private void setupBindings() {
        // Apenas o ComboBox do modelo será desativado, permitindo a edição de personalidade com o servidor rodando.
        modelComboBox.disableProperty().bind(isServerRunning);
        chatPane.disableProperty().bind(isServerRunning.not());

        aiStatusLabel.textProperty().bind(serverStatus); 

        toggleServerButton.textProperty().bind(
                Bindings.when(isServerRunning).then("Desativar Servidor de IA").otherwise("Ativar Servidor de IA"));

        serverStatus.addListener((obs, oldVal, newVal) -> {
            aiStatusLabel.getStyleClass().removeAll("success-label", "danger-label", "warning-label");
            if (newVal.equals("Ativo")) {
                aiStatusLabel.getStyleClass().add("success-label");
            } else if (newVal.equals("Inativo")) {
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
                availableAccounts.clear();
                availableAccounts.add("Selecione uma conta...");
                for (WhatsappAccount acc : accountsData) {
                    if (acc.getPhoneNumber() != null) {
                        availableAccounts.add(acc.getSessionId() + " (" + acc.getPhoneNumber() + ")");
                    }
                }
                accountComboBox.getSelectionModel().selectFirst();
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
        
        // Carrega a personalidade do Firebase
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

                    // Nova lógica para ler a saída do servidor e esperar pela confirmação
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(aiServerProcess.getInputStream()))) {
                        String line;
                        boolean serverReady = false;
                        while ((line = reader.readLine()) != null) {
                            System.out.println("[AI Server]: " + line); // Log para depuração
                            if (line.contains("listening on")) { 
                                serverReady = true;
                                break; 
                            }
                        }

                        if (serverReady) {
                            Platform.runLater(() -> {
                                isServerRunning.set(true);
                                serverStatus.set("Ativo");
                                JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Servidor de IA ativado com sucesso!");
                                toggleServerButton.setDisable(false);
                            });
                        } else {
                            throw new IOException("O servidor de IA foi encerrado inesperadamente antes de ficar pronto.");
                        }
                    }

                } catch (IOException e) {
                    if (aiServerProcess != null) {
                        aiServerProcess.destroy();
                    }
                    Platform.runLater(() -> {
                        isServerRunning.set(false);
                        serverStatus.set("Inativo");
                        JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro", "Não foi possível iniciar o servidor de IA: " + e.getMessage());
                        toggleServerButton.setDisable(false);
                    });
                }
            });
        } else {
            if (aiServerProcess != null) {
                aiServerProcess.destroy();
                isServerRunning.set(false);
                serverStatus.set("Inativo");
                JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Servidor de IA desativado.");
            }
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

        // Salva a personalidade no Firebase
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

    @FXML
    private void handleSendTestMessage() {
        String message = userTestInput.getText();
        if (message == null || message.isBlank())
            return;

        String userMessage = message;
        userTestInput.clear();
        chatHistoryArea.appendText("Você: " + userMessage + "\n");
        chatTestHistory.add(new java.util.HashMap<String, String>() {
            {
                put("role", "user");
                put("content", userMessage);
            }
        });

        sendTestMessageButton.setDisable(true);
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
                            chatTestHistory.add(new java.util.HashMap<String, String>() {
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
