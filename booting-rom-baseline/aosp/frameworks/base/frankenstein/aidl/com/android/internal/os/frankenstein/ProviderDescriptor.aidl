package com.android.internal.os.frankenstein;

parcelable ProviderDescriptor {
    String providerId;
    int providerVersion;
    long generation;
    int state;
    int ownerKind;
    String unavailableReason;
    String[] operationIds;
    String[] eventIds;
    String[] schemaIds;
}
