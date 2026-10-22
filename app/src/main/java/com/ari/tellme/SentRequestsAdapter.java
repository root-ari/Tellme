package com.ari.tellme;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the list of friend requests this user has sent.
 */
public class SentRequestsAdapter extends RecyclerView.Adapter<SentRequestsAdapter.SentRequestViewHolder>
        implements RequestProfileUpdater {

    private final List<FriendRequest> requests = new ArrayList<>();

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
    public SentRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.sent_request_item, parent, false);
        return new SentRequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SentRequestViewHolder holder, int position) {
        holder.bind(requests.get(position));
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    class SentRequestViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageAvatar;
        private final TextView textUsername;
        private final TextView textEmail;

        SentRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            imageAvatar = itemView.findViewById(R.id.image_sent_request_avatar);
            textUsername = itemView.findViewById(R.id.text_sent_request_username);
            textEmail = itemView.findViewById(R.id.text_sent_request_email);
        }

        void bind(FriendRequest request) {
            textUsername.setText(request.getDisplayName());
            textEmail.setText(request.getEmail() != null ? request.getEmail() : "");
        }
    }
}
