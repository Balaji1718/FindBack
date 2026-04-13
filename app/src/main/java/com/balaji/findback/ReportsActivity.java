package com.balaji.findback;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class ReportsActivity extends BaseActivity {

    private RecyclerView recyclerReports;
    private ReportAdapter adapter;
    private List<DocumentSnapshot> reportList;
    private FirebaseFirestore db;
    private String institutionId;
    
    private ProgressBar loadingProgress;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        setupToolbar("Pending Reports", true);
        setupAIButton();

        institutionId = getIntent().getStringExtra("institutionId");
        db = FirebaseFirestore.getInstance();
        reportList = new ArrayList<>();

        recyclerReports = findViewById(R.id.recyclerReports);
        loadingProgress = findViewById(R.id.loadingProgress);
        emptyText = findViewById(R.id.emptyText);
        
        recyclerReports.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new ReportAdapter();
        recyclerReports.setAdapter(adapter);

        loadReports();
    }

    private void loadReports() {
        if (institutionId == null) return;

        // Prompt 4: Show loading before fetch
        loadingProgress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        db.collection("reports")
                .whereEqualTo("institutionId", institutionId)
                .whereEqualTo("status", "pending")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener((value, error) -> {
                    // Prompt 4: Hide after success/failure
                    loadingProgress.setVisibility(View.GONE);
                    
                    if (error != null) {
                        Log.e("REPORT_UPDATE", "Error loading reports: " + error.getMessage());
                        if (reportList.isEmpty()) {
                            emptyText.setVisibility(View.VISIBLE);
                            emptyText.setText("No data available");
                        }
                        return;
                    }

                    if (value != null) {
                        reportList.clear();
                        if (!value.isEmpty()) {
                            reportList.addAll(value.getDocuments());
                            emptyText.setVisibility(View.GONE);
                        } else {
                            // Prompt 4: If no data -> show "No data available"
                            emptyText.setVisibility(View.VISIBLE);
                            emptyText.setText("No data available");
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.report_row, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = reportList.get(position);
            String reportId = doc.getId();
            String itemId = doc.getString("itemId");
            String title = doc.getString("itemTitle");
            String category = doc.getString("category");
            String message = doc.getString("message");
            
            String reporterId = doc.getString("reportedByUserId");
            String ownerId = doc.getString("itemOwnerId");

            holder.tvTitle.setText(title != null ? title : "Unknown Item");
            holder.tvCategory.setText("Category: " + category);
            holder.tvMessage.setText(message);
            
            // Safe Fetch for Reporter Name
            if (reporterId != null) {
                db.collection("users").document(reporterId).get()
                    .addOnSuccessListener(userDoc -> {
                        String name = userDoc.exists() ? userDoc.getString("name") : "Unknown User";
                        holder.tvReporter.setText("Reported by: " + name);
                    })
                    .addOnFailureListener(e -> holder.tvReporter.setText("Reported by: Unknown"));
            } else {
                holder.tvReporter.setText("Reported by: Unknown");
            }

            // Safe Fetch for Item Owner Name
            if (ownerId != null) {
                db.collection("users").document(ownerId).get()
                    .addOnSuccessListener(userDoc -> {
                        String name = userDoc.exists() ? userDoc.getString("name") : "Unknown User";
                        holder.tvOwner.setText("Item Owner: " + name);
                    })
                    .addOnFailureListener(e -> holder.tvOwner.setText("Item Owner: Unknown"));
            } else {
                holder.tvOwner.setText("Item Owner: Unknown");
            }

            holder.btnReadMore.setOnClickListener(v -> {
                new AlertDialog.Builder(ReportsActivity.this)
                        .setTitle("Report Details")
                        .setMessage(message != null ? message : "No message provided")
                        .setPositiveButton("Close", null)
                        .show();
            });

            holder.btnIgnore.setOnClickListener(v -> {
                db.collection("reports").document(reportId)
                        .update("status", "reviewed")
                        .addOnSuccessListener(unused -> Toast.makeText(ReportsActivity.this, "Report ignored", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Log.e("REPORT_UPDATE", e.getMessage()));
            });

            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(ReportsActivity.this)
                        .setTitle("Delete Item")
                        .setMessage("Are you sure you want to delete this reported item? This will also close all related reports.")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            if (itemId != null) {
                                db.collection("items").document(itemId).delete()
                                        .addOnSuccessListener(unused -> {
                                            db.collection("reports")
                                                    .whereEqualTo("itemId", itemId)
                                                    .get()
                                                    .addOnSuccessListener(query -> {
                                                        for (DocumentSnapshot reportDoc : query.getDocuments()) {
                                                            reportDoc.getReference().update("status", "reviewed")
                                                                    .addOnFailureListener(e -> {
                                                                        Log.e("REPORT_UPDATE", e.getMessage());
                                                                    });
                                                        }
                                                        Toast.makeText(ReportsActivity.this, "Item deleted and related reports closed", Toast.LENGTH_SHORT).show();
                                                    });
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e("REPORT_UPDATE", "Delete failed: " + e.getMessage());
                                            Toast.makeText(ReportsActivity.this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        });
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return reportList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvCategory, tvMessage, btnReadMore, tvReporter, tvOwner;
            Button btnIgnore, btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvReportTitle);
                tvCategory = itemView.findViewById(R.id.tvReportCategory);
                tvMessage = itemView.findViewById(R.id.tvReportMessage);
                btnReadMore = itemView.findViewById(R.id.btnReadMore);
                btnIgnore = itemView.findViewById(R.id.btnIgnore);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                tvReporter = itemView.findViewById(R.id.tvReporter);
                tvOwner = itemView.findViewById(R.id.tvOwner);
            }
        }
    }
}