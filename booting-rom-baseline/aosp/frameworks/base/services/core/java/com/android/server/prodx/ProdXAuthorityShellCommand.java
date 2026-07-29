package com.android.server.prodx;

import android.os.Binder;
import android.os.ShellCommand;
import java.io.PrintWriter;

public class ProdXAuthorityShellCommand extends ShellCommand {
    @Override
    public int onCommand(String cmd) {
        final PrintWriter pw = getOutPrintWriter();
        if ("health".equals(cmd)) {
            pw.println("ProdX Authority: not ready");
            return 0;
        } else if ("mode".equals(cmd)) {
            pw.println("Mode: DISABLED");
            return 0;
        }
        pw.println("Unknown command: " + cmd);
        return -1;
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("ProdX Authority commands:");
        pw.println("  health  - Show authority health");
        pw.println("  mode    - Show current mode");
    }
}
