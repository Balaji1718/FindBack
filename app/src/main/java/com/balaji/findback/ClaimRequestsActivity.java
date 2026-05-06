package com.balaji.findback;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class ClaimRequestsActivity extends BaseActivity {

    private static final String TAG = "CLAIM_DEBUG";
    
    RecyclerView recyclerClaims;
    TextView emptyText;
    View errorContainer;
    MaterialButton btnRetry;
    ProgressBar loadingProgress;
    BottomNavigationView bottomNavigation;

    FirebaseFirestore db;
    FirebaseAuth auth;

    List<Claim> claimList;
    ClaimAdapter adapter;
    ListenerRegistration claimsListener;
    String institutionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_claim_requests);

        setupToolbar("Claim Requests", true);
        
        SharedPreferences prefs = getSharedPreferences("app", MODE_PRIVATE);
        institutionId = prefs.getString("institutionId", null);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        recyclerClaims = findViewById(R.id.recyclerClaims);
        emptyText = findViewById(R.id.emptyText);
        errorContainer = findViewById(R.id.errorContainer);
        btnRetry = findViewById(R.id.btnRetry);
        loadingProgress = findViewById(R.id.loadingProgress);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        recyclerClaims.setLayoutManager(new LinearLayoutManager(this));
        claimList = new ArrayList<>();
        adapter = new ClaimAdapter(this, claimList, true);
        recyclerClaims.setAdapter(adapter);

        btnRetry.setOnClickListener(v -> startClaimsListener());
        
        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigation != null) {
            bottomNavigation.getMenu().findItem(R.id.nav_claims).setChecked(true);
        }
        startClaimsListener();
    }

    private void startClaimsListener() {
        if (auth.getCurrentUser() == null || institutionId == null) {
            showError("Session error. Please log in again.");
            return;
        }

        String currentUserUid = auth.getCurrentUser().getUid();

        if (claimsListener != null) {
            claimsListener.remove();
        }

        // Only show loading if list is empty to prevent flicker
        if (claimList.isEmpty()) {
            showLoading();
        }

        claimsListener = db.collection("claims")
                .whereEqualTo("institutionId", institutionId)
                .whereEqualTo("ownerId", currentUserUid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Firestore Error: " + error.getMessage());
                        String msg = error.getMessage() != null && error.getMessage().contains("index") 
                            ? "Finalizing data index... Please wait." 
                            : "Syncing issue. Tap Retry.";
                        showError(msg);
                        return;
                    }

                    if (value != null) {
                        claimList.clear();
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Claim claim = doc.toObject(Claim.class);
                            if (claim != null) {
                                claim.setId(doc.getId());
                                String status = claim.getStatus();
                                if (status != null && !status.equalsIgnoreCase("rejected") && !status.equalsIgnoreCase("returned")) {
                                    claimList.add(claim);
                                }
                            }
                        }

                        adapter.notifyDataSetChanged();

                        if (claimList.isEmpty()) {
                            showEmpty("No active claim requests");
                        } else {
                            showData();
                        }
                    }
                });
    }

    private void showLoading() {
        loadingProgress.setVisibility(View.VISIBLE);
        errorContainer.setVisibility(View.GONE);
        recyclerClaims.setVisibility(View.GONE);
    }

    private void showData() {
        loadingProgress.setVisibility(View.GONE);
        errorContainer.setVisibility(View.GONE);
        emptyText.setText(""); 
        recyclerClaims.setVisibility(View.VISIBLE);
    }

    private void showEmpty(String message) {
        loadingProgress.setVisibility(View.GONE);
        errorContainer.setVisibility(View.VISIBLE);
        emptyText.setText(message);
        btnRetry.setVisibility(View.GONE);
        recyclerClaims.setVisibility(View.GONE);
    }

    private void showError(String message) {
        loadingProgress.setVisibility(View.GONE);
        
        // SENIOR FIX: If we ALREADY have data, don't show the error screen!
        // Just show a small toast so the user knows sync failed but can still see the items.
        if (claimList.isEmpty()) {
            errorContainer.setVisibility(View.VISIBLE);
            emptyText.setText(message);
            btnRetry.setVisibility(View.VISIBLE);
            recyclerClaims.setVisibility(View.GONE);
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            // Ensure data stays visible
            showData();
        }
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
                return true;
            } else if (id == R.id.nav_post) {
                startActivity(new Intent(this, PostItemActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_claims) {
                showClaimsOptionsDialog();
                return false;
            } else if (id == R.id.nav_logout) {
                auth.signOut();
                startActivity(new Intent(this, InstitutionSelectionActivity.class));
                finish();
                return true;
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
                        Intent intent = new Intent(this, MyClaimActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        startActivity(intent);
                        overridePendingTransition(0, 0);
                        finish();
                    }
                }).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (claimsListener != null) {
            claimsListener.remove();
        }
    }
}
