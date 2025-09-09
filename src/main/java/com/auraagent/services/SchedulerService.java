package com.auraagent.services;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SchedulerService {

    private static SchedulerService instance;
    private final ScheduledExecutorService scheduler;

    private SchedulerService() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true); // Permite que a aplicação feche mesmo que tarefas estejam agendadas
            return t;
        });
    }

    public static synchronized SchedulerService getInstance() {
        if (instance == null) {
            instance = new SchedulerService();
        }
        return instance;
    }

    public void schedule(Runnable command, long delay, TimeUnit unit) {
        scheduler.schedule(command, delay, unit);
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            System.out.println("Serviço de agendamento encerrado.");
        }
    }
}