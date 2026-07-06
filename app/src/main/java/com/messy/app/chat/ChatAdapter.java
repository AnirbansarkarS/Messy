package com.messy.app.chat;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

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

        // Gravity
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.messageBubble.getLayoutParams();
        params.gravity = outgoing ? Gravity.END : Gravity.START;
        holder.messageBubble.setLayoutParams(params);

        // Constrain bubble max width to 75% of screen
        int maxWidth = (int) (holder.itemView.getResources().getDisplayMetrics().widthPixels * 0.75f);
        ViewGroup.LayoutParams lp = holder.messageBubble.getLayoutParams();
        lp.width = maxWidth;
        holder.messageBubble.setLayoutParams(lp);

        // Background drawable + text colors
        if (outgoing) {
            holder.messageBubble.setBackgroundResource(R.drawable.bubble_outgoing);
            holder.messageBodyTextView.setTextColor(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.message_outgoing_text));
            holder.messageTimestampTextView.setTextColor(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.message_timestamp_outgoing));
        } else {
            holder.messageBubble.setBackgroundResource(R.drawable.bubble_incoming);
            holder.messageBodyTextView.setTextColor(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.message_incoming_text));
            holder.messageTimestampTextView.setTextColor(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.message_timestamp_incoming));
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout messageBubble;
        final TextView messageBodyTextView;
        final TextView messageTimestampTextView;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageBubble = itemView.findViewById(R.id.messageBubble);
            messageBodyTextView = itemView.findViewById(R.id.messageBodyTextView);
            messageTimestampTextView = itemView.findViewById(R.id.messageTimestampTextView);
        }
    }
}