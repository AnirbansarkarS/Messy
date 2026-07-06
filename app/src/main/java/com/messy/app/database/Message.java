package com.messy.app.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.io.Serializable;

@Entity(tableName = "messages")
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

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