package com.auraagent.controllers;

import com.auraagent.helpers.ADBHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import net.sourceforge.tess4j.TesseractException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtractorController {

    @FXML
    private Button btnStart;
    @FXML
    private Button btnStop;
    @FXML
    private Button btnExport;
    @FXML
    private Spinner<Integer> spinnerScrollDelay;
    @FXML
    private Spinner<Integer> spinnerScrollSpeed;
    @FXML
    private TextArea txtOutput;

    private ADBHelper adbHelper;
    private List<String> phoneNumbers = new ArrayList<>();
    private boolean running = false;
    private volatile boolean stopRequested = false;

    @FXML
    public void initialize() {
        // Configura os spinners para controlar o tempo de rolagem e a velocidade
        spinnerScrollDelay.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(500, 5000, 1000, 100));
        spinnerScrollSpeed.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(100, 2000, 500, 50));

        // Ajusta o caminho para o executável do ADB
        String adbPath = "vendor/adb/adb.exe";
        adbHelper = new ADBHelper(adbPath);

        // Define as ações para os botões
        btnStart.setOnAction(e -> startExtraction());
        btnStop.setOnAction(e -> stopExtraction());
        btnExport.setOnAction(e -> exportCSV());
    }

    private void startExtraction() {
        if (running)
            return;

        running = true;
        stopRequested = false;
        btnStart.setDisable(true);
        btnStop.setDisable(false);
        btnExport.setDisable(true);
        phoneNumbers.clear();
        txtOutput.clear();

        int scrollDelay = spinnerScrollDelay.getValue();
        int scrollSpeed = spinnerScrollSpeed.getValue();

        new Thread(() -> {
            try {
                while (!stopRequested) {
                    String textDump = adbHelper.captureScreenText();
                    extractPhoneNumbers(textDump);
                    Platform.runLater(() -> updateTextArea());

                    // Desliza a tela para baixo
                    adbHelper.swipe(500, 1500, 500, 500, scrollSpeed);
                    Thread.sleep(scrollDelay);
                }
            } catch (TesseractException ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR,
                            "Erro ao extrair texto da imagem. Verifique a instalação do Tesseract.");
                    alert.showAndWait();
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Ocorreu um erro durante a extração.");
                    alert.showAndWait();
                });
            } finally {
                running = false;
                Platform.runLater(() -> {
                    btnStart.setDisable(false);
                    btnStop.setDisable(true);
                    btnExport.setDisable(false);
                });
            }
        }).start();
    }

    private void stopExtraction() {
        stopRequested = true;
    }

    private void extractPhoneNumbers(String text) {
        // Padrão de regex para pegar vários formatos de número de telefone do Brasil
        Pattern pattern = Pattern.compile("(\\+55)?\\s*\\(?(\\d{2})\\)?\\s*(\\d{4,5})\\-?(\\d{4})");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String ddd = matcher.group(2);
            String part1 = matcher.group(3);
            String part2 = matcher.group(4);

            // Padroniza o número para o formato +55 (XX) XXXXX-XXXX
            String standardizedNum = String.format("+55 (%s) %s-%s", ddd, part1, part2);

            // Adiciona o número padronizado se ainda não existir
            if (!phoneNumbers.contains(standardizedNum)) {
                phoneNumbers.add(standardizedNum);
                Platform.runLater(() -> {
                    System.out.println("Número encontrado: " + standardizedNum);
                });
            }
        }
    }

    private void updateTextArea() {
        txtOutput.clear();
        phoneNumbers.forEach(num -> txtOutput.appendText(num + "\n"));
    }

    private void exportCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Salvar CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = fileChooser.showSaveDialog(txtOutput.getScene().getWindow());
        if (file == null)
            return;

        try (FileWriter writer = new FileWriter(file)) {
            for (String num : phoneNumbers) {
                writer.append(num).append("\n");
            }
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "CSV exportado com sucesso!");
            alert.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Erro ao exportar CSV.");
            alert.showAndWait();
        }
    }
}
