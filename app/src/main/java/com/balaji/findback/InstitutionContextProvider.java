package com.balaji.findback;

import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class InstitutionContextProvider {

    public interface Callback {
        void onLoaded(String context);
    }

    public static void load(String institutionId, boolean isAdmin, Callback callback) {
        if (institutionId == null) {
            callback.onLoaded("No institution linked.");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        StringBuilder context = new StringBuilder();

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.getDefault());
        context.append("CURRENT SYSTEM TIME: ").append(sdf.format(new Date())).append("\n");
        context.append("INSTITUTION IDENTITY: ").append(institutionId).append("\n");

        // 1. STATS: Fetch accurate totals using server-side aggregation
        db.collection("items").whereEqualTo("institutionId", institutionId).count().get(AggregateSource.SERVER).addOnSuccessListener(totalTask -> {
            long totalItems = totalTask.getCount();
            
            db.collection("items").whereEqualTo("institutionId", institutionId).whereEqualTo("type", "LOST").count().get(AggregateSource.SERVER).addOnSuccessListener(lostTask -> {
                long lostCount = lostTask.getCount();
                long foundCount = totalItems - lostCount;

                context.append("\n[OVERALL STATISTICS]\n");
                context.append("- Total Records in Database: ").append(totalItems).append("\n");
                context.append("- Items reported as LOST: ").append(lostCount).append("\n");
                context.append("- Items reported as FOUND: ").append(foundCount).append("\n");

                // 2. RECENT ITEMS: Fetch detailed list for intent mapping
                db.collection("items")
                        .whereEqualTo("institutionId", institutionId)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(60) 
                        .get()
                        .addOnSuccessListener(items -> {
                            Map<String, Integer> categoryMap = new HashMap<>();
                            StringBuilder recentDetails = new StringBuilder();
                            
                            for (QueryDocumentSnapshot doc : items) {
                                String title = doc.getString("title");
                                String type = doc.getString("type");
                                String cat = doc.getString("category");
                                String status = doc.getString("status");
                                String desc = doc.getString("description");
                                if (desc != null && desc.length() > 60) desc = desc.substring(0, 57) + "...";
                                
                                if (cat != null) categoryMap.put(cat, categoryMap.getOrDefault(cat, 0) + 1);
                                
                                recentDetails.append("- ").append(title)
                                             .append(" (").append(type).append(")")
                                             .append(" | Cat: ").append(cat)
                                             .append(" | Details: ").append(desc != null ? desc : "No description")
                                             .append(" | Status: ").append(status != null ? status : "Active").append("\n");
                            }

                            context.append("[CATEGORY DISTRIBUTION]\n").append(categoryMap.toString()).append("\n");
                            context.append("\n[RECENTLY POSTED ITEMS (Top 60)]\n").append(recentDetails);

                            if (!isAdmin) {
                                callback.onLoaded(context.toString());
                                return;
                            }

                            // 3. ADMIN ONLY: Management & User Stats
                            db.collection("users").whereEqualTo("institutionId", institutionId).count().get(AggregateSource.SERVER).addOnSuccessListener(userTask -> {
                                long totalUsers = userTask.getCount();
                                
                                db.collection("claims").whereEqualTo("institutionId", institutionId).count().get(AggregateSource.SERVER).addOnSuccessListener(claimsTask -> {
                                    long totalClaims = claimsTask.getCount();
                                    
                                    db.collection("reports").whereEqualTo("institutionId", institutionId).count().get(AggregateSource.SERVER).addOnSuccessListener(reportsTask -> {
                                        long totalReports = reportsTask.getCount();

                                        context.append("\n[ADMIN MANAGEMENT DATA]\n");
                                        context.append("- Total Registered Users: ").append(totalUsers).append("\n");
                                        context.append("- Total Claims Received: ").append(totalClaims).append("\n");
                                        context.append("- Total Security/Spam Reports: ").append(totalReports).append("\n");
                                        
                                        db.collection("claims").whereEqualTo("institutionId", institutionId).get().addOnSuccessListener(claims -> {
                                            int pending = 0, approved = 0, returned = 0;
                                            for (QueryDocumentSnapshot d : claims) {
                                                String s = d.getString("status");
                                                if ("PENDING".equalsIgnoreCase(s)) pending++;
                                                else if ("APPROVED".equalsIgnoreCase(s)) approved++;
                                                else if ("RETURNED".equalsIgnoreCase(s)) returned++;
                                            }
                                            context.append("- Claims Breakdown -> Pending: ").append(pending)
                                                   .append(", Approved: ").append(approved)
                                                   .append(", Returned: ").append(returned).append("\n");
                                            callback.onLoaded(context.toString());
                                        }).addOnFailureListener(e -> callback.onLoaded(context.toString()));
                                    }).addOnFailureListener(e -> callback.onLoaded(context.toString()));
                                }).addOnFailureListener(e -> callback.onLoaded(context.toString()));
                            }).addOnFailureListener(e -> callback.onLoaded(context.toString()));
                        })
                        .addOnFailureListener(e -> callback.onLoaded(context.toString()));
            }).addOnFailureListener(e -> callback.onLoaded(context.toString()));
        }).addOnFailureListener(e -> callback.onLoaded("Error linking to institution database."));
    }
}
