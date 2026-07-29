package com.android.internal.os.frankenstein.diag;

import android.os.ParcelFileDescriptor;

parcelable DiagResult {
    int code;
    String schemaId;
    byte[] payload;
    ParcelFileDescriptor stream;
    long declaredLength = -1;
    boolean truncated;
    long generation;
}
