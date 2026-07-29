package com.android.server.frankenstein;

import com.android.internal.os.frankenstein.SchemaDescriptor;

import java.util.HashMap;
import java.util.Map;

final class SchemaRegistry {
    private final Map<String, Map<Integer, SchemaDescriptor>> mSchemas = new HashMap<>();

    synchronized void addRom(SchemaDescriptor schema) {
        validate(schema);
        Map<Integer, SchemaDescriptor> versions =
                mSchemas.computeIfAbsent(schema.schemaId, ignored -> new HashMap<>());
        if (versions.putIfAbsent(schema.version, schema) != null) {
            throw new IllegalArgumentException("duplicate schema version");
        }
    }

    synchronized SchemaDescriptor get(String schemaId, int[] acceptedVersions) {
        Map<Integer, SchemaDescriptor> versions = mSchemas.get(schemaId);
        if (versions == null) return null;
        if (acceptedVersions != null) {
            for (int version : acceptedVersions) {
                SchemaDescriptor candidate = versions.get(version);
                if (candidate != null) return candidate;
            }
        }
        return versions.values().stream()
                .max((left, right) -> Integer.compare(left.version, right.version))
                .orElse(null);
    }

    static void validatePayload(com.android.internal.os.frankenstein.BridgePayload payload) {
        if (payload == null) return;
        if (payload.data == null) payload.data = new byte[0];
        if (payload.data.length > BridgeConstants.MAX_INLINE_BYTES) {
            throw new IllegalArgumentException("inline payload exceeds 64 KiB");
        }
        if (payload.schemaId == null || payload.schemaVersion <= 0) {
            throw new IllegalArgumentException("payload lacks schema identity");
        }
        if (payload.encoding != 1) {
            throw new IllegalArgumentException("V1 requires deterministic CBOR encoding");
        }
    }

    private static void validate(SchemaDescriptor schema) {
        if (schema == null || schema.schemaId == null || schema.version <= 0
                || schema.canonicalSchema == null
                || schema.canonicalSchema.length > BridgeConstants.MAX_EXTERNAL_METADATA_BYTES) {
            throw new IllegalArgumentException("invalid schema");
        }
    }
}
