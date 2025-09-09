package com.auraagent.controllers;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class MainAppController {

    @FXML
    private BorderPane mainPane;
    @FXML
    private ToggleGroup navGroup;

    private String userId;
    private final Map<String, Pane> viewCache = new HashMap<>();
    private final Map<String, InitializableController> controllerCache = new HashMap<>();
    private InitializableController currentController = null;

    public void initialize(String userId) {
        this.userId = userId;
        setupShutdownHook();
        loadView("Campaign");

        navGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle != null) {
                if (currentController != null) {
                    currentController.onViewHidden();
                }
                // Troque o cast para ToggleButton
                ToggleButton selectedToggle = (ToggleButton) newToggle;
                loadView(selectedToggle.getId());
            }
        });
    }

    private void setupShutdownHook() {
        Platform.runLater(() -> {
            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                controllerCache.values().forEach(InitializableController::shutdown);
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
                Pane view = loader.load();

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