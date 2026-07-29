package com.frankenbridge.reachability;

import java.io.File;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/** Test-only probe: records capability, never values or file contents. */
public final class ReachabilityProbe {
    public static Map<String, String> probeBinderNames(String[] names) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : names) {
            try {
                Class<?> serviceManager = Class.forName("android.os.ServiceManager");
                Method checkService = serviceManager.getDeclaredMethod("checkService", String.class);
                Object binder = checkService.invoke(null, name);
                result.put(name, binder == null ? "DENIED_OR_ABSENT" : "REACHABLE");
            } catch (ReflectiveOperationException hiddenOrAbsent) {
                result.put(name, "DENIED");
            } catch (SecurityException denied) {
                result.put(name, "DENIED");
            } catch (RuntimeException partial) {
                result.put(name, "PARTIAL");
            }
        }
        return result;
    }

    public static Map<String, String> probePaths(String[] paths) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String path : paths) {
            try {
                File file = new File(path);
                result.put(path, file.exists() && file.canRead() ? "REACHABLE" : "DENIED");
            } catch (SecurityException denied) {
                result.put(path, "DENIED");
            }
        }
        return result;
    }

    private ReachabilityProbe() {}
}
