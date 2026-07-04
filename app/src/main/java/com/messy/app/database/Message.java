package com.messy.app.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages")
public class Message {
    @PrimaryKey
    @NonNull
    public String id;
    public String sender;
    public String receiver;
    public String body;
    public long timestamp;
    public boolean delivered;
    public int ttl;
}