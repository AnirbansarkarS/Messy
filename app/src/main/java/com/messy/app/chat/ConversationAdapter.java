package com.messy.app.chat;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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

    // Color palette for avatars (cycles based on contactId hash)
    private static final int[] AVATAR_COLORS = {
            R.color.avatar_bg_1,
            R.color.avatar_bg_2,
            R.color.avatar_bg_3,
            R.color.avatar_bg_4,
            R.color.avatar_bg_5,
    };

    private final List<ConversationSummary> conversations = new ArrayList<>();
    private final OnConversationClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("MMM d", Locale.getDefault());

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
        Context ctx = holder.itemView.getContext();

        holder.nameTextView.setText(conversation.contactName);
        holder.previewTextView.setText(conversation.lastMessage);
        holder.timestampTextView.setText(formatTimestamp(ctx, conversation.lastTimestamp));

        // Avatar initial
        String initial = conversation.contactName != null && !conversation.contactName.isEmpty()
                ? String.valueOf(conversation.contactName.charAt(0)).toUpperCase()
                : "?";
        holder.avatarInitialTextView.setText(initial);

        // Avatar background color
        int colorIndex = Math.abs(conversation.contactId.hashCode()) % AVATAR_COLORS.length;
        @ColorInt int color = ContextCompat.getColor(ctx, AVATAR_COLORS[colorIndex]);
        GradientDrawable drawable = (GradientDrawable) ContextCompat.getDrawable(ctx, R.drawable.avatar_circle).mutate();
        drawable.setColor(color);
        holder.avatarBackground.setBackground(drawable);

        holder.itemView.setOnClickListener(v -> listener.onConversationClicked(conversation));
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    private String formatTimestamp(Context context, long timestamp) {
        if (timestamp <= 0L) {
            return "";
        }
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        if (diff < 24L * 60 * 60 * 1000) {
            return dateFormat.format(new Date(timestamp));
        }
        return dateOnlyFormat.format(new Date(timestamp));
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        final TextView nameTextView;
        final TextView previewTextView;
        final TextView timestampTextView;
        final TextView avatarInitialTextView;
        final View avatarBackground;

        ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.conversationNameTextView);
            previewTextView = itemView.findViewById(R.id.conversationPreviewTextView);
            timestampTextView = itemView.findViewById(R.id.conversationTimestampTextView);
            avatarInitialTextView = itemView.findViewById(R.id.avatarInitialTextView);
            avatarBackground = itemView.findViewById(R.id.avatarBackground);
        }
    }
}