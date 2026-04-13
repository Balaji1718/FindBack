package com.balaji.findback;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class ItemDetailActivity extends BaseActivity {

    ImageView detailImage;
    TextView detailTitle, detailDescription;

    TextView statusPosted, statusClaimed, statusApproved, statusReturned;

    Button btnClaimItem;
    Button btnAdminOptions;

    LinearLayout timelineContainer;

    String itemId, ownerId, itemTitle, itemStatus, institutionId;
    ListenerRegistration itemListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);

        setupToolbar("Item Details", true);
        setupAIButton();

        detailImage = findViewById(R.id.detailImage);
        detailTitle = findViewById(R.id.detailTitle);
        detailDescription = findViewById(R.id.detailDescription);

        statusPosted = findViewById(R.id.statusPosted);
        statusClaimed = findViewById(R.id.statusClaimed);
        statusApproved = findViewById(R.id.statusApproved);
        statusReturned = findViewById(R.id.statusReturned);

        btnClaimItem = findViewById(R.id.btnClaimItem);
        timelineContainer = findViewById(R.id.timelineContainer);

        btnAdminOptions = new Button(this);
        btnAdminOptions.setText("Admin Options");
        btnAdminOptions.setVisibility(View.GONE);
        ((LinearLayout)btnClaimItem.getParent()).addView(btnAdminOptions);

        itemTitle = getIntent().getStringExtra("title");
        String description = getIntent().getStringExtra("description");
        String imageBase64 = getIntent().getStringExtra("imageBase64");

        itemId = getIntent().getStringExtra("itemId");
        ownerId = getIntent().getStringExtra("ownerId");
        itemStatus = getIntent().getStringExtra("status");
        institutionId = getIntent().getStringExtra("institutionId");

        detailTitle.setText(itemTitle);
        detailDescription.setText(description);

        // LOAD IMAGE WITH SECURITY BLUR
        if (imageBase64 != null) {
            try {
                byte[] decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                detailImage.setImageBitmap(bitmap);
            } catch (Exception e) {
                Log.e("ItemDetail", "Error decoding image", e);
            }
        }

        startItemUpdateListener();
        checkUserRoleAndPermissions();
    }

    private void startItemUpdateListener() {
        if (itemId == null) return;

        itemListener = FirebaseFirestore.getInstance().collection("items").document(itemId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e("ItemDetail", "Listen failed.", error);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        String currentStatus = snapshot.getString("status");
                        String currentTitle = snapshot.getString("title");
                        String currentDesc = snapshot.getString("description");

                        itemStatus = currentStatus;
                        detailTitle.setText(currentTitle);
                        detailDescription.setText(currentDesc);

                        updateStatusProgress(currentStatus);
                        buildTimeline(currentStatus);
                        
                        // Handle security blur based on updated status
                        handleImageBlur(currentStatus);
                    }
                });
    }

    private void handleImageBlur(String status) {
        if (status != null && status.equalsIgnoreCase("OPEN")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                detailImage.setRenderEffect(
                        RenderEffect.createBlurEffect(
                                25f,
                                25f,
                                Shader.TileMode.CLAMP
                        )
                );
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                detailImage.setRenderEffect(null);
            }
        }
    }

    private void checkUserRoleAndPermissions() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance().collection("users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String role = documentSnapshot.getString("role");

                    if ("admin".equals(role)) {
                        btnClaimItem.setVisibility(View.GONE);
                        btnAdminOptions.setVisibility(View.VISIBLE);
                        btnAdminOptions.setOnClickListener(v -> showAdminOptions());
                    } else {
                        if (currentUserId.equals(ownerId)
                                || "CLAIMED".equalsIgnoreCase(itemStatus)
                                || "RETURNED".equalsIgnoreCase(itemStatus)) {
                            btnClaimItem.setVisibility(View.GONE);
                        } else {
                            btnClaimItem.setVisibility(View.VISIBLE);
                            btnClaimItem.setOnClickListener(v -> {
                                Intent intent = new Intent(this, ClaimItemActivity.class);
                                intent.putExtra("itemId", itemId);
                                intent.putExtra("ownerId", ownerId);
                                intent.putExtra("itemTitle", itemTitle);
                                intent.putExtra("institutionId", institutionId);
                                startActivity(intent);
                            });
                        }
                    }
                });
    }

    private void showAdminOptions() {
        String[] options = {"Edit Item", "Delete Item", "Mark as Returned", "Flag Item"};

        new AlertDialog.Builder(this)
                .setTitle("Admin Moderation")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: editItem(); break;
                        case 1: deleteItem(); break;
                        case 2: markAsReturned(); break;
                        case 3: flagItem(); break;
                    }
                })
                .show();
    }

    private void editItem() {
        Toast.makeText(this, "Edit feature coming soon", Toast.LENGTH_SHORT).show();
    }

    private void deleteItem() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Item")
                .setMessage("Are you sure you want to delete this item?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    FirebaseFirestore.getInstance()
                            .collection("items")
                            .document(itemId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void markAsReturned() {
        FirebaseFirestore.getInstance()
                .collection("items")
                .document(itemId)
                .update("status", "RETURNED")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Marked as Returned", Toast.LENGTH_SHORT).show();
                });
    }

    private void flagItem() {
        Map<String, Object> flagData = new HashMap<>();
        flagData.put("flagged", true);
        flagData.put("flaggedBy", FirebaseAuth.getInstance().getCurrentUser().getUid());

        FirebaseFirestore.getInstance()
                .collection("items")
                .document(itemId)
                .update(flagData)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Item Flagged", Toast.LENGTH_SHORT).show());
    }

    private void updateStatusProgress(String status){
        int green = ContextCompat.getColor(this, android.R.color.holo_green_dark);
        int gray = ContextCompat.getColor(this, android.R.color.darker_gray);

        statusPosted.setTextColor(gray);
        statusClaimed.setTextColor(gray);
        statusApproved.setTextColor(gray);
        statusReturned.setTextColor(gray);

        if(status == null) {
            statusPosted.setTextColor(green);
            return;
        }

        // Standard sequence: OPEN (Posted) -> CLAIMED -> APPROVED -> RETURNED
        statusPosted.setTextColor(green);

        if(status.equalsIgnoreCase("CLAIMED")
                || status.equalsIgnoreCase("APPROVED")
                || status.equalsIgnoreCase("RETURNED")){
            statusClaimed.setTextColor(green);
        }

        if(status.equalsIgnoreCase("APPROVED")
                || status.equalsIgnoreCase("RETURNED")){
            statusApproved.setTextColor(green);
        }

        if(status.equalsIgnoreCase("RETURNED")){
            statusReturned.setTextColor(green);
        }
    }

    private void buildTimeline(String status){
        timelineContainer.removeAllViews();
        addTimeline("Item Posted");

        if(status == null) return;

        if(status.equalsIgnoreCase("CLAIMED")
                || status.equalsIgnoreCase("APPROVED")
                || status.equalsIgnoreCase("RETURNED")){
            addTimeline("Claim Requested");
        }

        if(status.equalsIgnoreCase("APPROVED")
                || status.equalsIgnoreCase("RETURNED")){
            addTimeline("Claim Approved");
        }

        if(status.equalsIgnoreCase("RETURNED")){
            addTimeline("Item Returned");
        }
    }

    private void addTimeline(String text){
        View view = getLayoutInflater().inflate(R.layout.timeline_row, null);
        TextView tv = view.findViewById(R.id.timelineText);
        tv.setText(text);
        timelineContainer.addView(view);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (itemListener != null) {
            itemListener.remove();
        }
    }
}