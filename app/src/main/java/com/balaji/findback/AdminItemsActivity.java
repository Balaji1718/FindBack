package com.balaji.findback;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_items);

        // Initialize Toolbar with back arrow
        setupToolbar("", true);

        institutionId = getIntent().getStringExtra("institutionId");

        recyclerView = findViewById(R.id.adminItemsRecycler);
        emptyText = findViewById(R.id.emptyText);
        loadingProgress = findViewById(R.id.loadingProgress);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        itemList = new ArrayList<>();
        // Pass true for isAdmin
        adapter = new ItemAdapter(this, itemList, true);

        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();

        loadItems();
    }

    private void loadItems(){
        if (institutionId == null) return;

        loadingProgress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        db.collection("items")
                .whereEqualTo("institutionId", institutionId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    loadingProgress.setVisibility(View.GONE);
                    itemList.clear();

                    for(var doc : queryDocumentSnapshots){
                        Item item = doc.toObject(Item.class);
                        if (item != null) {
                            item.setId(doc.getId());
                            itemList.add(item);
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
                })
                .addOnFailureListener(e -> {
                    loadingProgress.setVisibility(View.GONE);
                    Log.e("ADMIN_ITEMS", "Error loading items: " + e.getMessage());
                    emptyText.setVisibility(View.VISIBLE);
                    emptyText.setText("Failed to load items");
                    recyclerView.setVisibility(View.GONE);
                });
    }
}
