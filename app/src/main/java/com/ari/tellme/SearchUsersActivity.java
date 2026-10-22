package com.ari.tellme;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchUsersActivity extends AppCompatActivity {

    private String currentUid;
    private DatabaseReference usersRef;
    private DatabaseReference rootRef;
    private UsersAdapter adapter;
    private final List<User> allUsers = new ArrayList<>();
    private RecyclerView recyclerView;
    private TextView textEmpty;
    private TextInputEditText editSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search_users);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }
        currentUid = currentUser.getUid();
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        rootRef = FirebaseDatabase.getInstance().getReference();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        recyclerView = findViewById(R.id.recycler_view_search);
        textEmpty = findViewById(R.id.text_empty_search);
        editSearch = findViewById(R.id.edit_search);

        adapter = new UsersAdapter(user -> {
            if (user.getUid() != null && !user.getUid().equals(currentUid)) {
                sendFriendRequest(user);
            } else {
                Toast.makeText(this, R.string.cannot_add_self, Toast.LENGTH_SHORT).show();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadAllUsers();

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterUsers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void loadAllUsers() {
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allUsers.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    User user = User.fromSnapshot(child);
                    if (user != null && user.getUid() != null && !user.getUid().equals(currentUid)) {
                        allUsers.add(user);
                    }
                }
                filterUsers(editSearch.getText() != null ? editSearch.getText().toString() : "");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SearchUsersActivity.this, "Failed to load users: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterUsers(String query) {
        String q = query.toLowerCase().trim();
        List<User> filtered = new ArrayList<>();
        for (User u : allUsers) {
            if (TextUtils.isEmpty(q) ||
                    (u.getFullName() != null && u.getFullName().toLowerCase().contains(q)) ||
                    (u.getUsername() != null && u.getUsername().toLowerCase().contains(q)) ||
                    (u.getEmail() != null && u.getEmail().toLowerCase().contains(q))) {
                filtered.add(u);
            }
        }

        if (filtered.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            textEmpty.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            textEmpty.setVisibility(View.GONE);
            adapter.setUsers(filtered);
        }
    }

    private void sendFriendRequest(User targetUser) {
        String targetUid = targetUser.getUid();
        usersRef.child(currentUid).child("friends").child(targetUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Toast.makeText(SearchUsersActivity.this, R.string.already_friends, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        usersRef.child(currentUid).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot mySnapshot) {
                                User myProfile = User.fromSnapshot(mySnapshot);
                                if (myProfile == null) return;

                                Map<String, Object> requestData = new HashMap<>();
                                requestData.put("uid", currentUid);
                                requestData.put("fullName", myProfile.getFullName());
                                requestData.put("email", myProfile.getEmail());
                                requestData.put("username", myProfile.getUsername());
                                requestData.put("timestamp", System.currentTimeMillis());

                                Map<String, Object> sentData = new HashMap<>();
                                sentData.put("uid", targetUid);
                                sentData.put("fullName", targetUser.getFullName());
                                sentData.put("email", targetUser.getEmail());
                                sentData.put("username", targetUser.getUsername());
                                sentData.put("timestamp", System.currentTimeMillis());

                                Map<String, Object> updates = new HashMap<>();
                                updates.put("users/" + targetUid + "/friendRequests/" + currentUid, requestData);
                                updates.put("users/" + currentUid + "/sentRequests/" + targetUid, sentData);

                                rootRef.updateChildren(updates).addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Toast.makeText(SearchUsersActivity.this, R.string.request_sent, Toast.LENGTH_SHORT).show();
                                    } else {
                                        String err = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                                        Toast.makeText(SearchUsersActivity.this, "Failed to send request: " + err, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }
}
