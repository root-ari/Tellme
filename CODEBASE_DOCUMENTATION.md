# Tellme App - Complete Codebase Documentation & Architecture Guide

This document provides a thorough, deep-dive explanation of every component, activity, data model, security rule, and feature implemented in the **Tellme** Android application.

---

## 1. High-Level Architecture & Overview

Tellme is structured around the **Model-View-Controller / Activity-based Android architecture** combined with **Firebase Realtime Database** for real-time synchronization and **Firebase Authentication** for identity management.

```
[UI Layer (Activities & Adapters)]
         ↕
[Firebase Client SDK]
         ↕
[Firebase Security Rules (database.rules.json)]
         ↕
[Firebase Realtime Database / Auth Backend]
```

---

## 2. Core Modules & Component Breakdown

### A. Authentication & Security Layer
- **[signUp.java](file:///D:/Projects/Apps/Tellme/app/src/main/java/com/ari/tellme/signUp.java)**:
  - Handles user registration (`createUserWithEmailAndPassword`).
  - Atomically reserves unique usernames using Firebase Realtime Database Transactions (`runTransaction`) under `/usernames/{username}`.
  - Sends email verification (`sendEmailVerification()`) and signs out the user immediately (`auth.signOut()`) so unverified accounts cannot log in.
- **[login.java](file:///D:/Projects/Apps/Tellme/app/src/main/java/com/ari/tellme/login.java)**:
  - Authenticates users via email and password (`signInWithEmailAndPassword`).
  - Verifies `user.isEmailVerified()`. If unverified, displays a notice, signs out, and blocks entry.
- **[forgetPassword.java](file:///D:/Projects/Apps/Tellme/app/src/main/java/com/ari/tellme/forgetPassword.java)**:
  - Triggers password reset emails (`sendPasswordResetEmail`).
- **[accountSettings.java](file:///D:/Projects/Apps/Tellme/app/src/main/java/com/ari/tellme/accountSettings.java)**:
  - Allows password updates, email changes with re-authentication, and account deletion.

### B. Messaging & Chat Layer
- **[ChatActivity.java](file:///D:/Projects/Apps/Tellme/app/src/main/java/com/ari/tellme/ChatActivity.java)**:
  - Establishes a deterministic chat thread ID (`getChatId()`) combining both user UIDs alphabetically.
  - Listens for real-time message updates (`addValueEventListener`) ordered by `timestamp`.
  - Automatically marks incoming unread messages as seen (`read = true`) when loaded.
  - Sends messages optimistically with unique message IDs (`UUID.randomUUID()`).
- **[ChatMessage.java](file:///D:/Projects/Apps/Tellme/app/src/main/java/com/ari/tellme/ChatMessage.java)**:
  - Data model storing `messageId`, `senderUid`, `receiverUid`, `text`, `timestamp`, `read`, and `delivered`.
- **[ChatMessageAdapter.java](file:///D:/Projects/Apps/Tellme/app/src/main/java/com/ari/tellme/ChatMessageAdapter.java)**:
  - Manages sent vs. received message view types.
  - Binds message text, formatted timestamps (`HH:mm`), and delivery/seen status indicators (`✓` for delivered, `✓✓` for seen).

### C. Friend Discovery & Management Layer
- **[MainActivity.java](file:///D:/Projects/Apps/Tellme/app/src/main/java/com/ari/tellme/MainActivity.java)**:
  - Main dashboard displaying welcome greetings, quick actions, and the user's friends list.
- **[SearchUsersActivity.java](file:///D:/Projects/Apps/Tellme/app/src/main/java/com/ari/tellme/SearchUsersActivity.java)**:
  - Global user search across registered profiles matching names, usernames, or emails in real-time.
- **[friendRequests.java](file:///D:/Projects/Apps/Tellme/app/src/main/java/com/ari/tellme/friendRequests.java)**:
  - Manages incoming and outgoing friend requests with accept/reject functionality.

---

## 3. Security & Database Rules (`database.rules.json`)

The database enforces strict access controls:
- **Users (`/users/$uid`)**: Readable by authenticated users; writable only by the owning user.
- **Usernames (`/usernames/$username`)**: Readable by authenticated users; atomic reservation checks ownership on write.
- **Chats (`/chats/$chatId`)**: Restricted to participants where `$chatId.contains(auth.uid)`.
- **Messages (`/chats/$chatId/messages/$messageId`)**: Indexed on `"timestamp"` for optimized query sorting (`.indexOn`), and readable/writable strictly by chat participants.
