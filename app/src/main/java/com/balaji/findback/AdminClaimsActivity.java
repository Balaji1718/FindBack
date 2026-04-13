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

import java.util.ArrayList;
import java.util.List;

public class AdminClaimsActivity extends BaseActivity {

    RecyclerView recyclerView;
    TextView emptyText;
    ProgressBar loadingProgress;
    ClaimAdapter adapter;
    List<Claim> claimList;
    FirebaseFirestore db;
    String institutionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_claims);

        // Initialize Toolbar with back arrow
        setupToolbar("", true);

        institutionId = getIntent().getStringExtra("institutionId");

        recyclerView = findViewById(R.id.adminClaimsRecycler);
        emptyText = findViewById(R.id.emptyText);
        loadingProgress = findViewById(R.id.loadingProgress);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        claimList = new ArrayList<>();
        // 🔥 Admin has full control
        adapter = new ClaimAdapter(this, claimList, true);
        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        loadClaims();
    }

    private void loadClaims() {
        if (institutionId == null) return;

        loadingProgress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        db.collection("claims")
                .whereEqualTo("institutionId", institutionId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    loadingProgress.setVisibility(View.GONE);
                    claimList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Claim claim = doc.toObject(Claim.class);
                        if (claim != null) {
                            claim.setId(doc.getId()); // Ensure ID is set
                            claimList.add(claim);
                        }
                    }

                    if (claimList.isEmpty()) {
                        emptyText.setVisibility(View.VISIBLE);
                        emptyText.setText("No claims requested yet.");
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        emptyText.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                    }

                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    loadingProgress.setVisibility(View.GONE);
                    Log.e("ADMIN_CLAIMS", "Error loading claims: " + e.getMessage());
                    emptyText.setVisibility(View.VISIBLE);
                    emptyText.setText("Failed to load claims");
                    recyclerView.setVisibility(View.GONE);
                });
    }
}
