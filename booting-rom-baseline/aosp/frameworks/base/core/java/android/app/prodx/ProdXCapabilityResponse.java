package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXCapabilityResponse implements Parcelable {
    private final boolean mSuccess;
    private final byte[] mResultData;

    public ProdXCapabilityResponse(boolean success, byte[] resultData) {
        mSuccess = success;
        mResultData = resultData;
    }

    protected ProdXCapabilityResponse(Parcel in) {
        mSuccess = in.readBoolean();
        mResultData = in.createByteArray();
    }

    public boolean isSuccess() { return mSuccess; }
    public byte[] getResultData() { return mResultData; }

    public static final Creator<ProdXCapabilityResponse> CREATOR = new Creator<ProdXCapabilityResponse>() {
        @Override public ProdXCapabilityResponse createFromParcel(Parcel in) { return new ProdXCapabilityResponse(in); }
        @Override public ProdXCapabilityResponse[] newArray(int size) { return new ProdXCapabilityResponse[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeBoolean(mSuccess);
        dest.writeByteArray(mResultData);
    }
}
