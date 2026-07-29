package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXToken implements Parcelable {
    private final byte[] mTokenData;
    private final String mGrantId;
    private final long mEpoch;
    private final long mExpiresAt;
    private final String mAudience;
    private final long mPolicyEpoch;
    private final String mCallerIdentity;

    public ProdXToken(String grantId, long epoch, long expiresAt, String audience,
                      long policyEpoch, String callerIdentity) {
        mTokenData = new byte[0];
        mGrantId = grantId;
        mEpoch = epoch;
        mExpiresAt = expiresAt;
        mAudience = audience;
        mPolicyEpoch = policyEpoch;
        mCallerIdentity = callerIdentity;
    }

    protected ProdXToken(Parcel in) {
        mTokenData = in.createByteArray();
        mGrantId = in.readString();
        mEpoch = in.readLong();
        mExpiresAt = in.readLong();
        mAudience = in.readString();
        mPolicyEpoch = in.readLong();
        mCallerIdentity = in.readString();
    }

    public static ProdXToken decode(byte[] data) {
        return null;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > mExpiresAt;
    }

    public long getEpoch() { return mEpoch; }
    public long getExpiresAt() { return mExpiresAt; }
    public String getGrantId() { return mGrantId; }
    public String getAudience() { return mAudience; }
    public long getPolicyEpoch() { return mPolicyEpoch; }
    public byte[] getTokenData() { return mTokenData; }

    public boolean isBoundTo(String callerIdentity) {
        return mCallerIdentity != null && mCallerIdentity.equals(callerIdentity);
    }

    public static final Creator<ProdXToken> CREATOR = new Creator<ProdXToken>() {
        @Override public ProdXToken createFromParcel(Parcel in) { return new ProdXToken(in); }
        @Override public ProdXToken[] newArray(int size) { return new ProdXToken[size]; }
    };

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(mTokenData);
        dest.writeString(mGrantId);
        dest.writeLong(mEpoch);
        dest.writeLong(mExpiresAt);
        dest.writeString(mAudience);
        dest.writeLong(mPolicyEpoch);
        dest.writeString(mCallerIdentity);
    }
}
