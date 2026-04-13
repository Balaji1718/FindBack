package com.balaji.findback;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.balaji.findback.utils.ThemeManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends BaseActivity implements ItemAdapter.OnItemActionListener {

    private static final String TAG = "MainActivity";

    TextView welcomeText, institutionText, emptyText;

    RecyclerView recyclerViewItems;
    ItemAdapter itemAdapter;
    List<Item> itemList;

    SearchView searchView;
    ProgressBar loadingProgress;
    BottomNavigationView bottomNavigation;

    Button filterAll, filterLost, filterFound, filterClaimed, filterReturned;
    ImageView ivCheckLost, ivCheckFound, ivCheckClaimed, ivCheckReturned;
    
    Button btnOthers, btnMine;
    boolean showMine = false;

    FirebaseFirestore db;
    FirebaseAuth auth;

    String institutionId;
    ListenerRegistration itemsListener;

    private Set<String> selectedStatuses = new HashSet<>();
    private Set<String> selectedTypes = new HashSet<>();

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "Notification permission granted");
                } else {
                    Log.w(TAG, "Notification permission denied");
                    Toast.makeText(this, "Enable notifications in settings to receive updates", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupToolbar("Campus Lost & Found", false);
        setupAIButton();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });

        welcomeText = findViewById(R.id.welcomeText);
        institutionText = findViewById(R.id.institutionText);
        emptyText = findViewById(R.id.emptyText);

        recyclerViewItems = findViewById(R.id.recyclerViewItems);
        searchView = findViewById(R.id.searchView);
        loadingProgress = findViewById(R.id.loadingProgress);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        filterAll = findViewById(R.id.filterAll);
        filterLost = findViewById(R.id.filterLost);
        filterFound = findViewById(R.id.filterFound);
        filterClaimed = findViewById(R.id.filterClaimed);
        filterReturned = findViewById(R.id.filterReturned);

        ivCheckLost = findViewById(R.id.ivCheckLost);
        ivCheckFound = findViewById(R.id.ivCheckFound);
        ivCheckClaimed = findViewById(R.id.ivCheckClaimed);
        ivCheckReturned = findViewById(R.id.ivCheckReturned);
        
        btnOthers = findViewById(R.id.btnOthers);
        btnMine = findViewById(R.id.btnMine);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        itemList = new ArrayList<>();
        itemAdapter = new ItemAdapter(this, itemList);
        itemAdapter.setOnItemActionListener(this);

        recyclerViewItems.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewItems.setHasFixedSize(true);
        recyclerViewItems.setAdapter(itemAdapter);

        setupFilters();
        setupTabs();
        loadUserInfo();
        setupSearch();
        setupBottomNavigation();
        askNotificationPermission();

        SharedPreferences prefs = getSharedPreferences("migration", MODE_PRIVATE);
        boolean isDone = prefs.getBoolean("items_migrated", false);
        if (!isDone) {
            runItemMigration();
            prefs.edit().putBoolean("items_migrated", true).apply();
        }

        FirebaseMessaging.getInstance().subscribeToTopic("test")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FCM", "Subscribed to topic");
                    }
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    private void setupTabs() {
        updateTabsUI();
        btnOthers.setOnClickListener(v -> {
            showMine = false;
            updateTabsUI();
            startItemsListener();
        });
        btnMine.setOnClickListener(v -> {
            showMine = true;
            updateTabsUI();
            startItemsListener();
        });
    }

    private void updateTabsUI() {
        int activeColor = Color.parseColor("#4CAF50");
        int inactiveColor = Color.TRANSPARENT;
        
        // Fix: Make inactive text color theme-aware (White in Dark mode, Black in Light mode)
        int currentTheme = ThemeManager.getSavedTheme(this);
        int inactiveTextColor = (currentTheme == ThemeManager.DARK) ? Color.WHITE : Color.BLACK;
        int activeTextColor = Color.WHITE;

        btnOthers.setBackgroundColor(showMine ? inactiveColor : activeColor);
        btnOthers.setTextColor(showMine ? inactiveTextColor : activeTextColor);
        
        btnMine.setBackgroundColor(showMine ? activeColor : inactiveColor);
        btnMine.setTextColor(showMine ? activeTextColor : inactiveTextColor);
    }

    private void runItemMigration() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("items")
            .get()
            .addOnSuccessListener(query -> {
                for (DocumentSnapshot doc : query.getDocuments()) {
                    Map<String, Object> updates = new HashMap<>();
                    if (!doc.contains("reportCount")) updates.put("reportCount", 0);
                    if (!doc.contains("hidden")) updates.put("hidden", false);
                    if (!doc.contains("flagged")) updates.put("flagged", false);
                    if (!updates.isEmpty()) doc.getReference().update(updates);
                }
            });
    }

    @Override
    public void onReportClick(Item item) {
        showReportDialog(item);
    }

    private void showReportDialog(Item item) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_report, null);
        Spinner spinner = view.findViewById(R.id.spinnerCategory);
        EditText etMessage = view.findViewById(R.id.etMessage);
        String[] categories = {"Spam", "Fake Item", "Inappropriate", "Other"};
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Report Item")
                .setView(view)
                .setPositiveButton("Submit", null)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String category = spinner.getSelectedItem().toString();
            String message = etMessage.getText().toString().trim();
            if (message.isEmpty()) {
                etMessage.setError("Message required");
                return;
            }
            submitReport(item, category, message, dialog);
        });
    }

    private void submitReport(Item item, String category, String message, AlertDialog dialog) {
        String userId = auth.getUid();
        if (userId == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("itemId", item.getId());
        data.put("itemTitle", item.getTitle());
        data.put("itemOwnerId", item.getPostedBy());
        data.put("reportedByUserId", userId);
        data.put("category", category);
        data.put("message", message);
        data.put("timestamp", FieldValue.serverTimestamp());
        data.put("status", "pending");
        data.put("institutionId", item.getInstitutionId());

        db.collection("reports").add(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Report submitted", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void setupFilters() {
        filterAll.setOnClickListener(v -> {
            selectedTypes.clear();
            selectedStatuses.clear();
            updateFilterUI();
            startItemsListener();
        });
        filterLost.setOnClickListener(v -> toggleFilter(selectedTypes, "LOST"));
        filterFound.setOnClickListener(v -> toggleFilter(selectedTypes, "FOUND"));
        filterClaimed.setOnClickListener(v -> toggleFilter(selectedStatuses, "CLAIMED"));
        filterReturned.setOnClickListener(v -> toggleFilter(selectedStatuses, "RETURNED"));
        updateFilterUI();
    }

    private void toggleFilter(Set<String> set, String value) {
        if (set.contains(value)) set.remove(value);
        else set.add(value);
        updateFilterUI();
        startItemsListener();
    }

    private void updateFilterUI() {
        int defaultColor = Color.parseColor("#E0E0E0");
        int activeColor = Color.parseColor("#4CAF50");
        boolean isAll = selectedTypes.isEmpty() && selectedStatuses.isEmpty();

        filterAll.setBackgroundColor(isAll ? activeColor : defaultColor);
        filterAll.setTextColor(isAll ? Color.WHITE : Color.BLACK);

        updateBtnUI(filterLost, ivCheckLost, selectedTypes.contains("LOST"), activeColor, defaultColor);
        updateBtnUI(filterFound, ivCheckFound, selectedTypes.contains("FOUND"), activeColor, defaultColor);
        updateBtnUI(filterClaimed, ivCheckClaimed, selectedStatuses.contains("CLAIMED"), activeColor, defaultColor);
        updateBtnUI(filterReturned, ivCheckReturned, selectedStatuses.contains("RETURNED"), activeColor, defaultColor);
    }

    private void updateBtnUI(Button b, ImageView iv, boolean active, int ac, int dc) {
        b.setBackgroundColor(active ? ac : dc);
        b.setTextColor(active ? Color.WHITE : Color.BLACK);
        iv.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            if (id == R.id.nav_post) {
                startActivity(new Intent(this, PostItemActivity.class));
                return true;
            }
            if (id == R.id.nav_claims) {
                showClaimsOptionsDialog();
                return false;
            }
            if (id == R.id.nav_logout) {
                showLogoutConfirmation();
                return false;
            }
            return false;
        });
    }

    private void showClaimsOptionsDialog() {
        String[] options = {"My Claims", "Claim Requests (Admin/Owner)"};
        new AlertDialog.Builder(this)
                .setTitle("Claims Section")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) startActivity(new Intent(this, MyClaimActivity.class));
                    else startActivity(new Intent(this, ClaimRequestsActivity.class));
                }).show();
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this).setTitle("Logout").setMessage("Logout?")
                .setPositiveButton("Yes", (d, w) -> {
                    auth.signOut();
                    startActivity(new Intent(this, InstitutionSelectionActivity.class));
                    finish();
                }).setNegativeButton("No", null).show();
    }

    private void loadUserInfo() {
        String uid = auth.getUid();
        if (uid == null) return;
        loadingProgress.setVisibility(View.VISIBLE);
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        welcomeText.setText("Welcome, " + doc.getString("name"));
                        institutionId = doc.getString("institutionId");
                        institutionText.setText("Institution: " + institutionId);
                        startItemsListener();
                    }
                });
    }

    private void startItemsListener() {
        if (institutionId == null) {
            Log.e(TAG, "Cannot start items listener: institutionId is null");
            return;
        }
        if (itemsListener != null) itemsListener.remove();

        loadingProgress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        Log.d(TAG, "Starting items listener for: " + institutionId);

        // Fetch items and filter/sort in Java to avoid index requirements and Firestore limitations
        itemsListener = db.collection("items")
                .whereEqualTo("institutionId", institutionId)
                .addSnapshotListener((value, error) -> {
                    loadingProgress.setVisibility(View.GONE);
                    if (error != null) {
                        Log.e(TAG, "Error: " + error.getMessage());
                        return;
                    }

                    String currentUid = auth.getUid();
                    List<Item> newList = new ArrayList<>();

                    if (value != null) {
                        Log.d(TAG, "Fetched " + value.size() + " items from Firestore");
                        for (QueryDocumentSnapshot doc : value) {
                            Item item = doc.toObject(Item.class);
                            if (item == null) continue;
                            item.setId(doc.getId());

                            // 1. Hidden check (Java side to avoid query mismatch)
                            if (item.isHidden()) continue;

                            // 2. Others / Mine Filter
                            if (showMine) {
                                if (currentUid == null || !currentUid.equals(item.getPostedBy())) continue;
                            } else {
                                if (currentUid != null && currentUid.equals(item.getPostedBy())) continue;
                            }

                            // 3. Type Filter
                            if (!selectedTypes.isEmpty()) {
                                String type = item.getType();
                                if (type == null || !selectedTypes.contains(type.toUpperCase())) continue;
                            }

                            // 4. Status Filter
                            if (!selectedStatuses.isEmpty()) {
                                String status = item.getStatus();
                                if (status == null || !selectedStatuses.contains(status.toUpperCase())) continue;
                            }

                            newList.add(item);
                        }
                    }

                    Log.d(TAG, "Items after filtering: " + newList.size());

                    // Sort by timestamp (Descending)
                    Collections.sort(newList, (a, b) -> {
                        if (a.getTimestamp() == null || b.getTimestamp() == null) return 0;
                        return b.getTimestamp().compareTo(a.getTimestamp());
                    });

                    itemAdapter.updateList(newList);
                    
                    // Re-apply search filter if search is active
                    String q = searchView.getQuery().toString();
                    if (!q.isEmpty()) {
                        itemAdapter.filter(q);
                    }

                    updateEmptyState();
                });
    }

    private void updateEmptyState() {
        if (itemAdapter.getItemCount() == 0) {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText("No data available");
        } else {
            emptyText.setVisibility(View.GONE);
        }
    }

    private void setupSearch() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String q) { itemAdapter.filter(q); return false; }
            @Override
            public boolean onQueryTextChange(String q) { itemAdapter.filter(q); return false; }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (itemsListener != null) itemsListener.remove();
    }
}