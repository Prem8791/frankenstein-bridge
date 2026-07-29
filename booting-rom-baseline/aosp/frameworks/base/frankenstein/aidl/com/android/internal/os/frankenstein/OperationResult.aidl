package com.android.internal.os.frankenstein;

import android.os.ParcelFileDescriptor;
import com.android.internal.os.frankenstein.BridgePayload;
import com.android.internal.os.frankenstein.OperationStatus;

parcelable OperationResult {
    OperationStatus status;
    BridgePayload payload;
    ParcelFileDescriptor stream;
    String contentType;
    long declaredLength = -1;
    byte[] sha256;
    boolean truncated;
    String[] warnings;
}
