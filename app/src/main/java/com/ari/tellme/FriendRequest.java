package com.ari.tellme;

import com.google.firebase.database.DataSnapshot;

public class FriendRequest {
    private String uid;
    private String fullName;
    private String email;
    private String username;
    private long requestedAt;

    public FriendRequest() {
        // Default constructor required for Firebase DataSnapshot
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
     * The name shown in the lists.
     * <p>
     * The username is preferred, but a record without one falls back to the
     * full name so that a row never shows an empty label.
     */
    public String getDisplayName() {
        if (username != null && !username.isEmpty()) {
            return username;
        }
        if (fullName != null && !fullName.isEmpty()) {
            return fullName;
        }
        return "Unknown";
    }

    public long getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(long requestedAt) {
        this.requestedAt = requestedAt;
    }

    /**
     * Reads a friend request from a snapshot.
     * <p>
     * The request is stored under "friendRequests/{senderUid}" on the receiving
     * side and under "sentRequests/{receiverUid}" on the sending side, so the
     * snapshot key is the uid of the user the request is about. The key is used
     * when the "uid" field is missing or points at a different user, which
     * keeps records written by older versions of the app working.
     */
    public static FriendRequest fromSnapshot(DataSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        FriendRequest request = snapshot.getValue(FriendRequest.class);
        if (request == null) {
            return null;
        }
        String key = snapshot.getKey();
        if (key != null && !key.isEmpty()) {
            request.uid = key;
        }
        return request;
    }
}
