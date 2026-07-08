package com.messy.app.chat;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
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
    private TextInputEditText messageInputEditText;
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

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.chatToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Avatar initial in toolbar
        TextView chatTitleTextView = findViewById(R.id.chatTitleTextView);
        TextView chatAvatarInitial = findViewById(R.id.chatAvatarInitial);
        chatTitleTextView.setText(contactName);
        chatAvatarInitial.setText(contactName != null && !contactName.isEmpty()
                ? String.valueOf(contactName.charAt(0)).toUpperCase()
                : "?");

        // Messages list
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(chatAdapter);

        // Input
        messageInputEditText = findViewById(R.id.messageInputEditText);
        FloatingActionButton sendButton = findViewById(R.id.sendButton);
        applyClickAnimation(sendButton, this::sendMessage);

        // IME send action
        messageInputEditText.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });

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
        TextView chatTitleTextView = findViewById(R.id.chatTitleTextView);
        TextView chatAvatarInitial = findViewById(R.id.chatAvatarInitial);
        chatTitleTextView.setText(contactName);
        chatAvatarInitial.setText(contactName != null && !contactName.isEmpty()
                ? String.valueOf(contactName.charAt(0)).toUpperCase()
                : "?");
        loadMessages();
    }

    private void sendMessage() {
        if (messageInputEditText.getText() == null) return;
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

    private void applyClickAnimation(android.view.View view, Runnable action) {
        if (view == null) return;
        view.setOnClickListener(v -> {
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start();
                if (action != null) {
                    action.run();
                }
            }).start();
        });
    }
}