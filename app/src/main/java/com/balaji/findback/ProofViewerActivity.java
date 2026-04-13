package com.balaji.findback;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.FirebaseFirestore;

public class ProofViewerActivity extends BaseActivity {

    private static final String TAG = "ProofViewerActivity";
    private ImageView proofImage;
    private ImageButton btnClose;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_proof_viewer);

        proofImage = findViewById(R.id.proofImage);
        btnClose = findViewById(R.id.btnClose);
        progressBar = findViewById(R.id.progressBar);

        String claimId = getIntent().getStringExtra("claimId");

        if (claimId != null) {
            fetchClaimImages(claimId);
        } else {
            // Fallback for old intent style if any
            String imageBase64 = getIntent().getStringExtra("img1");
            if (imageBase64 != null && !imageBase64.isEmpty()) {
                displayBase64Image(imageBase64);
            } else {
                Toast.makeText(this, "No image found", Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        btnClose.setOnClickListener(v -> finish());
    }

    private void fetchClaimImages(String claimId) {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        FirebaseFirestore.getInstance()
                .collection("claims")
                .document(claimId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        String img1 = documentSnapshot.getString("proofImage1");
                        if (img1 != null && !img1.isEmpty()) {
                            displayBase64Image(img1);
                        } else {
                            Toast.makeText(this, "No proof image available", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Error fetching claim", e);
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                });
    }

    private void displayBase64Image(String base64String) {
        try {
            byte[] decoded = Base64.decode(base64String, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            proofImage.setImageBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error decoding image", e);
            Toast.makeText(this, "Error displaying image", Toast.LENGTH_SHORT).show();
        }
    }
}