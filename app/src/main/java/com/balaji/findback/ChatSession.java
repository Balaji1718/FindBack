package com.balaji.findback;

import com.google.firebase.firestore.PropertyName;
import java.util.Objects;

public class ChatSession {
    private String sessionId;
    private String title;
    private long lastTimestamp;
    private String userId;
    private String institutionId;

    public ChatSession() {} // Required for Firestore

    public ChatSession(String sessionId, String title, String userId, String institutionId) {
        this.sessionId = sessionId;
        this.title = title;
        this.lastTimestamp = System.currentTimeMillis();
        this.userId = userId;
        this.institutionId = institutionId;
    }

    @PropertyName("sessionId")
    public String getSessionId() { return sessionId; }
    @PropertyName("sessionId")
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    @PropertyName("title")
    public String getTitle() { return title; }
    @PropertyName("title")
    public void setTitle(String title) { this.title = title; }

    @PropertyName("lastTimestamp")
    public long getLastTimestamp() { return lastTimestamp; }
    @PropertyName("lastTimestamp")
    public void setLastTimestamp(long lastTimestamp) { this.lastTimestamp = lastTimestamp; }

    @PropertyName("userId")
    public String getUserId() { return userId; }
    @PropertyName("userId")
    public void setUserId(String userId) { this.userId = userId; }

    @PropertyName("institutionId")
    public String getInstitutionId() { return institutionId; }
    @PropertyName("institutionId")
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

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