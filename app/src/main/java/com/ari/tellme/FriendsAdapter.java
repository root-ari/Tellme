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

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.FriendViewHolder> {

    public interface OnFriendClickListener {
        void onFriendClick(User friend);
    }

    private final List<User> friends = new ArrayList<>();
    private final OnFriendClickListener listener;

    public FriendsAdapter(OnFriendClickListener listener) {
        this.listener = listener;
    }

    public void setFriends(List<User> newFriends) {
        friends.clear();
        friends.addAll(newFriends);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.friend_item, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        User friend = friends.get(position);
        holder.bind(friend);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFriendClick(friend);
            }
        });
    }

    @Override
    public int getItemCount() {
        return friends.size();
    }

    class FriendViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageAvatar;
        private final TextView textUsername;
        private final TextView textEmail;

        public FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            imageAvatar = itemView.findViewById(R.id.image_friend_avatar);
            textUsername = itemView.findViewById(R.id.text_friend_username);
            textEmail = itemView.findViewById(R.id.text_friend_email);
        }

        public void bind(User user) {
            textUsername.setText(user.getUsername() != null ? user.getUsername() : "Unknown");
            textEmail.setText(user.getEmail() != null ? user.getEmail() : "");
        }
    }
}