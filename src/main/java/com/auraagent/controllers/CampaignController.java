package com.auraagent.controllers;

import com.auraagent.models.ContactModel;
import com.auraagent.models.ReportModel;
import com.auraagent.models.WhatsappAccount;
import com.auraagent.services.AIService;
import com.auraagent.services.FirebaseService;
import com.auraagent.services.SchedulerService;
import com.auraagent.services.WhatsappService;
import com.auraagent.utils.JavaFxUtils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CampaignController implements MainAppController.InitializableController {

    @FXML
    private ComboBox<String> contactListComboBox;
    @FXML
    private TextArea contactsDisplay;
    @FXML
    private ListView<CheckBox> sendersListView;
    @FXML
    private TextArea spintaxMessage;
    @FXML
    private ComboBox<String> templateSelector;
    @FXML
    private ComboBox<String> delayComboBox;
    @FXML
    private Button startButton, pauseButton, stopButton, testSendButton, saveTemplateButton;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Button attachImageButton, removeImageButton;
    @FXML
    private Label attachedImageLabel;
    @FXML
    private CheckBox smartSendCheckBox;
    @FXML
    private DatePicker scheduleDatePicker;
    @FXML
    private TextField scheduleTimeField;
    @FXML
    private Button scheduleButton;

    private File attachedImageFile = null;
    private String userId;
    private String userToken;
    private final WhatsappService whatsappService = new WhatsappService();
    private Thread campaignThread;

    private final SimpleBooleanProperty isSending = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty isPaused = new SimpleBooleanProperty(false);

    private final ObservableList<String> contactListNames = FXCollections.observableArrayList();
    private final ObservableList<CheckBox> senderAccounts = FXCollections.observableArrayList();
    private final ObservableList<String> templateNames = FXCollections.observableArrayList();
    private final List<ContactModel> contactsInList = new ArrayList<>();

    private static final ExecutorService AI_TASK_EXECUTOR = Executors.newFixedThreadPool(1);

    @Override
    public void initialize(String userId) {
        this.userId = userId;
        setupBindings();
        setupUI();
    }

    @Override
    public void onViewShown() {
        refreshData();
    }

    @Override
    public void shutdown() {
        SchedulerService.getInstance().shutdown();
    }

    private void setupBindings() {
        contactListComboBox.disableProperty().bind(isSending);
        templateSelector.disableProperty().bind(isSending);
        sendersListView.disableProperty().bind(isSending);
        spintaxMessage.disableProperty().bind(isSending);
        delayComboBox.disableProperty().bind(isSending);
        testSendButton.disableProperty().bind(isSending);
        saveTemplateButton.disableProperty().bind(isSending);
        startButton.disableProperty().bind(isSending);
        pauseButton.disableProperty().bind(isSending.not());
        stopButton.disableProperty().bind(isSending.not());
        attachImageButton.disableProperty().bind(isSending);
        removeImageButton.disableProperty().bind(isSending);
        smartSendCheckBox.disableProperty().bind(isSending);
        scheduleDatePicker.disableProperty().bind(isSending);
        scheduleTimeField.disableProperty().bind(isSending);
        scheduleButton.disableProperty().bind(isSending);
        pauseButton.textProperty().bind(
                Bindings.when(isPaused).then("Retomar").otherwise("Pausar"));
    }

    private void setupUI() {
        contactListComboBox.setItems(contactListNames);
        sendersListView.setItems(senderAccounts);
        templateSelector.setItems(templateNames);
        delayComboBox.setItems(FXCollections.observableArrayList("5s", "10s", "15s", "30s", "60s"));
        delayComboBox.setValue("10s");
        contactListComboBox.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> loadSelectedContactList(newVal));
        templateSelector.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> loadSelectedTemplate(newVal));
    }

    public void refreshData() {
        FirebaseService.getContactListsAsync(userId, userToken).thenAcceptAsync(lists -> {
            Platform.runLater(() -> {
                String selected = contactListComboBox.getSelectionModel().getSelectedItem();
                contactListNames.clear();
                contactListNames.add("Selecione uma lista");
                if (lists != null)
                    contactListNames.addAll(lists.keySet().stream().sorted().collect(Collectors.toList()));
                if (selected != null && contactListNames.contains(selected)) {
                    contactListComboBox.getSelectionModel().select(selected);
                } else {
                    contactListComboBox.getSelectionModel().selectFirst();
                }
            });
        });

        FirebaseService.getCampaignTemplates(userId, userToken).thenAcceptAsync(templates -> {
            Platform.runLater(() -> {
                String selected = templateSelector.getEditor().getText();
                templateNames.clear();
                templateNames.add("Selecionar Modelo");
                if (templates != null)
                    templateNames.addAll(templates.keySet().stream().sorted().collect(Collectors.toList()));
                if (selected != null && !selected.isEmpty() && templateNames.contains(selected)) {
                    templateSelector.getSelectionModel().select(selected);
                } else {
                    templateSelector.getSelectionModel().selectFirst();
                }
            });
        });

        whatsappService.getStatusAsync().thenAcceptAsync(accounts -> {
            Platform.runLater(() -> {
                senderAccounts.clear();
                List<WhatsappAccount> connectedAccounts = accounts.stream()
                        .filter(acc -> "Conectado".equals(acc.getStatus()))
                        .collect(Collectors.toList());
                for (WhatsappAccount acc : connectedAccounts) {
                    senderAccounts.add(new CheckBox(acc.getSessionId() + " (" + acc.getPhoneNumber() + ")"));
                }
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void loadSelectedContactList(String listName) {
        contactsInList.clear();
        if (listName == null || listName.equals("Selecione uma lista")) {
            updateContactsDisplay();
            return;
        }

        statusLabel.setText("A carregar contatos...");
        FirebaseService.getContactsFromListAsync(userId, userToken, listName).thenAcceptAsync(contacts -> {
            Platform.runLater(() -> {
                if (contacts != null) {
                    contacts.forEach((phone, details) -> {
                        if (phone.equals("_placeholder"))
                            return;

                        if (details instanceof Map) {
                            String name = ((Map<String, String>) details).getOrDefault("name", "Sem Nome");
                            ContactModel contact = new ContactModel();
                            contact.setName(name);
                            contact.setPhone(phone);
                            contactsInList.add(contact);
                        } else if (details instanceof Boolean) {
                            ContactModel contact = new ContactModel();
                            contact.setName("Contato");
                            contact.setPhone(phone);
                            contactsInList.add(contact);
                        }
                    });
                }
                updateContactsDisplay();
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void loadSelectedTemplate(String templateName) {
        if (templateName == null || templateName.equals("Selecionar Modelo"))
            return;

        FirebaseService.getTemplateData(userId, userToken, templateName).thenAcceptAsync(data -> {
            Platform.runLater(() -> {
                if (data != null && data.containsKey("settings")) {
                    Map<String, Object> settings = (Map<String, Object>) data.get("settings");
                    spintaxMessage.setText((String) settings.getOrDefault("spintax_template", ""));
                    String delay = settings.getOrDefault("delay", "10").toString() + "s";
                    delayComboBox.setValue(delay);
                    statusLabel.setText("Modelo '" + templateName + "' carregado.");
                }
            });
        });
    }

    private void updateContactsDisplay() {
        String text = contactsInList.stream()
                .map(c -> c.getName() + ": " + c.getPhone())
                .collect(Collectors.joining("\n"));
        contactsDisplay.setText(text);
        statusLabel.setText(contactsInList.size() + " contatos carregados.");
    }

    private String parseSpintax(String text) {
        Random random = new Random();
        Pattern pattern = Pattern.compile("\\{([^\\{\\}]+)\\}");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String[] options = matcher.group(1).split("\\|");
            text = text.replace(matcher.group(0), options[random.nextInt(options.length)]);
            matcher = pattern.matcher(text);
        }
        return text;
    }

    @FXML
    private void handleAttachImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecionar Imagem para Envio");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imagens", "*.jpg", "*.png", "*.jpeg"),
                new FileChooser.ExtensionFilter("Todos os Ficheiros", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(attachImageButton.getScene().getWindow());
        if (selectedFile != null) {
            this.attachedImageFile = selectedFile;
            attachedImageLabel.setText("Anexo: " + selectedFile.getName());
        }
    }

    @FXML
    private void handleRemoveImage() {
        this.attachedImageFile = null;
        attachedImageLabel.setText("Nenhuma imagem anexada.");
    }

    @FXML
    private void handleScheduleCampaign() {
        LocalDate date = scheduleDatePicker.getValue();
        String timeString = scheduleTimeField.getText();

        if (date == null || timeString.isBlank()) {
            JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Dados Incompletos",
                    "Por favor, selecione a data e a hora para o agendamento.");
            return;
        }

        LocalTime time;
        try {
            time = LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Formato de Hora Inválido",
                    "Por favor, insira a hora no formato HH:mm (ex: 14:30).");
            return;
        }

        LocalDateTime scheduledDateTime = LocalDateTime.of(date, time);
        LocalDateTime now = LocalDateTime.now();

        if (scheduledDateTime.isBefore(now)) {
            JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Data Inválida",
                    "A data e hora do agendamento não podem estar no passado.");
            return;
        }

        if (!isCampaignReady()) {
            JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Campanha Incompleta",
                    "Selecione uma lista de contatos e pelo menos uma conta de envio antes de agendar.");
            return;
        }

        long delay = Duration.between(now, scheduledDateTime).toMillis();
        SchedulerService.getInstance().schedule(this::startSendingLogic, delay, TimeUnit.MILLISECONDS);

        isSending.set(true);
        statusLabel.setText("Campanha agendada para "
                + scheduledDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")));
        JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Agendamento Confirmado",
                "A sua campanha foi agendada com sucesso!");
    }

    @FXML
    private void handleStartSending() {
        if (!isCampaignReady()) {
            JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Aviso",
                    "Selecione uma lista de contatos e pelo menos uma conta de envio.");
            return;
        }
        startSendingLogic();
    }

    private void startSendingLogic() {
        if (smartSendCheckBox.isSelected() && !isAIServerActive()) {
            Platform.runLater(() -> JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Servidor de IA Inativo",
                    "Para usar o Envio Inteligente, por favor, ative o Servidor de IA na aba 'Treinador de IA'."));
            return;
        }

        Platform.runLater(() -> {
            isSending.set(true);
            isPaused.set(false);
        });

        campaignThread = new Thread(() -> {
            List<String> selectedSenders = senderAccounts.stream()
                    .filter(CheckBox::isSelected)
                    .map(cb -> cb.getText().split(" ")[0])
                    .collect(Collectors.toList());

            int totalContacts = contactsInList.size();
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            Path imagePath = (attachedImageFile != null) ? attachedImageFile.toPath() : null;

            for (int i = 0; i < totalContacts; i++) {
                if (!isSending.get())
                    break;
                while (isPaused.get()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                ContactModel contact = contactsInList.get(i);
                String sender = selectedSenders.get(i % selectedSenders.size());
                String baseMessage = parseSpintax(spintaxMessage.getText()).replace("{nome}", contact.getName());
                String messageToSend;

                if (smartSendCheckBox.isSelected()) {
                    final int currentProgress = i + 1;
                    Platform.runLater(() -> statusLabel
                            .setText(String.format("IA a gerar mensagem %d de %d...", currentProgress, totalContacts)));
                    messageToSend = AIService.rephraseMessageAsync(baseMessage, AI_TASK_EXECUTOR).join();
                } else {
                    messageToSend = baseMessage;
                }

                final int currentProgress = i + 1;
                Platform.runLater(() -> {
                    statusLabel.setText(String.format("Enviando %d de %d: para %s", currentProgress, totalContacts,
                            contact.getPhone()));
                    progressBar.setProgress((double) currentProgress / totalContacts);
                });

                whatsappService.sendMessageAsync(sender, contact.getPhone(), imagePath, messageToSend)
                        .thenAccept(success -> {
                            if (success)
                                successCount.incrementAndGet();
                            else
                                failCount.incrementAndGet();
                        }).join();

                try {
                    int delayMs = Integer.parseInt(delayComboBox.getValue().replace("s", "")) * 1000;
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (successCount.get() + failCount.get() > 0) {
                ReportModel report = new ReportModel();
                report.setDate(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
                report.setTotalContacts(successCount.get() + failCount.get());
                report.setSuccessCount(successCount.get());
                report.setFailCount(failCount.get());
                FirebaseService.saveReport(userId, report);
            }

            Platform.runLater(() -> {
                isSending.set(false);
                statusLabel.setText(
                        "Campanha finalizada. Sucessos: " + successCount.get() + ", Falhas: " + failCount.get());
                progressBar.setProgress(0.0);
            });
        });

        campaignThread.setDaemon(true);
        campaignThread.start();
    }

    private boolean isCampaignReady() {
        List<String> selectedSenders = senderAccounts.stream()
                .filter(CheckBox::isSelected)
                .map(cb -> cb.getText().split(" ")[0])
                .collect(Collectors.toList());
        return !contactsInList.isEmpty() && !selectedSenders.isEmpty();
    }

    private boolean isAIServerActive() {
        try {
            var client = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create("http://localhost:8080/"))
                    .build();
            client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @FXML
    private void handleTogglePause() {
        isPaused.set(!isPaused.get());
        statusLabel.setText(isPaused.get() ? "Campanha pausada." : "Campanha retomada.");
    }

    @FXML
    private void handleStopSending() {
        if (JavaFxUtils.showConfirmation(Alert.AlertType.CONFIRMATION, "Parar Campanha",
                "Tem certeza que deseja parar o envio?")) {
            isSending.set(false);
            isPaused.set(false);
            if (campaignThread != null) {
                campaignThread.interrupt();
            }
        }
    }

    @FXML
    private void handleSendTestMessage() {
        List<String> selectedSenders = senderAccounts.stream()
                .filter(CheckBox::isSelected)
                .map(cb -> cb.getText().split(" ")[0])
                .collect(Collectors.toList());

        if (selectedSenders.isEmpty()) {
            JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Aviso", "Selecione uma conta de envio para o teste.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Enviar Teste");
        dialog.setHeaderText("Digite o número de telefone para o teste (formato internacional, ex: 5561...):");
        dialog.setContentText("Número:");
        Optional<String> result = dialog.showAndWait();

        result.ifPresent(number -> {
            if (!number.isBlank()) {
                String testMessage = parseSpintax(spintaxMessage.getText()).replace("{nome}", "Teste");
                statusLabel.setText("Enviando mensagem de teste para " + number);
                Path imagePath = (attachedImageFile != null) ? attachedImageFile.toPath() : null;

                whatsappService.sendMessageAsync(selectedSenders.get(0), number, imagePath, testMessage)
                        .thenAccept(success -> Platform.runLater(() -> {
                            statusLabel.setText(
                                    success ? "Mensagem de teste enviada!" : "Falha ao enviar mensagem de teste.");
                        }));
            }
        });
    }

    @FXML
    private void handleSaveTemplate() {
        String templateName = templateSelector.getEditor().getText();
        String message = spintaxMessage.getText();
        String delay = delayComboBox.getValue();

        if (templateName == null || templateName.isBlank() || templateName.equals("Selecionar Modelo")) {
            JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Nome Inválido",
                    "Digite um nome para o modelo antes de salvar.");
            return;
        }

        FirebaseService.saveTemplate(userId, userToken, templateName, message, delay).thenAccept(success -> {
            if (success) {
                Platform.runLater(() -> {
                    JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Modelo salvo com sucesso.");
                    refreshData();
                });
            } else {
                Platform.runLater(() -> JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro",
                        "Não foi possível salvar o modelo."));
            }
        });
    }
}