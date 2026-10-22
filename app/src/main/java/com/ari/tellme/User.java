package com.ari.tellme;

import com.google.firebase.database.DataSnapshot;

public class User {
    private String uid;
    private String fullName;
    private String email;
    private String username;

    public User() {
        // Default constructor required for Firebase DataSnapshot
    }

    public User(String uid, String fullName, String email, String username) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.username = username;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Reads a user profile from a snapshot.
     * <p>
     * The "uid" field is optional in the database: the profile is stored under
     * "users/{uid}", so the uid is taken from the snapshot key when the field
     * is missing. Without this fallback the uid would be null and every record
     * built from it would point at an invalid "users/null" path.
     */
    public static User fromSnapshot(DataSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        User user = snapshot.getValue(User.class);
        if (user == null) {
            return null;
        }
        if ((user.uid == null || user.uid.isEmpty()) && snapshot.getKey() != null) {
            user.uid = snapshot.getKey();
        }
        return user;
    }
}