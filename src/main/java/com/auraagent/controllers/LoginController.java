package com.auraagent.controllers;

import com.auraagent.services.FirebaseService;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.util.function.Consumer;
import java.util.prefs.Preferences;

public class LoginController {

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;
    @FXML
    private Text statusMessage;
    @FXML
    private CheckBox rememberMeCheckBox;
    @FXML
    private BorderPane rootPane;

    private final SimpleStringProperty email = new SimpleStringProperty("");
    private final SimpleStringProperty statusMessageText = new SimpleStringProperty("");
    private final SimpleStringProperty loginButtonText = new SimpleStringProperty("Entrar");
    private final SimpleBooleanProperty isLoginEnabled = new SimpleBooleanProperty(true);

    private Consumer<String> onLoginSuccess;

    private final Preferences prefs = Preferences.userNodeForPackage(LoginController.class);
    private double xOffset = 0;
    private double yOffset = 0;

    @FXML
    public void initialize() {
        emailField.textProperty().bindBidirectional(email);
        statusMessage.textProperty().bind(statusMessageText);
        loginButton.textProperty().bind(loginButtonText);
        loginButton.disableProperty().bind(isLoginEnabled.not());

        loadPreferences();

        // Lógica para arrastar a janela (necessário para janela sem decoração)
        rootPane.setOnMousePressed(event -> {
            Stage stage = (Stage) rootPane.getScene().getWindow();
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        rootPane.setOnMouseDragged(event -> {
            Stage stage = (Stage) rootPane.getScene().getWindow();
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
    }

    public void setOnLoginSuccess(Consumer<String> onLoginSuccess) {
        this.onLoginSuccess = onLoginSuccess;
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        statusMessageText.set("");
        String password = passwordField.getText();

        if (email.get() == null || email.get().isBlank() || password.isBlank()) {
            statusMessageText.set("Por favor, preencha e-mail e senha.");
            return;
        }

        updateUIForLogin(true);

        // Chama o método correto: signInAsync
        FirebaseService.signInAsync(email.get(), password)
                .thenAcceptAsync(user -> {
                    if (user != null && user.getLocalId() != null) {
                        savePreferences();

                        if (onLoginSuccess != null) {
                            Platform.runLater(() -> onLoginSuccess.accept(user.getLocalId()));
                        }
                    } else {
                        Platform.runLater(() -> {
                            statusMessageText.set("E-mail ou senha inválidos.");
                            updateUIForLogin(false);
                        });
                    }
                }).exceptionally(ex -> {
                    Platform.runLater(() -> {
                        statusMessageText.set("Erro ao conectar: " + ex.getMessage());
                        updateUIForLogin(false);
                    });
                    return null;
                });
    }

    private void updateUIForLogin(boolean isLoggingIn) {
        isLoginEnabled.set(!isLoggingIn);
        loginButtonText.set(isLoggingIn ? "A entrar..." : "Entrar");
    }

    private void loadPreferences() {
        String savedEmail = prefs.get("email", null);
        String savedPassword = prefs.get("password", null);

        if (savedEmail != null && savedPassword != null) {
            emailField.setText(savedEmail);
            passwordField.setText(savedPassword);
            rememberMeCheckBox.setSelected(true);
        }
    }

    private void savePreferences() {
        if (rememberMeCheckBox.isSelected()) {
            prefs.put("email", emailField.getText());
            prefs.put("password", passwordField.getText());
        } else {
            prefs.remove("email");
            prefs.remove("password");
        }
    }

    // --- MÉTODOS PARA OS BOTÕES DA JANELA ---
    @FXML
    private void handleMinimize() {
        Stage stage = (Stage) rootPane.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void handleMaximize() {
        Stage stage = (Stage) rootPane.getScene().getWindow();
        stage.setMaximized(!stage.isMaximized());
    }

    @FXML
    private void handleClose() {
        Platform.exit(); // Fecha a aplicação de forma segura
        System.exit(0);
    }
}