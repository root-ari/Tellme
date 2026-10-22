package com.ari.tellme;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;

import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

public class login extends AppCompatActivity {
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private TextInputLayout textInputLayoutEmail;
    private TextInputLayout textInputLayoutPassword;
    private MaterialButton buttonSignIn;
    private MaterialButton buttonSignUp;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        auth = FirebaseAuth.getInstance();

        textInputLayoutEmail = findViewById(R.id.textInputLayout_email);
        textInputLayoutPassword = findViewById(R.id.textInputLayout_password);
        buttonSignIn = findViewById(R.id.button_sign_in);
        buttonSignUp = findViewById(R.id.button_sign_up);
        emailEditText = (TextInputEditText) textInputLayoutEmail.getEditText();
        passwordEditText = (TextInputEditText) textInputLayoutPassword.getEditText();

        buttonSignIn.setOnClickListener(v -> signIn());
        buttonSignUp.setOnClickListener(v -> {
            startActivity(new Intent(this, signUp.class));
        });
        findViewById(R.id.textView_forget).setOnClickListener(v -> resetPassword());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void signIn() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) { Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show(); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show(); return; }
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
                if (user != null && !user.isEmailVerified()) {
                    Toast.makeText(this, R.string.email_not_verified, Toast.LENGTH_LONG).show();
                    auth.signOut();
                    return;
                }
                Toast.makeText(this, "Signed in successfully", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                // Finish the login screen so it is not kept on the back stack.
                // Otherwise pressing back from the home screen returns to the
                // already-authenticated login form.
                finish();
            } else {
                Exception ex = task.getException();
                if (ex != null) { Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show(); } else { Toast.makeText(this, "Sign in failed", Toast.LENGTH_SHORT).show(); }
            }
        });
    }

    private void signUp() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) { Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show(); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show(); return; }
        if (password.length() < 6) { Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show(); return; }
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Account created. Please sign in.", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, login.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                Exception ex = task.getException();
                if (ex != null) { Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show(); } else { Toast.makeText(this, "Sign up failed", Toast.LENGTH_SHORT).show(); }
            }
        });
    }

    private void resetPassword() {
        String email = emailEditText.getText().toString().trim();
        if (TextUtils.isEmpty(email)) { Toast.makeText(this, "Enter your email address", Toast.LENGTH_SHORT).show(); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show(); return; }
        auth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            if (task.isSuccessful()) { Toast.makeText(this, "Reset link sent. Check your inbox.", Toast.LENGTH_SHORT).show(); } else {
                Exception ex = task.getException();
                if (ex != null) { Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show(); } else { Toast.makeText(this, "Unable to send reset email", Toast.LENGTH_SHORT).show(); }
            }
        });
    }
}
