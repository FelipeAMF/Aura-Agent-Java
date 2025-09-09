package com.auraagent.controllers;

import com.auraagent.models.WhatsappAccount;
import com.auraagent.utils.JavaFxUtils;
import com.auraagent.services.FirebaseService;
import com.auraagent.services.WhatsappService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ContactsController implements MainAppController.InitializableController {

    @FXML
    private ListView<RadioButton> contactListsView;
    @FXML
    private ListView<String> blacklistNumbersView;
    @FXML
    private Button deleteListButton;
    @FXML
    private ComboBox<String> senderAccountComboBox;
    @FXML
    private Button validateButton;
    @FXML
    private ProgressBar validationProgressBar;
    @FXML
    private Label validationStatusLabel;
    @FXML
    private GridPane resultsPane;
    @FXML
    private ListView<String> validNumbersListView;
    @FXML
    private ListView<String> invalidNumbersListView;

    private String userId;
    private String userToken;
    private final WhatsappService whatsappService = new WhatsappService();

    private final ToggleGroup contactListToggleGroup = new ToggleGroup();
    private final ObservableList<RadioButton> contactLists = FXCollections.observableArrayList();
    private final ObservableList<String> blacklistNumbers = FXCollections.observableArrayList();
    private final ObservableList<String> senderAccounts = FXCollections.observableArrayList();
    private final ObservableList<String> validNumbers = FXCollections.observableArrayList();
    private final ObservableList<String> invalidNumbers = FXCollections.observableArrayList();

    @Override
    public void initialize(String userId) {
        this.userId = userId;
        contactListsView.setItems(contactLists);
        blacklistNumbersView.setItems(blacklistNumbers);
        senderAccountComboBox.setItems(senderAccounts);
        validNumbersListView.setItems(validNumbers);
        invalidNumbersListView.setItems(invalidNumbers);

        blacklistNumbersView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        deleteListButton.disableProperty().bind(contactListToggleGroup.selectedToggleProperty().isNull());
        validateButton.disableProperty().bind(
                contactListToggleGroup.selectedToggleProperty().isNull()
                        .or(senderAccountComboBox.getSelectionModel().selectedItemProperty().isNull()));

        refreshData();
    }

    @Override
    public void onViewShown() {
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

        whatsappService.getStatusAsync().thenAcceptAsync(accounts -> {
            Platform.runLater(() -> {
                senderAccounts.clear();
                List<String> connectedAccounts = accounts.stream()
                        .filter(acc -> "Conectado".equals(acc.getStatus()))
                        .map(WhatsappAccount::getSessionId)
                        .collect(Collectors.toList());
                senderAccounts.addAll(connectedAccounts);
            });
        });
    }

    // MÉTODO DE FORMATAÇÃO CORRIGIDO E SIMPLIFICADO
    private String formatBrazilianPhoneNumber(String phone) {
        if (phone == null)
            return null;
        String cleaned = phone.replaceAll("\\D", "");

        if (cleaned.startsWith("55")) {
            cleaned = cleaned.substring(2);
        }

        if (cleaned.length() < 10 || cleaned.length() > 11) {
            return null;
        }

        String ddd = cleaned.substring(0, 2);
        String numberPart = cleaned.substring(2);

        String formattedNumber;
        if (numberPart.length() == 9) { // Celular com 9 dígitos
            formattedNumber = String.format("+55 (%s) %s-%s", ddd, numberPart.substring(0, 5), numberPart.substring(5));
        } else { // Celular antigo com 8 dígitos ou telefone fixo
            formattedNumber = String.format("+55 (%s) %s-%s", ddd, numberPart.substring(0, 4), numberPart.substring(4));
        }

        return formattedNumber;
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
                        String formatted = formatBrazilianPhoneNumber(contactNumber);
                        if (formatted != null && !contacts.contains(formatted)) {
                            contacts.add(formatted);
                        }
                    }
                }

                if (!contacts.isEmpty()) {
                    FirebaseService.addContactsToList(userId, listName, contacts).thenAccept(success -> {
                        if (success) {
                            Platform.runLater(() -> {
                                JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso",
                                        contacts.size() + " contatos importados e formatados para a lista '" + listName
                                                + "'.");
                                refreshData();
                            });
                        } else {
                            Platform.runLater(() -> JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro",
                                    "Não foi possível importar os contatos."));
                        }
                    });

                } else {
                    JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Arquivo Vazio",
                            "Nenhum número de telefone válido foi encontrado no arquivo selecionado.");
                }

            } catch (IOException e) {
                e.printStackTrace();
                JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro de Leitura",
                        "Ocorreu um erro ao ler o arquivo: " + e.getMessage());
            }
        }
    }

    // ... (restante do código permanece o mesmo)
    @FXML
    private void handleValidateList() {
        RadioButton selectedRadio = (RadioButton) contactListToggleGroup.getSelectedToggle();
        String listName = selectedRadio.getText();
        String sessionId = senderAccountComboBox.getSelectionModel().getSelectedItem();

        validNumbers.clear();
        invalidNumbers.clear();
        resultsPane.setVisible(false);
        resultsPane.setManaged(false);
        validationProgressBar.setVisible(true);
        validationStatusLabel.setVisible(true);
        validationStatusLabel.setManaged(true);
        validationStatusLabel.setText("A carregar contatos da lista '" + listName + "'...");

        FirebaseService.getContactsFromListAsync(userId, userToken, listName)
                .thenAcceptAsync(contactsMap -> {
                    if (contactsMap == null || contactsMap.keySet().stream().allMatch(k -> k.equals("_placeholder"))) {
                        Platform.runLater(() -> {
                            validationStatusLabel.setText("A lista de contatos está vazia.");
                            validationProgressBar.setVisible(false);
                        });
                        return;
                    }
                    List<String> numbersToValidate = new ArrayList<>(contactsMap.keySet());
                    numbersToValidate.remove("_placeholder");

                    Platform.runLater(() -> validationStatusLabel.setText(
                            "A contactar o servidor para validar " + numbersToValidate.size() + " números..."));

                    whatsappService.validateNumbersAsync(sessionId, numbersToValidate)
                            .thenAccept(validationResult -> Platform.runLater(() -> {
                                if (validationResult.isEmpty() && !numbersToValidate.isEmpty()) {
                                    validationStatusLabel.setText(
                                            "Falha na validação. O servidor não retornou resultados. Verifique o terminal para erros.");
                                    validationProgressBar.setVisible(false);
                                    return;
                                }

                                validationResult.forEach((number, isValid) -> {
                                    if (isValid) {
                                        validNumbers.add(number);
                                    } else {
                                        invalidNumbers.add(number);
                                    }
                                });

                                validationProgressBar.setVisible(false);
                                validationStatusLabel.setText("Validação concluída: " + validNumbers.size()
                                        + " válidos, " + invalidNumbers.size() + " inválidos.");
                                resultsPane.setVisible(true);
                                resultsPane.setManaged(true);
                            }));
                });
    }

    @FXML
    private void handleCreateListFromValid() {
        if (validNumbers.isEmpty()) {
            JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Aviso",
                    "Não há números válidos para criar uma nova lista.");
            return;
        }

        RadioButton selectedRadio = (RadioButton) contactListToggleGroup.getSelectedToggle();
        String originalListName = selectedRadio.getText();
        String newListName = originalListName + "_validos";

        FirebaseService.createContactList(userId, userToken, newListName).thenAccept(success -> {
            if (success) {
                FirebaseService.addContactsToList(userId, newListName, new ArrayList<>(validNumbers))
                        .thenAccept(addSuccess -> Platform.runLater(() -> {
                            if (addSuccess) {
                                JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso",
                                        "Nova lista '" + newListName + "' criada com os números válidos.");
                                refreshData();
                            } else {
                                JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro",
                                        "Não foi possível adicionar contatos à nova lista.");
                            }
                        }));
            } else {
                Platform.runLater(() -> JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro",
                        "Não foi possível criar a nova lista."));
            }
        });
    }

    @FXML
    private void handleRemoveInvalidFromList() {
        if (invalidNumbers.isEmpty()) {
            JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Aviso", "Não há números inválidos para remover.");
            return;
        }

        RadioButton selectedRadio = (RadioButton) contactListToggleGroup.getSelectedToggle();
        String listName = selectedRadio.getText();

        FirebaseService.removeContactsFromList(userId, listName, new ArrayList<>(invalidNumbers))
                .thenAccept(success -> Platform.runLater(() -> {
                    if (success) {
                        JavaFxUtils.showAlert(Alert.AlertType.INFORMATION, "Sucesso", invalidNumbers.size()
                                + " números inválidos foram removidos da lista '" + listName + "'.");
                        validNumbers.clear();
                        invalidNumbers.clear();
                        resultsPane.setVisible(false);
                        resultsPane.setManaged(false);
                        validationStatusLabel.setText("");
                    } else {
                        JavaFxUtils.showAlert(Alert.AlertType.ERROR, "Erro",
                                "Não foi possível remover os contatos inválidos.");
                    }
                }));
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