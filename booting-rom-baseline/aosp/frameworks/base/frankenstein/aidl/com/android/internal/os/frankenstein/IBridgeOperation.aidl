package com.android.internal.os.frankenstein;

import com.android.internal.os.frankenstein.OperationResult;
import com.android.internal.os.frankenstein.OperationStatus;

interface IBridgeOperation {
    OperationStatus getStatus();
    void cancel();
    OperationResult takeResult();
}
