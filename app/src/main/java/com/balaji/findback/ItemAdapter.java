package com.balaji.findback;

import android.content.Intent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Color;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.ArrayList;

public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {

    public interface OnItemActionListener {
        void onReportClick(Item item);
    }

    private Context context;
    private List<Item> itemList;
    private List<Item> fullList;
    private boolean isAdmin;
    private OnItemActionListener actionListener;
    private String currentUserRole;
    private String currentUserId;

    public ItemAdapter(Context context, List<Item> itemList) {
        this(context, itemList, false);
    }

    public ItemAdapter(Context context, List<Item> itemList, boolean isAdmin) {
        this.context = context;
        this.itemList = itemList;
        this.fullList = new ArrayList<>(itemList);
        this.isAdmin = isAdmin;
        
        // Fetch current user details for Prompt 2
        this.currentUserId = FirebaseAuth.getInstance().getUid();
        loadCurrentUserRole();
    }

    private void loadCurrentUserRole() {
        if (currentUserId == null) return;
        
        FirebaseFirestore.getInstance().collection("users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUserRole = documentSnapshot.getString("role");
                        notifyDataSetChanged();
                    }
                });
    }

    public void setOnItemActionListener(OnItemActionListener listener) {
        this.actionListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = itemList.get(position);

        // TITLE
        holder.textTitle.setText(item.getTitle() != null ? item.getTitle() : "Untitled Item");

        // TYPE
        String type = item.getType();
        if (type != null) {
            holder.textType.setText(type.toUpperCase());
            holder.textType.setTextColor(type.equalsIgnoreCase("lost") ? Color.RED : Color.parseColor("#4CAF50"));
        }

        // POSTED BY
        String name = item.getPostedByName();
        holder.textPostedBy.setText(name != null ? "Posted by: " + name : "Posted by: Anonymous");

        // STATUS & BADGE
        String status = item.getStatus();
        if (status != null) {
            holder.statusBadge.setText(status.toUpperCase());
            if (status.equalsIgnoreCase("OPEN")) {
                holder.statusBadge.setBackgroundColor(Color.parseColor("#4CAF50"));
            } else if (status.equalsIgnoreCase("CLAIMED")) {
                holder.statusBadge.setBackgroundColor(Color.parseColor("#FF9800"));
            } else if (status.equalsIgnoreCase("RETURNED")) {
                holder.statusBadge.setBackgroundColor(Color.parseColor("#9E9E9E"));
            }
        }

        // TIME
        Timestamp timestamp = item.getTimestamp();
        holder.textTime.setText(timestamp != null ? getTimeAgo(timestamp) : "just now");

        // ADMIN LOGIC
        if (isAdmin) {
            holder.adminMenuBtn.setVisibility(View.VISIBLE);
            holder.btnReport.setVisibility(View.GONE); // Hide report button for admin
            holder.adminMenuBtn.setOnClickListener(v -> showAdminOptions(item, position));
        } else {
            holder.adminMenuBtn.setVisibility(View.GONE);
            
            // Prompt 2: Hide report button logic
            boolean hideReport = false;
            
            // 1. Hide if admin
            if ("admin".equals(currentUserRole)) {
                hideReport = true;
            }
            
            // 2. Hide if own item
            if (item.getPostedBy() != null && currentUserId != null && item.getPostedBy().equals(currentUserId)) {
                hideReport = true;
            }
            
            if (hideReport) {
                holder.btnReport.setVisibility(View.GONE);
            } else {
                holder.btnReport.setVisibility(View.VISIBLE);
                holder.btnReport.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onReportClick(item);
                    }
                });
            }
        }

        // ITEM CLICK -> DETAIL
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ItemDetailActivity.class);
            intent.putExtra("title", item.getTitle());
            intent.putExtra("description", item.getDescription());
            intent.putExtra("imageBase64", item.getImageBase64());
            intent.putExtra("itemId", item.getId());
            intent.putExtra("ownerId", item.getPostedBy());
            intent.putExtra("status", item.getStatus());
            intent.putExtra("institutionId", item.getInstitutionId());
            context.startActivity(intent);
        });

        // IMAGE LOADING & BLUR LOGIC
        String imageBase64 = item.getImageBase64();
        
        // Reset RenderEffect (important for RecyclerView recycling)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            holder.imageItem.setRenderEffect(null);
        }

        if (imageBase64 != null && !imageBase64.isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                holder.imageItem.setImageBitmap(bitmap);

                // Apply blur only for OPEN items to protect privacy
                if (status != null && status.equalsIgnoreCase("OPEN")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        holder.imageItem.setRenderEffect(
                                RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP)
                        );
                    }
                }

                holder.imageItem.setOnClickListener(v -> {
                    if (status != null && (status.equalsIgnoreCase("CLAIMED") || status.equalsIgnoreCase("RETURNED"))) {
                        Intent intent = new Intent(context, ImagePreviewActivity.class);
                        intent.putExtra("imageBase64", imageBase64);
                        intent.putExtra("status", status);
                        context.startActivity(intent);
                    } else {
                        Toast.makeText(context, "Full image visible only after claim approval", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                holder.imageItem.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            holder.imageItem.setImageResource(android.R.drawable.ic_menu_gallery);
            holder.imageItem.setOnClickListener(null);
        }
    }

    private void showAdminOptions(Item item, int position) {
        String[] options = {"Mark as Returned", "Delete Item", "Flag Item"};
        new AlertDialog.Builder(context)
                .setTitle("Admin Moderation")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) markAsReturned(item, position);
                    else if (which == 1) deleteItem(item, position);
                    else if (which == 2) flagItem(item);
                })
                .show();
    }

    private void markAsReturned(Item item, int position) {
        FirebaseFirestore.getInstance().collection("items")
                .document(item.getId())
                .update("status", "RETURNED")
                .addOnSuccessListener(aVoid -> {
                    item.setStatus("RETURNED");
                    notifyItemChanged(position);
                    Toast.makeText(context, "Item marked as returned", Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteItem(Item item, int position) {
        FirebaseFirestore.getInstance().collection("items")
                .document(item.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    itemList.remove(position);
                    notifyItemRemoved(position);
                    Toast.makeText(context, "Item deleted", Toast.LENGTH_SHORT).show();
                });
    }

    private void flagItem(Item item) {
        FirebaseFirestore.getInstance().collection("items")
                .document(item.getId())
                .update("flagged", true)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(context, "Item flagged", Toast.LENGTH_SHORT).show());
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public void updateList(List<Item> newList) {
        itemList.clear();
        itemList.addAll(newList);
        fullList.clear();
        fullList.addAll(newList);
        notifyDataSetChanged();
    }

    public void filter(String text) {
        itemList.clear();
        if (text.isEmpty()) {
            itemList.addAll(fullList);
        } else {
            String filterPattern = text.toLowerCase().trim();
            for (Item item : fullList) {
                if (item.getTitle() != null && item.getTitle().toLowerCase().contains(filterPattern)) {
                    itemList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    private String getTimeAgo(Timestamp timestamp) {
        long time = timestamp.toDate().getTime();
        long now = System.currentTimeMillis();
        long diff = now - time;
        long minutes = diff / (1000 * 60);
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " minutes ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + " hours ago";
        long days = hours / 24;
        return days + " days ago";
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageItem;
        TextView textTitle, textType, textPostedBy, statusBadge, textTime;
        ImageButton adminMenuBtn, btnReport;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageItem = itemView.findViewById(R.id.imageItem);
            textTitle = itemView.findViewById(R.id.textTitle);
            textType = itemView.findViewById(R.id.textType);
            textPostedBy = itemView.findViewById(R.id.textPostedBy);
            statusBadge = itemView.findViewById(R.id.statusBadge);
            textTime = itemView.findViewById(R.id.textTime);
            adminMenuBtn = itemView.findViewById(R.id.adminMenuBtn);
            btnReport = itemView.findViewById(R.id.btnReport);
        }
    }
}
