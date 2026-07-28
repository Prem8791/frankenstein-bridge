package com.android.internal.os.frankenstein;

parcelable ExternalProviderRegistration {
    String providerId;
    long generation;
    String token;
    int ownerUid;
    int ownerUserId;
    long expiresElapsedMs;
}
