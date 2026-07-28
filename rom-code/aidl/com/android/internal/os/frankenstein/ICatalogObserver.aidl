package com.android.internal.os.frankenstein;

oneway interface ICatalogObserver {
    void onCatalogChanged(long generation, in String[] changedProviderIds);
}
