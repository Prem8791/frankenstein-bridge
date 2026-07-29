package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.List;
import java.util.ArrayList;

public class ProdXGrant implements Parcelable {

    public ProdXGrant() {
        this("", 0, "", "", 0L, 0L, false, false);
    }
    private final String mGrantId;
    private final int mUserId;
    private final String mPackageName;
    private final String mCapabilityId;
    private final long mGrantedAt;
    private final long mExpiresAt;
    private final boolean mActive;
    private final boolean mRevoked;

    public ProdXGrant(String grantId, int userId, String packageName, String capabilityId,
                      long grantedAt, boolean active) {
        this(grantId, userId, packageName, capabilityId, grantedAt, 0L, active, false);
    }

    public ProdXGrant(String grantId, int userId, String packageName, String capabilityId,
                      long grantedAt, long expiresAt, boolean active, boolean revoked) {
        mGrantId = grantId;
        mUserId = userId;
        mPackageName = packageName;
        mCapabilityId = capabilityId;
        mGrantedAt = grantedAt;
        mExpiresAt = expiresAt;
        mActive = active;
        mRevoked = revoked;
    }

    protected ProdXGrant(Parcel in) {
        mGrantId = in.readString();
        mUserId = in.readInt();
        mPackageName = in.readString();
        mCapabilityId = in.readString();
        mGrantedAt = in.readLong();
        mExpiresAt = in.readLong();
        mActive = in.readBoolean();
        mRevoked = in.readBoolean();
    }

    public String getGrantId() { return mGrantId; }
    public int getUserId() { return mUserId; }
    public String getPackageName() { return mPackageName; }
    public String getCapabilityId() { return mCapabilityId; }
    public long getGrantedAt() { return mGrantedAt; }
    public long getExpiresAt() { return mExpiresAt; }
    public boolean isActive() { return mActive; }
    public boolean isRevoked() { return mRevoked || !mActive; }
    public boolean isExpired() { return mExpiresAt > 0 && System.currentTimeMillis() > mExpiresAt; }

    public ProdXGrant getGrant(String grantId) {
        if (mGrantId != null && mGrantId.equals(grantId)) return this;
        return null;
    }

    public static final Creator<ProdXGrant> CREATOR = new Creator<ProdXGrant>() {
        @Override public ProdXGrant createFromParcel(Parcel in) { return new ProdXGrant(in); }
        @Override public ProdXGrant[] newArray(int size) { return new ProdXGrant[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mGrantId);
        dest.writeInt(mUserId);
        dest.writeString(mPackageName);
        dest.writeString(mCapabilityId);
        dest.writeLong(mGrantedAt);
        dest.writeLong(mExpiresAt);
        dest.writeBoolean(mActive);
        dest.writeBoolean(mRevoked);
    }
}
