package com.android.server.frankenstein;

final class BridgeConstants {
    static final String SERVICE_NAME = "frankenstein";
    static final String BROKER_PACKAGE = "com.frankenbridge.assistant";
    static final String ACCESS_PERMISSION =
            "android.permission.ACCESS_FRANKENSTEIN_BRIDGE";
    static final String REGISTER_PERMISSION =
            "android.permission.REGISTER_FRANKENSTEIN_PROVIDER";

    static final int OK = 0;
    static final int INVALID_ARGUMENT = 1;
    static final int PERMISSION_DENIED = 2;
    static final int USER_RESTRICTED = 3;
    static final int NOT_READY = 4;
    static final int UNAVAILABLE = 5;
    static final int VERSION_UNSUPPORTED = 6;
    static final int RESOURCE_EXHAUSTED = 7;
    static final int TIMEOUT = 8;
    static final int CANCELLED = 9;
    static final int PARTIAL = 10;
    static final int STALE_HANDLE = 11;
    static final int INTERNAL = 12;

    static final int MAX_INLINE_BYTES = 64 * 1024;
    static final int MAX_OPERATIONS_PER_UID = 16;
    static final int MAX_SUBSCRIPTIONS_PER_UID = 8;
    static final int MAX_STREAMS_PER_UID = 4;
    static final int MAX_EXTERNAL_PROVIDERS_PER_UID = 16;
    static final int MAX_EXTERNAL_METADATA_BYTES = 1024 * 1024;

    private BridgeConstants() {}
}
