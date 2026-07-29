package com.android.internal.os.frankenstein;

parcelable BridgePayload {
    String schemaId;
    int schemaVersion;
    int encoding;
    byte[] data;
}
