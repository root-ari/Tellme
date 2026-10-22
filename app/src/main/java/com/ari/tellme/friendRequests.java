package com.ari.tellme;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shows the friend requests this user received and the ones this user sent.
 * <p>
 * A request is stored twice:
 * <ul>
 *     <li>{@code users/{receiverUid}/friendRequests/{senderUid}} - incoming request</li>
 *     <li>{@code users/{senderUid}/sentRequests/{receiverUid}} - outgoing request</li>
 * </ul>
 * Accepting turns the pair into friends on both sides and removes both entries.
 */
public class friendRequests extends AppCompatActivity implements FriendRequestsAdapter.OnRequestActionListener {

    private FirebaseAuth auth;
    private String currentUid;
    private DatabaseReference usersRef;
    private DatabaseReference rootRef;

    private RecyclerView recyclerViewRequests;
    private RecyclerView recyclerViewSentRequests;
    private TextView textEmptyRequests;
    private TextView textEmptySentRequests;

    private FriendRequestsAdapter requestsAdapter;
    private SentRequestsAdapter sentRequestsAdapter;

    /** Uids whose profile is currently being looked up */
    private final Set<String> pendingProfileUids = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_friend_requests);

        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No user signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUid = currentUser.getUid();
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        rootRef = FirebaseDatabase.getInstance().getReference();

        recyclerViewRequests = findViewById(R.id.recycler_view_requests);
        recyclerViewSentRequests = findViewById(R.id.recycler_view_sent_requests);
        textEmptyRequests = findViewById(R.id.text_empty_requests);
        textEmptySentRequests = findViewById(R.id.text_empty_sent_requests);

        requestsAdapter = new FriendRequestsAdapter(this);
        sentRequestsAdapter = new SentRequestsAdapter();

        recyclerViewRequests.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewRequests.setAdapter(requestsAdapter);
        recyclerViewSentRequests.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewSentRequests.setAdapter(sentRequestsAdapter);

        loadIncomingRequests();
        loadSentRequests();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    /** Load the requests other users sent to this user */
    private void loadIncomingRequests() {
        DatabaseReference ref = usersRef.child(currentUid).child("friendRequests");
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<FriendRequest> requestList = new ArrayList<>();
                for (DataSnapshot requestSnapshot : snapshot.getChildren()) {
                    FriendRequest request = FriendRequest.fromSnapshot(requestSnapshot);
                    if (request != null) {
                        requestList.add(request);
                    }
                }

                if (requestList.isEmpty()) {
                    recyclerViewRequests.setVisibility(View.GONE);
                    textEmptyRequests.setVisibility(View.VISIBLE);
                } else {
                    recyclerViewRequests.setVisibility(View.VISIBLE);
                    textEmptyRequests.setVisibility(View.GONE);
                    requestsAdapter.setRequests(requestList);
                    refreshRequestProfiles(requestList, requestsAdapter);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(friendRequests.this, "Failed to load requests: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Load the requests this user sent to other users */
    private void loadSentRequests() {
        DatabaseReference ref = usersRef.child(currentUid).child("sentRequests");
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<FriendRequest> requestList = new ArrayList<>();
                for (DataSnapshot requestSnapshot : snapshot.getChildren()) {
                    FriendRequest request = FriendRequest.fromSnapshot(requestSnapshot);
                    if (request != null) {
                        requestList.add(request);
                    }
                }

                if (requestList.isEmpty()) {
                    recyclerViewSentRequests.setVisibility(View.GONE);
                    textEmptySentRequests.setVisibility(View.VISIBLE);
                } else {
                    recyclerViewSentRequests.setVisibility(View.VISIBLE);
                    textEmptySentRequests.setVisibility(View.GONE);
                    sentRequestsAdapter.setRequests(requestList);
                    refreshRequestProfiles(requestList, sentRequestsAdapter);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(friendRequests.this, "Failed to load sent requests: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    @Override
    public void onAccept(FriendRequest request) {
        String requesterUid = request.getUid();
        if (requesterUid == null) {
            Toast.makeText(this, "Invalid request", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fetch the requester profile so both friendship records carry the same fields.
        usersRef.child(requesterUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                User requester = User.fromSnapshot(snapshot);
                if (requester == null) {
                    Toast.makeText(friendRequests.this, getString(R.string.user_not_found), Toast.LENGTH_SHORT).show();
                    return;
                }

                usersRef.child(currentUid).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot meSnapshot) {
                        User me = User.fromSnapshot(meSnapshot);
                        if (me == null) {
                            Toast.makeText(friendRequests.this, getString(R.string.user_not_found), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        HashMap<String, Object> updates = new HashMap<>();
                        updates.put("users/" + currentUid + "/friends/" + requesterUid, friendData(requester));
                        updates.put("users/" + requesterUid + "/friends/" + currentUid, friendData(me));
                        updates.put("users/" + currentUid + "/friendRequests/" + requesterUid, null);
                        updates.put("users/" + requesterUid + "/sentRequests/" + currentUid, null);

                        rootRef.updateChildren(updates).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(friendRequests.this, getString(R.string.request_accepted), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(friendRequests.this, "Failed to accept request: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(friendRequests.this, "Failed to load profile: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(friendRequests.this, "Failed to load user: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    @Override
    public void onReject(FriendRequest request) {
        String requesterUid = request.getUid();
        if (requesterUid == null) {
            Toast.makeText(this, "Invalid request", Toast.LENGTH_SHORT).show();
            return;
        }

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("users/" + currentUid + "/friendRequests/" + requesterUid, null);
        updates.put("users/" + requesterUid + "/sentRequests/" + currentUid, null);

        rootRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(friendRequests.this, getString(R.string.request_rejected), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(friendRequests.this, "Failed to reject request: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Refresh the name and email shown for every request from the profile in
     * the "users" node.
     * <p>
     * The request records also carry these fields, but records written by
     * earlier versions of the app stored the wrong user in them, so the
     * profile is treated as the source of truth.
     * <p>
     * The requests keep their place in the adapter list: the updater replaces
     * the display fields of the request with the matching uid, so no record is
     * added twice and no listener is registered twice. A uid that is already
     * being looked up is skipped, so a reload of the list does not start a
     * second lookup for the same user.
     */
    private void refreshRequestProfiles(List<FriendRequest> requests,
                                        final RequestProfileUpdater updater) {
        for (FriendRequest request : requests) {
            final String uid = request.getUid();
            if (uid == null || uid.isEmpty() || pendingProfileUids.contains(uid)) {
                continue;
            }
            pendingProfileUids.add(uid);
            usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    pendingProfileUids.remove(uid);
                    User user = User.fromSnapshot(snapshot);
                    if (user == null) {
                        return;
                    }
                    updater.updateRequestProfile(uid, user.getFullName(),
                            user.getEmail(), user.getUsername());
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    pendingProfileUids.remove(uid);
                    // Keep the values that came with the request record.
                }
            });
        }
    }

    /** Build the record stored in a "friends" node */
    private HashMap<String, Object> friendData(User user) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("fullName", user.getFullName());
        data.put("email", user.getEmail());
        data.put("username", user.getUsername());
        data.put("addedAt", System.currentTimeMillis());
        return data;
    }
}
