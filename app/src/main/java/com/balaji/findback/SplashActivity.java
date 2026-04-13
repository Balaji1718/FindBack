package com.balaji.findback;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SplashActivity extends AppCompatActivity {

    private String[] messages = {
            "Loading...",
            "Connecting...",
            "Almost ready..."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView text = findViewById(R.id.loadingText);

        // 🔁 Change text dynamically (Prompt 8)
        Handler handler = new Handler(Looper.getMainLooper());
        for (int i = 0; i < messages.length; i++) {
            int index = i;
            handler.postDelayed(() -> {
                if (text != null) {
                    text.setText(messages[index]);
                }
            }, i * 1000);
        }

        // Navigation logic (Prompt 8)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            if (user != null) {
                // If user logged in -> Route based on role (Safety addition)
                checkUserRoleAndNavigate(user.getUid());
            } else {
                // Else -> InstitutionSelectionActivity
                startActivity(new Intent(this, InstitutionSelectionActivity.class));
                finish();
            }
        }, 3000);
    }

    private void checkUserRoleAndNavigate(String uid) {
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String role = document.getString("role");
                        String institutionId = document.getString("institutionId");

                        Intent intent;
                        if ("admin".equals(role)) {
                            intent = new Intent(SplashActivity.this, AdminDashboardActivity.class);
                        } else {
                            intent = new Intent(SplashActivity.this, MainActivity.class);
                        }
                        
                        if (institutionId != null) {
                            intent.putExtra("institutionId", institutionId);
                        }
                        
                        startActivity(intent);
                        finish();
                    } else {
                        // If user record missing, go to selection
                        startActivity(new Intent(this, InstitutionSelectionActivity.class));
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    // On failure, go to selection
                    startActivity(new Intent(this, InstitutionSelectionActivity.class));
                    finish();
                });
    }
}
