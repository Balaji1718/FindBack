package com.balaji.findback;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Claim {

    private String id;
    private String itemId;
    private String itemTitle;
    private String ownerId;
    private String claimerId;
    private String proofMessage;

    private String proofImage1;
    private String proofImage2;

    private String institutionId;

    private String status;
    private Timestamp timestamp;

    public Claim() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public void setItemTitle(String itemTitle) {
        this.itemTitle = itemTitle;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getClaimerId() {
        return claimerId;
    }

    public void setClaimerId(String claimerId) {
        this.claimerId = claimerId;
    }

    public String getProofMessage() {
        return proofMessage;
    }

    public void setProofMessage(String proofMessage) {
        this.proofMessage = proofMessage;
    }

    public String getProofImage1() {
        return proofImage1;
    }

    public void setProofImage1(String proofImage1) {
        this.proofImage1 = proofImage1;
    }

    public String getProofImage2() {
        return proofImage2;
    }

    public void setProofImage2(String proofImage2) {
        this.proofImage2 = proofImage2;
    }

    public String getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(String institutionId) {
        this.institutionId = institutionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }
}