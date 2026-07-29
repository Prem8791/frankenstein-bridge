package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXProviderManifest implements Parcelable {
    private final String mProviderId;
    private final String mProviderName;
    private final String mVersion;

    public ProdXProviderManifest(String providerId, String providerName, String version) {
        mProviderId = providerId;
        mProviderName = providerName;
        mVersion = version;
    }

    protected ProdXProviderManifest(Parcel in) {
        mProviderId = in.readString();
        mProviderName = in.readString();
        mVersion = in.readString();
    }

    public String getProviderId() { return mProviderId; }
    public String getProviderName() { return mProviderName; }
    public String getVersion() { return mVersion; }

    public static final Creator<ProdXProviderManifest> CREATOR = new Creator<ProdXProviderManifest>() {
        @Override public ProdXProviderManifest createFromParcel(Parcel in) { return new ProdXProviderManifest(in); }
        @Override public ProdXProviderManifest[] newArray(int size) { return new ProdXProviderManifest[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mProviderId);
        dest.writeString(mProviderName);
        dest.writeString(mVersion);
    }
}
