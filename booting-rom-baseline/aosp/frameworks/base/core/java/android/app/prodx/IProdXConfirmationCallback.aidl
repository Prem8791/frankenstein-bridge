package android.app.prodx;

oneway interface IProdXConfirmationCallback {
    void onChallengeReady(in byte[] challenge);
    void onProofVerified(boolean success);
    void onCancelled();
}
