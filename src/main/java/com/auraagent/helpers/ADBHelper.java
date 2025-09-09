package com.auraagent.helpers;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class ADBHelper {

    private final String adbPath;

    public ADBHelper(String adbPath) {
        this.adbPath = adbPath;
    }

    public String execCommand(String command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(adbPath, "shell", command);
        Process process = builder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        process.waitFor();
        return output.toString();
    }

    public String execCommandWithOutputToFile(String command, String localPath)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(adbPath, "shell", command, "/sdcard/screen.png");
        Process process = builder.start();
        process.waitFor();

        ProcessBuilder pullBuilder = new ProcessBuilder(adbPath, "pull", "/sdcard/screen.png", localPath);
        Process pullProcess = pullBuilder.start();
        pullProcess.waitFor();

        return localPath;
    }

    public void swipe(int startX, int startY, int endX, int endY, int durationMs)
            throws IOException, InterruptedException {
        execCommand("input swipe " + startX + " " + startY + " " + endX + " " + endY + " " + durationMs);
    }

    public String captureScreenText() throws IOException, InterruptedException, TesseractException {
        String screenshotPath = "screenshot.png";
        execCommandWithOutputToFile("screencap -p", screenshotPath);
        File imageFile = new File(screenshotPath);
        ITesseract tesseract = new Tesseract();
        // É importante configurar o caminho para o diretório de dados do Tesseract
        // Se você não tiver os dados, o Tesseract não funcionará.
        // O caminho pode variar dependendo da sua instalação.
        tesseract.setDatapath("vendor/Tesseract-OCR/tessdata");
        // tesseract.setLanguage("por"); // Descomente para usar o idioma português

        String result = tesseract.doOCR(imageFile);
        imageFile.delete(); // Deleta a imagem após a extração
        return result;
    }
}
