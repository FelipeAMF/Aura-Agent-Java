package com.auraagent.controllers;

import com.auraagent.models.TemplateModel;
import com.auraagent.services.FirebaseService;
import com.auraagent.utils.JavaFxUtils;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Map;

public class TemplatesController implements MainAppController.InitializableController {

    @FXML
    private ListView<TemplateModel> templatesListView;
    @FXML
    private VBox editorPane;
    @FXML
    private TextField nameField;
    @FXML
    private TextArea spintaxContentArea;
    @FXML
    private TextField delayField;
    @FXML
    private Button saveButton;
    @FXML
    private Button deleteButton; // Adicionada a referência ao botão de apagar

    private String userId;
    private String userToken;

    private final ObservableList<TemplateModel> templates = FXCollections.observableArrayList();
    // A propriedade a seguir rastreia o modelo atualmente selecionado na lista
    private final SimpleObjectProperty<TemplateModel> selectedTemplate = new SimpleObjectProperty<>();

    @Override
    public void initialize(String userId) {
        this.userId = userId;

        templatesListView.setItems(templates);
        // Define como o nome do modelo será exibido na lista
        templatesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(TemplateModel item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.getName());
            }
        });

        // --- BINDINGS (Ligações) ---
        // O painel de edição só fica visível se um modelo estiver selecionado ou se um
        // novo for criado
        editorPane.visibleProperty().bind(selectedTemplate.isNotNull());
        // O botão de apagar só fica ativo se um modelo estiver selecionado na lista
        deleteButton.disableProperty().bind(templatesListView.getSelectionModel().selectedItemProperty().isNull());

        // Quando um item é selecionado na lista, atualiza os campos do editor
        templatesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            selectedTemplate.set(newSelection);
            if (newSelection != null) {
                // Preenche o painel de edição com os dados do modelo selecionado
                nameField.setText(newSelection.getName());
                spintaxContentArea.setText(newSelection.getSpintaxContent());
                delayField.setText(String.valueOf(newSelection.getDelayInSeconds()));
            } else {
                // Limpa o painel se nada estiver selecionado
                clearEditorFields();
            }
        });

        // Carrega os dados iniciais do Firebase
        loadTemplatesAsync();
    }

    /**
     * Carrega os modelos de campanha do Firebase e atualiza a lista na interface.
     */
    @SuppressWarnings("unchecked")
    private void loadTemplatesAsync() {
        FirebaseService.getCampaignTemplates(userId, userToken).thenAcceptAsync(templatesData -> {
            Platform.runLater(() -> {
                templates.clear();
                if (templatesData != null) {
                    templatesData.forEach((name, data) -> {
                        if (data instanceof Map) {
                            Map<String, Object> settings = (Map<String, Object>) ((Map<?, ?>) data).get("settings");
                            if (settings != null) {
                                TemplateModel t = new TemplateModel();
                                t.setName(name);
                                t.setSpintaxContent((String) settings.getOrDefault("spintax_template", ""));
                                // Converte o delay para inteiro, com um valor padrão de 5
                                int delay = Integer.parseInt(settings.getOrDefault("delay", "5").toString());
                                t.setDelayInSeconds(delay);
                                templates.add(t);
                            }
                        }
                    });
                }
            });
        });
    }

    /**
     * Ação do botão "Novo Modelo". Limpa a seleção e o painel de edição para um
     * novo registo.
     */
    @FXML
    private void handleNewTemplate() {
        templatesListView.getSelectionModel().clearSelection();
        // Cria um modelo "fantasma" para ativar o painel de edição e limpar os campos
        selectedTemplate.set(new TemplateModel());

        nameField.setText("");
        spintaxContentArea.setText("Olá, {tudo bem|como vai}? Visite nosso site!");
        delayField.setText("5");
        nameField.requestFocus(); // Foca no campo de nome
    }

    /**
     * Ação do botão "Salvar Alterações". Salva o modelo (novo ou editado) no
     * Firebase.
     */
    @FXML
    private void handleSaveTemplate() {
        String name = nameField.getText();
        String content = spintaxContentArea.getText();
        String delay = delayField.getText();

        if (name == null || name.isBlank()) {
            JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro", "O nome do modelo não pode estar vazio.");
            return;
        }

        // Converte o delay para um formato de string "Xs" para compatibilidade com a
        // tela de campanha
        String delayValue = delay.replaceAll("[^0-9]", "");

        FirebaseService.saveTemplate(userId, userToken, name, content, delayValue).thenAccept(success -> {
            if (success) {
                Platform.runLater(() -> {
                    JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso",
                            "Modelo '" + name + "' salvo com sucesso.");
                    loadTemplatesAsync(); // Recarrega a lista
                    clearSelectionAndHideEditor();
                });
            } else {
                Platform.runLater(() -> JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro",
                        "Não foi possível salvar o modelo."));
            }
        });
    }

    /**
     * Ação do botão "Excluir". Remove o modelo selecionado do Firebase.
     */
    @FXML
    private void handleDeleteTemplate() {
        TemplateModel toDelete = selectedTemplate.get();
        if (toDelete == null) {
            JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Aviso", "Nenhum modelo selecionado para apagar.");
            return;
        }

        if (JavaFxUtils.showConfirmation(Alert.AlertType.CONFIRMATION, "Confirmar Exclusão",
                "Tem certeza que deseja apagar o modelo '" + toDelete.getName() + "'?")) {
            FirebaseService.deleteTemplate(userId, userToken, toDelete.getName()).thenAccept(success -> {
                if (success) {
                    Platform.runLater(() -> {
                        JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Modelo apagado.");
                        loadTemplatesAsync();
                        clearSelectionAndHideEditor();
                    });
                }
            });
        }
    }

    /** Limpa os campos do editor. */
    private void clearEditorFields() {
        nameField.clear();
        spintaxContentArea.clear();
        delayField.clear();
    }

    /** Limpa a seleção e esconde o painel de edição. */
    private void clearSelectionAndHideEditor() {
        selectedTemplate.set(null);
        templatesListView.getSelectionModel().clearSelection();
    }
}