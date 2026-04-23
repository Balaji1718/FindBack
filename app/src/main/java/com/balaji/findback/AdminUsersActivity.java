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

public class AdminUsersActivity extends BaseActivity {

    FirebaseFirestore db;
    String institutionId;
    TextView userCount, emptyText;
    RecyclerView recyclerView;
    ProgressBar loadingProgress;
    List<UserModel> userList;
    UserAdapter adapter;
    ListenerRegistration userListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_users);

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

        startListeningUsers();
    }

    private void startListeningUsers() {
        if (institutionId == null) {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText("No institution ID provided");
            return;
        }

        loadingProgress.setVisibility(View.VISIBLE);
        
        // Remove role filter to show ALL users in the institution, 
        // including admins if they belong to this institutionId.
        // Also added real-time updates via addSnapshotListener
        userListener = db.collection("users")
                .whereEqualTo("institutionId", institutionId)
                .addSnapshotListener((value, error) -> {
                    loadingProgress.setVisibility(View.GONE);
                    
                    if (error != null) {
                        Log.e("ADMIN_USERS", "Listen failed: " + error.getMessage());
                        return;
                    }

                    userList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value) {
                            UserModel user = doc.toObject(UserModel.class);
                            if (user != null) {
                                user.setUid(doc.getId());
                                userList.add(user);
                            }
                        }
                    }

                    updateUI();
                });
    }

    private void updateUI() {
        int count = userList.size();
        userCount.setText("Total Users: " + count);

        if (userList.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText("No users found for this institution");
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null) {
            userListener.remove();
        }
    }
}
