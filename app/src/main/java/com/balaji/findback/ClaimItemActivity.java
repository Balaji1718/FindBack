package com.balaji.findback;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ClaimItemActivity extends BaseActivity {

    private EditText etProof;
    private Button btnSubmitClaim, btnAttachImage;

    private ImageView proofImage1, proofImage2;
    private ImageButton removeImage1, removeImage2;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private String itemId;
    private String ownerId;
    private String itemTitle;
    private String institutionId;

    private String proofImage1Base64 = "";
    private String proofImage2Base64 = "";

    private int imageCount = 0;

    private ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {

                if (uri == null) return;

                try {

                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                    // Resize image to prevent large Base64 strings
                    Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 800, 800, true);

                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    // Reduced quality to 40 to decrease Firestore document size and avoid Binder transaction limits
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream);

                    byte[] bytes = stream.toByteArray();
                    String base64 = Base64.encodeToString(bytes, Base64.DEFAULT);

                    if (imageCount == 0) {

                        proofImage1.setImageBitmap(resizedBitmap);
                        proofImage1.setVisibility(View.VISIBLE);
                        removeImage1.setVisibility(View.VISIBLE);

                        proofImage1Base64 = base64;

                    } else {

                        proofImage2.setImageBitmap(resizedBitmap);
                        proofImage2.setVisibility(View.VISIBLE);
                        removeImage2.setVisibility(View.VISIBLE);

                        proofImage2Base64 = base64;
                    }

                    imageCount++;

                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Image load failed", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_claim_item);

        setupToolbar("Submit Claim", true);

        etProof = findViewById(R.id.etProof);
        btnSubmitClaim = findViewById(R.id.btnSubmitClaim);
        btnAttachImage = findViewById(R.id.btnAttachImage);

        proofImage1 = findViewById(R.id.proofImage1);
        proofImage2 = findViewById(R.id.proofImage2);

        removeImage1 = findViewById(R.id.removeImage1);
        removeImage2 = findViewById(R.id.removeImage2);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        itemId = getIntent().getStringExtra("itemId");
        ownerId = getIntent().getStringExtra("ownerId");
        itemTitle = getIntent().getStringExtra("itemTitle");
        institutionId = getIntent().getStringExtra("institutionId");

        btnAttachImage.setOnClickListener(v -> {

            if (imageCount >= 2) {
                Toast.makeText(this,
                        "Maximum 2 proof images allowed",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            imagePickerLauncher.launch("image/*");
        });

        removeImage1.setOnClickListener(v -> {

            proofImage1.setImageDrawable(null);
            proofImage1.setVisibility(View.GONE);
            removeImage1.setVisibility(View.GONE);

            proofImage1Base64 = "";
            imageCount = Math.max(0, imageCount - 1);
        });

        removeImage2.setOnClickListener(v -> {

            proofImage2.setImageDrawable(null);
            proofImage2.setVisibility(View.GONE);
            removeImage2.setVisibility(View.GONE);

            proofImage2Base64 = "";
            imageCount = Math.max(0, imageCount - 1);
        });

        btnSubmitClaim.setOnClickListener(v -> checkDuplicateClaim());
    }

    private void checkDuplicateClaim() {

        if (auth.getCurrentUser() == null) {

            Toast.makeText(this,
                    "User not logged in",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String claimerId = auth.getCurrentUser().getUid();

        // Problem 2 Fix: Added status check and forced server source to avoid cache issues after deletion
        db.collection("claims")
                .whereEqualTo("itemId", itemId)
                .whereEqualTo("claimerId", claimerId)
                .whereEqualTo("status", "pending")
                .whereEqualTo("institutionId", institutionId)
                .get(Source.SERVER)
                .addOnSuccessListener(query -> {

                    if (!query.isEmpty()) {

                        Toast.makeText(this,
                                "You already submitted a claim",
                                Toast.LENGTH_LONG).show();

                    } else {

                        submitClaim();
                    }
                })
                .addOnFailureListener(e ->

                        Toast.makeText(this,
                                "Error checking claims",
                                Toast.LENGTH_SHORT).show());
    }

    private void submitClaim() {

        String proof = etProof.getText().toString().trim();

        if (TextUtils.isEmpty(proof)) {

            Toast.makeText(this,
                    "Explain why this item belongs to you",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (auth.getCurrentUser() == null) return;

        String claimerId = auth.getCurrentUser().getUid();

        Map<String, Object> claim = new HashMap<>();

        claim.put("itemId", itemId);
        claim.put("itemTitle", itemTitle);
        claim.put("ownerId", ownerId);
        claim.put("claimerId", claimerId);
        claim.put("institutionId", institutionId);

        claim.put("proofMessage", proof);

        claim.put("proofImage1", proofImage1Base64);
        claim.put("proofImage2", proofImage2Base64);

        claim.put("status", "pending");
        claim.put("timestamp", FieldValue.serverTimestamp());

        db.collection("claims")
                .add(claim)
                .addOnSuccessListener(doc -> {

                    Toast.makeText(this,
                            "Claim submitted successfully",
                            Toast.LENGTH_SHORT).show();

                    finish();
                })
                .addOnFailureListener(e ->

                        Toast.makeText(this,
                                "Submission failed",
                                Toast.LENGTH_SHORT).show());
    }
}