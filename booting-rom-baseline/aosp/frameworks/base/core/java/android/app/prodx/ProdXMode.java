package android.app.prodx;

import android.os.Parcel;
import android.os.Parcelable;

public enum ProdXMode implements Parcelable {
    DISABLED(0),
    INVENTORY_ONLY(1),
    SHADOW_POLICY(2),
    TEST_NO_OP(3);

    private final int mValue;

    ProdXMode(int value) { mValue = value; }

    public int getValue() { return mValue; }

    public static ProdXMode fromValue(int value) {
        for (ProdXMode m : values()) { if (m.mValue == value) return m; }
        return DISABLED;
    }

    public static final Creator<ProdXMode> CREATOR = new Creator<ProdXMode>() {
        @Override public ProdXMode createFromParcel(Parcel source) { return ProdXMode.valueOf(source.readString()); }
        @Override public ProdXMode[] newArray(int size) { return new ProdXMode[size]; }
    };

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { dest.writeString(name()); }
}
