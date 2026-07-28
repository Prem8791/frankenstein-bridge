package com.android.internal.os.frankenstein.diag;

import com.android.internal.os.frankenstein.diag.DiagRequest;
import com.android.internal.os.frankenstein.diag.DiagResult;

interface IFrankensteinDiagnostic {
    long getGeneration();
    DiagResult execute(in DiagRequest request);
    void cancel(long operationId);
}
