package com.balaji.findback;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class AdminClaimsActivity extends BaseActivity {

    private static final String TAG = "ADMIN_CLAIMS";
    RecyclerView recyclerView;
    TextView emptyText;
    ProgressBar loadingProgress;
    ClaimAdapter adapter;
    List<Claim> claimList;
    FirebaseFirestore db;
    String institutionId;
    ListenerRegistration claimsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_claims);

        setupToolbar("Institution Claims", true);

        institutionId = getIntent().getStringExtra("institutionId");

        recyclerView = findViewById(R.id.adminClaimsRecycler);
        emptyText = findViewById(R.id.emptyText);
        loadingProgress = findViewById(R.id.loadingProgress);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        claimList = new ArrayList<>();
        adapter = new ClaimAdapter(this, claimList, true);
        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        startListeningClaims();
    }

    private void startListeningClaims() {
        if (institutionId == null) return;

        showLoading();

        // Optimized real-time listener
        claimsListener = db.collection("claims")
                .whereEqualTo("institutionId", institutionId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed: " + error.getMessage());
                        // If it's an index error, don't show the technical message to the user
                        if (error.getMessage() != null && error.getMessage().contains("index")) {
                            showError("Setting up database indexing. Please wait a moment...");
                        } else {
                            showError("Failed to load claims. Please try again.");
                        }
                        return;
                    }

                    claimList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value) {
                            Claim claim = doc.toObject(Claim.class);
                            if (claim != null) {
                                claim.setId(doc.getId());
                                claimList.add(claim);
                            }
                        }
                    }

                    if (claimList.isEmpty()) {
                        showEmpty("No claims available for this institution.");
                    } else {
                        showData();
                    }

                    adapter.notifyDataSetChanged();
                });
    }

    private void showLoading() {
        loadingProgress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
    }

    private void showData() {
        loadingProgress.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        emptyText.setText(""); // Wipe technical error messages
        recyclerView.setVisibility(View.VISIBLE);
    }

    private void showEmpty(String message) {
        loadingProgress.setVisibility(View.GONE);
        emptyText.setText(message);
        emptyText.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }

    private void showError(String message) {
        loadingProgress.setVisibility(View.GONE);
        // Only show error text if list is empty to prevent peeking behind cards
        if (claimList.isEmpty()) {
            emptyText.setText(message);
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
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
