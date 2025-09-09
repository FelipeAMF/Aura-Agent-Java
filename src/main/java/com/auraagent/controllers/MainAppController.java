package com.auraagent.controllers;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node; // Importação importante
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class MainAppController {

    @FXML
    private BorderPane mainPane;
    @FXML
    private ToggleGroup navGroup;
    @FXML
    private HBox titleBar;

    private String userId;
    // Alterado de Pane para Node para aceitar qualquer tipo de layout (incluindo
    // ScrollPane)
    private final Map<String, Node> viewCache = new HashMap<>();
    private final Map<String, InitializableController> controllerCache = new HashMap<>();
    private InitializableController currentController = null;

    private double xOffset = 0;
    private double yOffset = 0;

    public void initialize(String userId) {
        this.userId = userId;
        setupShutdownHook();
        loadView("Campaign"); // Carrega a primeira tela

        navGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle != null) {
                if (currentController != null) {
                    currentController.onViewHidden();
                }
                ToggleButton selectedToggle = (ToggleButton) newToggle;
                loadView(selectedToggle.getId());
            }
        });

        // Permite arrastar a janela pela barra de título
        titleBar.setOnMousePressed(event -> {
            Stage stage = (Stage) mainPane.getScene().getWindow();
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event -> {
            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
    }

    private void setupShutdownHook() {
        // Platform.runLater garante que a cena já existe quando este código for
        // executado
        Platform.runLater(() -> {
            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                // Notifica todos os controladores para desligarem
                controllerCache.values().forEach(InitializableController::shutdown);
                // Fecha a aplicação de forma segura
                Platform.exit();
                System.exit(0);
            });
        });
    }

    private void loadView(String viewName) {
        try {
            if (viewCache.containsKey(viewName)) {
                mainPane.setCenter(viewCache.get(viewName));
                currentController = controllerCache.get(viewName);
                if (currentController != null) {
                    currentController.onViewShown();
                }
            } else {
                String fxmlPath = "/com/auraagent/views/" + viewName + "View.fxml";
                URL resourceUrl = getClass().getResource(fxmlPath);

                if (resourceUrl == null) {
                    System.err.println("Erro Crítico: Não foi possível encontrar o arquivo FXML: " + fxmlPath);
                    return;
                }

                FXMLLoader loader = new FXMLLoader(resourceUrl);
                // Carrega como um Node genérico
                Node view = loader.load();

                Object controller = loader.getController();
                if (controller instanceof InitializableController) {
                    ((InitializableController) controller).initialize(userId);
                    controllerCache.put(viewName, (InitializableController) controller);
                    currentController = (InitializableController) controller;
                    currentController.onViewShown();
                }

                viewCache.put(viewName, view);
                mainPane.setCenter(view);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- MÉTODOS PARA OS BOTÕES DA JANELA ---

    @FXML
    private void handleMinimize() {
        Stage stage = (Stage) mainPane.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void handleMaximize() {
        Stage stage = (Stage) mainPane.getScene().getWindow();
        stage.setMaximized(!stage.isMaximized());
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) mainPane.getScene().getWindow();
        stage.close(); // Isso vai acionar o setOnCloseRequest que definimos
    }

    public interface InitializableController {
        void initialize(String userId);

        default void onViewShown() {
        }

        default void onViewHidden() {
        }

        default void shutdown() {
        }
    }
}