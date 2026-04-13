package com.balaji.findback;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class InstitutionContextProvider {

    public interface Callback {
        void onLoaded(String context);
    }

    public static void load(String institutionId, Callback callback) {
        if (institutionId == null) {
            callback.onLoaded("No institution linked.");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        StringBuilder context = new StringBuilder();

        context.append("Institution Context: ").append(institutionId).append("\n\n");

        // 1. Fetch Items with details
        db.collection("items")
                .whereEqualTo("institutionId", institutionId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(items -> {
                    int totalItems = items.size();
                    int lost = 0, found = 0;
                    Map<String, Integer> categoryMap = new HashMap<>();

                    context.append("--- Items Overview ---\n");
                    for (QueryDocumentSnapshot doc : items) {
                        String type = doc.getString("type");
                        String cat = doc.getString("category");
                        if ("LOST".equalsIgnoreCase(type)) lost++;
                        else if ("FOUND".equalsIgnoreCase(type)) found++;
                        
                        if (cat != null) categoryMap.put(cat, categoryMap.getOrDefault(cat, 0) + 1);
                    }

                    context.append("Total: ").append(totalItems).append(" (Lost: ").append(lost).append(", Found: ").append(found).append(")\n");
                    context.append("Categories: ").append(categoryMap.toString()).append("\n\n");

                    // 2. Fetch Claims with details
                    db.collection("claims")
                            .whereEqualTo("institutionId", institutionId)
                            .get()
                            .addOnSuccessListener(claims -> {
                                int totalClaims = claims.size();
                                int pending = 0, approved = 0, returned = 0;

                                for (QueryDocumentSnapshot doc : claims) {
                                    String status = doc.getString("status");
                                    if ("PENDING".equalsIgnoreCase(status)) pending++;
                                    else if ("APPROVED".equalsIgnoreCase(status)) approved++;
                                    else if ("RETURNED".equalsIgnoreCase(status)) returned++;
                                }

                                context.append("--- Claims Overview ---\n");
                                context.append("Total Claims: ").append(totalClaims).append("\n");
                                context.append("Pending: ").append(pending).append("\n");
                                context.append("Approved: ").append(approved).append("\n");
                                context.append("Returned: ").append(returned).append("\n\n");

                                // 3. Recent Activity (Latest 5 items)
                                context.append("--- Recent Activity ---\n");
                                int count = 0;
                                for (QueryDocumentSnapshot doc : items) {
                                    if (count++ >= 5) break;
                                    context.append("- ").append(doc.getString("title"))
                                           .append(" (").append(doc.getString("type")).append(")\n");
                                }

                                callback.onLoaded(context.toString());
                            })
                            .addOnFailureListener(e -> callback.onLoaded(context.toString()));
                })
                .addOnFailureListener(e -> callback.onLoaded(context.toString()));
    }
}