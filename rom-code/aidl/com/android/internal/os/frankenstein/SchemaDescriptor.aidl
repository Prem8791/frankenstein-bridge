package com.android.internal.os.frankenstein;

parcelable SchemaDescriptor {
    String schemaId;
    int version;
    int encoding;
    byte[] canonicalSchema;
    byte[] sha256;
}
