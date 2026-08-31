package app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import objects.Job;

public class JobManager {

    @FunctionalInterface
    public interface JobTask {
        void run(Job job) throws Exception;
    }

    private static final AtomicReference<Job> CURRENT = new AtomicReference<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rdp-copy-worker");
        t.setDaemon(true);
        return t;
    });

    public static Job start(JobTask task) {
        Job existing = CURRENT.get();
        if (existing != null && existing.getStatus() == Job.Status.RUNNING) {
            throw new IllegalStateException("A copy job is already running (id=" + existing.getId() + ")");
        }
        Job job = new Job();
        CURRENT.set(job);
        EXECUTOR.submit(() -> {
            try {
                job.setStatus(Job.Status.RUNNING);
                job.appendLog("Job started");
                task.run(job);
                job.setStatus(Job.Status.DONE);
                job.setCurrentLabel("Completed");
                job.appendLog("Job completed in " + job.getElapsedMs() + " ms");
            } catch (Throwable t) {
                job.setFatalError(rootCauseMessage(t));
                job.setStatus(Job.Status.FAILED);
                job.setCurrentLabel("Failed");
                job.appendLog("FATAL: " + rootCauseMessage(t));
                t.printStackTrace();
            } finally {
                job.markEnded();
            }
        });
        return job;
    }

    public static Job current() {
        return CURRENT.get();
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        String msg = cur.getMessage();
        return (msg == null || msg.isBlank()) ? cur.getClass().getSimpleName() : msg;
    }
}