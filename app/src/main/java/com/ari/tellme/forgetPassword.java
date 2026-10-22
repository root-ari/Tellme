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
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

public class forgetPassword extends AppCompatActivity {

    private TextInputLayout textInputLayoutEmail;
    private TextInputEditText emailEditText;
    private MaterialButton buttonSendReset;
    private TextView textViewBackToSignIn;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_forget_password);

        textInputLayoutEmail = findViewById(R.id.textInputLayout_email);
        buttonSendReset = findViewById(R.id.button_send_reset);
        textViewBackToSignIn = findViewById(R.id.textView_back_to_sign_in);

        emailEditText = (TextInputEditText) textInputLayoutEmail.getEditText();

        auth = FirebaseAuth.getInstance();

        textViewBackToSignIn.setOnClickListener(v -> {
            Intent intent = new Intent(forgetPassword.this, login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        buttonSendReset.setOnClickListener(v -> sendResetPassword());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void sendResetPassword() {
        String email = emailEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Sending reset link...", Toast.LENGTH_SHORT).show();

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Reset link sent. Check your inbox.", Toast.LENGTH_SHORT).show();
                    } else {
                        Exception ex = task.getException();
                        if (ex instanceof FirebaseAuthInvalidUserException) {
                            Toast.makeText(this, "No account found with this email", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}
