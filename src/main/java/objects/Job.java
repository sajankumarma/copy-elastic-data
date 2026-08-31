package objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class Job {

    public enum Status { PENDING, RUNNING, DONE, FAILED }

    private static final int LOG_LIMIT = 200;

    private final String id = UUID.randomUUID().toString();
    private final long startedAt = System.currentTimeMillis();
    private volatile long endedAt = 0;
    private volatile Status status = Status.PENDING;
    private volatile String fatalError = null;
    private volatile String currentLabel = "Preparing...";
    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicInteger completedQueries = new AtomicInteger(0);
    private final AtomicInteger docsProcessed = new AtomicInteger(0);
    private final AtomicInteger docsCreated = new AtomicInteger(0);
    private final AtomicInteger docsUpdated = new AtomicInteger(0);
    private final AtomicInteger docsNoop = new AtomicInteger(0);
    private final AtomicInteger docsErrored = new AtomicInteger(0);
    private final AtomicInteger docsNotFound = new AtomicInteger(0);
    private final List<String> log = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, ProcessResult> results = Collections.synchronizedMap(new LinkedHashMap<>());

    public String getId() { return id; }
    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }
    public String getFatalError() { return fatalError; }
    public void setFatalError(String fatalError) { this.fatalError = fatalError; }
    public String getCurrentLabel() { return currentLabel; }
    public void setCurrentLabel(String label) { this.currentLabel = label == null ? "" : label; }
    public int getTotalQueries() { return totalQueries.get(); }
    public void setTotalQueries(int n) { totalQueries.set(Math.max(0, n)); }
    public int getCompletedQueries() { return completedQueries.get(); }
    public int incrementCompletedQueries() { return completedQueries.incrementAndGet(); }
    public int getDocsProcessed() { return docsProcessed.get(); }
    public int getDocsCreated() { return docsCreated.get(); }
    public int getDocsUpdated() { return docsUpdated.get(); }
    public int getDocsNoop() { return docsNoop.get(); }
    public int getDocsErrored() { return docsErrored.get(); }
    public int getDocsNotFound() { return docsNotFound.get(); }
    public long getStartedAt() { return startedAt; }
    public long getEndedAt() { return endedAt; }
    public void markEnded() { this.endedAt = System.currentTimeMillis(); }
    public long getElapsedMs() {
        long end = endedAt == 0 ? System.currentTimeMillis() : endedAt;
        return end - startedAt;
    }

    public Map<String, ProcessResult> getResults() { return results; }

    public void recordResult(String key, ProcessResult result) {
        results.put(key, result);
        docsProcessed.incrementAndGet();
        if (result == null) return;
        switch (result.getStatus()) {
            case CREATED -> docsCreated.incrementAndGet();
            case UPDATED -> docsUpdated.incrementAndGet();
            case NOOP -> docsNoop.incrementAndGet();
            case NOT_FOUND -> docsNotFound.incrementAndGet();
            case ERRORED -> docsErrored.incrementAndGet();
        }
    }

    public void appendLog(String line) {
        if (line == null) return;
        synchronized (log) {
            log.add(line);
            while (log.size() > LOG_LIMIT) log.remove(0);
        }
    }

    public List<String> snapshotLog() {
        synchronized (log) {
            return new ArrayList<>(log);
        }
    }
}