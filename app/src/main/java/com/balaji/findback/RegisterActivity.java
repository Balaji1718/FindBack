package com.balaji.findback;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends BaseActivity {

    EditText registerName, registerEmail, registerPassword, registerConfirmPassword;
    Button registerButton;
    TextView registerTitle;
    View registerNameLayout, registerEmailLayout, registerPasswordLayout, registerConfirmPasswordLayout;
    ProgressBar loadingProgress;
    Toolbar toolbar;

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    String institutionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        institutionId = getIntent().getStringExtra("institutionId");

        if (institutionId == null) {
            Toast.makeText(this, "Institution not selected!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        registerTitle = findViewById(R.id.registerTitle);
        registerNameLayout = findViewById(R.id.registerNameLayout);
        registerEmailLayout = findViewById(R.id.registerEmailLayout);
        registerPasswordLayout = findViewById(R.id.registerPasswordLayout);
        registerConfirmPasswordLayout = findViewById(R.id.registerConfirmPasswordLayout);

        registerName = findViewById(R.id.registerName);
        registerEmail = findViewById(R.id.registerEmail);
        registerPassword = findViewById(R.id.registerPassword);
        registerConfirmPassword = findViewById(R.id.registerConfirmPassword);
        registerButton = findViewById(R.id.registerButton);
        loadingProgress = findViewById(R.id.loadingProgress);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Register");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        startIntroAnimations();

        registerButton.setOnClickListener(view -> registerUser());
    }

    @Override
    protected boolean shouldForceLightMode() {
        return true; // Force light mode for entry screen
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void startIntroAnimations() {
        registerTitle.animate().alpha(1f).setDuration(800).start();
        registerNameLayout.animate().alpha(1f).translationY(0).setDuration(800).setStartDelay(100).start();
        registerEmailLayout.animate().alpha(1f).translationY(0).setDuration(800).setStartDelay(200).start();
        registerPasswordLayout.animate().alpha(1f).translationY(0).setDuration(800).setStartDelay(300).start();
        registerConfirmPasswordLayout.animate().alpha(1f).translationY(0).setDuration(800).setStartDelay(400).start();
        registerButton.animate().alpha(1f).translationY(0).setDuration(800).setStartDelay(500).start();
    }

    private void registerUser() {

        String name = registerName.getText().toString().trim();
        String email = registerEmail.getText().toString().trim();
        String password = registerPassword.getText().toString().trim();
        String confirmPassword = registerConfirmPassword.getText().toString().trim();

        if (name.isEmpty()) {
            registerName.setError("Name required");
            return;
        }

        if (email.isEmpty()) {
            registerEmail.setError("Email required");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            registerEmail.setError("Enter valid email");
            return;
        }

        if (password.length() < 6) {
            registerPassword.setError("Minimum 6 characters required");
            return;
        }

        if (!password.equals(confirmPassword)) {
            registerConfirmPassword.setError("Passwords do not match");
            return;
        }

        showLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        if (mAuth.getCurrentUser() == null) {
                            showLoading(false);
                            Toast.makeText(this, "User creation error", Toast.LENGTH_LONG).show();
                            return;
                        }

                        String uid = mAuth.getCurrentUser().getUid();

                        Map<String, Object> userData = new HashMap<>();
                        userData.put("name", name);
                        userData.put("email", email);
                        userData.put("institutionId", institutionId);

                        // default role for new user
                        userData.put("role", "student");

                        // required for admin control
                        userData.put("status", "ACTIVE");

                        db.collection("users")
                                .document(uid)
                                .set(userData)
                                .addOnSuccessListener(unused -> {
                                    showLoading(false);

                                    Toast.makeText(this,
                                            "Registration Successful",
                                            Toast.LENGTH_SHORT).show();

                                    Intent intent = new Intent(
                                            RegisterActivity.this,
                                            LoginActivity.class
                                    );

                                    intent.putExtra("institutionId", institutionId);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    showLoading(false);
                                    Toast.makeText(this,
                                            "Firestore Error: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                });

                    } else {
                        showLoading(false);

                        Exception e = task.getException();

                        Log.e("REGISTER_ERROR",
                                e != null ? e.getMessage() : "Unknown error");

                        Toast.makeText(this,
                                "Registration Failed: " +
                                        (e != null ? e.getMessage() : "Unknown error"),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showLoading(boolean isLoading) {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        if (registerButton != null) {
            registerButton.setEnabled(!isLoading);
            registerButton.setAlpha(isLoading ? 0.5f : 1.0f);
        }
    }
}
