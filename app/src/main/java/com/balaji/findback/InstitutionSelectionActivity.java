package com.balaji.findback;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class InstitutionSelectionActivity extends BaseActivity {

    RecyclerView recyclerView;
    EditText searchBar;
    List<Institution> institutionList;
    List<Institution> filteredList;
    InstitutionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_institution_selection);

        setupToolbar("Institution Selection", false);

        recyclerView = findViewById(R.id.recyclerView);
        searchBar = findViewById(R.id.searchBar);

        institutionList = new ArrayList<>();
        filteredList = new ArrayList<>();
        adapter = new InstitutionAdapter(filteredList);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadInstitutions();

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        checkUserLogin();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    protected boolean shouldForceLightMode() {
        return false;
    }

    private void checkUserLogin() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            FirebaseFirestore.getInstance().collection("users")
                    .document(auth.getCurrentUser().getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String role = doc.getString("role");
                            String instId = doc.getString("institutionId");
                            
                            // Cache institutionId immediately to speed up navigation
                            SharedPreferences.Editor editor = getSharedPreferences("app", MODE_PRIVATE).edit();
                            editor.putString("institutionId", instId);
                            editor.apply();

                            Intent intent;
                            if ("admin".equals(role)) {
                                intent = new Intent(this, AdminDashboardActivity.class);
                            } else {
                                intent = new Intent(this, MainActivity.class);
                            }
                            intent.putExtra("institutionId", instId);
                            startActivity(intent);
                            finish();
                        }
                    });
        }
    }

    private void loadInstitutions() {
        FirebaseFirestore.getInstance().collection("institutions")
                .get()
                .addOnSuccessListener(query -> {
                    institutionList.clear();
                    for (QueryDocumentSnapshot doc : query) {
                        institutionList.add(new Institution(doc.getId(), doc.getString("name")));
                    }
                    filteredList.clear();
                    filteredList.addAll(institutionList);
                    adapter.notifyDataSetChanged();
                });
    }

    private void filter(String text) {
        filteredList.clear();
        for (Institution item : institutionList) {
            if (item.name.toLowerCase().contains(text.toLowerCase())) {
                filteredList.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    class Institution {
        String id, name;
        Institution(String id, String name) { this.id = id; this.name = name; }
    }

    class InstitutionAdapter extends RecyclerView.Adapter<InstitutionAdapter.ViewHolder> {
        List<Institution> list;
        InstitutionAdapter(List<Institution> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_institution, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Institution item = list.get(position);
            holder.name.setText(item.name);
            holder.itemView.setOnClickListener(v -> {
                SharedPreferences.Editor editor = getSharedPreferences("app", MODE_PRIVATE).edit();
                editor.putString("institutionId", item.id);
                editor.putString("institutionName", item.name);
                editor.apply();

                Intent intent = new Intent(InstitutionSelectionActivity.this, LoginActivity.class);
                intent.putExtra("institutionId", item.id);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            ViewHolder(View v) { super(v); name = v.findViewById(R.id.institutionName); }
        }
    }
}
