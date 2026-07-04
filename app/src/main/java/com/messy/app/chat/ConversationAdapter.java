package com.messy.app.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.messy.app.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {
    public interface OnConversationClickListener {
        void onConversationClicked(ConversationSummary conversation);
    }

    private final List<ConversationSummary> conversations = new ArrayList<>();
    private final OnConversationClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());

    public ConversationAdapter(OnConversationClickListener listener) {
        this.listener = listener;
    }

    public void setConversations(List<ConversationSummary> newConversations) {
        conversations.clear();
        conversations.addAll(newConversations);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new ConversationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        ConversationSummary conversation = conversations.get(position);
        holder.nameTextView.setText(conversation.contactName);
        holder.previewTextView.setText(conversation.lastMessage);
        holder.timestampTextView.setText(formatTimestamp(holder.itemView.getContext(), conversation.lastTimestamp));
        holder.itemView.setOnClickListener(v -> listener.onConversationClicked(conversation));
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    private String formatTimestamp(Context context, long timestamp) {
        if (timestamp <= 0L) {
            return context.getString(R.string.no_conversations);
        }
        return dateFormat.format(new Date(timestamp));
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        final TextView nameTextView;
        final TextView previewTextView;
        final TextView timestampTextView;

        ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.conversationNameTextView);
            previewTextView = itemView.findViewById(R.id.conversationPreviewTextView);
            timestampTextView = itemView.findViewById(R.id.conversationTimestampTextView);
        }
    }
}