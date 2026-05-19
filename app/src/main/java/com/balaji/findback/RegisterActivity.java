package com.balaji.findback;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends BaseActivity {

    EditText etName, etEmail, etPassword;
    Button btnRegister;
    ProgressBar progressBar;
    TextView txtInstitution;
    FirebaseAuth auth;
    FirebaseFirestore db;
    String institutionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        setupToolbar("Register", true);

        institutionId = getIntent().getStringExtra("institutionId");
        
        // Fetch institution name from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("app", MODE_PRIVATE);
        String institutionName = prefs.getString("institutionName", "Unknown");

        etName = findViewById(R.id.registerName);
        etEmail = findViewById(R.id.registerEmail);
        etPassword = findViewById(R.id.registerPassword);
        btnRegister = findViewById(R.id.registerButton);
        progressBar = findViewById(R.id.loadingProgress);
        
        // Use the common ID txtInstitution if available, otherwise find registerTitle 
        // to set the institution context. In activity_register.xml, we have registerTitle.
        TextView title = findViewById(R.id.registerTitle);
        if (title != null) {
            title.setText("Register for\n" + institutionName);
        }

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        btnRegister.setOnClickListener(v -> registerUser());

        // Ensure visibility
        if (title != null) title.setAlpha(1f);
        findViewById(R.id.registerNameLayout).setAlpha(1f);
        findViewById(R.id.registerNameLayout).setTranslationY(0f);
        findViewById(R.id.registerEmailLayout).setAlpha(1f);
        findViewById(R.id.registerEmailLayout).setTranslationY(0f);
        findViewById(R.id.registerPasswordLayout).setAlpha(1f);
        findViewById(R.id.registerPasswordLayout).setTranslationY(0f);
        findViewById(R.id.registerConfirmPasswordLayout).setAlpha(1f);
        findViewById(R.id.registerConfirmPasswordLayout).setTranslationY(0f);
        btnRegister.setAlpha(1f);
        btnRegister.setTranslationY(0f);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    protected boolean shouldForceLightMode() {
        return false;
    }

    private void registerUser() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    saveUserToFirestore(authResult.getUser().getUid(), name, email, password);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Registration Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveUserToFirestore(String uid, String name, String email, String password) {
        Map<String, Object> user = new HashMap<>();
        user.put("name", name);
        user.put("email", email);
        user.put("password", password);
        user.put("role", "user");
        user.put("institutionId", institutionId);
        user.put("status", "ACTIVE");

        db.collection("users").document(uid)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Registration Successful", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Data Save Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
