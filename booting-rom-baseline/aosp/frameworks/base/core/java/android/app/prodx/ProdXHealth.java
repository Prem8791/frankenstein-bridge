package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXHealth implements Parcelable {
    private final boolean mOperational;
    private final String mStatus;

    public ProdXHealth(boolean operational, String status) {
        mOperational = operational;
        mStatus = status;
    }

    protected ProdXHealth(Parcel in) {
        mOperational = in.readBoolean();
        mStatus = in.readString();
    }

    public boolean isOperational() { return mOperational; }
    public String getStatus() { return mStatus; }

    public static final Creator<ProdXHealth> CREATOR = new Creator<ProdXHealth>() {
        @Override public ProdXHealth createFromParcel(Parcel in) { return new ProdXHealth(in); }
        @Override public ProdXHealth[] newArray(int size) { return new ProdXHealth[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeBoolean(mOperational);
        dest.writeString(mStatus);
    }
}
