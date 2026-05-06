package com.balaji.findback;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends BaseActivity {

    EditText etEmail, etPassword;
    Button btnLogin;
    TextView btnGoRegister, txtInstitution;
    ProgressBar progressBar;
    FirebaseAuth auth;
    String institutionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        setupToolbar("Login", true);

        institutionId = getIntent().getStringExtra("institutionId");
        
        // Fetch institution name from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("app", MODE_PRIVATE);
        String institutionName = prefs.getString("institutionName", "Unknown");

        etEmail = findViewById(R.id.emailEditText);
        etPassword = findViewById(R.id.passwordEditText);
        btnLogin = findViewById(R.id.loginButton);
        btnGoRegister = findViewById(R.id.goToRegister);
        txtInstitution = findViewById(R.id.txtInstitution);
        progressBar = findViewById(R.id.loadingProgress);

        if (txtInstitution != null) {
            txtInstitution.setText("Logging into: " + institutionName);
        }

        auth = FirebaseAuth.getInstance();

        btnLogin.setOnClickListener(v -> loginUser());
        btnGoRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            intent.putExtra("institutionId", institutionId);
            startActivity(intent);
        });

        // Ensure UI elements are visible
        findViewById(R.id.loginTitle).setAlpha(1f);
        if (txtInstitution != null) txtInstitution.setAlpha(1f);
        findViewById(R.id.emailInputLayout).setAlpha(1f);
        findViewById(R.id.emailInputLayout).setTranslationY(0f);
        findViewById(R.id.passwordInputLayout).setAlpha(1f);
        findViewById(R.id.passwordInputLayout).setTranslationY(0f);
        btnLogin.setAlpha(1f);
        btnLogin.setTranslationY(0f);
        btnGoRegister.setAlpha(1f);
    }

    @Override
    protected boolean shouldForceLightMode() {
        return true; 
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    checkUserRole(authResult.getUser().getUid());
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Login Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void checkUserRole(String uid) {
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    progressBar.setVisibility(View.GONE);
                    if (document.exists()) {
                        String role = document.getString("role");
                        String userInstId = document.getString("institutionId");

                        if (institutionId != null && !institutionId.equals(userInstId)) {
                            auth.signOut();
                            Toast.makeText(this, "Unauthorized: This account belongs to another institution", Toast.LENGTH_LONG).show();
                            return;
                        }

                        Intent intent;
                        if ("admin".equals(role)) {
                            intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
                        } else {
                            intent = new Intent(LoginActivity.this, MainActivity.class);
                        }
                        intent.putExtra("institutionId", userInstId);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                });
    }
}
