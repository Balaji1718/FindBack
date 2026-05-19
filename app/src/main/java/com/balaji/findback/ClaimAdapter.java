package com.balaji.findback;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class ClaimAdapter extends RecyclerView.Adapter<ClaimAdapter.ViewHolder>{

    Context context;
    List<Claim> claimList;
    boolean isOwner;
    FirebaseFirestore db;

    public ClaimAdapter(Context context,List<Claim> claimList,boolean isOwner){
        this.context=context;
        this.claimList=claimList;
        this.isOwner=isOwner;
        db=FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,int viewType){
        View view= LayoutInflater.from(context).inflate(R.layout.claim_row,parent,false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder,int position){
        Claim claim=claimList.get(position);

        holder.textItemTitle.setText(claim.getItemTitle());
        holder.textProof.setText(claim.getProofMessage());
        
        String status = claim.getStatus() != null ? claim.getStatus().toUpperCase() : "PENDING";
        holder.textStatus.setText("Status: " + status);

        // Issue 4: Theme aware colors for status
        TypedValue typedValue = new TypedValue();
        if ("APPROVED".equalsIgnoreCase(status)) {
            holder.textStatus.setTextColor(Color.parseColor("#43A047")); // Green
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            holder.textStatus.setTextColor(Color.parseColor("#E53935")); // Red
        } else {
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true);
            holder.textStatus.setTextColor(typedValue.data);
        }

        // Issue 3: Fetch Item details to get Contact Info (Robust visibility)
        db.collection("items").document(claim.getItemId())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Item item = documentSnapshot.toObject(Item.class);
                        if (item != null) {
                            String contact = item.getContactInfo();
                            if (contact == null || contact.isEmpty()) contact = "Not provided";
                            
                            if (isOwner) {
                                // Owner of the item sees requester's proof and can see item contact too
                                holder.tvContact.setVisibility(View.VISIBLE);
                                holder.tvContact.setText("Your Contact: " + contact);
                            } else {
                                // Requester Screen
                                if ("approved".equalsIgnoreCase(claim.getStatus())) {
                                    holder.tvContact.setVisibility(View.VISIBLE);
                                    holder.tvContact.setText("Owner Contact: " + contact);
                                    holder.tvContact.setTextColor(Color.parseColor("#43A047"));
                                } else {
                                    holder.tvContact.setVisibility(View.VISIBLE);
                                    holder.tvContact.setText("Contact will be visible after approval");
                                    context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true);
                                    holder.tvContact.setTextColor(typedValue.data);
                                }
                            }
                        }
                    }
                });

        // Image loading logic remains same but ensuring no crashes
        loadProofImages(claim, holder);

        View.OnClickListener openViewer = v -> {
            Intent i = new Intent(context, ProofViewerActivity.class);
            i.putExtra("claimId", claim.getId());
            context.startActivity(i);
        };

        holder.imgProof1.setOnClickListener(openViewer);
        holder.imgProof2.setOnClickListener(openViewer);
        holder.textProof.setOnClickListener(openViewer);

        setupButtons(claim, holder, position);
    }

    private void loadProofImages(Claim claim, ViewHolder holder) {
        if (claim.getProofImage1() != null && !claim.getProofImage1().isEmpty()) {
            holder.imgProof1.setVisibility(View.VISIBLE);
            try {
                byte[] bytes = Base64.decode(claim.getProofImage1(), Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                holder.imgProof1.setImageBitmap(bmp);
            } catch (Exception e) { holder.imgProof1.setVisibility(View.GONE); }
        } else { holder.imgProof1.setVisibility(View.GONE); }

        if (claim.getProofImage2() != null && !claim.getProofImage2().isEmpty()) {
            holder.imgProof2.setVisibility(View.VISIBLE);
            try {
                byte[] bytes = Base64.decode(claim.getProofImage2(), Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                holder.imgProof2.setImageBitmap(bmp);
            } catch (Exception e) { holder.imgProof2.setVisibility(View.GONE); }
        } else { holder.imgProof2.setVisibility(View.GONE); }
    }

    private void setupButtons(Claim claim, ViewHolder holder, int position) {
        if(!isOwner){
            holder.btnApprove.setVisibility(View.GONE);
            holder.btnReject.setVisibility(View.GONE);
            holder.btnReturn.setVisibility(View.GONE);
            holder.btnDelete.setVisibility(View.VISIBLE);
        } else {
            holder.btnDelete.setVisibility(View.GONE);
            String status = claim.getStatus();
            if ("approved".equalsIgnoreCase(status)) {
                holder.btnApprove.setVisibility(View.VISIBLE);
                holder.btnApprove.setText("APPROVED");
                holder.btnApprove.setEnabled(false);
                holder.btnReject.setVisibility(View.GONE);
                holder.btnReturn.setVisibility(View.VISIBLE);
                holder.btnReturn.setEnabled(true);
                holder.btnReturn.setAlpha(1.0f);
            } else {
                holder.btnApprove.setVisibility(View.VISIBLE);
                holder.btnApprove.setText("APPROVE");
                holder.btnApprove.setEnabled(true);
                holder.btnReject.setVisibility(View.VISIBLE);
                holder.btnReturn.setVisibility(View.VISIBLE);
                holder.btnReturn.setEnabled(false);
                holder.btnReturn.setAlpha(0.5f);
            }
        }

        holder.btnDelete.setOnClickListener(v-> deleteClaim(claim, position));
        holder.btnApprove.setOnClickListener(v-> updateStatus(claim, "approved", position));
        holder.btnReject.setOnClickListener(v-> updateStatus(claim, "rejected", position));
        holder.btnReturn.setOnClickListener(v-> updateStatus(claim, "returned", position));
    }

    private void deleteClaim(Claim claim, int position) {
        new AlertDialog.Builder(context).setTitle("Delete Claim").setMessage("Are you sure?")
                .setPositiveButton("Delete",(d,w)->{
                    db.collection("claims").document(claim.getId()).delete()
                            .addOnSuccessListener(aVoid -> Toast.makeText(context, "Claim deleted", Toast.LENGTH_SHORT).show());
                }).setNegativeButton("Cancel",null).show();
    }

    private void updateStatus(Claim claim, String status, int position) {
        db.collection("claims").document(claim.getId()).update("status", status)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(context, "Status Updated: " + status, Toast.LENGTH_SHORT).show();
                    if ("approved".equals(status)) {
                        db.collection("items").document(claim.getItemId()).update("status", "CLAIMED");
                    } else if ("returned".equals(status)) {
                        db.collection("items").document(claim.getItemId()).update("status", "RETURNED");
                    }
                });
    }

    @Override
    public int getItemCount(){
        return claimList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder{
        TextView textItemTitle,textProof,textStatus, tvContact;
        ImageView imgProof1, imgProof2;
        Button btnApprove,btnReject,btnReturn,btnDelete;

        public ViewHolder(@NonNull View itemView){
            super(itemView);
            textItemTitle=itemView.findViewById(R.id.textItemTitle);
            textProof=itemView.findViewById(R.id.textProof);
            textStatus=itemView.findViewById(R.id.textStatus);
            tvContact = itemView.findViewById(R.id.tvContact);
            imgProof1 = itemView.findViewById(R.id.imgProof1);
            imgProof2 = itemView.findViewById(R.id.imgProof2);
            btnApprove=itemView.findViewById(R.id.btnApprove);
            btnReject=itemView.findViewById(R.id.btnReject);
            btnReturn=itemView.findViewById(R.id.btnReturn);
            btnDelete=itemView.findViewById(R.id.btnDelete);
        }
    }
}
