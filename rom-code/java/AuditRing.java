package com.android.server.frankenstein;

import android.os.SystemClock;
import android.util.IndentingPrintWriter;

import java.util.ArrayDeque;

final class AuditRing {
    private static final int MAX_RECORDS = 512;
    private final ArrayDeque<Record> mRecords = new ArrayDeque<>(MAX_RECORDS);

    synchronized void add(String action, int uid, int userId, String stableId, int code) {
        if (mRecords.size() == MAX_RECORDS) mRecords.removeFirst();
        mRecords.addLast(new Record(SystemClock.elapsedRealtime(), action, uid, userId,
                stableId, code));
    }

    synchronized void dump(IndentingPrintWriter writer) {
        for (Record record : mRecords) {
            writer.println(record.elapsedMs + " " + record.action + " uid=" + record.uid
                    + " user=" + record.userId + " id=" + record.stableId
                    + " code=" + record.code);
        }
    }

    private record Record(long elapsedMs, String action, int uid, int userId,
            String stableId, int code) {}
}
