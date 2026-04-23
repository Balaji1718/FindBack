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

public class AdminItemsActivity extends BaseActivity {

    RecyclerView recyclerView;
    TextView emptyText;
    ProgressBar loadingProgress;
    ItemAdapter adapter;
    List<Item> itemList;
    FirebaseFirestore db;
    String institutionId;
    ListenerRegistration itemListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_items);

        setupToolbar("", true);

        institutionId = getIntent().getStringExtra("institutionId");

        recyclerView = findViewById(R.id.adminItemsRecycler);
        emptyText = findViewById(R.id.emptyText);
        loadingProgress = findViewById(R.id.loadingProgress);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        itemList = new ArrayList<>();
        adapter = new ItemAdapter(this, itemList, true);
        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();

        startListeningItems();
    }

    private void startListeningItems() {
        if (institutionId == null) return;

        loadingProgress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        // Real-time updates with SnapshotListener
        itemListener = db.collection("items")
                .whereEqualTo("institutionId", institutionId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    loadingProgress.setVisibility(View.GONE);
                    
                    if (error != null) {
                        Log.e("ADMIN_ITEMS", "Listen failed: " + error.getMessage());
                        emptyText.setVisibility(View.VISIBLE);
                        emptyText.setText("Error: " + error.getMessage());
                        return;
                    }

                    itemList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value) {
                            Item item = doc.toObject(Item.class);
                            if (item != null) {
                                item.setId(doc.getId());
                                itemList.add(item);
                            }
                        }
                    }

                    if (itemList.isEmpty()) {
                        emptyText.setVisibility(View.VISIBLE);
                        emptyText.setText("No items found for this institution.");
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
        if (itemListener != null) {
            itemListener.remove();
        }
    }
}
