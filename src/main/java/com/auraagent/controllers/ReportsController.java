package com.auraagent.controllers;

import com.auraagent.models.ReportModel;
import com.auraagent.services.FirebaseService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.util.Comparator;

public class ReportsController implements MainAppController.InitializableController {

    @FXML
    private ListView<ReportModel> reportsListView;
    private String userId;
    private final ObservableList<ReportModel> reports = FXCollections.observableArrayList();

    @Override
    public void initialize(String userId) {
        this.userId = userId;
        reportsListView.setItems(reports);

        // Define como cada item (ReportModel) é renderizado na lista.
        // Isto cria o "card" visual para cada relatório.
        reportsListView.setCellFactory(lv -> new ListCell<>() {
            private final VBox card = new VBox(5);
            private final Text dateText = new Text();
            private final Text totalText = new Text();
            private final Text successText = new Text();
            private final Text failText = new Text();

            {
                // Estrutura do "card" e aplicação das classes de estilo do CSS
                dateText.getStyleClass().add("report-card-date");
                successText.getStyleClass().add("success-label"); // Estilo para texto de sucesso
                failText.getStyleClass().add("danger-label"); // Estilo para texto de falha
                card.getStyleClass().add("report-card");
                card.getChildren().addAll(dateText, totalText, successText, failText);
            }

            @Override
            protected void updateItem(ReportModel report, boolean empty) {
                super.updateItem(report, empty);
                if (empty || report == null) {
                    setGraphic(null);
                } else {
                    dateText.setText("Data: " + report.getDate());
                    totalText.setText("Total de Contatos: " + report.getTotalContacts());
                    successText.setText("Sucessos: " + report.getSuccessCount());
                    failText.setText("Falhas: " + report.getFailCount());
                    setGraphic(card);
                }
            }
        });

        // A primeira carga de dados acontece quando a vista é mostrada
    }

    /**
     * Este método é chamado pelo MainAppController sempre que a tela de Relatórios
     * é exibida.
     * Garante que os dados estão sempre atualizados.
     */
    @Override
    public void onViewShown() {
        System.out.println("[DIAGNÓSTICO] A tela de Relatórios foi exibida. A recarregar dados...");
        refreshData();
    }

    /**
     * Ação que pode ser chamada para atualizar os dados da vista a partir do
     * Firebase.
     */
    public void refreshData() {
        if (userId == null || userId.isBlank()) {
            reportsListView.setPlaceholder(new Label("ID de utilizador inválido."));
            return;
        }

        reportsListView.setPlaceholder(new Label("A carregar relatórios..."));

        // --- LÓGICA FUNCIONAL ---
        FirebaseService.getReportsAsync(userId).thenAcceptAsync(reportsData -> {
            // Garante que a atualização da UI acontece no thread do JavaFX
            Platform.runLater(() -> {
                reports.clear();
                if (reportsData != null && !reportsData.isEmpty()) {
                    // Ordena os relatórios pela data, do mais recente para o mais antigo
                    reportsData.values().stream()
                            .sorted(Comparator.comparing(ReportModel::getDate).reversed())
                            .forEach(reports::add);
                } else {
                    // Se não houver dados, mostra uma mensagem
                    reportsListView.setPlaceholder(new Label("Nenhum relatório encontrado."));
                }
            });
        }).exceptionally(ex -> {
            // Em caso de erro na chamada ao serviço, mostra uma mensagem de erro
            Platform.runLater(() -> {
                reportsListView.setPlaceholder(new Label("Erro ao carregar relatórios."));
                ex.printStackTrace();
            });
            return null;
        });
    }
}