package com.balaji.findback;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;
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
        holder.textStatus.setText("Status: "+claim.getStatus());

        // Fetch Item details to get Contact Info
        db.collection("items").document(claim.getItemId())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Item item = documentSnapshot.toObject(Item.class);
                        if (item != null && item.getContactInfo() != null) {
                            if (isOwner) {
                                // Poster Screen
                                holder.tvContact.setText("Contact : " + item.getContactInfo());
                            } else {
                                // Claimer Screen
                                if ("approved".equalsIgnoreCase(claim.getStatus())) {
                                    holder.tvContact.setText("Contact : " + item.getContactInfo());
                                } else {
                                    holder.tvContact.setText("Contact : Waiting for approval");
                                }
                            }
                        }
                    }
                });

        // Decode and set Proof Image 1
        if (claim.getProofImage1() != null && !claim.getProofImage1().isEmpty()) {
            holder.imgProof1.setVisibility(View.VISIBLE);
            try {
                byte[] bytes = Base64.decode(claim.getProofImage1(), Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                holder.imgProof1.setImageBitmap(bmp);
            } catch (Exception e) {
                holder.imgProof1.setVisibility(View.GONE);
            }
        } else {
            holder.imgProof1.setVisibility(View.GONE);
        }

        // Decode and set Proof Image 2
        if (claim.getProofImage2() != null && !claim.getProofImage2().isEmpty()) {
            holder.imgProof2.setVisibility(View.VISIBLE);
            try {
                byte[] bytes = Base64.decode(claim.getProofImage2(), Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                holder.imgProof2.setImageBitmap(bmp);
            } catch (Exception e) {
                holder.imgProof2.setVisibility(View.GONE);
            }
        } else {
            holder.imgProof2.setVisibility(View.GONE);
        }

        // Trigger Image Viewer on click
        View.OnClickListener openViewer = v -> {
            Intent i = new Intent(context, ProofViewerActivity.class);
            i.putExtra("claimId", claim.getId()); // Pass only claimId to avoid Binder transaction buffer overflow
            context.startActivity(i);
        };

        holder.imgProof1.setOnClickListener(openViewer);
        holder.imgProof2.setOnClickListener(openViewer);
        holder.textProof.setOnClickListener(openViewer);

        if(!isOwner){
            holder.btnApprove.setVisibility(View.GONE);
            holder.btnReject.setVisibility(View.GONE);
            holder.btnReturn.setVisibility(View.GONE);
            holder.btnDelete.setVisibility(View.VISIBLE);
        } else {
            // Owner/Admin view - Claim Requests screen
            holder.btnDelete.setVisibility(View.GONE);
            
            String status = claim.getStatus();
            if ("approved".equalsIgnoreCase(status)) {
                holder.btnApprove.setVisibility(View.VISIBLE);
                holder.btnApprove.setText("APPROVED");
                holder.btnApprove.setEnabled(false);
                holder.btnApprove.setTextColor(Color.GRAY);
                
                holder.btnReject.setVisibility(View.GONE);
                
                holder.btnReturn.setVisibility(View.VISIBLE);
                holder.btnReturn.setEnabled(true);
                holder.btnReturn.setAlpha(1.0f);
            } else if ("returned".equalsIgnoreCase(status) || "rejected".equalsIgnoreCase(status)) {
                // Should be removed by listener, but hide buttons just in case of delay
                holder.btnApprove.setVisibility(View.GONE);
                holder.btnReject.setVisibility(View.GONE);
                holder.btnReturn.setVisibility(View.GONE);
            } else {
                // Pending status
                holder.btnApprove.setVisibility(View.VISIBLE);
                holder.btnApprove.setText("APPROVE");
                holder.btnApprove.setEnabled(true);
                holder.btnApprove.setTextColor(context.getResources().getColor(R.color.purple_500));
                
                holder.btnReject.setVisibility(View.VISIBLE);
                
                holder.btnReturn.setVisibility(View.VISIBLE);
                holder.btnReturn.setEnabled(false);
                holder.btnReturn.setAlpha(0.5f);
            }
        }

        holder.btnDelete.setOnClickListener(v->{
            new AlertDialog.Builder(context)
                    .setTitle("Delete Claim")
                    .setMessage("Are you sure?")
                    .setPositiveButton("Delete",(d,w)->{
                        db.collection("claims")
                                .document(claim.getId())
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(context, "Claim deleted", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(context, "Delete failed: "+e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    })
                    .setNegativeButton("Cancel",null)
                    .show();
        });

        holder.btnApprove.setOnClickListener(v->{
            int currentPos = holder.getAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;

            db.collection("claims").document(claim.getId()).update("status","approved")
                    .addOnSuccessListener(aVoid -> {
                        claim.setStatus("approved");
                        notifyItemChanged(currentPos);
                        Toast.makeText(context, "Claim Approved", Toast.LENGTH_SHORT).show();
                        db.collection("items").document(claim.getItemId()).update("status", "CLAIMED");
                    })
                    .addOnFailureListener(e -> Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        holder.btnReject.setOnClickListener(v->{
            int currentPos = holder.getAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;

            db.collection("claims").document(claim.getId()).update("status","rejected")
                    .addOnSuccessListener(aVoid -> {
                        // Immediately remove from list locally for smooth experience
                        claimList.remove(currentPos);
                        notifyItemRemoved(currentPos);
                        
                        Toast.makeText(context, "Claim Rejected", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        holder.btnReturn.setOnClickListener(v->{
            int currentPos = holder.getAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;

            db.collection("claims").document(claim.getId()).update("status","returned")
                    .addOnSuccessListener(aVoid -> {
                        // Immediately remove from list locally so it disappears as requested
                        claimList.remove(currentPos);
                        notifyItemRemoved(currentPos);

                        Toast.makeText(context, "Marked as Returned", Toast.LENGTH_SHORT).show();
                        db.collection("items").document(claim.getItemId()).update("status", "RETURNED");
                    })
                    .addOnFailureListener(e -> Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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