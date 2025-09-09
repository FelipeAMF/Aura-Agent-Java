package com.auraagent.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProcessManager {
    private static Process nodeProcess;
    private static ScheduledExecutorService healthCheckScheduler;
    private static final String SERVER_BASE_URL = "http://localhost";
    private static int currentPort = 3000;
    private static final int MAX_PORT_ATTEMPTS = 10;

    public static void startNodeServer() {
        try {
            // Verificar se o servidor já está rodando
            Integer runningPort = findRunningServer();
            if (runningPort != null) {
                currentPort = runningPort;
                System.out.println("✅ Servidor Node.js já está rodando na porta: " + currentPort);
                startHealthCheck();
                return;
            }

            // Tentar iniciar em portas diferentes
            boolean started = false;
            for (int attempt = 0; attempt < MAX_PORT_ATTEMPTS; attempt++) {
                if (tryStartServer(currentPort)) {
                    started = true;
                    break;
                }
                currentPort++;
            }

            if (started) {
                System.out.println("✅ Servidor Node.js iniciado na porta: " + currentPort);
                startHealthCheck();
            } else {
                System.err.println("❌ Não foi possível iniciar o servidor Node.js");
            }

        } catch (Exception e) {
            System.err.println("❌ Erro ao iniciar servidor Node: " + e.getMessage());
        }
    }

    private static boolean tryStartServer(int port) {
        try {
            if (isPortAvailable(port)) {
                ProcessBuilder pb = new ProcessBuilder("vendor\\node\\node.exe", "servidor-node\\index.js");
                pb.redirectErrorStream(true);
                pb.environment().put("PORT", String.valueOf(port));

                nodeProcess = pb.start();

                // Ler output do processo
                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(nodeProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println("[NODE] " + line);
                        }
                    } catch (IOException e) {
                        System.err.println("Erro ao ler output do Node: " + e.getMessage());
                    }
                }).start();

                return waitForServerReady(port, 20);
            }
            return false;
        } catch (IOException e) {
            System.err.println("Erro ao iniciar servidor na porta " + port + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean isPortAvailable(int port) {
        try {
            new java.net.Socket("localhost", port).close();
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static Integer findRunningServer() {
        // Check a wider range of ports to find existing Node.js servers
        for (int port = 3000; port < 3020; port++) { // Increased range to 3020
            if (isServerRunning(port)) {
                return port;
            }
        }
        return null;
    }

    private static boolean isServerRunning(int port) {
        try {
            URL url = new URL(SERVER_BASE_URL + ":" + port + "/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.connect();
            return conn.getResponseCode() == 200;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean waitForServerReady(int port, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (isServerRunning(port)) {
                return true;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    private static void startHealthCheck() {
        if (healthCheckScheduler != null) {
            healthCheckScheduler.shutdown();
        }

        healthCheckScheduler = Executors.newSingleThreadScheduledExecutor();
        healthCheckScheduler.scheduleAtFixedRate(() -> {
            if (!isServerRunning(currentPort)) {
                System.err.println("❌ Servidor Node.js parou, reiniciando...");
                stopNodeServer();
                startNodeServer();
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    public static void stopNodeServer() {
        if (healthCheckScheduler != null) {
            healthCheckScheduler.shutdownNow();
        }

        if (nodeProcess != null && nodeProcess.isAlive()) {
            nodeProcess.destroyForcibly();
            nodeProcess = null; // Clear the reference
        }

        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                Runtime.getRuntime().exec("taskkill /f /im node.exe");
            } else {
                Runtime.getRuntime().exec("pkill -f node");
            }
        } catch (IOException e) {
            System.err.println("Erro ao limpar processos Node: " + e.getMessage());
        }
    }

    public static int getCurrentPort() {
        return currentPort;
    }

    public static String getServerUrl() {
        return SERVER_BASE_URL + ":" + currentPort;
    }
}