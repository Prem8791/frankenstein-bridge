package com.android.server.frankenstein;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Strict V1 CBOR subset decoder for flat request maps. */
public final class BridgeCbor {
    public static Map<String, Object> decodeFlatMap(byte[] encoded) {
        if (encoded == null || encoded.length == 0) return Collections.emptyMap();
        if (encoded.length > BridgeConstants.MAX_INLINE_BYTES) {
            throw new IllegalArgumentException("CBOR request too large");
        }
        Reader reader = new Reader(encoded);
        int count = reader.length(5);
        if (count > 64) throw new IllegalArgumentException("too many CBOR fields");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = reader.text();
            if (result.containsKey(key)) throw new IllegalArgumentException("duplicate CBOR key");
            int initial = reader.peek() & 0xff;
            int major = initial >>> 5;
            Object value;
            if (major == 0) value = reader.unsigned();
            else if (major == 1) value = -1L - reader.unsigned();
            else if (major == 3) value = reader.text();
            else if (initial == 0xf4 || initial == 0xf5) value = reader.bool();
            else throw new IllegalArgumentException("unsupported CBOR request value");
            result.put(key, value);
        }
        if (!reader.exhausted()) throw new IllegalArgumentException("trailing CBOR input");
        return Collections.unmodifiableMap(result);
    }

    public static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String)) throw new IllegalArgumentException(key + " must be text");
        String text = (String) value;
        if (text.length() > 512) throw new IllegalArgumentException(key + " too long");
        return text;
    }

    public static long integer(Map<String, Object> map, String key, long fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Long)) throw new IllegalArgumentException(key + " must be integer");
        return (Long) value;
    }

    private static final class Reader {
        private final ByteBuffer mBuffer;

        Reader(byte[] encoded) {
            mBuffer = ByteBuffer.wrap(encoded);
        }

        int peek() {
            require(1);
            return mBuffer.get(mBuffer.position());
        }

        boolean exhausted() {
            return !mBuffer.hasRemaining();
        }

        int length(int requiredMajor) {
            long value = argument(requiredMajor);
            if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("CBOR length overflow");
            return (int) value;
        }

        long unsigned() {
            return argument(0);
        }

        String text() {
            int length = length(3);
            if (length > 4096) throw new IllegalArgumentException("CBOR text too large");
            require(length);
            byte[] bytes = new byte[length];
            mBuffer.get(bytes);
            String result = new String(bytes, StandardCharsets.UTF_8);
            if (!java.util.Arrays.equals(bytes, result.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("invalid UTF-8");
            }
            return result;
        }

        boolean bool() {
            int initial = mBuffer.get() & 0xff;
            if (initial == 0xf4) return false;
            if (initial == 0xf5) return true;
            throw new IllegalArgumentException("not a boolean");
        }

        private long argument(int requiredMajor) {
            require(1);
            int initial = mBuffer.get() & 0xff;
            if ((initial >>> 5) != requiredMajor) {
                throw new IllegalArgumentException("unexpected CBOR major type");
            }
            int additional = initial & 31;
            if (additional < 24) return additional;
            if (additional == 24) {
                require(1);
                int value = mBuffer.get() & 0xff;
                if (value < 24) throw new IllegalArgumentException("non-canonical integer");
                return value;
            }
            if (additional == 25) {
                require(2);
                int value = mBuffer.getShort() & 0xffff;
                if (value <= 0xff) throw new IllegalArgumentException("non-canonical integer");
                return value;
            }
            if (additional == 26) {
                require(4);
                long value = mBuffer.getInt() & 0xffffffffL;
                if (value <= 0xffff) throw new IllegalArgumentException("non-canonical integer");
                return value;
            }
            if (additional == 27) {
                require(8);
                long value = mBuffer.getLong();
                if (value < 0 || value <= 0xffffffffL) {
                    throw new IllegalArgumentException("non-canonical integer");
                }
                return value;
            }
            throw new IllegalArgumentException("indefinite/reserved CBOR argument");
        }

        private void require(int count) {
            if (mBuffer.remaining() < count) throw new IllegalArgumentException("truncated CBOR");
        }
    }

    private BridgeCbor() {}
}
