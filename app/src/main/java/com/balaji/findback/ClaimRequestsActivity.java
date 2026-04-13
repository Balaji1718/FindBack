package com.balaji.findback;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class ClaimRequestsActivity extends BaseActivity {

    private static final String TAG = "ClaimRequestsActivity";
    RecyclerView recyclerClaims;
    TextView emptyText;
    ProgressBar loadingProgress;
    BottomNavigationView bottomNavigation;

    FirebaseFirestore db;
    FirebaseAuth auth;

    List<Claim> claimList;
    ClaimAdapter adapter;

    ListenerRegistration claimsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_claim_requests);

        setupToolbar("Claim Requests", true);

        recyclerClaims = findViewById(R.id.recyclerClaims);
        emptyText = findViewById(R.id.emptyText);
        loadingProgress = findViewById(R.id.loadingProgress);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        claimList = new ArrayList<>();

        // OWNER MODE
        adapter = new ClaimAdapter(this, claimList, true);

        recyclerClaims.setLayoutManager(new LinearLayoutManager(this));
        recyclerClaims.setAdapter(adapter);

        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (bottomNavigation != null) {
            bottomNavigation.getMenu().findItem(R.id.nav_claims).setChecked(true);
        }
        
        // Start listener in onResume to ensure it's active when screen is visible
        startClaimsListener();
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
                        finish();

                    } else {
                        // Already here
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

    private void startClaimsListener() {

        if (auth.getCurrentUser() == null) return;

        String currentUser = auth.getCurrentUser().getUid();

        if (claimsListener != null) {
            claimsListener.remove();
        }

        loadingProgress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        claimsListener = db.collection("claims")
                .whereEqualTo("ownerId", currentUser)
                .addSnapshotListener((value, error) -> {
                    loadingProgress.setVisibility(View.GONE);

                    if (error != null) {
                        Log.e(TAG, "Listen failed: " + error.getMessage());
                        emptyText.setVisibility(View.VISIBLE);
                        emptyText.setText("Failed to load claims");
                        return;
                    }

                    if (value != null) {

                        claimList.clear();

                        for (DocumentSnapshot doc : value.getDocuments()) {

                            Claim claim = doc.toObject(Claim.class);

                            if (claim != null) {

                                claim.setId(doc.getId());

                                // Robust Case-Insensitive Filtering
                                String status = claim.getStatus();
                                if (status != null && 
                                    !status.equalsIgnoreCase("rejected") && 
                                    !status.equalsIgnoreCase("returned")) {
                                    
                                    claimList.add(claim);
                                }
                            }
                        }

                        adapter.notifyDataSetChanged();

                        if (claimList.isEmpty()) {
                            emptyText.setVisibility(View.VISIBLE);
                            emptyText.setText("No claims available");
                        } else {
                            emptyText.setVisibility(View.GONE);
                        }
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop listener when activity is not visible to save resources
        if (claimsListener != null) {
            claimsListener.remove();
            claimsListener = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (claimsListener != null) {
            claimsListener.remove();
        }
    }
}
