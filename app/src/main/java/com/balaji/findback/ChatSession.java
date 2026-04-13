package com.balaji.findback;

import java.util.Objects;

public class ChatSession {
    private String sessionId;
    private String title;
    private long lastTimestamp;
    private String userId;

    public ChatSession() {} // Needed for Firestore

    public ChatSession(String sessionId, String title, String userId) {
        this.sessionId = sessionId;
        this.title = title;
        this.lastTimestamp = System.currentTimeMillis();
        this.userId = userId;
    }

    public String getSessionId() { return sessionId; }
    public String getTitle() { return title; }
    public long getLastTimestamp() { return lastTimestamp; }
    public String getUserId() { return userId; }

    public void setLastTimestamp(long lastTimestamp) {
        this.lastTimestamp = lastTimestamp;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatSession that = (ChatSession) o;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
}