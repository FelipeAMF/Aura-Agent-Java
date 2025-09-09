package com.auraagent.controllers;

import com.auraagent.utils.JavaFxUtils;
import com.auraagent.services.FirebaseService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ContactsController implements MainAppController.InitializableController {

    @FXML
    private ListView<RadioButton> contactListsView;
    @FXML
    private ListView<String> blacklistNumbersView;
    @FXML
    private Button deleteListButton;

    private String userId;
    private String userToken;

    private final ToggleGroup contactListToggleGroup = new ToggleGroup();
    private final ObservableList<RadioButton> contactLists = FXCollections.observableArrayList();
    private final ObservableList<String> blacklistNumbers = FXCollections.observableArrayList();

    @Override
    public void initialize(String userId) {
        this.userId = userId;
        contactListsView.setItems(contactLists);
        blacklistNumbersView.setItems(blacklistNumbers);
        blacklistNumbersView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        deleteListButton.disableProperty().bind(contactListToggleGroup.selectedToggleProperty().isNull());

        refreshData();
    }

    public void refreshData() {
        FirebaseService.getContactListsAsync(userId, userToken).thenAcceptAsync(lists -> {
            Platform.runLater(() -> {
                contactLists.clear();
                contactListToggleGroup.getToggles().clear();
                if (lists != null) {
                    lists.keySet().stream().sorted().forEach(listName -> {
                        RadioButton rb = new RadioButton(listName);
                        rb.setToggleGroup(contactListToggleGroup);
                        contactLists.add(rb);
                    });
                }
            });
        });

        FirebaseService.getBlacklist(userId, userToken).thenAcceptAsync(blacklist -> {
            Platform.runLater(() -> {
                blacklistNumbers.clear();
                if (blacklist != null) {
                    blacklistNumbers.addAll(blacklist.keySet());
                }
            });
        });
    }

    @FXML
    private void handleCreateNewList() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nova Lista");
        dialog.setHeaderText("Digite o nome da nova lista de contatos:");
        dialog.setContentText("Nome:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(listName -> {
            if (!listName.isBlank()) {
                FirebaseService.createContactList(userId, userToken, listName).thenAccept(success -> {
                    if (success) {
                        Platform.runLater(this::refreshData);
                    }
                });
            }
        });
    }

    @FXML
    private void handleImportCsv() {
        RadioButton selectedListRadio = (RadioButton) contactListToggleGroup.getSelectedToggle();
        if (selectedListRadio == null) {
            JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Nenhuma Lista Selecionada",
                    "Por favor, selecione uma lista para importar os contatos.");
            return;
        }
        String listName = selectedListRadio.getText();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecionar Arquivo CSV de Contatos");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos CSV", "*.csv"));
        File file = fileChooser.showOpenDialog(contactListsView.getScene().getWindow());

        if (file != null) {
            List<String> contacts = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String contactNumber = line.trim();
                    if (!contactNumber.isEmpty()) {
                        contacts.add(contactNumber);
                    }
                }

                if (!contacts.isEmpty()) {
                    FirebaseService.addContactsToList(userId, listName, contacts).thenAccept(success -> {
                        if (success) {
                            Platform.runLater(() -> {
                                JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso",
                                        contacts.size() + " contatos importados para a lista '" + listName + "'.");
                                refreshData();
                            });
                        } else {
                            Platform.runLater(() -> JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro",
                                    "Não foi possível importar os contatos."));
                        }
                    });

                } else {
                    JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Arquivo Vazio",
                            "Nenhum contato encontrado no arquivo selecionado.");
                }

            } catch (IOException e) {
                e.printStackTrace();
                JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro de Leitura",
                        "Ocorreu um erro ao ler o arquivo: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleDeleteSelectedList() {
        RadioButton selected = (RadioButton) contactListToggleGroup.getSelectedToggle();
        if (selected != null) {
            if (JavaFxUtils.showConfirmation(Alert.AlertType.CONFIRMATION, "Confirmar Exclusão",
                    "Tem a certeza que deseja excluir a lista '" + selected.getText() + "'?")) {
                FirebaseService.deleteContactList(userId, userToken, selected.getText())
                        .thenAccept(success -> {
                            if (success)
                                Platform.runLater(this::refreshData);
                        });
            }
        }
    }

    @FXML
    private void handleAddToBlacklist() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Adicionar à Blacklist");
        dialog.setHeaderText("Digite o número de telefone a ser bloqueado (apenas números):");
        dialog.setContentText("Número:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(number -> {
            String sanitizedNumber = number.replaceAll("[^0-9]", "");
            if (!sanitizedNumber.isBlank()) {
                FirebaseService.addToBlacklist(userId, userToken, List.of(sanitizedNumber))
                        .thenAccept(success -> {
                            if (success)
                                Platform.runLater(this::refreshData);
                        });
            }
        });
    }

    @FXML
    private void handleRemoveFromBlacklist() {
        List<String> selectedNumbers = blacklistNumbersView.getSelectionModel().getSelectedItems();

        if (selectedNumbers.isEmpty()) {
            JavaFxUtils.showAlert(Alert.AlertType.WARNING, "Aviso", "Nenhum número selecionado para remover.");
            return;
        }

        if (JavaFxUtils.showConfirmation(Alert.AlertType.CONFIRMATION, "Confirmar Remoção",
                "Tem certeza que deseja remover os " + selectedNumbers.size()
                        + " números selecionados da blacklist?")) {
            FirebaseService.removeFromBlacklist(userId, userToken, selectedNumbers)
                    .thenAccept(success -> {
                        if (success)
                            Platform.runLater(this::refreshData);
                    });
        }
    }

    @FXML
    private void handleClearBlacklist() {
        if (blacklistNumbers.isEmpty()) {
            JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Aviso", "A blacklist já está vazia.");
            return;
        }

        if (JavaFxUtils.showConfirmation(Alert.AlertType.CONFIRMATION, "Confirmar Limpeza Total",
                "TEM CERTEZA?\n\nEsta ação irá remover TODOS os " + blacklistNumbers.size()
                        + " números da blacklist. Esta ação não pode ser desfeita.")) {
            FirebaseService.clearBlacklist(userId, userToken)
                    .thenAccept(success -> {
                        if (success)
                            Platform.runLater(this::refreshData);
                    });
        }
    }
}