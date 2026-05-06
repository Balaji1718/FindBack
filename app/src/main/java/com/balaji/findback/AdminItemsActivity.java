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

        setupToolbar("Institution Items", true);

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

        showLoading();

        itemListener = db.collection("items")
                .whereEqualTo("institutionId", institutionId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("ADMIN_ITEMS", "Listen failed: " + error.getMessage());
                        showError("Failed to load items. Tap Retry.");
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
                        showEmpty("No items found for this institution.");
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
        emptyText.setText("");
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
        if (itemList.isEmpty()) {
            emptyText.setText(message);
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (itemListener != null) {
            itemListener.remove();
        }
    }
}
