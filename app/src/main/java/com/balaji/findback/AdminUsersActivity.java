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

    private static final String TAG = "ADMIN_USERS";
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

        setupToolbar("Institution Users", true);

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
            showEmpty("No institution ID provided");
            return;
        }

        showLoading();
        
        // Filter: Show all users EXCEPT those with the "admin" role
        userListener = db.collection("users")
                .whereEqualTo("institutionId", institutionId)
                .whereNotEqualTo("role", "admin")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed: " + error.getMessage());
                        showError("Failed to load users.");
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

                    if (userList.isEmpty()) {
                        showEmpty("No regular users found for this institution");
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
        userCount.setVisibility(View.GONE);
    }

    private void showData() {
        loadingProgress.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        emptyText.setText("");
        recyclerView.setVisibility(View.VISIBLE);
        userCount.setVisibility(View.VISIBLE);
        userCount.setText("Total Users: " + userList.size());
    }

    private void showEmpty(String message) {
        loadingProgress.setVisibility(View.GONE);
        emptyText.setText(message);
        emptyText.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        userCount.setVisibility(View.VISIBLE);
        userCount.setText("Total Users: 0");
    }

    private void showError(String message) {
        loadingProgress.setVisibility(View.GONE);
        if (userList.isEmpty()) {
            emptyText.setText(message);
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null) {
            userListener.remove();
        }
    }
}
