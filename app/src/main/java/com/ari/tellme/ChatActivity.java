package com.ari.tellme;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatActivity extends AppCompatActivity {

    private String currentUid;
    private String peerUid;
    private DatabaseReference messagesRef;
    private com.google.android.material.textfield.TextInputEditText editMessage;
    private com.google.android.material.button.MaterialButton buttonSend;
    private RecyclerView recyclerViewMessages;
    private ChatMessageAdapter adapter;
    private View textEmptyChat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, login.class));
            finish();
            return;
        }
        currentUid = user.getUid();

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        peerUid = intent.getStringExtra("peerUid");
        String peerFullName = intent.getStringExtra("peerFullName");
        if (peerUid == null) {
            Toast.makeText(this, R.string.user_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(peerFullName != null ? peerFullName : peerUid);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        editMessage = findViewById(R.id.edit_message);
        buttonSend = findViewById(R.id.button_send);
        recyclerViewMessages = findViewById(R.id.recycler_view_messages);
        textEmptyChat = findViewById(R.id.text_empty_chat);

        String chatId = getChatId(currentUid, peerUid);
        messagesRef = FirebaseDatabase.getInstance()
                .getReference("chats").child(chatId).child("messages");

        adapter = new ChatMessageAdapter(currentUid);
        recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewMessages.setAdapter(adapter);

        Query query = messagesRef.orderByChild("timestamp");
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ChatMessage> loaded = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ChatMessage message = child.getValue(ChatMessage.class);
                    if (message != null) {
                        if (message.getMessageId() == null) {
                            message.setMessageId(child.getKey());
                        }
                        if (message.getReceiverUid() != null && message.getReceiverUid().equals(currentUid) && !message.isRead()) {
                            String key = child.getKey();
                            if (key != null) {
                                messagesRef.child(key).child("read").setValue(true);
                            }
                            message.setRead(true);
                        }
                        loaded.add(message);
                    }
                }
                if (loaded.isEmpty()) {
                    textEmptyChat.setVisibility(View.VISIBLE);
                } else {
                    textEmptyChat.setVisibility(View.GONE);
                }
                adapter.setMessages(loaded);
                if (!loaded.isEmpty()) {
                    recyclerViewMessages.scrollToPosition(loaded.size() - 1);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChatActivity.this,
                        "Failed to load messages: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });

        buttonSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String text = editMessage.getText() != null ? editMessage.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) {
            editMessage.setError(getString(R.string.field_required));
            return;
        }

        buttonSend.setEnabled(false);

        // Optimistic local message using a client-generated id so the listener
        // updates the same node instead of creating a duplicate.
        String messageId = UUID.randomUUID().toString();
        ChatMessage message = new ChatMessage();
        message.setMessageId(messageId);
        message.setSenderUid(currentUid);
        message.setReceiverUid(peerUid);
        message.setText(text);
        message.setTimestamp(System.currentTimeMillis());
        message.setRead(false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("senderUid", currentUid);
        payload.put("receiverUid", peerUid);
        payload.put("text", text);
        payload.put("timestamp", ServerValue.TIMESTAMP);
        payload.put("read", false);

        adapter.addMessage(message);
        recyclerViewMessages.scrollToPosition(adapter.getItemCount() - 1);

        messagesRef.child(messageId).setValue(payload)
                .addOnSuccessListener(aVoid -> editMessage.setText(""))
                .addOnFailureListener(e -> Toast.makeText(ChatActivity.this,
                        "Message not sent: " + e.getMessage(), Toast.LENGTH_SHORT).show())
                .addOnCompleteListener(task -> buttonSend.setEnabled(true));
    }

    /**
     * Deterministic key from both uids so there is only one chat thread
     * between a given pair of users.
     */
    private static String getChatId(String uidA, String uidB) {
        return uidA.compareTo(uidB) < 0 ? uidA + "_" + uidB : uidB + "_" + uidA;
    }
}
