package com.ari.tellme;

/**
 * Implemented by the adapters that show friend requests so that the profile
 * data of a request can be replaced when it is refreshed from the "users" node.
 * <p>
 * The lookup happens by uid and not by object reference because the adapters
 * rebuild their list every time the database sends a new value, so a request
 * object held by a listener can already be outdated.
 */
public interface RequestProfileUpdater {
    /**
     * Replace the display data of the request with the given uid.
     * Does nothing when no request with that uid is displayed.
     */
    void updateRequestProfile(String uid, String fullName, String email, String username);
}
