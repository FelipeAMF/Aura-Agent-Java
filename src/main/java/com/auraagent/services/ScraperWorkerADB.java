package com.auraagent.services;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScraperWorkerADB {

    private final int stopThreshold;
    private final int scrollPauseMs;
    private final Tesseract tesseract;
    private final Set<String> alreadyFoundNumbers = new HashSet<>();
    private final String adbPath;

    public ScraperWorkerADB(int stopThreshold, double scrollPauseSeconds, String adbPath) {
        this.stopThreshold = stopThreshold;
        this.scrollPauseMs = (int) (scrollPauseSeconds * 1000);
        this.adbPath = adbPath;

        this.tesseract = new Tesseract();
        try {
            File tessdataFolder = new File(System.getProperty("user.dir"), "vendor/Tesseract-OCR/tessdata");
            tesseract.setDatapath(tessdataFolder.getCanonicalPath());
            tesseract.setLanguage("por");
            tesseract.setPageSegMode(6);
            tesseract.setOcrEngineMode(1);
            tesseract.setTessVariable("tessedit_char_whitelist", "0123456789+-() ");
        } catch (Exception e) {
            System.err.println("ERRO AO CONFIGURAR TESSERACT: " + e.getMessage());
        }
    }

    public void run(Consumer<String> progress, AtomicBoolean cancellationToken) {
        int scrollsWithoutFinding = 0;

        try {
            progress.accept("A iniciar a captura via ADB...");

            while (scrollsWithoutFinding < stopThreshold && !cancellationToken.get()) {
                BufferedImage screenshot = captureScreen();

                if (screenshot == null) {
                    progress.accept("ERRO: Não foi possível capturar a tela.");
                    return;
                }

                String extractedText;
                try {
                    extractedText = tesseract.doOCR(screenshot);
                } catch (TesseractException e) {
                    progress.accept("Erro OCR: " + e.getMessage());
                    continue;
                }

                Set<String> numbersInScreen = findPhoneNumbers(extractedText);

                boolean newNumberFound = false;
                for (String number : numbersInScreen) {
                    if (alreadyFoundNumbers.add(number)) {
                        progress.accept("✅ NÚMERO: " + number);
                        newNumberFound = true;
                    }
                }

                if (newNumberFound)
                    scrollsWithoutFinding = 0;
                else
                    scrollsWithoutFinding++;

                progress.accept(String.format("Análise concluída (%d/%d)...", scrollsWithoutFinding, stopThreshold));

                if (scrollsWithoutFinding < stopThreshold && !cancellationToken.get()) {
                    performScroll();
                    Thread.sleep(scrollPauseMs);
                }
            }

            progress.accept("Extração concluída. Total encontrados: " + alreadyFoundNumbers.size());

        } catch (Exception e) {
            e.printStackTrace();
            progress.accept("ERRO CRÍTICO: " + e.getMessage());
        }
    }

    private BufferedImage captureScreen() throws IOException, InterruptedException {
        // Executa o ADB screencap
        ProcessBuilder pb = new ProcessBuilder(adbPath, "exec-out", "screencap", "-p");
        Process process = pb.start();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (InputStream is = process.getInputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
        }

        process.waitFor();

        byte[] imageBytes = baos.toByteArray();
        return ImageIO.read(new ByteArrayInputStream(imageBytes));
    }

    private void performScroll() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(adbPath, "shell", "input", "swipe", "500", "1500", "500", "500", "300");
        Process p = pb.start();
        p.waitFor();
    }

    private Set<String> findPhoneNumbers(String text) {
        Set<String> numbers = new HashSet<>();
        if (text == null || text.isEmpty())
            return numbers;

        Pattern pattern = Pattern.compile(
                "(\\+?55\\s?)?(\\(?\\d{2}\\)?\\s?)?(9?\\d{4}[-\\s]?\\d{4})|" +
                        "(\\d{2}\\s\\d{4,5}-?\\d{4})|" +
                        "(\\(\\d{2}\\)\\s?\\d{4,5}-?\\d{4})");

        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String rawNumber = matcher.group().replaceAll("[^0-9]", "");
            if (rawNumber.startsWith("55"))
                rawNumber = rawNumber.substring(2);

            if (rawNumber.length() == 10 || rawNumber.length() == 11) {
                String formattedNumber;
                if (rawNumber.length() == 11) {
                    formattedNumber = String.format("(%s) %s-%s",
                            rawNumber.substring(0, 2),
                            rawNumber.substring(2, 7),
                            rawNumber.substring(7));
                } else {
                    formattedNumber = String.format("(%s) %s-%s",
                            rawNumber.substring(0, 2),
                            rawNumber.substring(2, 6),
                            rawNumber.substring(6));
                }
                numbers.add(formattedNumber);
            }
        }
        return numbers;
    }
}
