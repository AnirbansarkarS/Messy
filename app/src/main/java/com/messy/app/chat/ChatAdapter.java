package com.messy.app.chat;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.messy.app.R;
import com.messy.app.database.Message;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {
    private final List<Message> messages = new ArrayList<>();
    private final String selfId;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    public ChatAdapter(String selfId) {
        this.selfId = selfId;
    }

    public void setMessages(List<Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        boolean outgoing = selfId.equals(message.sender);

        holder.messageBodyTextView.setText(message.body);
        holder.messageTimestampTextView.setText(dateFormat.format(new Date(message.timestamp)));

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) holder.messageCardView.getLayoutParams();
        layoutParams.gravity = outgoing ? Gravity.END : Gravity.START;
        holder.messageCardView.setLayoutParams(layoutParams);
        holder.messageCardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), outgoing ? R.color.message_outgoing : R.color.message_incoming));
        holder.messageBodyTextView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), outgoing ? R.color.message_outgoing_text : R.color.message_incoming_text));
        holder.messageTimestampTextView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), outgoing ? R.color.message_outgoing_text : R.color.message_incoming_text));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView messageCardView;
        final TextView messageBodyTextView;
        final TextView messageTimestampTextView;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageCardView = itemView.findViewById(R.id.messageCardView);
            messageBodyTextView = itemView.findViewById(R.id.messageBodyTextView);
            messageTimestampTextView = itemView.findViewById(R.id.messageTimestampTextView);
        }
    }
}