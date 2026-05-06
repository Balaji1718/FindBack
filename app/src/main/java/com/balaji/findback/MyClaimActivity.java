package com.balaji.findback;

import android.content.Intent;
import android.content.SharedPreferences;
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
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class MyClaimActivity extends BaseActivity {

    private static final String TAG = "MY_CLAIMS_DEBUG";
    RecyclerView recyclerClaims;
    TextView emptyText;
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
        setContentView(R.layout.activity_my_claim);

        setupToolbar("My Claims", true);
        setupAIButton();
        
        SharedPreferences appPrefs = getSharedPreferences("app", MODE_PRIVATE);
        institutionId = appPrefs.getString("institutionId", null);

        recyclerClaims = findViewById(R.id.recyclerClaims);
        emptyText = findViewById(R.id.emptyText);
        loadingProgress = findViewById(R.id.loadingProgress);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        claimList = new ArrayList<>();
        adapter = new ClaimAdapter(this, claimList, false);

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
        startClaimsListener();
    }

    private void startClaimsListener() {
        if (auth.getCurrentUser() == null || institutionId == null) {
            showError("User session lost. Please log in again.");
            return;
        }

        String currentUser = auth.getCurrentUser().getUid();

        if (claimsListener != null) claimsListener.remove();

        showLoading();

        // Check if an index is required for this query in Logcat (claimerId + institutionId + timestamp)
        claimsListener = db.collection("claims")
                .whereEqualTo("institutionId", institutionId)
                .whereEqualTo("claimerId", currentUser)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed: " + error.getMessage());
                        showError("Permission error or indexing in progress. Please check logcat for index link.");
                        return;
                    }

                    if (value != null) {
                        claimList.clear();
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Claim claim = doc.toObject(Claim.class);
                            if (claim != null) {
                                claim.setId(doc.getId());
                                claimList.add(claim);
                            }
                        }

                        adapter.notifyDataSetChanged();
                        
                        if (claimList.isEmpty()) {
                            showEmpty("You haven't made any claims yet.");
                        } else {
                            showData();
                        }
                    }
                });
    }

    private void showLoading() {
        loadingProgress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        recyclerClaims.setVisibility(View.GONE);
    }

    private void showData() {
        loadingProgress.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        emptyText.setText(""); 
        recyclerClaims.setVisibility(View.VISIBLE);
    }

    private void showEmpty(String message) {
        loadingProgress.setVisibility(View.GONE);
        emptyText.setText(message);
        emptyText.setVisibility(View.VISIBLE);
        recyclerClaims.setVisibility(View.GONE);
    }

    private void showError(String message) {
        loadingProgress.setVisibility(View.GONE);
        if (claimList.isEmpty()) {
            emptyText.setText(message);
            emptyText.setVisibility(View.VISIBLE);
            recyclerClaims.setVisibility(View.GONE);
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
                        // Current Screen
                    } else {
                        Intent intent = new Intent(this, ClaimRequestsActivity.class);
                        // Instant theme-aware transition
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        startActivity(intent);
                        overridePendingTransition(0, 0);
                        finish();
                    }
                }).show();
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this).setTitle("Logout").setMessage("Are you sure?")
                .setPositiveButton("Logout", (d, w) -> {
                    auth.signOut();
                    Intent intent = new Intent(this, InstitutionSelectionActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }).setNegativeButton("Cancel", null).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (claimsListener != null) {
            claimsListener.remove();
        }
    }
}
