package com.balaji.findback;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class FirestoreClaimRepository {

    private FirebaseFirestore db;

    public FirestoreClaimRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public Query getClaimsForOwner(String ownerId) {
        return db.collection("claims")
                .whereEqualTo("ownerId", ownerId);
    }

    public Query getClaimsForClaimer(String claimerId) {
        return db.collection("claims")
                .whereEqualTo("claimerId", claimerId);
    }

    public void approveClaim(String claimId) {
        db.collection("claims")
                .document(claimId)
                .update("status", "approved");
    }

    public void rejectClaim(String claimId) {
        db.collection("claims")
                .document(claimId)
                .update("status", "rejected");
    }

    public void markReturned(String claimId) {
        db.collection("claims")
                .document(claimId)
                .update("status", "returned");
    }

    public void deleteClaim(String claimId) {
        db.collection("claims")
                .document(claimId)
                .delete();
    }
}