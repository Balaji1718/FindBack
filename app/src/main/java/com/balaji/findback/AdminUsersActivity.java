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

public class AdminUsersActivity extends BaseActivity {

    FirebaseFirestore db;
    String institutionId;
    TextView userCount, emptyText;
    RecyclerView recyclerView;
    ProgressBar loadingProgress;
    List<UserModel> userList;
    UserAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_users);

        // Initialize Toolbar with back arrow and empty title to match screenshot
        setupToolbar("", true);

        institutionId = getIntent().getStringExtra("institutionId");

        userCount = findViewById(R.id.userCount);
        emptyText = findViewById(R.id.emptyText);
        recyclerView = findViewById(R.id.adminUsersRecycler);
        loadingProgress = findViewById(R.id.loadingProgress);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        userList = new ArrayList<>();
        adapter = new UserAdapter(userList);
        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();

        loadUsers();
    }

    private void loadUsers(){
        if (institutionId == null) return;

        loadingProgress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        // Prompt 1: Replace Firestore query to hide admin users
        db.collection("users")
                .whereEqualTo("role", "user")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    loadingProgress.setVisibility(View.GONE);

                    userList.clear();

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        UserModel user = doc.toObject(UserModel.class);
                        if (user != null) {
                            String role = doc.getString("role");
                            
                            // Safety: If role is null -> treat as "user"
                            if (role == null) role = "user";
                            
                            // Double check role and filter by institutionId in Java
                            if ("user".equals(role)) {
                                String userInstId = doc.getString("institutionId");
                                if (institutionId.equals(userInstId)) {
                                    user.setUid(doc.getId());
                                    userList.add(user);
                                }
                            }
                        }
                    }

                    int count = userList.size();
                    userCount.setText("Total Users: " + count);

                    if (userList.isEmpty()) {
                        emptyText.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        emptyText.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                    }

                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    loadingProgress.setVisibility(View.GONE);
                    Log.e("ADMIN_USERS", "Error loading users: " + e.getMessage());
                    emptyText.setVisibility(View.VISIBLE);
                    emptyText.setText("Failed to load users");
                });
    }
}
