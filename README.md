# Tellme - Daily Messaging App for Android

Tellme is a modern, real-time Android messaging application built with Java, Jetpack components, and Firebase (Authentication & Realtime Database). It features secure user registration with email verification, real-time 1-on-1 chat with "Delivered" (`✓`) and "Seen" (`✓✓`) receipts, friend management, and username discovery.

---

## Features

- **Authentication & Security**:
  - Email & password sign up with automated email verification dispatch.
  - Login security enforcement preventing unverified users from accessing the app.
  - Password reset and secure re-authentication for sensitive changes.
- **Real-Time Messaging**:
  - 1-on-1 real-time chat threads with deterministic chat IDs (`chatId`).
  - WhatsApp-style chat bubbles with rounded corner styling.
  - **Delivered (`✓`)** and **Seen (`✓✓`)** status tracking.
- **Friend Management & User Search**:
  - Search registered users by username, full name, or email.
  - Send, accept, or reject friend requests.
  - View friends list and start instant chats.
- **Modern UI/UX**:
  - Built with Material Design 3 components, Edge-to-Edge support, and intuitive navigation.

---

## Project Architecture & Tech Stack

- **Language**: Java
- **UI Framework**: Android Views (ConstraintLayout, RecyclerView, Material Components)
- **Backend**: Firebase Realtime Database & Firebase Authentication
- **Min SDK**: 30 (Android 11+)
- **Compile SDK**: 37

---

## Project Structure

```
com.ari.tellme/
├── MainActivity.java           # Home dashboard & friends list
├── ChatActivity.java           # Real-time chat & read tracking
├── login.java                  # Sign in with email verification check
├── signUp.java                 # Account creation & verification email
├── SearchUsersActivity.java    # User discovery & search
├── friendRequests.java         # Friend requests (incoming/sent)
├── profile.java                # User profile view
├── accountSettings.java        # Security & account settings
├── forgetPassword.java         # Password reset flow
├── ChatMessage.java            # Message data model
├── ChatMessageAdapter.java     # RecyclerView adapter for chat messages
├── User.java                   # User profile data model
└── ... (Adapters & helper classes)
```

---

## Setup & Configuration

1. Clone the repository.
2. Add your `google-services.json` file into the `app/` directory.
3. Sync project with Gradle files.
4. Deploy to an Android emulator or physical device (API 30+).

---

## License

MIT License
