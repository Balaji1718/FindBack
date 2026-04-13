package com.balaji.findback;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class PostItemActivity extends BaseActivity {

    private String institutionId;
    private static final int PICK_IMAGE = 1;

    private EditText etTitle, etDescription, etContactInfo;
    private Spinner spinnerType;
    private ImageView ivImage;
    private Button btnSelect, btnPost;
    private ProgressBar loadingProgress;
    private BottomNavigationView bottomNavigation;

    private Uri imageUri;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_item);

        setupToolbar("Post Item", true);

        auth = FirebaseAuth.getInstance();
        institutionId = getIntent().getStringExtra("institutionId");

        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        etContactInfo = findViewById(R.id.etContactInfo);
        spinnerType = findViewById(R.id.spinnerType);
        ivImage = findViewById(R.id.ivImage);
        btnSelect = findViewById(R.id.btnSelect);
        btnPost = findViewById(R.id.btnPost);
        loadingProgress = findViewById(R.id.loadingProgress);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        // 🔥 Use Uppercase to match Firestore Structure requirements
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"LOST", "FOUND"}
        );

        spinnerType.setAdapter(adapter);

        // Select image
        btnSelect.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Image"), PICK_IMAGE);
        });

        // Post item
        btnPost.setOnClickListener(v -> {

            if (imageUri == null) {
                Toast.makeText(this, "Please select image", Toast.LENGTH_SHORT).show();
                return;
            }

            if (etTitle.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Enter item title", Toast.LENGTH_SHORT).show();
                return;
            }

            String contactInfo = etContactInfo.getText().toString().trim();
            if(contactInfo.isEmpty()){
                etContactInfo.setError("Please enter contact information");
                return;
            }

            try {
                showLoading(true);
                String base64Image = convertToBase64(imageUri);
                saveToFirestore(base64Image);
            } catch (Exception e) {
                showLoading(false);
                e.printStackTrace();
                Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
            }
        });

        setupBottomNavigation();
    }

    private void showLoading(boolean isLoading) {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        if (btnPost != null) {
            btnPost.setEnabled(!isLoading);
            btnPost.setAlpha(isLoading ? 0.5f : 1.0f);
        }
        if (btnSelect != null) {
            btnSelect.setEnabled(!isLoading);
            btnSelect.setAlpha(isLoading ? 0.5f : 1.0f);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sync the bottom navigation selection to "Post"
        if (bottomNavigation != null) {
            bottomNavigation.getMenu().findItem(R.id.nav_post).setChecked(true);
        }
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                finish(); // Go back to MainActivity
                return true;
            }
            else if (id == R.id.nav_post) {
                return true; // Already here
            }
            else if (id == R.id.nav_claims) {
                showClaimsOptionsDialog();
                return false;
            }
            else if (id == R.id.nav_logout) {
                showLogoutConfirmation();
                return false;
            }
            return false;
        });
    }

    private void showClaimsOptionsDialog() {
        String[] options = {"My Claims", "Claim Requests (Admin/Owner)"};
        new AlertDialog.Builder(this)
                .setTitle("Claims Section")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, MyClaimActivity.class));
                    } else {
                        startActivity(new Intent(this, ClaimRequestsActivity.class));
                    }
                })
                .show();
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    auth.signOut();
                    Intent intent = new Intent(this, InstitutionSelectionActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {

            imageUri = data.getData();
            ivImage.setImageURI(imageUri);
        }
    }

    // Resize + compress image and convert to Base64
    private String convertToBase64(Uri uri) throws Exception {

        Bitmap originalBitmap = MediaStore.Images.Media.getBitmap(
                this.getContentResolver(), uri);

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                originalBitmap,
                800,
                800,
                true
        );

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        // Reduced quality to 35 to further decrease Firestore document size and avoid Binder transaction limits
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 35, stream);

        byte[] bytes = stream.toByteArray();

        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    // Save item to Firestore
    private void saveToFirestore(String base64Image) {

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            showLoading(false);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {

                    if (doc.exists()) {

                        String instId = doc.getString("institutionId");
                        String userName = doc.getString("name");

                        performSave(
                                instId != null ? instId : institutionId,
                                userName,
                                base64Image,
                                user.getUid()
                        );

                    } else {
                        showLoading(false);
                        Toast.makeText(this, "User profile not found!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void performSave(String instId, String userName, String base64Image, String uid) {

        if (instId == null) {
            showLoading(false);
            Toast.makeText(this, "Institution ID missing!", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = etTitle.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String contactInfo = etContactInfo.getText().toString().trim();
        String type = spinnerType.getSelectedItem().toString().toUpperCase(); // 🔥 Ensure uppercase

        Map<String, Object> item = new HashMap<>();

        item.put("title", title);
        item.put("description", description);
        item.put("contactInfo", contactInfo);
        item.put("type", type);
        item.put("institutionId", instId);
        item.put("postedBy", uid);
        item.put("postedByName", userName != null ? userName : "Anonymous");
        item.put("status", "OPEN"); // Important for filtering
        item.put("imageBase64", base64Image);
        item.put("reportCount", 0);
        item.put("flagged", false);
        item.put("hidden", false);
        item.put("timestamp", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection("items")
                .add(item)
                .addOnSuccessListener(documentReference -> {
                    showLoading(false);
                    Toast.makeText(this, "Item Posted Successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to save item", Toast.LENGTH_SHORT).show();
                });
    }
}
