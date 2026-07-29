package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXCapabilityDescriptor implements Parcelable {
    private final String mCapabilityId;
    private final String mProviderId;
    private final String mVersion;

    public ProdXCapabilityDescriptor(String capabilityId, String providerId, String version) {
        mCapabilityId = capabilityId;
        mProviderId = providerId;
        mVersion = version;
    }

    protected ProdXCapabilityDescriptor(Parcel in) {
        mCapabilityId = in.readString();
        mProviderId = in.readString();
        mVersion = in.readString();
    }

    public String getCapabilityId() { return mCapabilityId; }
    public String getProviderId() { return mProviderId; }
    public String getVersion() { return mVersion; }

    public static final Creator<ProdXCapabilityDescriptor> CREATOR = new Creator<ProdXCapabilityDescriptor>() {
        @Override public ProdXCapabilityDescriptor createFromParcel(Parcel in) { return new ProdXCapabilityDescriptor(in); }
        @Override public ProdXCapabilityDescriptor[] newArray(int size) { return new ProdXCapabilityDescriptor[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mCapabilityId);
        dest.writeString(mProviderId);
        dest.writeString(mVersion);
    }
}
