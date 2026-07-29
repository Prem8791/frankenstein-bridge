package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public class ProdXRegistryGeneration implements Parcelable {
    private final long mGenerationId;
    private final long mTimestamp;

    public ProdXRegistryGeneration(long generationId, long timestamp) {
        mGenerationId = generationId;
        mTimestamp = timestamp;
    }

    protected ProdXRegistryGeneration(Parcel in) {
        mGenerationId = in.readLong();
        mTimestamp = in.readLong();
    }

    public long getGenerationId() { return mGenerationId; }
    public long getTimestamp() { return mTimestamp; }

    public static final Creator<ProdXRegistryGeneration> CREATOR = new Creator<ProdXRegistryGeneration>() {
        @Override public ProdXRegistryGeneration createFromParcel(Parcel in) { return new ProdXRegistryGeneration(in); }
        @Override public ProdXRegistryGeneration[] newArray(int size) { return new ProdXRegistryGeneration[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mGenerationId);
        dest.writeLong(mTimestamp);
    }
}
