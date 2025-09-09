package com.auraagent.controllers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.auraagent.models.WhatsappAccount;
import com.auraagent.services.AIService;
import com.auraagent.services.WhatsappService;
import com.auraagent.utils.JavaFxUtils;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WhatsAppController implements MainAppController.InitializableController {

    @FXML
    private TextField intervalSecondsField;
    @FXML
    private ListView<CheckBox> accountsListView;
    @FXML
    private TextArea logOutputArea, warmerMessagesArea;
    @FXML
    private Button startButton, stopButton;

    private String userId;
    private final WhatsappService whatsappService = new WhatsappService();
    private final ObservableList<CheckBox> accounts = FXCollections.observableArrayList();
    private final SimpleBooleanProperty isWarmerRunning = new SimpleBooleanProperty(false);
    private Timeline warmerTimeline;
    private final Random random = new Random();
    private static final ExecutorService AI_TASK_EXECUTOR = Executors.newFixedThreadPool(2);

    // Histórico de conversas por par de contas
    private final Map<String, List<Map<String, String>>> conversationHistories = new ConcurrentHashMap<>();
    // Personalidades por conta
    private final Map<String, String> accountPersonalities = new ConcurrentHashMap<>();
    // Turno da conversa (quem deve responder quem)
    private final Map<String, String> conversationTurn = new ConcurrentHashMap<>();

    private final List<String> shortResponses = Arrays.asList(
            "haha", "kkk", "sim", "não", "talvez", "pode ser", "top", "boa!",
            "entendi", "vdd", "show", "beleza", "👍", "👀", "😂", "🤔");

    private final List<String> mixedPersonalities = Arrays.asList(
            "Você é uma amiga próxima, fala de forma curta e descontraída, como WhatsApp. Evite textões.",
            "Você é sério e analítico, mas responde de forma breve, poucas palavras, direto.",
            "Você é criativo e sonhador, mas sempre manda frases curtas, tipo WhatsApp.",
            "Você gosta de tecnologia, mas escreve rápido, respostas objetivas e curtas.",
            "Você é calmo e zen, mas responde com frases curtas e simples.");

    @Override
    public void initialize(String userId) {
        this.userId = userId;
        accountsListView.setItems(accounts);

        startButton.disableProperty().bind(isWarmerRunning);
        stopButton.disableProperty().bind(isWarmerRunning.not());
        intervalSecondsField.disableProperty().bind(isWarmerRunning);
        accountsListView.disableProperty().bind(isWarmerRunning);
        warmerMessagesArea.disableProperty().bind(isWarmerRunning);

        loadAccountsAsync();
    }

    private void loadAccountsAsync() {
        whatsappService.getStatusAsync().thenAcceptAsync(accountsData -> {
            Platform.runLater(() -> {
                accounts.clear();
                for (WhatsappAccount acc : accountsData) {
                    if ("Conectado".equals(acc.getStatus())) {
                        accounts.add(new CheckBox(acc.getSessionId() + " (" + acc.getPhoneNumber() + ")"));
                    }
                }
            });
        });
    }

    @FXML
    private void handleStartWarmer() {
        List<String> selectedAccounts = accounts.stream()
                .filter(CheckBox::isSelected)
                .map(cb -> cb.getText().split(" ")[0])
                .collect(Collectors.toList());

        List<String> starterMessages = Arrays.stream(warmerMessagesArea.getText().split("\\n"))
                .filter(s -> !s.trim().isEmpty()).collect(Collectors.toList());

        if (selectedAccounts.size() < 2) {
            JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Aviso",
                    "Selecione pelo menos 2 contas para o aquecimento.");
            return;
        }
        if (starterMessages.isEmpty()) {
            JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Aviso",
                    "Insira pelo menos uma mensagem inicial para o aquecimento.");
            return;
        }

        try {
            int interval = Integer.parseInt(intervalSecondsField.getText());
            if (interval <= 0) {
                JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Aviso", "O intervalo deve ser um número positivo.");
                return;
            }

            isWarmerRunning.set(true);
            conversationHistories.clear();
            accountPersonalities.clear();
            conversationTurn.clear();

            // Atribui uma "personalidade" a cada conta
            selectedAccounts.forEach(acc -> {
                String randomPersonality = mixedPersonalities.get(random.nextInt(mixedPersonalities.size()));
                accountPersonalities.put(acc, randomPersonality);
            });

            logMessage("--- Aquecedor com IA Iniciado ---");

            kickoffConversation(selectedAccounts, starterMessages);

            warmerTimeline = new Timeline(
                    new KeyFrame(Duration.seconds(interval), e -> runWarmerCycle(selectedAccounts, starterMessages)));
            warmerTimeline.setCycleCount(Timeline.INDEFINITE);
            warmerTimeline.play();

        } catch (NumberFormatException e) {
            JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro", "Intervalo inválido. Por favor, insira um número.");
        }
    }

    private void kickoffConversation(List<String> accounts, List<String> messages) {
        logMessage("Iniciando uma nova conversa...");

        Collections.shuffle(accounts);
        String acc1 = accounts.get(0);
        String acc2 = accounts.get(1);

        // Define quem fala primeiro
        conversationTurn.put(acc1, acc2);

        String starter = messages.get(random.nextInt(messages.size()));
        sendMessageBetweenAccounts(acc1, acc2, starter, "user");
    }

    private void runWarmerCycle(List<String> accounts, List<String> starterMessages) {
        logMessage("Ciclo de aquecimento rodando...");

        if (conversationTurn.isEmpty())
            return;

        String sender = conversationTurn.keySet().iterator().next();
        String receiver = conversationTurn.get(sender);
        if (receiver == null)
            return;

        // Alterna turno
        conversationTurn.clear();
        conversationTurn.put(receiver, sender);

        String convKey = getConversationKey(sender, receiver);
        final List<Map<String, String>> history = conversationHistories
                .computeIfAbsent(convKey, k -> new ArrayList<>());

        // 20% de chance de mandar resposta curta estilo WhatsApp
        if (random.nextDouble() < 0.2) {
            String shortReply = shortResponses.get(random.nextInt(shortResponses.size()));
            history.add(Map.of("role", "assistant", "content", shortReply));
            sendMessageBetweenAccounts(receiver, sender, shortReply, "assistant");
            return;
        }

        // Prompt para IA: conteúdo simples, curioso, curto
        String personality = accountPersonalities.get(receiver);
        List<Object> aiInput = new ArrayList<>(history);

        aiInput.add(Map.of("role", "system", "content",
                personality +
                        " Você está em uma conversa de WhatsApp. " +
                        "Responda em no máximo 2 frases curtas (até 80 caracteres cada). " +
                        "Fale como amigo, trazendo curiosidades ou comentários naturais. " +
                        "Exemplo: 'Você sabia que as estrelas que vemos já podem ter morrido?' " +
                        "A outra pessoa pode reagir com surpresa, risada ou interesse. " +
                        "NUNCA diga 'aguardando', 'entendido' ou 'próxima mensagem'."));

        AIService.generateResponseAsync(aiInput, personality, AI_TASK_EXECUTOR).thenAccept(aiResponse -> {
            String truncated = truncateMessage(aiResponse, 120);

            // Se sair resposta robótica, troca por algo curto
            if (truncated.toLowerCase().contains("aguardando") ||
                    truncated.toLowerCase().contains("entendido") ||
                    truncated.toLowerCase().contains("próxima mensagem")) {
                truncated = shortResponses.get(random.nextInt(shortResponses.size()));
            }

            history.add(Map.of("role", "assistant", "content", truncated));
            sendMessageBetweenAccounts(receiver, sender, truncated, "assistant");
        });
    }

    private void sendMessageBetweenAccounts(String senderId, String receiverId, String message, String role) {
        findPhoneNumber(receiverId).thenAccept(receiverPhoneNumber -> {
            if (receiverPhoneNumber == null) {
                logMessage("ERRO: Não encontrei número de " + receiverId);
                return;
            }
            logMessage(String.format("'%s' -> '%s': %s", senderId, receiverId, message));
            whatsappService.sendMessageAsync(senderId, receiverPhoneNumber, message);

            String convKey = getConversationKey(senderId, receiverId);
            conversationHistories.computeIfAbsent(convKey, k -> new ArrayList<>()) 
                    .add(Map.of("role", role, "content", message));
        });
    }

    private String getConversationKey(String acc1, String acc2) {
        return acc1.compareTo(acc2) < 0 ? acc1 + "|" + acc2 : acc2 + "|" + acc1;
    }

    private CompletableFuture<String> findPhoneNumber(String sessionId) {
        return whatsappService.getStatusAsync().thenApply(allAccounts -> allAccounts.stream()
                .filter(acc -> acc.getSessionId().equals(sessionId))
                .map(WhatsappAccount::getPhoneNumber)
                .findFirst()
                .orElse(null));
    }

    @FXML
    private void handleStopWarmer() {
        if (warmerTimeline != null) {
            warmerTimeline.stop();
        }
        isWarmerRunning.set(false);
        conversationHistories.clear();
        conversationTurn.clear();
        logMessage("--- Aquecedor Parado ---");
    }

    private void logMessage(String message) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logOutputArea.appendText(String.format("[%s] %s\n", timestamp, message));
        });
    }

    private String truncateMessage(String message, int maxChars) {
        if (message == null || message.isEmpty())
            return "";
        return message.length() <= maxChars ? message : message.substring(0, maxChars) + "...";
    }
}
