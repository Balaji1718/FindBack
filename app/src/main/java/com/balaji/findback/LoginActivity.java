package com.balaji.findback;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import com.google.firebase.messaging.FirebaseMessaging;

public class LoginActivity extends BaseActivity {

    EditText emailEditText, passwordEditText;
    Button loginButton;
    TextView goToRegister, loginTitle, txtInstitution;
    View emailInputLayout, passwordInputLayout;
    ProgressBar loadingProgress;
    Toolbar toolbar;

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    String institutionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        institutionId = getIntent().getStringExtra("institutionId");

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Login");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loginTitle = findViewById(R.id.loginTitle);
        txtInstitution = findViewById(R.id.txtInstitution);
        emailInputLayout = findViewById(R.id.emailInputLayout);
        passwordInputLayout = findViewById(R.id.passwordInputLayout);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        goToRegister = findViewById(R.id.goToRegister);
        loadingProgress = findViewById(R.id.loadingProgress);

        // ✅ Show selected institution name
        SharedPreferences prefs = getSharedPreferences("app", MODE_PRIVATE);
        String name = prefs.getString("institutionName", "Unknown");
        txtInstitution.setText("Logging into: " + name);

        startIntroAnimations();

        loginButton.setOnClickListener(v -> loginUser());

        goToRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            intent.putExtra("institutionId", institutionId);
            startActivity(intent);
        });
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

        loginTitle.animate().alpha(1f).setDuration(800).start();
        txtInstitution.animate().alpha(1f).setDuration(800).setStartDelay(100).start();

        emailInputLayout.animate().alpha(1f).translationY(0).setDuration(800).setStartDelay(200).start();
        passwordInputLayout.animate().alpha(1f).translationY(0).setDuration(800).setStartDelay(400).start();

        loginButton.animate().alpha(1f).translationY(0).setDuration(800).setStartDelay(600).start();
        goToRegister.animate().alpha(1f).setDuration(800).setStartDelay(800).start();
    }

    private void loginUser() {

        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (email.isEmpty()) {
            emailEditText.setError("Email required");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter valid email");
            return;
        }

        if (password.isEmpty()) {
            passwordEditText.setError("Password required");
            return;
        }

        // ✅ Show loading and disable button during login
        showLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        if(mAuth.getCurrentUser() == null) {
                            showLoading(false);
                            return;
                        }

                        String uid = mAuth.getCurrentUser().getUid();

                        db.collection("users")
                                .document(uid)
                                .get()
                                .addOnSuccessListener(document -> {

                                    if (!document.exists()) {
                                        showLoading(false);
                                        Toast.makeText(this,"User data not found",Toast.LENGTH_LONG).show();
                                        return;
                                    }

                                    String role = document.getString("role");
                                    String status = document.getString("status");
                                    institutionId = document.getString("institutionId");

                                    if ("BLOCKED".equals(status)) {

                                        FirebaseAuth.getInstance().signOut();
                                        showLoading(false);

                                        Toast.makeText(this,
                                                "Your account has been blocked by admin.",
                                                Toast.LENGTH_LONG).show();
                                        return;
                                    }

                                    saveFCMToken(uid);

                                    if ("admin".equals(role)) {

                                        Intent intent = new Intent(this, AdminDashboardActivity.class);
                                        intent.putExtra("institutionId", institutionId);
                                        startActivity(intent);

                                    } else {

                                        Intent intent = new Intent(this, MainActivity.class);
                                        intent.putExtra("institutionId", institutionId);
                                        startActivity(intent);
                                    }

                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    showLoading(false);
                                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });

                    } else {
                        showLoading(false);
                        Toast.makeText(this,
                                "Login Failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showLoading(boolean isLoading) {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        if (loginButton != null) {
            loginButton.setEnabled(!isLoading);
            loginButton.setAlpha(isLoading ? 0.5f : 1.0f);
        }
    }

    private void saveFCMToken(String userId) {

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()) return;

                    String token = task.getResult();

                    db.collection("users")
                            .document(userId)
                            .update("fcmToken", token);
                });
    }
}
