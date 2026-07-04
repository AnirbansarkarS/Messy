package com.messy.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(Message message);

    @Query("SELECT * FROM messages WHERE receiver = :userId ORDER BY timestamp ASC")
    List<Message> getMessagesFor(String userId);

    @Query("SELECT * FROM messages WHERE (sender = :userId AND receiver = :contactId) OR (sender = :contactId AND receiver = :userId) ORDER BY timestamp ASC")
    List<Message> getConversation(String userId, String contactId);

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    Message findById(String id);

    @Update
    void update(Message message);

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    List<Message> getAllMessages();
}