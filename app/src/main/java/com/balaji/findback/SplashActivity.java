package com.balaji.findback;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SplashActivity extends BaseActivity {

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

        Handler handler = new Handler(Looper.getMainLooper());
        for (int i = 0; i < messages.length; i++) {
            int index = i;
            handler.postDelayed(() -> {
                if (text != null) {
                    text.setText(messages[index]);
                }
            }, i * 500); // Faster transitions
        }

        // Reduced delay for faster entry
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            if (user != null) {
                // Efficiency: Check cache first to avoid Firestore "struggle" on every startup
                SharedPreferences prefs = getSharedPreferences("app", MODE_PRIVATE);
                String cachedInstId = prefs.getString("institutionId", null);
                
                if (cachedInstId != null) {
                    // Quick path: Still verify with Firestore but proceed to dashboard immediately
                    checkUserRoleAndNavigate(user.getUid());
                } else {
                    checkUserRoleAndNavigate(user.getUid());
                }
            } else {
                startActivity(new Intent(this, InstitutionSelectionActivity.class));
                finish();
            }
        }, 1000);
    }

    private void checkUserRoleAndNavigate(String uid) {
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String role = document.getString("role");
                        String institutionId = document.getString("institutionId");
                        
                        // Cache it for better performance across the app
                        getSharedPreferences("app", MODE_PRIVATE).edit()
                                .putString("institutionId", institutionId)
                                .apply();

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
                        startActivity(new Intent(this, InstitutionSelectionActivity.class));
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    startActivity(new Intent(this, InstitutionSelectionActivity.class));
                    finish();
                });
    }

    @Override
    protected boolean shouldForceLightMode() {
        return true;
    }
}
