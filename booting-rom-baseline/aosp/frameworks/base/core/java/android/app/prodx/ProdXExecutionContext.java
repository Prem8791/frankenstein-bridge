package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXExecutionContext implements Parcelable {
    private final int mCallingUid;
    private final int mUserId;
    private final String mPackageName;
    private final String mPurpose;

    public ProdXExecutionContext(int uid, int userId, String packageName, String purpose) {
        mCallingUid = uid;
        mUserId = userId;
        mPackageName = packageName;
        mPurpose = purpose;
    }

    protected ProdXExecutionContext(Parcel in) {
        mCallingUid = in.readInt();
        mUserId = in.readInt();
        mPackageName = in.readString();
        mPurpose = in.readString();
    }

    public int getCallingUid() { return mCallingUid; }
    public int getUserId() { return mUserId; }
    public String getPackageName() { return mPackageName; }
    public String getPurpose() { return mPurpose; }

    public static final Creator<ProdXExecutionContext> CREATOR = new Creator<ProdXExecutionContext>() {
        @Override public ProdXExecutionContext createFromParcel(Parcel in) { return new ProdXExecutionContext(in); }
        @Override public ProdXExecutionContext[] newArray(int size) { return new ProdXExecutionContext[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCallingUid);
        dest.writeInt(mUserId);
        dest.writeString(mPackageName);
        dest.writeString(mPurpose);
    }
}
