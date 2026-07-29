package android.app.prodx;

oneway interface IProdXRegistryObserver {
    void onRegistryChanged(long generationId);
}
