package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXTransactionReference implements Parcelable {
    private final String mTransactionId;
    private final long mCreatedAt;

    public ProdXTransactionReference(String transactionId, long createdAt) {
        mTransactionId = transactionId;
        mCreatedAt = createdAt;
    }

    protected ProdXTransactionReference(Parcel in) {
        mTransactionId = in.readString();
        mCreatedAt = in.readLong();
    }

    public String getTransactionId() { return mTransactionId; }
    public long getCreatedAt() { return mCreatedAt; }

    public static final Creator<ProdXTransactionReference> CREATOR = new Creator<ProdXTransactionReference>() {
        @Override public ProdXTransactionReference createFromParcel(Parcel in) { return new ProdXTransactionReference(in); }
        @Override public ProdXTransactionReference[] newArray(int size) { return new ProdXTransactionReference[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mTransactionId);
        dest.writeLong(mCreatedAt);
    }
}
