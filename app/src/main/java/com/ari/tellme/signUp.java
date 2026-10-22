package com.ari.tellme;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

public class signUp extends AppCompatActivity {
    private TextInputEditText fullNameEditText;
    private TextInputEditText usernameEditText;
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private TextInputEditText confirmPasswordEditText;
    private TextInputLayout textInputLayoutFullName;
    private TextInputLayout textInputLayoutUsername;
    private TextInputLayout textInputLayoutEmail;
    private TextInputLayout textInputLayoutPassword;
    private TextInputLayout textInputLayoutConfirmPassword;
    private MaterialButton buttonSignUp;
    private TextView textViewHaveAccount;
    private FirebaseAuth auth;
    private DatabaseReference userRef;
    private DatabaseReference usernamesRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);
        auth = FirebaseAuth.getInstance();
        userRef = FirebaseDatabase.getInstance().getReference("users");
        textInputLayoutFullName = findViewById(R.id.textInputLayout_fullName);
        textInputLayoutUsername = findViewById(R.id.textInputLayout_username);
        textInputLayoutEmail = findViewById(R.id.textInputLayout_email);
        textInputLayoutPassword = findViewById(R.id.textInputLayout_password);
        textInputLayoutConfirmPassword = findViewById(R.id.textInputLayout_confirmPassword);
        buttonSignUp = findViewById(R.id.button_sign_up);
        textViewHaveAccount = findViewById(R.id.textView_have_account);
        fullNameEditText = (TextInputEditText) textInputLayoutFullName.getEditText();
        usernameEditText = (TextInputEditText) textInputLayoutUsername.getEditText();
        emailEditText = (TextInputEditText) textInputLayoutEmail.getEditText();
        passwordEditText = (TextInputEditText) textInputLayoutPassword.getEditText();
        confirmPasswordEditText = (TextInputEditText) textInputLayoutConfirmPassword.getEditText();
        buttonSignUp.setOnClickListener(v -> signUp());
        textViewHaveAccount.setOnClickListener(v -> { startActivity(new Intent(this, login.class)); finish(); });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> { Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()); v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom); return insets; });

        usernamesRef = FirebaseDatabase.getInstance().getReference("usernames");
    }

    private void signUp() {
        String fullName = fullNameEditText.getText().toString().trim();
        String username = usernameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        String confirmPassword = confirmPasswordEditText.getText().toString();
        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(username) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) { Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show(); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show(); return; }
        if (password.length() < 6) { Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show(); return; }
        if (!password.equals(confirmPassword)) { Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show(); return; }

        // Create the Auth account first so we have a UID, then reserve the
        // username atomically. The reservation uses a transaction so two
        // sign-ups cannot claim the same username.
        createAccount(fullName, username, email, password);
    }

    private void createAccount(String fullName, String username, String email, String password) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                Exception ex = task.getException();
                if (ex != null) { Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show(); } else { Toast.makeText(this, "Sign up failed", Toast.LENGTH_SHORT).show(); }
                return;
            }

            final String uid = auth.getCurrentUser().getUid();
            reserveUsername(username, uid, fullName, email);
        });
    }

    /**
     * Atomically reserve the username for the given uid.
     * When the username is already taken the freshly created account is removed again.
     */
    private void reserveUsername(final String username, final String uid,
                                 final String fullName, final String email) {
        usernamesRef.child(username).runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(
                    com.google.firebase.database.MutableData currentData) {
                if (currentData.getValue() != null) {
                    return com.google.firebase.database.Transaction.abort();
                }
                HashMap<String, Object> usernameEntry = new HashMap<>();
                usernameEntry.put("uid", uid);
                currentData.setValue(usernameEntry);
                return com.google.firebase.database.Transaction.success(currentData);
            }

            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error,
                                   boolean committed,
                                   com.google.firebase.database.DataSnapshot currentData) {
                if (error != null) {
                    Toast.makeText(signUp.this, "Failed to reserve username: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    rollbackAccount(uid);
                    return;
                }
                if (!committed) {
                    usernameEditText.setError(getString(R.string.username_taken));
                    rollbackAccount(uid);
                    return;
                }
                saveProfile(uid, username, fullName, email);
            }
        });
    }

    /** Write the profile record for the new user */
    private void saveProfile(final String uid, final String username,
                             final String fullName, final String email) {
        HashMap<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("fullName", fullName);
        user.put("email", email);
        user.put("username", username);
        userRef.child(uid).setValue(user).addOnCompleteListener(task2 -> {
            if (task2.isSuccessful()) {
                com.google.firebase.auth.FirebaseUser currentUser = auth.getCurrentUser();
                Runnable navigateToLogin = () -> {
                    Intent intent = new Intent(signUp.this, login.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                };

                if (currentUser != null) {
                    currentUser.sendEmailVerification().addOnCompleteListener(task3 -> {
                        Snackbar.make(findViewById(R.id.main), R.string.verification_email_sent, Snackbar.LENGTH_INDEFINITE)
                                .setAction("OK", v -> navigateToLogin.run())
                                .show();
                    });
                } else {
                    Snackbar.make(findViewById(R.id.main), R.string.verification_email_sent, Snackbar.LENGTH_INDEFINITE)
                            .setAction("OK", v -> navigateToLogin.run())
                            .show();
                }
            } else {
                Toast.makeText(signUp.this, "Failed to save profile data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Remove the half-created account when the username could not be reserved */
    private void rollbackAccount(final String uid) {
        com.google.firebase.auth.FirebaseUser newUser = auth.getCurrentUser();
        if (newUser != null && uid.equals(newUser.getUid())) {
            newUser.delete();
        }
        auth.signOut();
    }
}
