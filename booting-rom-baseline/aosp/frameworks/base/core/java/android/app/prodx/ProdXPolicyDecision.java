package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXPolicyDecision implements Parcelable {
    private final boolean mAllowed;
    private final String mReason;
    private final boolean mRequiresConfirmation;
    private final byte[] mProofChallenge;

    public ProdXPolicyDecision(boolean allowed, String reason) {
        this(allowed, reason, false, new byte[0]);
    }

    public ProdXPolicyDecision(boolean allowed, String reason,
                                boolean requiresConfirmation, byte[] proofChallenge) {
        mAllowed = allowed;
        mReason = reason;
        mRequiresConfirmation = requiresConfirmation;
        mProofChallenge = proofChallenge;
    }

    protected ProdXPolicyDecision(Parcel in) {
        mAllowed = in.readBoolean();
        mReason = in.readString();
        mRequiresConfirmation = in.readBoolean();
        mProofChallenge = in.createByteArray();
    }

    public boolean isAllowed() { return mAllowed; }
    public String getReason() { return mReason; }
    public boolean isRequiresConfirmation() { return mRequiresConfirmation; }
    public byte[] getProofChallenge() { return mProofChallenge; }

    public static final Creator<ProdXPolicyDecision> CREATOR = new Creator<ProdXPolicyDecision>() {
        @Override public ProdXPolicyDecision createFromParcel(Parcel in) { return new ProdXPolicyDecision(in); }
        @Override public ProdXPolicyDecision[] newArray(int size) { return new ProdXPolicyDecision[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeBoolean(mAllowed);
        dest.writeString(mReason);
        dest.writeBoolean(mRequiresConfirmation);
        dest.writeByteArray(mProofChallenge);
    }
}
