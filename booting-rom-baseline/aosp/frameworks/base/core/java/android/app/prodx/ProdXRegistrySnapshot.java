package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** @hide */
public final class ProdXRegistrySnapshot implements Parcelable {
    private final ProdXRegistryGeneration mGeneration;
    private final List<ProdXRegistryEntry> mEntries;
    private final String mPreviousSnapshotHash;
    private final String mRootHash;

    public ProdXRegistrySnapshot(
            ProdXRegistryGeneration generation,
            List<ProdXRegistryEntry> entries,
            String previousSnapshotHash,
            String rootHash) {
        mGeneration = Objects.requireNonNull(generation);
        mEntries = Collections.unmodifiableList(new ArrayList<>(entries));
        mPreviousSnapshotHash = previousSnapshotHash;
        mRootHash = Objects.requireNonNull(rootHash);
    }

    private ProdXRegistrySnapshot(Parcel in) {
        mGeneration = Objects.requireNonNull(
                in.readTypedObject(ProdXRegistryGeneration.CREATOR));
        List<ProdXRegistryEntry> entries = in.createTypedArrayList(ProdXRegistryEntry.CREATOR);
        mEntries = entries == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(entries);
        mPreviousSnapshotHash = in.readString();
        mRootHash = Objects.requireNonNull(in.readString());
    }

    public ProdXRegistryGeneration getGeneration() {
        return mGeneration;
    }

    public List<ProdXRegistryEntry> getEntries() {
        return mEntries;
    }

    public String getPreviousSnapshotHash() {
        return mPreviousSnapshotHash;
    }

    public String getRootHash() {
        return mRootHash;
    }

    public static final Creator<ProdXRegistrySnapshot> CREATOR =
            new Creator<ProdXRegistrySnapshot>() {
                @Override
                public ProdXRegistrySnapshot createFromParcel(Parcel in) {
                    return new ProdXRegistrySnapshot(in);
                }

                @Override
                public ProdXRegistrySnapshot[] newArray(int size) {
                    return new ProdXRegistrySnapshot[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mGeneration, flags);
        dest.writeTypedList(mEntries, flags);
        dest.writeString(mPreviousSnapshotHash);
        dest.writeString(mRootHash);
    }
}
