package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/** @hide */
public final class ProdXRegistryEntry implements Parcelable {
    private final ProdXCapabilityDescriptor mDescriptor;
    private final ProdXProviderManifest mProvider;
    private final boolean mAvailable;

    public ProdXRegistryEntry(
            ProdXCapabilityDescriptor descriptor,
            ProdXProviderManifest provider,
            boolean available) {
        mDescriptor = Objects.requireNonNull(descriptor);
        mProvider = Objects.requireNonNull(provider);
        mAvailable = available;
    }

    private ProdXRegistryEntry(Parcel in) {
        mDescriptor = Objects.requireNonNull(
                in.readTypedObject(ProdXCapabilityDescriptor.CREATOR));
        mProvider = Objects.requireNonNull(in.readTypedObject(ProdXProviderManifest.CREATOR));
        mAvailable = in.readBoolean();
    }

    public ProdXCapabilityDescriptor getDescriptor() {
        return mDescriptor;
    }

    public ProdXProviderManifest getProvider() {
        return mProvider;
    }

    public boolean isAvailable() {
        return mAvailable;
    }

    public static final Creator<ProdXRegistryEntry> CREATOR =
            new Creator<ProdXRegistryEntry>() {
                @Override
                public ProdXRegistryEntry createFromParcel(Parcel in) {
                    return new ProdXRegistryEntry(in);
                }

                @Override
                public ProdXRegistryEntry[] newArray(int size) {
                    return new ProdXRegistryEntry[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mDescriptor, flags);
        dest.writeTypedObject(mProvider, flags);
        dest.writeBoolean(mAvailable);
    }
}
