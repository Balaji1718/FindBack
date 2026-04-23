package com.balaji.findback;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {

    private List<UserModel> userList;

    public UserAdapter(List<UserModel> userList) {
        this.userList = userList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.user_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserModel user = userList.get(position);

        holder.userName.setText(user.getName() != null ? user.getName() : "Unknown User");
        holder.userEmail.setText(user.getEmail());

        String status = user.getStatus() != null ? user.getStatus() : "ACTIVE";

        holder.userRoleStatus.setText(
                "Role: " + user.getRole() + " | Status: " + status
        );

        holder.userOptions.setOnClickListener(v -> showOptionsDialog(v, user, position));
    }

    private void showOptionsDialog(View v, UserModel user, int position) {
        String[] options = {
                "Edit User Info",
                "BLOCKED".equals(user.getStatus()) ? "Unblock User" : "Block User",
                "Delete User"
        };

        new AlertDialog.Builder(v.getContext())
                .setTitle("Manage User")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showEditUserDialog(v, user, position);
                    else if (which == 1) toggleBlockStatus(v, user, position);
                    else if (which == 2) deleteUser(v, user, position);
                })
                .show();
    }

    private void showEditUserDialog(View v, UserModel user, int position) {
        View dialogView = LayoutInflater.from(v.getContext()).inflate(R.layout.dialog_edit_user, null);
        EditText etName = dialogView.findViewById(R.id.etName);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        EditText etContact = dialogView.findViewById(R.id.etContact);
        Spinner spinnerRole = dialogView.findViewById(R.id.spinnerRole);

        // Pre-fill data
        etName.setText(user.getName());
        etEmail.setText(user.getEmail());
        etContact.setText(user.getContactInfo());

        String[] roles = {"user", "admin"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(v.getContext(), android.R.layout.simple_spinner_dropdown_item, roles);
        spinnerRole.setAdapter(adapter);
        
        int rolePos = "admin".equals(user.getRole()) ? 1 : 0;
        spinnerRole.setSelection(rolePos);

        new AlertDialog.Builder(v.getContext())
                .setView(dialogView)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newName = etName.getText().toString().trim();
                    String newEmail = etEmail.getText().toString().trim();
                    String newPassword = etPassword.getText().toString().trim();
                    String newContact = etContact.getText().toString().trim();
                    String newRole = spinnerRole.getSelectedItem().toString();

                    if (newName.isEmpty() || newEmail.isEmpty()) {
                        Toast.makeText(v.getContext(), "Name and Email are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("name", newName);
                    updates.put("email", newEmail);
                    updates.put("contactInfo", newContact);
                    updates.put("role", newRole);
                    if (!newPassword.isEmpty()) {
                        updates.put("password", newPassword);
                    }

                    FirebaseFirestore.getInstance().collection("users")
                            .document(user.getUid())
                            .update(updates)
                            .addOnSuccessListener(aVoid -> {
                                user.setName(newName);
                                user.setEmail(newEmail);
                                user.setContactInfo(newContact);
                                user.setRole(newRole);
                                if (!newPassword.isEmpty()) user.setPassword(newPassword);
                                
                                notifyItemChanged(position);
                                Toast.makeText(v.getContext(), "User updated successfully", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> Toast.makeText(v.getContext(), "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleBlockStatus(View v, UserModel user, int position) {
        String newStatus = "BLOCKED".equals(user.getStatus()) ? "ACTIVE" : "BLOCKED";
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .update("status", newStatus)
                .addOnSuccessListener(aVoid -> {
                    user.setStatus(newStatus);
                    notifyItemChanged(position);
                    Toast.makeText(v.getContext(), "User " + newStatus, Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteUser(View v, UserModel user, int position) {
        new AlertDialog.Builder(v.getContext())
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete this user?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(user.getUid())
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                if(position >= 0 && position < userList.size()){
                                    userList.remove(position);
                                    notifyItemRemoved(position);
                                    notifyItemRangeChanged(position,userList.size());
                                }
                                Toast.makeText(v.getContext(), "User Deleted", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView userName, userEmail, userRoleStatus;
        ImageButton userOptions;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            userName = itemView.findViewById(R.id.userName);
            userEmail = itemView.findViewById(R.id.userEmail);
            userRoleStatus = itemView.findViewById(R.id.userRoleStatus);
            userOptions = itemView.findViewById(R.id.userOptions);
        }
    }
}
