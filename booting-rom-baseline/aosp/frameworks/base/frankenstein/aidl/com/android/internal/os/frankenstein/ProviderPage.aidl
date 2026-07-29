package com.android.internal.os.frankenstein;

import com.android.internal.os.frankenstein.ProviderDescriptor;

parcelable ProviderPage {
    ProviderDescriptor[] providers;
    String nextPageToken;
    long catalogGeneration;
}
