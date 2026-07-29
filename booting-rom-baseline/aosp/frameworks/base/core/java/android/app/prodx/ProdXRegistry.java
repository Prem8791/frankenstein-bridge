package android.app.prodx;

public class ProdXRegistry {
    private long mCurrentEpoch = 0;

    public ProdXRegistry() {}

    public long currentEpoch() {
        return mCurrentEpoch;
    }

    public String resolveCallerIdentity(int callerUid) {
        return "uid:" + callerUid;
    }
}
