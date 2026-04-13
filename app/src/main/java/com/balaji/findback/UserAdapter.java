package com.balaji.findback;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

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
                "BLOCKED".equals(user.getStatus()) ? "Unblock User" : "Block User",
                "Promote to Admin",
                "Delete User"
        };

        new AlertDialog.Builder(v.getContext())
                .setTitle("Manage User")
                .setItems(options, (dialog, which) -> {

                    if (which == 0) toggleBlockStatus(v, user, position);

                    if (which == 1) promoteToAdmin(v, user, position);

                    if (which == 2) deleteUser(v, user, position);
                })
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

                    Toast.makeText(v.getContext(),
                            "User " + newStatus,
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void promoteToAdmin(View v, UserModel user, int position) {

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .update("role", "admin")
                .addOnSuccessListener(aVoid -> {

                    user.setRole("admin");
                    notifyItemChanged(position);

                    Toast.makeText(v.getContext(),
                            "User promoted to Admin",
                            Toast.LENGTH_SHORT).show();
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

                                Toast.makeText(v.getContext(),
                                        "User Deleted",
                                        Toast.LENGTH_SHORT).show();
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