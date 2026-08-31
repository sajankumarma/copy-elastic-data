package objects;

public class ProcessResult {

    public enum Status {
        CREATED,
        UPDATED,
        NOOP,
        ERRORED,
        NOT_FOUND
    }

    private final Status status;
    private final String message;

    public ProcessResult(Status status, String message) {
        this.status = status;
        this.message = message == null ? "" : message;
    }

    public static ProcessResult fromEsResult(String esResult) {
        if (esResult == null || esResult.isBlank()) {
            return new ProcessResult(Status.ERRORED, "empty result from target");
        }
        return switch (esResult.toLowerCase()) {
            case "created" -> new ProcessResult(Status.CREATED, "");
            case "updated" -> new ProcessResult(Status.UPDATED, "");
            case "noop" -> new ProcessResult(Status.NOOP, "");
            default -> new ProcessResult(Status.ERRORED, "unexpected result: " + esResult);
        };
    }

    public static ProcessResult errored(String message) {
        return new ProcessResult(Status.ERRORED, message);
    }

    public static ProcessResult notFound(String message) {
        return new ProcessResult(Status.NOT_FOUND, message);
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String statusLabel() {
        return status.name().toLowerCase();
    }
}