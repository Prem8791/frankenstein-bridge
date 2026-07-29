package com.prodx.assistant;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

public final class ProdXRecognitionService extends RecognitionService {
    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        try {
            listener.error(SpeechRecognizer.ERROR_SERVER);
        } catch (RemoteException ignored) {
            // The recognition client disconnected before receiving the result.
        }
    }

    @Override
    protected void onCancel(Callback listener) {
    }

    @Override
    protected void onStopListening(Callback listener) {
        try {
            listener.results(Bundle.EMPTY);
        } catch (RemoteException ignored) {
            // The recognition client disconnected before receiving the result.
        }
    }
}
