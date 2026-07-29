package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXAuditRecord implements Parcelable {
    private final String mRecordId;
    private final String mTransactionId;
    private final long mTimestamp;
    private final int mUserId;
    private final String mAction;

    public ProdXAuditRecord(String recordId, String transactionId, long timestamp, int userId, String action) {
        mRecordId = recordId;
        mTransactionId = transactionId;
        mTimestamp = timestamp;
        mUserId = userId;
        mAction = action;
    }

    protected ProdXAuditRecord(Parcel in) {
        mRecordId = in.readString();
        mTransactionId = in.readString();
        mTimestamp = in.readLong();
        mUserId = in.readInt();
        mAction = in.readString();
    }

    public String getRecordId() { return mRecordId; }
    public String getTransactionId() { return mTransactionId; }
    public long getTimestamp() { return mTimestamp; }
    public int getUserId() { return mUserId; }
    public String getAction() { return mAction; }

    public static final Creator<ProdXAuditRecord> CREATOR = new Creator<ProdXAuditRecord>() {
        @Override public ProdXAuditRecord createFromParcel(Parcel in) { return new ProdXAuditRecord(in); }
        @Override public ProdXAuditRecord[] newArray(int size) { return new ProdXAuditRecord[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mRecordId);
        dest.writeString(mTransactionId);
        dest.writeLong(mTimestamp);
        dest.writeInt(mUserId);
        dest.writeString(mAction);
    }
}
