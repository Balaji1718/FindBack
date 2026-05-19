package com.balaji.findback;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    private String existingImageBase64;
    private FirebaseAuth auth;
    
    private boolean isEditMode = false;
    private String editItemId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_item);

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

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"LOST", "FOUND"}
        );
        spinnerType.setAdapter(adapter);

        // ✅ CHECK IF IN EDIT MODE
        isEditMode = getIntent().getBooleanExtra("isEdit", false);
        if (isEditMode) {
            setupEditMode();
        } else {
            setupToolbar("Post Item", true);
        }

        btnSelect.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Image"), PICK_IMAGE);
        });

        btnPost.setOnClickListener(v -> {
            if (!isEditMode && imageUri == null) {
                Toast.makeText(this, "Please select image", Toast.LENGTH_SHORT).show();
                return;
            }

            if (etTitle.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Enter item title", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                showLoading(true);
                String base64Image = (imageUri != null) ? convertToBase64(imageUri) : existingImageBase64;
                saveToFirestore(base64Image);
            } catch (Exception e) {
                showLoading(false);
                Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
            }
        });

        setupBottomNavigation();
    }

    private void setupEditMode() {
        setupToolbar("Edit Item", true);
        editItemId = getIntent().getStringExtra("itemId");
        etTitle.setText(getIntent().getStringExtra("title"));
        etDescription.setText(getIntent().getStringExtra("description"));
        etContactInfo.setText(getIntent().getStringExtra("contactInfo"));
        btnPost.setText("Update Item");

        String type = getIntent().getStringExtra("type");
        if (type != null) {
            int pos = type.equalsIgnoreCase("LOST") ? 0 : 1;
            spinnerType.setSelection(pos);
        }

        existingImageBase64 = getIntent().getStringExtra("imageBase64");
        if (existingImageBase64 != null && !existingImageBase64.isEmpty()) {
            byte[] decoded = Base64.decode(existingImageBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            ivImage.setImageBitmap(bitmap);
        }
    }

    private void showLoading(boolean isLoading) {
        if (loadingProgress != null) loadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        if (btnPost != null) btnPost.setEnabled(!isLoading);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigation != null) bottomNavigation.getMenu().findItem(R.id.nav_post).setChecked(true);
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) { finish(); return true; }
            if (id == R.id.nav_claims) { showClaimsOptionsDialog(); return false; }
            if (id == R.id.nav_logout) { showLogoutConfirmation(); return false; }
            return false;
        });
    }

    private void showClaimsOptionsDialog() {
        String[] options = {"My Claims", "Claim Requests (Admin/Owner)"};
        new AlertDialog.Builder(this).setTitle("Claims Section").setItems(options, (dialog, which) -> {
            if (which == 0) startActivity(new Intent(this, MyClaimActivity.class));
            else startActivity(new Intent(this, ClaimRequestsActivity.class));
        }).show();
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this).setTitle("Logout").setMessage("Logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    auth.signOut();
                    startActivity(new Intent(this, InstitutionSelectionActivity.class));
                    finish();
                }).setNegativeButton("Cancel", null).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            imageUri = data.getData();
            ivImage.setImageURI(imageUri);
        }
    }

    private String convertToBase64(Uri uri) throws Exception {
        Bitmap originalBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 800, 800, true);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 35, stream);
        return Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT);
    }

    private void saveToFirestore(String base64Image) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String title = etTitle.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String contactInfo = etContactInfo.getText().toString().trim();
        String type = spinnerType.getSelectedItem().toString().toUpperCase();

        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String userName = documentSnapshot.getString("name");
                    if (userName == null) userName = "User";

                    Map<String, Object> data = new HashMap<>();
                    data.put("title", title);
                    data.put("description", description);
                    data.put("contactInfo", contactInfo);
                    data.put("type", type);
                    data.put("imageBase64", base64Image);
                    data.put("lastUpdated", FieldValue.serverTimestamp());
                    data.put("postedByName", userName);

                    if (isEditMode) {
                        FirebaseFirestore.getInstance().collection("items").document(editItemId)
                                .update(data)
                                .addOnSuccessListener(v -> {
                                    showLoading(false);
                                    Toast.makeText(this, "Item Updated", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                    } else {
                        // New item logic
                        data.put("institutionId", institutionId);
                        data.put("postedBy", user.getUid());
                        data.put("status", "OPEN");
                        data.put("timestamp", FieldValue.serverTimestamp());
                        FirebaseFirestore.getInstance().collection("items").add(data)
                                .addOnSuccessListener(v -> {
                                    showLoading(false);
                                    Toast.makeText(this, "Item Posted", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to get user info", Toast.LENGTH_SHORT).show();
                });
    }
}
