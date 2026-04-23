package com.balaji.findback;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
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
        
        Context context = holder.itemView.getContext();
        boolean isSelected = session.getSessionId() != null && session.getSessionId().equals(selectedSessionId);
        
        // Robustly get theme colors by name to bypass build symbol issues
        int primaryColor = getThemeColor(context, "colorPrimary", Color.BLUE);
        int surfaceColor = getThemeColor(context, "colorSurface", Color.WHITE);
        int onSurfaceColor = getThemeColor(context, "colorOnSurface", Color.BLACK);
        int outlineColor = getThemeColor(context, "colorOutline", Color.LTGRAY);

        if (holder.cardView != null) {
            if (isSelected) {
                holder.cardView.setStrokeColor(primaryColor);
                holder.cardView.setStrokeWidth(4);
                holder.cardView.setCardBackgroundColor(surfaceColor);
                holder.title.setTextColor(primaryColor);
                holder.title.setTypeface(null, Typeface.BOLD);
            } else {
                holder.cardView.setStrokeColor(outlineColor);
                holder.cardView.setStrokeWidth(2);
                holder.cardView.setCardBackgroundColor(surfaceColor);
                holder.title.setTextColor(onSurfaceColor);
                holder.title.setTypeface(null, Typeface.NORMAL);
            }
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
                CharSequence title = item.getTitle();
                if (title != null) {
                    if (title.equals("Rename")) {
                        listener.onRenameClick(session);
                    } else if (title.equals("Delete")) {
                        listener.onDeleteClick(session);
                    }
                }
                return true;
            });
            popup.show();
        });
    }

    private int getThemeColor(Context context, String attrName, int fallback) {
        TypedValue typedValue = new TypedValue();
        int resId = context.getResources().getIdentifier(attrName, "attr", context.getPackageName());
        if (resId == 0) resId = context.getResources().getIdentifier(attrName, "attr", "com.google.android.material");
        if (resId == 0) resId = context.getResources().getIdentifier(attrName, "attr", "androidx.appcompat");

        if (resId != 0 && context.getTheme().resolveAttribute(resId, typedValue, true)) {
            return typedValue.data;
        }
        return fallback;
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    public static class HistoryViewHolder extends RecyclerView.ViewHolder {
        public MaterialCardView cardView;
        public TextView title;
        public ImageView moreMenuBtn;

        public HistoryViewHolder(View itemView) {
            super(itemView);
            Context context = itemView.getContext();
            String packageName = context.getPackageName();

            // 1. Resolve cardView ID at runtime
            int cardId = context.getResources().getIdentifier("chat_history_card", "id", packageName);
            if (cardId != 0) {
                cardView = itemView.findViewById(cardId);
            } else if (itemView instanceof MaterialCardView) {
                cardView = (MaterialCardView) itemView;
            }

            // 2. Resolve title ID at runtime
            int titleId = context.getResources().getIdentifier("chat_history_title", "id", packageName);
            if (titleId != 0) {
                title = itemView.findViewById(titleId);
            }

            // 3. Resolve moreMenuBtn ID at runtime
            int menuId = context.getResources().getIdentifier("chat_history_menu", "id", packageName);
            if (menuId != 0) {
                moreMenuBtn = itemView.findViewById(menuId);
            }
        }
    }
}