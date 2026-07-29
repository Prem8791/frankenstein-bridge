package com.android.server.frankenstein;

import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.internal.os.frankenstein.diag.DiagRequest;
import com.android.internal.os.frankenstein.diag.DiagResult;
import com.android.internal.os.frankenstein.diag.IFrankensteinDiagnostic;

import java.util.concurrent.Executor;

public final class DiagnosticDaemonConnector implements IBinder.DeathRecipient {
    static final String SERVICE =
            "com.android.internal.os.frankenstein.diag.IFrankensteinDiagnostic/default";

    interface Listener {
        void onDaemonState(boolean available, long generation);
    }

    private final Handler mControl;
    private final Executor mIo;
    private final Listener mListener;
    private volatile IFrankensteinDiagnostic mDaemon;
    private long mRetryMs = 1_000;

    DiagnosticDaemonConnector(Handler control, Executor io, Listener listener) {
        mControl = control;
        mIo = io;
        mListener = listener;
    }

    void start() {
        mControl.post(this::connect);
    }

    public void execute(DiagRequest request, java.util.function.Consumer<DiagResult> completion) {
        IFrankensteinDiagnostic daemon = mDaemon;
        if (daemon == null) {
            completion.accept(unavailable());
            return;
        }
        mIo.execute(() -> {
            try {
                completion.accept(daemon.execute(request));
            } catch (RemoteException e) {
                binderDied();
                completion.accept(unavailable());
            }
        });
    }

    @Override
    public void binderDied() {
        mDaemon = null;
        mListener.onDaemonState(false, 0);
        mControl.postDelayed(this::connect, mRetryMs);
        mRetryMs = Math.min(60_000, mRetryMs * 2);
    }

    private void connect() {
        IBinder binder = ServiceManager.checkService(SERVICE);
        if (binder == null) {
            mControl.postDelayed(this::connect, mRetryMs);
            mRetryMs = Math.min(60_000, mRetryMs * 2);
            return;
        }
        try {
            binder.linkToDeath(this, 0);
            IFrankensteinDiagnostic daemon = IFrankensteinDiagnostic.Stub.asInterface(binder);
            long generation = daemon.getGeneration();
            mDaemon = daemon;
            mRetryMs = 1_000;
            mListener.onDaemonState(true, generation);
        } catch (RemoteException e) {
            binderDied();
        }
    }

    private static DiagResult unavailable() {
        DiagResult result = new DiagResult();
        result.code = BridgeConstants.UNAVAILABLE;
        result.payload = new byte[0];
        result.declaredLength = -1;
        return result;
    }
}
