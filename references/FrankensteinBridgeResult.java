/*
 * Copyright (C) 2026 The Frankenstein Bridge Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.os.frankenstein;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Result parcel for Frankenstein Bridge capability calls.
 *
 * @hide
 */
public class FrankensteinBridgeResult implements Parcelable {

    public static final int STATUS_OK = 0;
    public static final int STATUS_DENIED = 1;
    public static final int STATUS_ERROR = 2;

    public static final int DENIAL_NONE = 0;
    public static final int DENIAL_INVALID_CALLER = 1;
    public static final int DENIAL_PERMISSION = 2;
    public static final int DENIAL_UNAVAILABLE = 3;

    public int status;
    public int denialCode;
    public String errorMessage;
    public Bundle data;
    public long latencyMs;

    public FrankensteinBridgeResult() {
        this.status = STATUS_OK;
        this.denialCode = DENIAL_NONE;
        this.errorMessage = null;
        this.data = null;
        this.latencyMs = 0;
    }

    public FrankensteinBridgeResult(int status, int denialCode, String errorMessage,
            Bundle data, long latencyMs) {
        this.status = status;
        this.denialCode = denialCode;
        this.errorMessage = errorMessage;
        this.data = data;
        this.latencyMs = latencyMs;
    }

    public static FrankensteinBridgeResult ok(Bundle data) {
        return new FrankensteinBridgeResult(STATUS_OK, DENIAL_NONE, null, data, 0);
    }

    public static FrankensteinBridgeResult ok(Bundle data, long latencyMs) {
        return new FrankensteinBridgeResult(STATUS_OK, DENIAL_NONE, null, data, latencyMs);
    }

    public static FrankensteinBridgeResult denied(int denialCode, String message) {
        return new FrankensteinBridgeResult(STATUS_DENIED, denialCode, message, null, 0);
    }

    public static FrankensteinBridgeResult error(String message) {
        return new FrankensteinBridgeResult(STATUS_ERROR, DENIAL_NONE, message, null, 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(status);
        dest.writeInt(denialCode);
        dest.writeString(errorMessage);
        dest.writeBundle(data);
        dest.writeLong(latencyMs);
    }

    public static final Parcelable.Creator<FrankensteinBridgeResult> CREATOR =
            new Parcelable.Creator<FrankensteinBridgeResult>() {
                public FrankensteinBridgeResult createFromParcel(Parcel in) {
                    return new FrankensteinBridgeResult(in);
                }
                public FrankensteinBridgeResult[] newArray(int size) {
                    return new FrankensteinBridgeResult[size];
                }
            };

    private FrankensteinBridgeResult(Parcel in) {
        status = in.readInt();
        denialCode = in.readInt();
        errorMessage = in.readString();
        data = in.readBundle();
        latencyMs = in.readLong();
    }
}
