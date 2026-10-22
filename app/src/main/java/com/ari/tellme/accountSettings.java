package com.ari.tellme;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class accountSettings extends AppCompatActivity {

    private TextInputEditText editName;
    private TextInputEditText editUsername;
    private TextInputEditText editEmail;
    private TextView textDisplayName;
    private TextView textUsername;

    private FirebaseAuth auth;
    private DatabaseReference usersRef;
    private String currentUid;
    private String currentUsername = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_account_settings);

        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No user signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUid = currentUser.getUid();
        usersRef = FirebaseDatabase.getInstance().getReference("users").child(currentUid);

        editName = findViewById(R.id.edit_settings_name);
        editUsername = findViewById(R.id.edit_settings_username);
        editEmail = findViewById(R.id.edit_settings_email);
        textDisplayName = findViewById(R.id.text_settings_display_name);
        textUsername = findViewById(R.id.text_settings_username);

        editEmail.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "");

        loadProfile();

        MaterialButton buttonSave = findViewById(R.id.button_save_profile);
        buttonSave.setOnClickListener(v -> saveProfile());

        MaterialButton buttonChangePassword = findViewById(R.id.button_change_password);
        buttonChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        MaterialButton buttonSignOut = findViewById(R.id.button_sign_out_settings);
        buttonSignOut.setOnClickListener(v -> signOut());

        MaterialButton buttonDelete = findViewById(R.id.button_delete_account);
        buttonDelete.setOnClickListener(v -> confirmDeleteAccount());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    /** Load the user's profile data from the Realtime Database */
    private void loadProfile() {
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String fullName = snapshot.child("fullName").getValue(String.class);
                String username = snapshot.child("username").getValue(String.class);
                String email = snapshot.child("email").getValue(String.class);

                if (fullName != null && !fullName.isEmpty()) {
                    editName.setText(fullName);
                    textDisplayName.setText(fullName);
                }
                if (username != null && !username.isEmpty()) {
                    currentUsername = username;
                    editUsername.setText(username);
                    textUsername.setText("@" + username);
                }
                if (email != null && !email.isEmpty()) {
                    editEmail.setText(email);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(accountSettings.this, "Unable to load profile: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    /** Validate and persist the edited profile fields */
    private void saveProfile() {
        final String newDisplayName = editName.getText().toString().trim();
        final String newUsername = editUsername.getText().toString().trim();
        final String newEmail = editEmail.getText().toString().trim();

        if (TextUtils.isEmpty(newDisplayName)) {
            editName.setError(getString(R.string.field_required));
            return;
        }
        if (TextUtils.isEmpty(newUsername)) {
            editUsername.setError(getString(R.string.username_required));
            return;
        }
        if (TextUtils.isEmpty(newEmail)) {
            editEmail.setError(getString(R.string.field_required));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            editEmail.setError(getString(R.string.invalid_email));
            return;
        }

        // The username reservation is checked atomically inside saveProfileUpdates()
        saveProfileUpdates(newDisplayName, newUsername, newEmail);
    }


    /**
     * Write profile changes to Auth and the Realtime Database.
     * <p>
     * The steps are ordered so that nothing is half-applied:
     * 1. the new username is reserved (atomic check-and-set),
     * 2. an email change is confirmed with the current password,
     * 3. the display name is updated in Firebase Auth,
     * 4. the profile is written to the Realtime Database,
     * 5. only then the previous username reservation is released.
     */
    private void saveProfileUpdates(final String newDisplayName, final String newUsername, final String newEmail) {
        final FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No user signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        final String oldUsername = currentUsername;

        if (!TextUtils.equals(newUsername, oldUsername)) {
            // Atomic check-and-set so two users cannot claim the same username
            reserveUsername(newUsername, () -> continueProfileUpdate(
                    user, newDisplayName, newUsername, newEmail, oldUsername));
            return;
        }

        continueProfileUpdate(user, newDisplayName, newUsername, newEmail, oldUsername);
    }

    /** Step 2: confirm an email change with the current password before touching Auth. */
    private void continueProfileUpdate(final FirebaseUser user,
                                       final String newDisplayName,
                                       final String newUsername,
                                       final String newEmail,
                                       final String oldUsername) {
        if (!TextUtils.equals(newEmail, user.getEmail())) {
            confirmEmailChange(user, newDisplayName, newUsername, newEmail, oldUsername);
            return;
        }
        applyDisplayNameAndWrite(user, newDisplayName, newUsername, newEmail, oldUsername);
    }

    /**
     * Ask for the current password, then change the email address.
     * Firebase requires a recent sign-in before an email address may be changed.
     */
    private void confirmEmailChange(final FirebaseUser user,
                                     final String newDisplayName,
                                     final String newUsername,
                                     final String newEmail,
                                     final String oldUsername) {
        final EditText inputPassword = new EditText(this);
        inputPassword.setHint(R.string.current_password);
        inputPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_email_change);
        builder.setMessage(R.string.reauthenticate_prompt);
        builder.setView(inputPassword);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String password = inputPassword.getText().toString();
                if (TextUtils.isEmpty(password)) {
                    inputPassword.setError(getString(R.string.field_required));
                    return;
                }
                reauthenticateAndChangeEmail(user, password, newDisplayName, newUsername, newEmail, oldUsername);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    /** Re-authenticate, then apply the email change and continue the update chain. */
    private void reauthenticateAndChangeEmail(final FirebaseUser user,
                                              String password,
                                              final String newDisplayName,
                                              final String newUsername,
                                              final String newEmail,
                                              final String oldUsername) {
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);
        user.reauthenticate(credential).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(accountSettings.this, getString(R.string.password_wrong), Toast.LENGTH_SHORT).show();
                return;
            }
            user.updateEmail(newEmail).addOnCompleteListener(emailTask -> {
                if (!emailTask.isSuccessful()) {
                    // Nothing else was written yet, so the profile is left untouched.
                    Toast.makeText(accountSettings.this, getString(R.string.email_change_failed), Toast.LENGTH_SHORT).show();
                    return;
                }
                applyDisplayNameAndWrite(user, newDisplayName, newUsername, newEmail, oldUsername);
            });
        });
    }

    /** Step 3 and 4: update the Auth display name, then write the profile to the database. */
    private void applyDisplayNameAndWrite(final FirebaseUser user,
                                           final String newDisplayName,
                                           final String newUsername,
                                           final String newEmail,
                                           final String oldUsername) {
        if (!TextUtils.equals(newDisplayName, user.getDisplayName())) {
            UserProfileChangeRequest profileRequest = new UserProfileChangeRequest.Builder()
                    .setDisplayName(newDisplayName)
                    .build();
            user.updateProfile(profileRequest).addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Toast.makeText(accountSettings.this, getString(R.string.profile_save_failed), Toast.LENGTH_SHORT).show();
                    return;
                }
                writeProfileToDatabase(newDisplayName, newUsername, newEmail, oldUsername);
            });
            return;
        }

        writeProfileToDatabase(newDisplayName, newUsername, newEmail, oldUsername);
    }

    /**
     * Atomically reserve {@code newUsername} for this user.
     * The transaction aborts when the username already belongs to somebody else.
     * {@code onReserved} runs after the reservation succeeded.
     */
    private void reserveUsername(final String newUsername, final Runnable onReserved) {
        final DatabaseReference newUsernameRef = FirebaseDatabase.getInstance()
                .getReference("usernames").child(newUsername);

        newUsernameRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                Object existing = currentData.getValue();
                if (existing != null) {
                    String takenUid;
                    if (existing instanceof Map) {
                        Object uidValue = ((Map<?, ?>) existing).get("uid");
                        takenUid = uidValue == null ? null : uidValue.toString();
                    } else {
                        takenUid = existing.toString();
                    }
                    if (!currentUid.equals(takenUid)) {
                        // Somebody else owns this username
                        return Transaction.abort();
                    }
                }
                HashMap<String, Object> usernameEntry = new HashMap<>();
                usernameEntry.put("uid", currentUid);
                currentData.setValue(usernameEntry);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) {
                    Toast.makeText(accountSettings.this,
                            "Failed to reserve username: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!committed) {
                    editUsername.setError(getString(R.string.username_taken));
                    return;
                }
                // The reservation succeeded; continue with the rest of the update.
                onReserved.run();
            }
        });
    }

    /** Remove a username reservation, but only when it still belongs to this user */
    private void releaseUsernameReservation(String username) {
        releaseUsernameReservation(username, null);
    }

    /**
     * Remove a username reservation, but only when it still belongs to this user.
     * {@code onDone} (when not null) runs after the reservation has been released.
     */
    private void releaseUsernameReservation(String username, final Runnable onDone) {
        if (TextUtils.isEmpty(username)) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        final DatabaseReference oldUsernameRef = FirebaseDatabase.getInstance()
                .getReference("usernames").child(username);
        oldUsernameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String ownerUid = snapshot.child("uid").getValue(String.class);
                    if (currentUid.equals(ownerUid)) {
                        oldUsernameRef.removeValue();
                    }
                }
                if (onDone != null) {
                    onDone.run();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Ignore - the reservation stays reserved
                if (onDone != null) {
                    onDone.run();
                }
            }
        });
    }

    /**
     * Write the profile fields to the Realtime Database.
     * The previous username reservation is released only after the write succeeded.
     */
    private void writeProfileToDatabase(final String newDisplayName,
                                        final String newUsername,
                                        final String newEmail,
                                        final String oldUsername) {
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("uid", currentUid);
        updates.put("fullName", newDisplayName);
        updates.put("username", newUsername);
        updates.put("email", newEmail);

        usersRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                currentUsername = newUsername;
                textDisplayName.setText(newDisplayName);
                textUsername.setText("@" + newUsername);
                editUsername.setText(newUsername);
                // Free the previous reservation (only when it is still ours).
                // When the username did not change, the current reservation stays.
                if (!TextUtils.equals(oldUsername, newUsername)) {
                    releaseUsernameReservation(oldUsername);
                }
                Toast.makeText(accountSettings.this, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(accountSettings.this, getString(R.string.profile_save_failed), Toast.LENGTH_SHORT).show();
            }
        });
    }


    /** Dialog to change the account password (requires re-authentication) */
    private void showChangePasswordDialog() {
        final FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "No user signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.change_password);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        final EditText inputCurrent = new EditText(this);
        inputCurrent.setHint(R.string.current_password);
        inputCurrent.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputCurrent);

        final EditText inputNew = new EditText(this);
        inputNew.setHint(R.string.new_password);
        inputNew.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputNew);

        final EditText inputConfirm = new EditText(this);
        inputConfirm.setHint(R.string.confirm_new_password);
        inputConfirm.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputConfirm);

        builder.setView(layout);

        builder.setPositiveButton(R.string.update_password, null);
        builder.setNegativeButton(android.R.string.cancel, null);
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                // Set the click listener here so that a validation error does not
                // automatically dismiss the dialog.
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String currentPassword = inputCurrent.getText().toString();
                        String newPassword = inputNew.getText().toString();
                        String confirmPassword = inputConfirm.getText().toString();

                        if (TextUtils.isEmpty(currentPassword)) {
                            inputCurrent.setError(getString(R.string.field_required));
                            return;
                        }
                        if (newPassword.length() < 6) {
                            inputNew.setError(getString(R.string.password_too_short));
                            return;
                        }
                        if (!newPassword.equals(confirmPassword)) {
                            inputConfirm.setError(getString(R.string.password_mismatch));
                            return;
                        }

                        dialog.dismiss();
                        reauthenticateAndChangePassword(user, currentPassword, newPassword);
                    }
                });
            }
        });
        dialog.show();
    }

    /** Re-authenticate with the current password, then update to the new one */
    private void reauthenticateAndChangePassword(FirebaseUser user, String currentPassword, String newPassword) {
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(accountSettings.this, getString(R.string.password_wrong), Toast.LENGTH_SHORT).show();
                return;
            }
            user.updatePassword(newPassword).addOnCompleteListener(task2 -> {
                if (task2.isSuccessful()) {
                    Toast.makeText(accountSettings.this, getString(R.string.password_updated), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(accountSettings.this, "Failed to update password: " + task2.getException().getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
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

    /** Ask for confirmation (with the current password), then delete the account and its data */
    private void confirmDeleteAccount() {
        final FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "No user signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText inputPassword = new EditText(this);
        inputPassword.setHint(R.string.current_password);
        inputPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_account);
        builder.setMessage(R.string.delete_account_confirm);
        builder.setView(inputPassword);
        builder.setPositiveButton(R.string.delete_account, null);
        builder.setNegativeButton(android.R.string.cancel, null);
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                // Set the click listener here so that a validation error does not
                // automatically dismiss the dialog.
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String password = inputPassword.getText().toString();
                        if (TextUtils.isEmpty(password)) {
                            inputPassword.setError(getString(R.string.field_required));
                            return;
                        }
                        dialog.dismiss();
                        deleteAccount(user, password);
                    }
                });
            }
        });
        dialog.show();
    }

    /**
     * Re-authenticate, then remove the Realtime Database records and the Firebase Auth user.
     */
    private void deleteAccount(final FirebaseUser user, String password) {
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);

        user.reauthenticate(credential).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(accountSettings.this, getString(R.string.password_wrong), Toast.LENGTH_SHORT).show();
                return;
            }

            // Remove this user from the friends lists of their friends, then
            // release the username, then drop the profile, and only afterwards
            // delete the Auth account.
            removeFromFriendsLists(() ->
                    releaseUsernameReservation(currentUsername, () ->
                            usersRef.removeValue().addOnCompleteListener(removeTask ->
                                    deleteAuthAccount(user))));
        });
    }

    /** Delete the Firebase Auth user and return to the login screen */
    private void deleteAuthAccount(final FirebaseUser user) {
        user.delete().addOnCompleteListener(deleteTask -> {
            if (deleteTask.isSuccessful()) {
                Toast.makeText(accountSettings.this, getString(R.string.account_deleted), Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(accountSettings.this, login.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(accountSettings.this, "Failed to delete account: " + deleteTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Delete this user's reference from every friend's "friends" list.
     * {@code onDone} (when not null) runs once the cleanup has been triggered.
     */
    private void removeFromFriendsLists(final Runnable onDone) {
        final DatabaseReference friendsRef = FirebaseDatabase.getInstance()
                .getReference("users").child(currentUid).child("friends");

        friendsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot friendSnapshot : snapshot.getChildren()) {
                    String friendUid = friendSnapshot.getKey();
                    if (friendUid != null) {
                        FirebaseDatabase.getInstance().getReference("users")
                                .child(friendUid).child("friends").child(currentUid).removeValue();
                    }
                }
                removePendingRequests(onDone);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Ignore
                removePendingRequests(onDone);
            }
        });
    }

    /**
     * Delete the pending friend requests of this user, both the incoming
     * and the outgoing ones, so no dead request is left behind.
     */
    private void removePendingRequests(final Runnable onDone) {
        final DatabaseReference requestsRef = FirebaseDatabase.getInstance()
                .getReference("users").child(currentUid).child("friendRequests");
        final DatabaseReference sentRef = FirebaseDatabase.getInstance()
                .getReference("users").child(currentUid).child("sentRequests");

        requestsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot requestSnapshot : snapshot.getChildren()) {
                    String senderUid = requestSnapshot.getKey();
                    if (senderUid != null) {
                        FirebaseDatabase.getInstance().getReference("users")
                                .child(senderUid).child("sentRequests").child(currentUid).removeValue();
                    }
                }
                removeSentRequests(onDone);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Ignore
                removeSentRequests(onDone);
            }
        });
    }

    /** Delete the outgoing friend requests of this user */
    private void removeSentRequests(final Runnable onDone) {
        final DatabaseReference sentRef = FirebaseDatabase.getInstance()
                .getReference("users").child(currentUid).child("sentRequests");

        sentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot requestSnapshot : snapshot.getChildren()) {
                    String receiverUid = requestSnapshot.getKey();
                    if (receiverUid != null) {
                        FirebaseDatabase.getInstance().getReference("users")
                                .child(receiverUid).child("friendRequests").child(currentUid).removeValue();
                    }
                }
                if (onDone != null) {
                    onDone.run();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Ignore
                if (onDone != null) {
                    onDone.run();
                }
            }
        });
    }
}
