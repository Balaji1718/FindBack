package com.balaji.findback;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatMessage> messages = new ArrayList<>();
    private OnDownloadClickListener downloadClickListener;

    public interface OnDownloadClickListener {
        void onDownloadPdf(String content);
        void onDownloadWord(String content);
    }

    public void setOnDownloadClickListener(OnDownloadClickListener listener) {
        this.downloadClickListener = listener;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public void removeLoadingMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getType() == ChatMessage.TYPE_LOADING) {
                messages.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    public void clearMessages() {
        int size = messages.size();
        messages.clear();
        notifyItemRangeRemoved(0, size);
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == ChatMessage.TYPE_USER) {
            return new UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false));
        } else if (viewType == ChatMessage.TYPE_LOADING) {
            return new LoadingViewHolder(inflater.inflate(R.layout.item_chat_loading, parent, false));
        } else {
            return new AiViewHolder(inflater.inflate(R.layout.item_chat_ai, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).chatMessage.setText(message.getMessage());
        } else if (holder instanceof AiViewHolder) {
            AiViewHolder aiHolder = (AiViewHolder) holder;
            String text = message.getMessage();
            aiHolder.chatMessage.setText(text);

            boolean showPdf = message.isOfferPdf();
            boolean showWord = message.isOfferWord();

            if (showPdf || showWord) {
                aiHolder.downloadContainer.setVisibility(View.VISIBLE);
                
                aiHolder.btnPdfCard.setVisibility(showPdf ? View.VISIBLE : View.GONE);
                aiHolder.btnWordCard.setVisibility(showWord ? View.VISIBLE : View.GONE);

                // Add simple fade-in animation for reliability and feel
                Animation fadeIn = AnimationUtils.loadAnimation(aiHolder.itemView.getContext(), android.R.anim.fade_in);
                aiHolder.downloadContainer.startAnimation(fadeIn);

                aiHolder.btnPdfCard.setOnClickListener(v -> {
                    if (downloadClickListener != null) downloadClickListener.onDownloadPdf(text);
                });
                
                aiHolder.btnWordCard.setOnClickListener(v -> {
                    if (downloadClickListener != null) downloadClickListener.onDownloadWord(text);
                });
            } else {
                aiHolder.downloadContainer.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView chatMessage;
        UserViewHolder(View itemView) {
            super(itemView);
            chatMessage = itemView.findViewById(R.id.chatMessage);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView chatMessage;
        LinearLayout downloadContainer;
        MaterialCardView btnPdfCard, btnWordCard;

        AiViewHolder(View itemView) {
            super(itemView);
            chatMessage = itemView.findViewById(R.id.chatMessage);
            downloadContainer = itemView.findViewById(R.id.downloadContainer);
            btnPdfCard = itemView.findViewById(R.id.btnPdfCard);
            btnWordCard = itemView.findViewById(R.id.btnWordCard);
        }
    }

    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        LoadingViewHolder(View itemView) {
            super(itemView);
        }
    }
}