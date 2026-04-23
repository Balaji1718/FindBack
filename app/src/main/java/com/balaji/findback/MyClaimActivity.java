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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MyClaimActivity extends BaseActivity {

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
        setContentView(R.layout.activity_my_claim);

        setupToolbar("My Claims", true);
        setupAIButton();

        recyclerClaims = findViewById(R.id.recyclerClaims);
        emptyText = findViewById(R.id.emptyText);
        loadingProgress = findViewById(R.id.loadingProgress);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        claimList = new ArrayList<>();

        // ⭐ CLAIMER MODE
        adapter = new ClaimAdapter(this, claimList, false);

        recyclerClaims.setLayoutManager(new LinearLayoutManager(this));
        recyclerClaims.setAdapter(adapter);

        setupBottomNavigation();
        startClaimsListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigation != null) {
            bottomNavigation.getMenu().findItem(R.id.nav_claims).setChecked(true);
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
            }
            else if (id == R.id.nav_post) {
                startActivity(new Intent(this, PostItemActivity.class));
                finish();
                return true;
            }
            else if (id == R.id.nav_claims) {
                showClaimsOptionsDialog();
                return false;
            }
            else if (id == R.id.nav_logout) {
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
                        // Already in My Claims
                    } else {
                        startActivity(new Intent(this, ClaimRequestsActivity.class));
                        finish();
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
                .whereEqualTo("claimerId", currentUser)
                .addSnapshotListener((value, error) -> {
                    loadingProgress.setVisibility(View.GONE);

                    if (error != null) {
                        Log.e("MY_CLAIMS", "Listen failed: " + error.getMessage());
                        emptyText.setText("Failed to load claims");
                        emptyText.setVisibility(View.VISIBLE);
                        return;
                    }

                    if (value != null) {
                        claimList.clear();

                        for (QueryDocumentSnapshot doc : value) {
                            Claim claim = doc.toObject(Claim.class);
                            if (claim != null) {
                                claim.setId(doc.getId());
                                claimList.add(claim);
                            }
                        }

                        adapter.notifyDataSetChanged();
                        
                        // ✅ FIX: Ensure correct visibility and reset text to avoid stale error messages
                        if (claimList.isEmpty()) {
                            emptyText.setText("No claims available");
                            emptyText.setVisibility(View.VISIBLE);
                        } else {
                            emptyText.setVisibility(View.GONE);
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (claimsListener != null) {
            claimsListener.remove();
        }
    }
}