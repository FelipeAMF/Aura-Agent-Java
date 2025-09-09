package com.auraagent.controllers;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Optional;

import com.auraagent.models.WhatsappAccount;
import com.auraagent.services.WhatsappService;
import com.auraagent.utils.JavaFxUtils;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class SettingsController implements MainAppController.InitializableController {

    @FXML
    private ListView<WhatsappAccount> accountsListView;
    @FXML
    private ImageView qrCodeImageView;
    @FXML
    private Text qrStatusText;

    private String userId;
    private final WhatsappService whatsappService = new WhatsappService();
    private final ObservableList<WhatsappAccount> accounts = FXCollections.observableArrayList();
    private Timeline statusTimer;

    @Override
    public void initialize(String userId) {
        this.userId = userId;
        accountsListView.setItems(accounts);

        accountsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(WhatsappAccount item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String phone = item.getPhoneNumber() != null ? " (" + item.getPhoneNumber() + ")" : "";
                    setText(String.format("%s%s - [%s]", item.getSessionId(), phone, item.getStatus()));
                }
            }
        });

        accountsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateQrCodeDisplay(newVal);
        });

        setupStatusTimer();
        refreshData();
    }

    private void setupStatusTimer() {
        statusTimer = new Timeline(new KeyFrame(Duration.seconds(3), event -> refreshData()));
        statusTimer.setCycleCount(Timeline.INDEFINITE);
        statusTimer.play();
    }

    private void refreshData() {
        WhatsappAccount currentSelection = accountsListView.getSelectionModel().getSelectedItem();
        whatsappService.getStatusAsync().thenAcceptAsync(sessions -> {
            Platform.runLater(() -> {
                accounts.setAll(sessions);
                if (currentSelection != null) {
                    Optional<WhatsappAccount> reselect = accounts.stream()
                            .filter(acc -> acc.getSessionId().equals(currentSelection.getSessionId()))
                            .findFirst();
                    reselect.ifPresent(acc -> accountsListView.getSelectionModel().select(acc));
                }
                updateQrCodeDisplay(accountsListView.getSelectionModel().getSelectedItem());
            });
        });
    }

    private void updateQrCodeDisplay(WhatsappAccount account) {
        if (account == null) {
            qrCodeImageView.setImage(null);
            qrStatusText.setText("Selecione ou adicione uma conta.");
            qrCodeImageView.setVisible(false);
            qrStatusText.setVisible(true);
            return;
        }

        if ("QR Code".equals(account.getStatus()) && account.getQrCode() != null && !account.getQrCode().isBlank()) {
            try {
                String base64Data = account.getQrCode().split(",")[1];
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                Image image = new Image(new ByteArrayInputStream(imageBytes));
                qrCodeImageView.setImage(image);
                qrCodeImageView.setVisible(true);
                qrStatusText.setVisible(false);
            } catch (Exception e) {
                qrStatusText.setText("Erro ao renderizar QR Code.");
                qrCodeImageView.setVisible(false);
                qrStatusText.setVisible(true);
            }
        } else if ("Conectado".equals(account.getStatus())) {
            qrCodeImageView.setImage(null);
            qrStatusText.setText("✅ Conta conectada com sucesso!");
            qrCodeImageView.setVisible(false);
            qrStatusText.setVisible(true);
        } else {
            qrCodeImageView.setImage(null);
            qrStatusText.setText("Aguardando status do servidor...");
            qrCodeImageView.setVisible(false);
            qrStatusText.setVisible(true);
        }
    }

    @FXML
    private void handleAddAccount() {
        TextInputDialog dialog = new TextInputDialog("Nova_Conta");
        dialog.setTitle("Nova Conta");
        dialog.setHeaderText("Digite um nome para a nova conta (sem espaços ou caracteres especiais):");
        dialog.setContentText("Nome da Sessão:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(sessionId -> {
            if (!sessionId.isBlank()) {
                String sanitizedSessionId = sessionId.replaceAll("[^a-zA-Z0-9_-]", "");
                boolean exists = accounts.stream()
                        .anyMatch(acc -> acc.getSessionId().equalsIgnoreCase(sanitizedSessionId));
                if (exists) {
                    JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Aviso", "Uma conta com este nome já existe.");
                    return;
                }
                whatsappService.connectAsync(sanitizedSessionId)
                        .thenAccept(success -> {
                            Platform.runLater(() -> {
                                if (success) {
                                    JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso",
                                            "Solicitação de conexão enviada para a nova conta. Aguarde o QR Code.");
                                } else {
                                    JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro",
                                            "Não foi possível conectar a nova conta.");
                                }
                                refreshData();
                            });
                        });
            }
        });
    }

    @FXML
    private void handleDisconnectAccount() {
        WhatsappAccount selected = accountsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Aviso", "Selecione uma conta da lista para desconectar.");
            return;
        }
        whatsappService.disconnectAsync(selected.getSessionId())
                .thenAccept(success -> {
                    Platform.runLater(() -> {
                        if (success) {
                            JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso",
                                    "Solicitação de desconexão enviada.");
                        } else {
                            JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro", "Erro ao desconectar.");
                        }
                        refreshData();
                    });
                });
        qrCodeImageView.setImage(null);
        qrStatusText.setText("Desconectando... Aguarde.");
    }
}
