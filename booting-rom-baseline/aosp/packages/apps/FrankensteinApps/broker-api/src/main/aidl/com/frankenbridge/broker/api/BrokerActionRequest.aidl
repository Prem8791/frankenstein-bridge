package com.frankenbridge.broker.api;

import android.os.PersistableBundle;

/** Replaceable app-facing action request. This is not part of the ROM ABI. */
parcelable BrokerActionRequest {
    int schemaVersion = 0;
    String actionId;
    PersistableBundle arguments;
}
