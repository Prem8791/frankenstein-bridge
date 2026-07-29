package com.frankenbridge.broker.api;

import android.os.PersistableBundle;

/** Versioned result returned by the replaceable app-facing broker. */
parcelable BrokerActionResult {
    const int STATUS_OK = 0;
    const int STATUS_INVALID_REQUEST = 1;
    const int STATUS_NOT_AUTHORIZED = 2;
    const int STATUS_UNSUPPORTED = 3;
    const int STATUS_UNAVAILABLE = 4;
    const int STATUS_BUSY = 5;
    const int STATUS_INTERNAL_ERROR = 6;

    int schemaVersion = 1;
    int status = STATUS_INTERNAL_ERROR;
    String message;
    PersistableBundle data;
}
