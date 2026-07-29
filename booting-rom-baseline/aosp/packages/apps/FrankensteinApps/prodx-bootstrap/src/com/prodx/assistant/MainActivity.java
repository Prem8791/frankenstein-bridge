package com.prodx.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView status = new TextView(this);
        status.setPadding(48, 48, 48, 48);
        status.setText(
                "ProdX privileged foundation is installed.\n\n"
                        + "Install the current platform-signed ProdX Assistant APK "
                        + "to add the AI interface, MiniCPM, ONNX wake word, and actions.");
        setContentView(status);
    }
}
