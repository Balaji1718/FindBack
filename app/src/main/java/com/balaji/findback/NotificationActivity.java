package com.balaji.findback;

import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class NotificationActivity extends BaseActivity {

    RecyclerView recyclerView;
    NotificationAdapter adapter;

    List<NotificationModel> notificationList;

    FirebaseFirestore db;
    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        setupToolbar("Notifications", true);
        setupAIButton();

        recyclerView = findViewById(R.id.notificationRecycler);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        notificationList = new ArrayList<>();

        adapter = new NotificationAdapter(notificationList);

        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        loadNotifications();
    }

    private void loadNotifications(){

        String userId = auth.getCurrentUser().getUid();

        db.collection("notifications")
                .whereEqualTo("userId",userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    notificationList.clear();

                    for(DocumentSnapshot doc : queryDocumentSnapshots){

                        String title = doc.getString("title");
                        String message = doc.getString("message");

                        notificationList.add(new NotificationModel(title,message));
                    }

                    adapter.notifyDataSetChanged();
                });
    }
}