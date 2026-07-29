package com.android.internal.os.frankenstein;

parcelable EventDescriptor {
    String providerId;
    String eventId;
    int version;
    String schemaId;
    boolean userScoped;
    boolean replayable = true;
}
