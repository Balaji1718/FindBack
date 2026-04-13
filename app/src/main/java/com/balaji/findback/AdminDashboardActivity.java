package com.balaji.findback;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class AdminDashboardActivity extends BaseActivity {

    String institutionId;

    TextView adminText;
    TextView tvTotalItems;
    TextView tvLost;
    TextView tvFound;
    TextView tvReturned;
    TextView tvClaims;
    TextView tvReportBadge;

    AnalyticsWheelView analyticsWheel;

    Button logoutButton;
    Button viewItemsBtn;
    Button viewClaimsBtn;
    Button viewUsersBtn;
    Button viewReportsBtn;

    FirebaseFirestore db;
    FirebaseAuth auth;

    ListenerRegistration itemsListener;
    ListenerRegistration claimsListener;
    ListenerRegistration reportsListener;

    int totalItems = 0;
    int lostItems = 0;
    int foundItems = 0;
    int returnedItems = 0;
    int claimsCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        setupToolbar("Admin Dashboard", false);
        setupAIButton();

        institutionId = getIntent().getStringExtra("institutionId");

        adminText = findViewById(R.id.adminText);
        tvTotalItems = findViewById(R.id.tvTotalItems);
        tvLost = findViewById(R.id.tvLost);
        tvFound = findViewById(R.id.tvFound);
        tvReturned = findViewById(R.id.tvReturned);
        tvClaims = findViewById(R.id.tvClaims);
        tvReportBadge = findViewById(R.id.tvReportBadge);

        analyticsWheel = findViewById(R.id.analyticsWheel);

        viewItemsBtn = findViewById(R.id.viewItemsBtn);
        viewClaimsBtn = findViewById(R.id.viewClaimsBtn);
        viewUsersBtn = findViewById(R.id.viewUsersBtn);
        viewReportsBtn = findViewById(R.id.viewReportsBtn);
        logoutButton = findViewById(R.id.logoutButton);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        loadAdminName();
        startRealtimeAnalytics();
        startReportsBadgeListener();

        viewItemsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminItemsActivity.class);
            intent.putExtra("institutionId", institutionId);
            startActivity(intent);
        });

        viewClaimsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminClaimsActivity.class);
            intent.putExtra("institutionId", institutionId);
            startActivity(intent);
        });

        viewUsersBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminUsersActivity.class);
            intent.putExtra("institutionId", institutionId);
            startActivity(intent);
        });

        viewReportsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReportsActivity.class);
            intent.putExtra("institutionId", institutionId);
            startActivity(intent);
        });

        logoutButton.setOnClickListener(v -> confirmLogout());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // action_theme is handled in BaseActivity.onOptionsItemSelected
        return super.onOptionsItemSelected(item);
    }

    private void loadAdminName() {

        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    String name = doc.getString("name");
                    if (name == null) name = "Admin";

                    adminText.setText(
                            "Welcome, " + name +
                                    "\nInstitution: " + institutionId
                    );
                });
    }

    private void startRealtimeAnalytics() {

        if (institutionId == null) return;

        // Listen ITEMS
        itemsListener = db.collection("items")
                .whereEqualTo("institutionId", institutionId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e("ADMIN_ITEMS", error.getMessage());
                        return;
                    }

                    if (snapshots == null) return;

                    totalItems = snapshots.size();
                    lostItems = 0;
                    foundItems = 0;
                    returnedItems = 0;

                    for (var doc : snapshots) {

                        String type = doc.getString("type");
                        String status = doc.getString("status");

                        if ("LOST".equalsIgnoreCase(type)) lostItems++;
                        if ("FOUND".equalsIgnoreCase(type)) foundItems++;
                        if ("RETURNED".equalsIgnoreCase(status)) returnedItems++;
                    }

                    updateDashboard();
                });

        // Listen CLAIMS
        claimsListener = db.collection("claims")
                .whereEqualTo("institutionId", institutionId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e("ADMIN_CLAIMS", error.getMessage());
                        return;
                    }

                    if (snapshots == null) return;

                    claimsCount = snapshots.size();

                    updateDashboard();
                });
    }

    private void startReportsBadgeListener() {
        if (institutionId == null) return;

        reportsListener = db.collection("reports")
                .whereEqualTo("institutionId", institutionId)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        Log.e("ADMIN_BADGE", error.getMessage());
                        return;
                    }

                    if (snap != null) {
                        int count = snap.size();
                        if (count > 0) {
                            tvReportBadge.setVisibility(View.VISIBLE);
                            tvReportBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                        } else {
                            tvReportBadge.setVisibility(View.GONE);
                        }
                    }
                });
    }

    private void updateDashboard() {

        int segments = lostItems + foundItems + claimsCount + returnedItems;

        if (segments == 0) return;

        float lostPercent = ((float) lostItems / segments) * 100;
        float foundPercent = ((float) foundItems / segments) * 100;
        float claimsPercent = ((float) claimsCount / segments) * 100;
        float returnedPercent = ((float) returnedItems / segments) * 100;

        tvTotalItems.setText(String.valueOf(totalItems));

        tvLost.setText("Lost Items : " + Math.round(lostPercent) + "%");
        tvFound.setText("Found Items : " + Math.round(foundPercent) + "%");
        tvClaims.setText("Claims : " + Math.round(claimsPercent) + "%");
        tvReturned.setText("Returned : " + Math.round(returnedPercent) + "%");

        analyticsWheel.setPercentages(
                lostPercent,
                foundPercent,
                claimsPercent,
                returnedPercent
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (itemsListener != null) itemsListener.remove();
        if (claimsListener != null) claimsListener.remove();
        if (reportsListener != null) reportsListener.remove();
    }

    private void confirmLogout() {

        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (d, w) -> {

                    auth.signOut();

                    Intent intent = new Intent(this, InstitutionSelectionActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
