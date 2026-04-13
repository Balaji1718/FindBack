package com.balaji.findback;

public class ClaimStatusUtil {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_RETURNED = "returned";

    public static boolean isPending(String status) {
        return STATUS_PENDING.equals(status);
    }

    public static boolean isApproved(String status) {
        return STATUS_APPROVED.equals(status);
    }

    public static boolean isRejected(String status) {
        return STATUS_REJECTED.equals(status);
    }

    public static boolean isReturned(String status) {
        return STATUS_RETURNED.equals(status);
    }
}