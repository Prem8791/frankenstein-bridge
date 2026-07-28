package com.android.internal.os.frankenstein.diag;

parcelable DiagRequest {
    int operation;
    String scope;
    String opaqueId;
    long offset;
    long length;
    int timeoutMs;
    String[] filters;
}
