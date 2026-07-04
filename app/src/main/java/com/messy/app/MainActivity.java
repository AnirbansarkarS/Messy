package com.messy.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.messy.app.chat.ChatActivity;
import com.messy.app.chat.ConversationAdapter;
import com.messy.app.chat.ConversationSummary;
import com.messy.app.database.AppDatabase;
import com.messy.app.database.Message;
import com.messy.app.database.MessageDao;
import com.messy.app.utils.AppExecutors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private final ConversationAdapter conversationAdapter = new ConversationAdapter(this::openConversation);
    private MessageDao messageDao;
    private TextView emptyView;
    private RecyclerView conversationsRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        messageDao = AppDatabase.getInstance(this).messageDao();

        conversationsRecyclerView = findViewById(R.id.conversationsRecyclerView);
        conversationsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        conversationsRecyclerView.setAdapter(conversationAdapter);

        emptyView = findViewById(R.id.emptyView);

        Button openSavedMessagesButton = findViewById(R.id.openSavedMessagesButton);
        openSavedMessagesButton.setOnClickListener(v -> openSavedMessages());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConversations();
    }

    private void loadConversations() {
        AppExecutors.execute(() -> {
            List<Message> messages = messageDao.getAllMessages();
            List<ConversationSummary> conversations = buildConversationSummaries(messages);
            AppExecutors.postToMain(() -> {
                conversationAdapter.setConversations(conversations);
                emptyView.setVisibility(conversations.isEmpty() ? View.VISIBLE : View.GONE);
                conversationsRecyclerView.setVisibility(conversations.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    private List<ConversationSummary> buildConversationSummaries(List<Message> messages) {
        Map<String, ConversationSummaryBuilder> builders = new LinkedHashMap<>();
        for (Message message : messages) {
            String contactId = resolveContactId(message);
            String contactName = resolveContactName(message, contactId);
            ConversationSummaryBuilder builder = builders.get(contactId);
            if (builder == null) {
                builder = new ConversationSummaryBuilder(contactId, contactName);
                builders.put(contactId, builder);
            }
            builder.update(message.body, message.timestamp);
        }

        List<ConversationSummary> summaries = new ArrayList<>();
        for (ConversationSummaryBuilder builder : builders.values()) {
            summaries.add(builder.build());
        }
        summaries.sort((left, right) -> Long.compare(right.lastTimestamp, left.lastTimestamp));
        return summaries;
    }

    private String resolveContactId(Message message) {
        if (ChatActivity.SELF_ID.equals(message.sender) && ChatActivity.SELF_ID.equals(message.receiver)) {
            return ChatActivity.SELF_ID;
        }
        if (ChatActivity.SELF_ID.equals(message.sender)) {
            return message.receiver;
        }
        return message.sender;
    }

    private String resolveContactName(Message message, String contactId) {
        if (ChatActivity.SELF_ID.equals(contactId)) {
            return getString(R.string.saved_messages);
        }
        if (ChatActivity.SELF_ID.equals(message.sender)) {
            return message.receiver;
        }
        return message.sender;
    }

    private void openConversation(ConversationSummary conversation) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONTACT_ID, conversation.contactId);
        intent.putExtra(ChatActivity.EXTRA_CONTACT_NAME, conversation.contactName);
        startActivity(intent);
    }

    private void openSavedMessages() {
        openConversation(new ConversationSummary(
                ChatActivity.SELF_ID,
                getString(R.string.saved_messages),
                "",
                System.currentTimeMillis()
        ));
    }

    private static final class ConversationSummaryBuilder {
        private final String contactId;
        private final String contactName;
        private String lastMessage = "";
        private long lastTimestamp;

        private ConversationSummaryBuilder(String contactId, String contactName) {
            this.contactId = contactId;
            this.contactName = contactName;
        }

        private void update(String message, long timestamp) {
            if (timestamp >= lastTimestamp) {
                lastMessage = message;
                lastTimestamp = timestamp;
            }
        }

        private ConversationSummary build() {
            return new ConversationSummary(contactId, contactName, lastMessage, lastTimestamp);
        }
    }
}