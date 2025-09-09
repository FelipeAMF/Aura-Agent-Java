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
import javafx.scene.text.Text;

import java.util.function.Consumer;
import java.util.prefs.Preferences; // Import necessário

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
    private CheckBox rememberMeCheckBox; // Referência para a nova CheckBox

    private final SimpleStringProperty email = new SimpleStringProperty("");
    private final SimpleStringProperty statusMessageText = new SimpleStringProperty("");
    private final SimpleStringProperty loginButtonText = new SimpleStringProperty("Entrar");
    private final SimpleBooleanProperty isLoginEnabled = new SimpleBooleanProperty(true);

    private Consumer<String> onLoginSuccess;

    // Objeto para aceder às preferências salvas no computador do utilizador
    private final Preferences prefs = Preferences.userNodeForPackage(LoginController.class);

    @FXML
    public void initialize() {
        emailField.textProperty().bindBidirectional(email);
        statusMessage.textProperty().bind(statusMessageText);
        loginButton.textProperty().bind(loginButtonText);
        loginButton.disableProperty().bind(isLoginEnabled.not());

        // --- LÓGICA PARA CARREGAR DADOS SALVOS ---
        loadPreferences();
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

        FirebaseService.signInAsync(email.get(), password)
                .thenAcceptAsync(user -> {
                    if (user != null && user.getLocalId() != null) {
                        // --- LÓGICA PARA SALVAR DADOS ---
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

    /**
     * Carrega o e-mail e a senha salvos (se existirem) quando a aplicação inicia.
     */
    private void loadPreferences() {
        String savedEmail = prefs.get("email", null);
        String savedPassword = prefs.get("password", null);

        if (savedEmail != null && savedPassword != null) {
            emailField.setText(savedEmail);
            passwordField.setText(savedPassword);
            rememberMeCheckBox.setSelected(true);
        }
    }

    /**
     * Salva ou apaga as credenciais com base na seleção da CheckBox "Lembrar-me".
     */
    private void savePreferences() {
        if (rememberMeCheckBox.isSelected()) {
            // Se a caixa está marcada, salva o e-mail and a senha
            prefs.put("email", emailField.getText());
            prefs.put("password", passwordField.getText());
        } else {
            // Se a caixa não está marcada, remove quaisquer credenciais salvas
            prefs.remove("email");
            prefs.remove("password");
        }
    }
}