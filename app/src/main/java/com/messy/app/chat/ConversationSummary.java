package com.messy.app.chat;

public class ConversationSummary {
    public final String contactId;
    public final String contactName;
    public final String lastMessage;
    public final long lastTimestamp;

    public ConversationSummary(String contactId, String contactName, String lastMessage, long lastTimestamp) {
        this.contactId = contactId;
        this.contactName = contactName;
        this.lastMessage = lastMessage;
        this.lastTimestamp = lastTimestamp;
    }
}