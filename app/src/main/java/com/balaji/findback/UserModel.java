package com.balaji.findback;

public class UserModel {
    private String uid;
    private String name;
    private String email;
    private String role;
    private String institutionId;
    private String status; // ACTIVE or BLOCKED

    public UserModel() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
