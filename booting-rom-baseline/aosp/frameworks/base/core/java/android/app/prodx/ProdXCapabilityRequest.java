package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXCapabilityRequest implements Parcelable {
    private final String mCapabilityId;
    private final String mPurpose;
    private final String mTargetProvider;
    private final String mIdempotencyKey;
    private final String mSchemaRef;
    private final Long mTimeoutMs;

    public ProdXCapabilityRequest(
            String capabilityId,
            String purpose,
            String targetProvider,
            String idempotencyKey,
            String schemaRef,
            Long timeoutMs) {
        mCapabilityId = capabilityId;
        mPurpose = purpose;
        mTargetProvider = targetProvider;
        mIdempotencyKey = idempotencyKey;
        mSchemaRef = schemaRef;
        mTimeoutMs = timeoutMs;
    }

    protected ProdXCapabilityRequest(Parcel in) {
        mCapabilityId = in.readString();
        mPurpose = in.readString();
        mTargetProvider = in.readString();
        mIdempotencyKey = in.readString();
        mSchemaRef = in.readString();
        mTimeoutMs = in.readLong();
    }

    public String getCapabilityId() { return mCapabilityId; }
    public String getPurpose() { return mPurpose; }
    public String getTargetProvider() { return mTargetProvider; }
    public String getIdempotencyKey() { return mIdempotencyKey; }
    public String getSchemaRef() { return mSchemaRef; }
    public Long getTimeoutMs() { return mTimeoutMs; }

    public ProdXCapabilityDescriptor getDescriptor() {
        return new ProdXCapabilityDescriptor(mCapabilityId, mTargetProvider, "1");
    }


    public static final Creator<ProdXCapabilityRequest> CREATOR = new Creator<ProdXCapabilityRequest>() {
        @Override public ProdXCapabilityRequest createFromParcel(Parcel in) { return new ProdXCapabilityRequest(in); }
        @Override public ProdXCapabilityRequest[] newArray(int size) { return new ProdXCapabilityRequest[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mCapabilityId);
        dest.writeString(mPurpose);
        dest.writeString(mTargetProvider);
        dest.writeString(mIdempotencyKey);
        dest.writeString(mSchemaRef);
        dest.writeLong(mTimeoutMs != null ? mTimeoutMs : 0L);
    }
}
