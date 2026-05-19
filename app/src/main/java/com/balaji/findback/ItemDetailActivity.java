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
import android.util.TypedValue;
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
    TextView detailTitle, detailDescription, tvContactInfo;

    TextView statusPosted, statusClaimed, statusApproved, statusReturned;
    TextView arrow1, arrow2, arrow3;

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
        tvContactInfo = findViewById(R.id.tvContactInfo);

        statusPosted = findViewById(R.id.statusPosted);
        statusClaimed = findViewById(R.id.statusClaimed);
        statusApproved = findViewById(R.id.statusApproved);
        statusReturned = findViewById(R.id.statusReturned);
        
        arrow1 = findViewById(R.id.arrow1);
        arrow2 = findViewById(R.id.arrow2);
        arrow3 = findViewById(R.id.arrow3);

        btnClaimItem = findViewById(R.id.btnClaimItem);
        timelineContainer = findViewById(R.id.timelineContainer);

        btnAdminOptions = new Button(this);
        btnAdminOptions.setText("Admin Options");
        btnAdminOptions.setVisibility(View.GONE);
        ((LinearLayout)btnClaimItem.getParent()).addView(btnAdminOptions);

        itemId = getIntent().getStringExtra("itemId");
        ownerId = getIntent().getStringExtra("ownerId");
        itemStatus = getIntent().getStringExtra("status");
        institutionId = getIntent().getStringExtra("institutionId");

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
                        Item item = snapshot.toObject(Item.class);
                        if (item == null) return;

                        itemStatus = item.getStatus();
                        detailTitle.setText(item.getTitle());
                        detailDescription.setText(item.getDescription());

                        String currentUserId = FirebaseAuth.getInstance().getUid();
                        boolean isOwner = ownerId != null && ownerId.equals(currentUserId);
                        
                        // Status flow: OPEN -> CLAIMED (Approved) -> RETURNED
                        if (isOwner || "CLAIMED".equalsIgnoreCase(itemStatus) || "RETURNED".equalsIgnoreCase(itemStatus)) {
                            tvContactInfo.setVisibility(View.VISIBLE);
                            tvContactInfo.setText("Contact Info: " + (item.getContactInfo() != null ? item.getContactInfo() : "Not provided"));
                        } else {
                            tvContactInfo.setVisibility(View.GONE);
                        }

                        updateStatusProgress(itemStatus);
                        buildTimeline(itemStatus);
                        handleImageBlur(itemStatus, item.getImageBase64());
                    }
                });
    }

    private void handleImageBlur(String status, String imageBase64) {
        if (imageBase64 != null) {
            try {
                byte[] decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                detailImage.setImageBitmap(bitmap);
            } catch (Exception e) {
                Log.e("ItemDetail", "Error decoding image", e);
            }
        }

        if (status != null && status.equalsIgnoreCase("OPEN")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                detailImage.setRenderEffect(RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP));
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                detailImage.setRenderEffect(null);
            }
        }
    }

    private void checkUserRoleAndPermissions() {
        String currentUserId = FirebaseAuth.getInstance().getUid();
        if (currentUserId == null) return;

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
                                intent.putExtra("itemTitle", detailTitle.getText().toString());
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
                }).show();
    }

    private void editItem() {
        FirebaseFirestore.getInstance().collection("items").document(itemId).get()
                .addOnSuccessListener(doc -> {
                    Item item = doc.toObject(Item.class);
                    if (item == null) return;
                    Intent intent = new Intent(this, PostItemActivity.class);
                    intent.putExtra("isEdit", true);
                    intent.putExtra("itemId", itemId);
                    intent.putExtra("title", item.getTitle());
                    intent.putExtra("description", item.getDescription());
                    intent.putExtra("contactInfo", item.getContactInfo());
                    intent.putExtra("type", item.getType());
                    intent.putExtra("imageBase64", item.getImageBase64());
                    startActivity(intent);
                });
    }

    private void deleteItem() {
        new AlertDialog.Builder(this).setTitle("Delete Item").setMessage("Are you sure?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    FirebaseFirestore.getInstance().collection("items").document(itemId).delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                }).setNegativeButton("Cancel", null).show();
    }

    private void markAsReturned() {
        FirebaseFirestore.getInstance().collection("items").document(itemId).update("status", "RETURNED")
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Marked as Returned", Toast.LENGTH_SHORT).show());
    }

    private void flagItem() {
        FirebaseFirestore.getInstance().collection("items").document(itemId).update("flagged", true)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Item Flagged", Toast.LENGTH_SHORT).show());
    }

    private void updateStatusProgress(String status){
        TypedValue typedValue = new TypedValue();
        // Fix: Use androidx.appcompat.R.attr.colorPrimary as it is defined in AppCompat library
        // This is necessary because android.nonTransitiveRClass is enabled.
        getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
        int activeColor = typedValue.data;
        
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true);
        int inactiveColor = typedValue.data;

        statusPosted.setTextColor(inactiveColor);
        statusClaimed.setTextColor(inactiveColor);
        statusApproved.setTextColor(inactiveColor);
        statusReturned.setTextColor(inactiveColor);
        
        arrow1.setTextColor(inactiveColor);
        arrow2.setTextColor(inactiveColor);
        arrow3.setTextColor(inactiveColor);

        statusPosted.setTextColor(activeColor);
        if(status == null) return;

        // Sequence: OPEN -> CLAIMED (Approved) -> RETURNED
        if(status.equalsIgnoreCase("CLAIMED") || status.equalsIgnoreCase("RETURNED")){
            statusClaimed.setTextColor(activeColor);
            statusApproved.setTextColor(activeColor);
            arrow1.setTextColor(activeColor);
            arrow2.setTextColor(activeColor);
        }
        if(status.equalsIgnoreCase("RETURNED")){
            statusReturned.setTextColor(activeColor);
            arrow3.setTextColor(activeColor);
        }
    }

    private void buildTimeline(String status){
        timelineContainer.removeAllViews();
        addTimeline("Item Posted");
        if(status == null) return;
        if(status.equalsIgnoreCase("CLAIMED") || status.equalsIgnoreCase("RETURNED")) {
            addTimeline("Claim Requested");
            addTimeline("Claim Approved");
        }
        if(status.equalsIgnoreCase("RETURNED")) addTimeline("Item Returned");
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
        if (itemListener != null) itemListener.remove();
    }
}
