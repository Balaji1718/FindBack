package com.balaji.findback;

public class ChatMessage {
    public static final int TYPE_USER = 1;
    public static final int TYPE_AI = 2;
    public static final int TYPE_LOADING = 3;

    private String message;
    private int type;
    private long timestamp;
    
    // New fields for specific format availability
    private boolean offerPdf = false;
    private boolean offerWord = false;

    public ChatMessage(String message, int type) {
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessage() { return message; }
    public int getType() { return type; }
    public long getTimestamp() { return timestamp; }

    public boolean isOfferPdf() { return offerPdf; }
    public void setOfferPdf(boolean offerPdf) { this.offerPdf = offerPdf; }

    public boolean isOfferWord() { return offerWord; }
    public void setOfferWord(boolean offerWord) { this.offerWord = offerWord; }
}