package com.messy.app.chat;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.messy.app.R;
import com.messy.app.database.AppDatabase;
import com.messy.app.database.Message;
import com.messy.app.database.MessageDao;
import com.messy.app.utils.AppExecutors;

import java.util.List;
import java.util.UUID;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_CONTACT_ID = "extra_contact_id";
    public static final String EXTRA_CONTACT_NAME = "extra_contact_name";
    public static final String SELF_ID = "me";

    private final ChatAdapter chatAdapter = new ChatAdapter(SELF_ID);
    private MessageDao messageDao;
    private String contactId;
    private String contactName;
    private EditText messageInputEditText;
    private RecyclerView messagesRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        messageDao = AppDatabase.getInstance(this).messageDao();
        contactId = getIntent().getStringExtra(EXTRA_CONTACT_ID);
        if (TextUtils.isEmpty(contactId)) {
            contactId = SELF_ID;
        }
        contactName = getIntent().getStringExtra(EXTRA_CONTACT_NAME);
        if (TextUtils.isEmpty(contactName)) {
            contactName = SELF_ID.equals(contactId) ? getString(R.string.saved_messages) : contactId;
        }

        TextView titleTextView = findViewById(R.id.chatTitleTextView);
        titleTextView.setText(contactName);

        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(chatAdapter);

        messageInputEditText = findViewById(R.id.messageInputEditText);
        Button sendButton = findViewById(R.id.sendButton);
        sendButton.setOnClickListener(v -> sendMessage());

        loadMessages();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        contactId = intent.getStringExtra(EXTRA_CONTACT_ID);
        if (TextUtils.isEmpty(contactId)) {
            contactId = SELF_ID;
        }
        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME);
        if (TextUtils.isEmpty(contactName)) {
            contactName = SELF_ID.equals(contactId) ? getString(R.string.saved_messages) : contactId;
        }
        TextView titleTextView = findViewById(R.id.chatTitleTextView);
        titleTextView.setText(contactName);
        loadMessages();
    }

    private void sendMessage() {
        String body = messageInputEditText.getText().toString().trim();
        if (body.isEmpty()) {
            return;
        }

        Message message = new Message();
        message.id = UUID.randomUUID().toString();
        message.sender = SELF_ID;
        message.receiver = contactId;
        message.body = body;
        message.timestamp = System.currentTimeMillis();
        message.delivered = true;
        message.ttl = 0;

        messageInputEditText.setText("");

        AppExecutors.execute(() -> {
            messageDao.insert(message);
            AppExecutors.postToMain(this::loadMessages);
        });
    }

    private void loadMessages() {
        AppExecutors.execute(() -> {
            List<Message> messages = messageDao.getConversation(SELF_ID, contactId);
            AppExecutors.postToMain(() -> {
                chatAdapter.setMessages(messages);
                if (!messages.isEmpty()) {
                    messagesRecyclerView.scrollToPosition(messages.size() - 1);
                }
            });
        });
    }
}