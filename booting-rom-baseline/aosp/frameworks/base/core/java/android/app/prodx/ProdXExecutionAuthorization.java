package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXExecutionAuthorization implements Parcelable {
    private final String mAuthorizationId;
    private final byte[] mToken;
    private final long mExpiresAt;
    private final Long mExpiryMs;
    private final String mAudience;
    private final Integer mCallerUid;
    private final Long mRegistryGeneration;
    private final Long mPolicyEpoch;
    private final Long mGrantEpoch;
    private final String mNonce;
    private final String mProof;

    public ProdXExecutionAuthorization(String authorizationId, byte[] token, long expiresAt) {
        mAuthorizationId = authorizationId;
        mToken = token;
        mExpiresAt = expiresAt;
        mExpiryMs = null;
        mAudience = null;
        mCallerUid = null;
        mRegistryGeneration = null;
        mPolicyEpoch = null;
        mGrantEpoch = null;
        mNonce = null;
        mProof = null;
    }

    public ProdXExecutionAuthorization(String authorizationId, byte[] token, long expiresAt,
                                        Long expiryMs, String audience, Integer callerUid,
                                        Long registryGeneration, Long policyEpoch, Long grantEpoch,
                                        String nonce, String proof) {
        mAuthorizationId = authorizationId;
        mToken = token;
        mExpiresAt = expiresAt;
        mExpiryMs = expiryMs;
        mAudience = audience;
        mCallerUid = callerUid;
        mRegistryGeneration = registryGeneration;
        mPolicyEpoch = policyEpoch;
        mGrantEpoch = grantEpoch;
        mNonce = nonce;
        mProof = proof;
    }

    protected ProdXExecutionAuthorization(Parcel in) {
        mAuthorizationId = in.readString();
        mToken = in.createByteArray();
        mExpiresAt = in.readLong();
        mExpiryMs = in.readLong();
        mAudience = in.readString();
        mCallerUid = in.readInt();
        mRegistryGeneration = in.readLong();
        mPolicyEpoch = in.readLong();
        mGrantEpoch = in.readLong();
        mNonce = in.readString();
        mProof = in.readString();
    }

    public String getAuthorizationId() { return mAuthorizationId; }
    public byte[] getToken() { return mToken; }
    public long getExpiresAt() { return mExpiresAt; }
    public Long getExpiryMs() { return mExpiryMs; }
    public String getAudience() { return mAudience; }
    public Integer getCallerUid() { return mCallerUid; }
    public Long getRegistryGeneration() { return mRegistryGeneration; }
    public Long getPolicyEpoch() { return mPolicyEpoch; }
    public Long getGrantEpoch() { return mGrantEpoch; }
    public String getNonce() { return mNonce; }
    public String getProof() { return mProof; }

    public static final Creator<ProdXExecutionAuthorization> CREATOR = new Creator<ProdXExecutionAuthorization>() {
        @Override public ProdXExecutionAuthorization createFromParcel(Parcel in) { return new ProdXExecutionAuthorization(in); }
        @Override public ProdXExecutionAuthorization[] newArray(int size) { return new ProdXExecutionAuthorization[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mAuthorizationId);
        dest.writeByteArray(mToken);
        dest.writeLong(mExpiresAt);
        dest.writeLong(mExpiryMs != null ? mExpiryMs : 0L);
        dest.writeString(mAudience);
        dest.writeInt(mCallerUid != null ? mCallerUid : 0);
        dest.writeLong(mRegistryGeneration != null ? mRegistryGeneration : 0L);
        dest.writeLong(mPolicyEpoch != null ? mPolicyEpoch : 0L);
        dest.writeLong(mGrantEpoch != null ? mGrantEpoch : 0L);
        dest.writeString(mNonce);
        dest.writeString(mProof);
    }
}
