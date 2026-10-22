package com.ari.tellme;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the incoming friend requests list.
 * Each row can be accepted or rejected through the supplied listener.
 */
public class FriendRequestsAdapter extends RecyclerView.Adapter<FriendRequestsAdapter.RequestViewHolder>
        implements RequestProfileUpdater {

    public interface OnRequestActionListener {
        void onAccept(FriendRequest request);

        void onReject(FriendRequest request);
    }

    private final List<FriendRequest> requests = new ArrayList<>();
    private final OnRequestActionListener listener;

    public FriendRequestsAdapter(OnRequestActionListener listener) {
        this.listener = listener;
    }

    public void setRequests(List<FriendRequest> newRequests) {
        requests.clear();
        requests.addAll(newRequests);
        notifyDataSetChanged();
    }

    @Override
    public void updateRequestProfile(String uid, String fullName, String email, String username) {
        for (FriendRequest request : requests) {
            if (uid != null && uid.equals(request.getUid())) {
                request.setFullName(fullName);
                request.setEmail(email);
                request.setUsername(username);
                notifyDataSetChanged();
                return;
            }
        }
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.friend_request_item, parent, false);
        return new RequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        holder.bind(requests.get(position));
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    class RequestViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageAvatar;
        private final TextView textUsername;
        private final TextView textEmail;
        private final MaterialButton buttonAccept;
        private final MaterialButton buttonReject;

        RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            imageAvatar = itemView.findViewById(R.id.image_request_avatar);
            textUsername = itemView.findViewById(R.id.text_request_username);
            textEmail = itemView.findViewById(R.id.text_request_email);
            buttonAccept = itemView.findViewById(R.id.button_accept_request);
            buttonReject = itemView.findViewById(R.id.button_reject_request);
        }

        void bind(FriendRequest request) {
            textUsername.setText(request.getDisplayName());
            textEmail.setText(request.getEmail() != null ? request.getEmail() : "");

            buttonAccept.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAccept(request);
                }
            });

            buttonReject.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReject(request);
                }
            });
        }
    }
}
