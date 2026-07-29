package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXSubscriptionLease implements Parcelable {
    private final String mLeaseId;
    private final String mSourceId;
    private final long mExpiresAt;

    public ProdXSubscriptionLease(String leaseId, String sourceId, long expiresAt) {
        mLeaseId = leaseId;
        mSourceId = sourceId;
        mExpiresAt = expiresAt;
    }

    protected ProdXSubscriptionLease(Parcel in) {
        mLeaseId = in.readString();
        mSourceId = in.readString();
        mExpiresAt = in.readLong();
    }

    public String getLeaseId() { return mLeaseId; }
    public String getSourceId() { return mSourceId; }
    public long getExpiresAt() { return mExpiresAt; }

    public static final Creator<ProdXSubscriptionLease> CREATOR = new Creator<ProdXSubscriptionLease>() {
        @Override public ProdXSubscriptionLease createFromParcel(Parcel in) { return new ProdXSubscriptionLease(in); }
        @Override public ProdXSubscriptionLease[] newArray(int size) { return new ProdXSubscriptionLease[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mLeaseId);
        dest.writeString(mSourceId);
        dest.writeLong(mExpiresAt);
    }
}
