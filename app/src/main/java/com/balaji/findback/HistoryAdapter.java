package com.balaji.findback;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final List<ChatSession> sessions = new ArrayList<>();
    private final OnHistoryClickListener listener;
    private String selectedSessionId = null;

    public interface OnHistoryClickListener {
        void onChatClick(ChatSession session);
        void onRenameClick(ChatSession session);
        void onDeleteClick(ChatSession session);
    }

    public HistoryAdapter(OnHistoryClickListener listener) {
        this.listener = listener;
    }

    public void setSessions(List<ChatSession> newSessions) {
        sessions.clear();
        sessions.addAll(newSessions);
        notifyDataSetChanged();
    }

    public void setSelectedSessionId(String sessionId) {
        this.selectedSessionId = sessionId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        ChatSession session = sessions.get(position);
        holder.title.setText(session.getTitle());
        
        boolean isSelected = session.getSessionId().equals(selectedSessionId);
        
        if (isSelected) {
            holder.cardView.setStrokeColor(Color.parseColor("#4F86F7"));
            holder.cardView.setStrokeWidth(4);
            holder.cardView.setCardBackgroundColor(Color.parseColor("#F0F4FF"));
            holder.title.setTextColor(Color.parseColor("#4F86F7"));
            holder.title.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            holder.cardView.setStrokeColor(Color.parseColor("#E0E0E0"));
            holder.cardView.setStrokeWidth(2);
            holder.cardView.setCardBackgroundColor(Color.WHITE);
            holder.title.setTextColor(Color.parseColor("#212121"));
            holder.title.setTypeface(null, android.graphics.Typeface.NORMAL);
        }

        holder.itemView.setOnClickListener(v -> {
            setSelectedSessionId(session.getSessionId());
            listener.onChatClick(session);
        });
        
        holder.moreMenuBtn.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.getMenu().add("Rename");
            popup.getMenu().add("Delete");
            
            popup.setOnMenuItemClickListener(item -> {
                if (item.getTitle().equals("Rename")) {
                    listener.onRenameClick(session);
                } else if (item.getTitle().equals("Delete")) {
                    listener.onDeleteClick(session);
                }
                return true;
            });
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView title;
        ImageView moreMenuBtn;

        HistoryViewHolder(View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            title = itemView.findViewById(R.id.historyTitle);
            moreMenuBtn = itemView.findViewById(R.id.moreMenuBtn);
        }
    }
}