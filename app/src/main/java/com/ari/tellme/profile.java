package com.ari.tellme;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class profile extends AppCompatActivity {

    private TextView textDisplayName;
    private TextView textUsername;
    private TextView textEmail;
    private FirebaseAuth auth;
    private DatabaseReference usersRef;
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);

        textDisplayName = findViewById(R.id.text_display_name);
        textUsername = findViewById(R.id.text_username);
        textEmail = findViewById(R.id.text_email);

        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser != null) {
            currentUid = currentUser.getUid();
            textEmail.setText(currentUser.getEmail() != null ? currentUser.getEmail() : getString(R.string.placeholder_email));

            usersRef = FirebaseDatabase.getInstance().getReference("users").child(currentUid);
            usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    String fullName = snapshot.child("fullName").getValue(String.class);
                    if (fullName != null && !fullName.isEmpty()) {
                        textDisplayName.setText(fullName);
                    }
                    String username = snapshot.child("username").getValue(String.class);
                    if (username != null && !username.isEmpty()) {
                        textUsername.setText("@" + username);
                    }
                    String emailDb = snapshot.child("email").getValue(String.class);
                    if (emailDb != null && !emailDb.isEmpty()) {
                        textEmail.setText(emailDb);
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    Toast.makeText(profile.this, "Unable to load profile: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(this, "No user signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}
