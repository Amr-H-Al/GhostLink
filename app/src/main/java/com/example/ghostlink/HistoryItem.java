package com.example.ghostlink;

public class HistoryItem {
    private final String id;
    private final long timestamp;
    private final String label;
    private final int confidence;
    private final String reason;
    private final String snippet;
    private final String screenshotPath;

    public HistoryItem(long timestamp, String label, int confidence, String reason, String snippet, String screenshotPath) {
        this.id = String.valueOf(timestamp);
        this.timestamp = timestamp;
        this.label = label;
        this.confidence = confidence;
        this.reason = reason;
        this.snippet = snippet;
        this.screenshotPath = screenshotPath;
    }

    public String getId() { return id; }
    public long getTimestamp() { return timestamp; }
    public String getLabel() { return label; }
    public int getConfidence() { return confidence; }
    public String getReason() { return reason; }
    public String getSnippet() { return snippet; }
    public String getScreenshotPath() { return screenshotPath; }
}
