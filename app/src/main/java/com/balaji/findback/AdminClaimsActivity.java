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

        setupToolbar("", true);

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

        loadingProgress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        // Real-time updates with SnapshotListener
        claimsListener = db.collection("claims")
                .whereEqualTo("institutionId", institutionId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    loadingProgress.setVisibility(View.GONE);
                    
                    if (error != null) {
                        Log.e("ADMIN_CLAIMS", "Listen failed: " + error.getMessage());
                        emptyText.setVisibility(View.VISIBLE);
                        emptyText.setText("Error: " + error.getMessage());
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
                        emptyText.setVisibility(View.VISIBLE);
                        emptyText.setText("No claims requested yet.");
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        emptyText.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                    }

                    adapter.notifyDataSetChanged();
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
