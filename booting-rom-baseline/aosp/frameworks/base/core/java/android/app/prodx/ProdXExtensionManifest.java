package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXExtensionManifest implements Parcelable {
    private final String mExtensionId;
    private final String mPackageName;
    private final String mVersion;

    public ProdXExtensionManifest(String extensionId, String packageName, String version) {
        mExtensionId = extensionId;
        mPackageName = packageName;
        mVersion = version;
    }

    protected ProdXExtensionManifest(Parcel in) {
        mExtensionId = in.readString();
        mPackageName = in.readString();
        mVersion = in.readString();
    }

    public String getExtensionId() { return mExtensionId; }
    public String getPackageName() { return mPackageName; }
    public String getVersion() { return mVersion; }

    public static final Creator<ProdXExtensionManifest> CREATOR = new Creator<ProdXExtensionManifest>() {
        @Override public ProdXExtensionManifest createFromParcel(Parcel in) { return new ProdXExtensionManifest(in); }
        @Override public ProdXExtensionManifest[] newArray(int size) { return new ProdXExtensionManifest[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mExtensionId);
        dest.writeString(mPackageName);
        dest.writeString(mVersion);
    }
}
