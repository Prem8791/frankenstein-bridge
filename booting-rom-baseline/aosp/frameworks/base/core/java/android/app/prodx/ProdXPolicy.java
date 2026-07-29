package android.app.prodx;

public class ProdXPolicy {
    public ProdXPolicy() {}

    public PolicyResult evaluate(String capabilityId, String callerIdentity, ProdXGrant grant) {
        return new PolicyResult(true, "allowed");
    }

    public static class PolicyResult {
        private final boolean mAllowed;
        private final String mReason;

        public PolicyResult(boolean allowed, String reason) {
            mAllowed = allowed;
            mReason = reason;
        }

        public boolean isAllowed() { return mAllowed; }
        public String denyReason() { return mReason; }
        public double confidence() { return mAllowed ? 1.0 : 0.0; }
    }
}
