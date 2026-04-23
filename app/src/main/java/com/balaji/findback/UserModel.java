package com.balaji.findback;

public class UserModel {
    private String uid;
    private String name;
    private String email;
    private String password; // Added for admin management
    private String contactInfo; // Added for contact info
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

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
