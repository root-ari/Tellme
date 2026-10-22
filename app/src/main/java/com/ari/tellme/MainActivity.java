package com.ari.tellme;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.view.View;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.EditText;
import android.text.TextUtils;

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
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private DatabaseReference usersRef;
    private DatabaseReference rootRef;
    private String currentUid;

    private TextView textWelcome;
    private TextView textUsername;
    private TextView textEmptyFriends;
    private DatabaseReference friendsRef;

    private RecyclerView recyclerViewFriends;
    private FriendsAdapter friendsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        rootRef = FirebaseDatabase.getInstance().getReference();

        if (auth.getCurrentUser() == null) {
            Intent intent = new Intent(this, login.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return;
        }

        currentUid = auth.getCurrentUser().getUid();

        textWelcome = findViewById(R.id.text_welcome);
        textUsername = findViewById(R.id.text_username);

        final FirebaseUser user = auth.getCurrentUser();

        // Load the signed-in user's profile. The Auth display name is never set
        // during sign-up, so the name shown on the welcome card comes from the
        // "fullName" field of the profile record.
        usersRef.child(currentUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String fullName = snapshot.child("fullName").getValue(String.class);
                if (fullName == null || fullName.isEmpty()) {
                    fullName = user != null ? user.getDisplayName() : null;
                }
                if (fullName == null || fullName.isEmpty()) {
                    fullName = getString(R.string.app_name);
                }
                textWelcome.setText(getString(R.string.welcome_back_user, fullName));

                String username = snapshot.child("username").getValue(String.class);
                if (username == null || username.isEmpty()) {
                    username = snapshot.child("fullName").getValue(String.class);
                }
                if (username == null || username.isEmpty()) {
                    username = "username";
                }
                textUsername.setText("@" + username);

                repairMissingUid(snapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                textUsername.setText(getString(R.string.welcome_username_placeholder));
            }
        });

        
        

        // Set up friends RecyclerView
        friendsRef = FirebaseDatabase.getInstance().getReference("users").child(currentUid).child("friends");
        textEmptyFriends = findViewById(R.id.text_empty_friends);
        recyclerViewFriends = findViewById(R.id.recycler_view_friends);
        friendsAdapter = new FriendsAdapter(friend -> {
            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
            intent.putExtra("peerUid", friend.getUid());
            intent.putExtra("peerFullName", friend.getFullName());
            intent.putExtra("peerUsername", friend.getUsername());
            startActivity(intent);
        });
        recyclerViewFriends.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewFriends.setAdapter(friendsAdapter);

        // Load friends
        loadFriends();

        Spinner quickActionSpinner = findViewById(R.id.quick_action_dropdown);
        ArrayAdapter<CharSequence> quickActionAdapter = ArrayAdapter.createFromResource(
                this, R.array.quick_actions_array, android.R.layout.simple_spinner_item);
        quickActionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        quickActionSpinner.setAdapter(quickActionAdapter);
        // The first entry is a placeholder ("Select Action"). Keeping the spinner on it
        // means the OnItemSelectedListener callback that Android fires automatically when
        // the adapter is attached does not launch an activity, so the app always starts
        // on the welcome screen instead of jumping straight into My Profile.
        quickActionSpinner.setSelection(0);
        quickActionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();

                if (selected.equals(getString(R.string.action_select_action))) {
                    return;
                }

                if (selected.equals(getString(R.string.action_my_profile))) {
                    startActivity(new Intent(MainActivity.this, profile.class));
                } else if (selected.equals(getString(R.string.action_account_settings))) {
                    startActivity(new Intent(MainActivity.this, accountSettings.class));
                } else if (selected.equals(getString(R.string.search_users))) {
                    startActivity(new Intent(MainActivity.this, SearchUsersActivity.class));
                } else if (selected.equals(getString(R.string.add_friend))) {
                    showAddFriendDialog();
                } else if (selected.equals(getString(R.string.friend_requests))) {
                    startActivity(new Intent(MainActivity.this, friendRequests.class));
                } else if (selected.equals(getString(R.string.action_help_support))) {
                    showHelpSupportDialog();
                } else if (selected.equals(getString(R.string.action_about_app))) {
                    showAboutAppDialog();
                } else if (selected.equals(getString(R.string.action_sign_out_account))) {
                    signOut();
                }

                // Reset the spinner back to the placeholder so re-opening the screen
                // does not re-trigger the same action.
                parent.setSelection(0);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_profile) {
            Intent intent = new Intent(this, profile.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, accountSettings.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_help) {
            showHelpSupportDialog();
            return true;
        }

        if (id == R.id.action_about) {
            showAboutAppDialog();
            return true;
        }

        if (id == R.id.action_sign_out) {
            signOut();
            return true;
        }

        if (id == R.id.action_exit) {
            finishAffinity();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /** Sign the user out and return to the login screen */
    private void signOut() {
        auth.signOut();
        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, login.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    /** Show the "Coming Soon" message for the Help & Support screen */
    private void showHelpSupportDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_help_support)
                .setMessage(R.string.help_support_coming_soon)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Show the About App dialog, including the "Your Daily Messing App" tagline */
    private void showAboutAppDialog() {
        String message = getString(R.string.about_app_tagline)
                + "\n\n"
                + getString(R.string.about_app_description)
                + "\n\n"
                + getString(R.string.about_app_version);

        new AlertDialog.Builder(this)
                .setTitle(R.string.about_app_title)
                .setMessage(message)
                .setPositiveButton(R.string.about_app_close, null)
                .show();
    }

    /** Load friends from Realtime Database */
    private void loadFriends() {
        friendsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<User> friendList = new ArrayList<>();
                for (DataSnapshot friendSnapshot : snapshot.getChildren()) {
                    User friend = User.fromSnapshot(friendSnapshot);
                    if (friend != null) {
                        friendList.add(friend);
                    }
                }

                if (friendList.isEmpty()) {
                    recyclerViewFriends.setVisibility(android.view.View.GONE);
                    textEmptyFriends.setVisibility(android.view.View.VISIBLE);
                } else {
                    recyclerViewFriends.setVisibility(android.view.View.VISIBLE);
                    textEmptyFriends.setVisibility(android.view.View.GONE);
                    friendsAdapter.setFriends(friendList);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(MainActivity.this, "Failed to load friends: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Show dialog to add a friend by username */
    private void showAddFriendDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_friend);

        final EditText inputUsername = new EditText(this);
        inputUsername.setHint(R.string.enter_username);
        inputUsername.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        builder.setView(inputUsername);

        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String targetUsername = inputUsername.getText().toString().trim();
                if (TextUtils.isEmpty(targetUsername)) {
                    inputUsername.setError(getString(R.string.username_required));
                    return;
                }
                addFriend(targetUsername);
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    /** Add a friend by looking up username in usernames/ node */
    private void addFriend(String targetUsername) {
        FirebaseDatabase.getInstance().getReference("usernames").child(targetUsername)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            Toast.makeText(MainActivity.this, getString(R.string.user_not_found), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String friendUid = snapshot.child("uid").getValue(String.class);
                        if (friendUid == null) {
                            Toast.makeText(MainActivity.this, getString(R.string.user_not_found), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (currentUid.equals(friendUid)) {
                            Toast.makeText(MainActivity.this, getString(R.string.cannot_add_self), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        checkAlreadyFriends(friendUid);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(MainActivity.this, "Failed to look up user: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Check the relationship with the target user before sending a request:
     * already friends, or a request is already pending in either direction.
     */
    private void checkAlreadyFriends(final String friendUid) {
        friendsRef.child(friendUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot friendSnapshot) {
                if (friendSnapshot.exists()) {
                    Toast.makeText(MainActivity.this, getString(R.string.already_friends), Toast.LENGTH_SHORT).show();
                    return;
                }
                checkPendingRequests(friendUid);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(MainActivity.this, "Failed to check friends: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Reject the send when a request is already pending in either direction.
     * The incoming request is checked first: when the other user already asked us,
     * the request is accepted straight away instead of sending a duplicate.
     */
    private void checkPendingRequests(final String friendUid) {
        // The other user already sent us a request -> accept it directly.
        usersRef.child(currentUid).child("friendRequests").child(friendUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            acceptIncomingRequest(friendUid);
                            return;
                        }
                        checkSentRequest(friendUid);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(MainActivity.this, "Failed to check requests: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Reject the send when we already sent a request to this user */
    private void checkSentRequest(final String friendUid) {
        usersRef.child(currentUid).child("sentRequests").child(friendUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Toast.makeText(MainActivity.this, getString(R.string.already_requested), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        fetchFriendProfile(friendUid);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(MainActivity.this, "Failed to check requests: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchFriendProfile(final String friendUid) {
        usersRef.child(friendUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot userSnapshot) {
                User friend = User.fromSnapshot(userSnapshot);
                if (friend == null) {
                    Toast.makeText(MainActivity.this, getString(R.string.user_not_found), Toast.LENGTH_SHORT).show();
                    return;
                }
                sendFriendRequest(friend);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(MainActivity.this, "Failed to load user: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Store a friend request on both sides:
     * the target receives it under "friendRequests", we track it under "sentRequests".
     */
    private void sendFriendRequest(final User friend) {
        usersRef.child(currentUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot meSnapshot) {
                User me = User.fromSnapshot(meSnapshot);
                if (me == null) {
                    Toast.makeText(MainActivity.this, getString(R.string.user_not_found), Toast.LENGTH_SHORT).show();
                    return;
                }

                // The copy stored on the receiver's side describes the sender,
                // and the copy stored on the sender's side describes the receiver.
                HashMap<String, Object> requestForReceiver = buildRequestData(me);
                HashMap<String, Object> requestForSender = buildRequestData(friend);

                HashMap<String, Object> updates = new HashMap<>();
                updates.put("users/" + friend.getUid() + "/friendRequests/" + currentUid, requestForReceiver);
                updates.put("users/" + currentUid + "/sentRequests/" + friend.getUid(), requestForSender);

                rootRef.updateChildren(updates).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(MainActivity.this, getString(R.string.request_sent), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Failed to send request: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(MainActivity.this, "Failed to load profile: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Accept a request that the other user already sent to us.
     * Both users become friends and both request entries are removed.
     */
    private void acceptIncomingRequest(final String friendUid) {
        usersRef.child(friendUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                User friend = User.fromSnapshot(snapshot);
                if (friend == null) {
                    Toast.makeText(MainActivity.this, getString(R.string.user_not_found), Toast.LENGTH_SHORT).show();
                    return;
                }

                usersRef.child(currentUid).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot meSnapshot) {
                        User me = User.fromSnapshot(meSnapshot);
                        if (me == null) {
                            Toast.makeText(MainActivity.this, getString(R.string.user_not_found), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        HashMap<String, Object> updates = new HashMap<>();
                        updates.put("users/" + currentUid + "/friends/" + friendUid, friendData(friend));
                        updates.put("users/" + friendUid + "/friends/" + currentUid, friendData(me));
                        updates.put("users/" + currentUid + "/friendRequests/" + friendUid, null);
                        updates.put("users/" + friendUid + "/sentRequests/" + currentUid, null);

                        rootRef.updateChildren(updates).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                loadFriends();
                                Toast.makeText(MainActivity.this, getString(R.string.request_accepted), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, "Failed to accept request: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(MainActivity.this, "Failed to load profile: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(MainActivity.this, "Failed to load user: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Add the "uid" field to a profile record that does not have one yet.
     * <p>
     * Profiles are stored under "users/{uid}", so the uid of the record is the
     * key it is stored under. Records created before the "uid" field was
     * written are repaired here so that every record carries a usable uid.
     */
    private void repairMissingUid(DataSnapshot snapshot) {
        String storedUid = snapshot.child("uid").getValue(String.class);
        if (storedUid != null && !storedUid.isEmpty()) {
            return;
        }
        String key = snapshot.getKey();
        if (key == null || key.isEmpty()) {
            return;
        }
        usersRef.child(key).child("uid").setValue(key);
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

    /**
     * Build the record stored in a "friendRequests" / "sentRequests" node.
     * The record always describes the user the request is about, so the
     * "uid" field holds that user's uid.
     */
    private HashMap<String, Object> buildRequestData(User user) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("fullName", user.getFullName());
        data.put("email", user.getEmail());
        data.put("username", user.getUsername());
        data.put("requestedAt", System.currentTimeMillis());
        return data;
    }
}