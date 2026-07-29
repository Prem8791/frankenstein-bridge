package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXCapabilityError implements Parcelable {
    private final int mErrorCode;
    private final String mMessage;

    public ProdXCapabilityError(int errorCode, String message) {
        mErrorCode = errorCode;
        mMessage = message;
    }

    protected ProdXCapabilityError(Parcel in) {
        mErrorCode = in.readInt();
        mMessage = in.readString();
    }

    public int getErrorCode() { return mErrorCode; }
    public String getMessage() { return mMessage; }

    public static final Creator<ProdXCapabilityError> CREATOR = new Creator<ProdXCapabilityError>() {
        @Override public ProdXCapabilityError createFromParcel(Parcel in) { return new ProdXCapabilityError(in); }
        @Override public ProdXCapabilityError[] newArray(int size) { return new ProdXCapabilityError[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mErrorCode);
        dest.writeString(mMessage);
    }
}
