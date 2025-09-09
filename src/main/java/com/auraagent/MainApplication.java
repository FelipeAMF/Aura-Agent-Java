package com.auraagent;

import java.io.IOException;

import com.auraagent.controllers.LoginController;
import com.auraagent.controllers.MainAppController;
import com.auraagent.services.FirebaseService;
import com.auraagent.services.ProcessManager;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage; // Import necessário
import javafx.stage.StageStyle;

public class MainApplication extends Application {

    private Stage primaryStage;
    private double xOffset = 0;
    private double yOffset = 0;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        FirebaseService.initialize(); // Inicializa a conexão com o Firebase
        ProcessManager.startNodeServer(); // Inicia o servidor Node.js

        // Adiciona um shutdown hook para garantir que o servidor Node.js seja encerrado
        // ao fechar a aplicação
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Desligando o servidor Node.js via shutdown hook...");
            ProcessManager.stopNodeServer();
        }));

        primaryStage.setTitle("Aura Agent");
        primaryStage.setResizable(true); // Adiciona flexibilidade à janela
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/logo_aura.ico")));

        // Remove a barra de título padrão do sistema
        primaryStage.initStyle(StageStyle.UNDECORATED);

        showLoginView();
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> {
            ProcessManager.stopNodeServer(); // Garante que o processo Node.js é encerrado
        });
    }

    private void showLoginView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auraagent/views/LoginView.fxml"));
            Parent root = loader.load();

            LoginController controller = loader.getController();
            controller.setOnLoginSuccess(this::showMainAppView);

            primaryStage.setScene(new Scene(root, 1200, 800));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showMainAppView(String userId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auraagent/views/MainAppView.fxml"));
            Parent root = loader.load();

            // Lógica para permitir que a janela seja arrastada
            root.setOnMousePressed(event -> {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            });
            root.setOnMouseDragged(event -> {
                primaryStage.setX(event.getScreenX() - xOffset);
                primaryStage.setY(event.getScreenY() - yOffset);
            });

            MainAppController controller = loader.getController();
            controller.initialize(userId); // Passa o ID do utilizador para o controller principal

            primaryStage.setScene(new Scene(root, 1200, 800));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

}