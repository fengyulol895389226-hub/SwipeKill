
package com.swipekill.android;

import android.os.Binder;
import android.os.Build;
import android.util.Log;
import android.content.Intent;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

public final class Entry extends XposedModule {
    private static final String TAG = "SwipeKill";

    private void report(String message) {
        String line = "V1.0.1 " + message;
        Log.i(TAG, line);
        try {
            log(Log.INFO, TAG, line);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam p) {
        report("MODULE_LOADED sdk=" + Build.VERSION.SDK_INT);
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam p) {
        report("SYSTEM_SERVER_STARTING");

        if (Build.VERSION.SDK_INT != 36) {
            report("Unsupported SDK");
            return;
        }

        ClassLoader cl = p.getClassLoader();

        scan(cl,
            "com.android.server.wm.ActivityTaskManagerService",
            "removeTask", "removeAllVisibleRecentTasks");

        scan(cl,
            "com.android.server.wm.ActivityTaskSupervisor",
            "removeTask", "removeTaskById");

        report("SETUP_COMPLETE diagnostic only");
    }

    private void scan(ClassLoader cl, String className,
                      String... names) {
        try {
            Class<?> type = Class.forName(className, false, cl);
            int count = 0;

            for (Method m : type.getDeclaredMethods()) {
                boolean match = false;

                for (String name : names) {
                    if (name.equals(m.getName())) match = true;
                }

                if (!match) continue;

                final String signature = m.toString();

                try {
                    hook(m)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            try {
                                String msg = "EVENT "
                                    + type.getSimpleName()
                                    + "." + m.getName()
                                    + " uid=" + Binder.getCallingUid();

                                for (Object arg : chain.getArgs()) {
                                    if (arg instanceof Integer
                                        || arg instanceof Boolean
                                        || arg instanceof String) {
                                        msg += " arg=" + arg;
                                    } else if (arg != null
                                        && arg.getClass().getName().equals(
                                            "com.android.server.wm.Task")) {
                                        try {
                                            Method base = arg.getClass()
                                                .getMethod("getBaseIntent");
                                            base.setAccessible(true);
                                            Intent i = (Intent) base.invoke(arg);

                                            if (i != null) {
                                                msg += " package="
                                                    + (i.getComponent() == null
                                                    ? i.getPackage()
                                                    : i.getComponent().getPackageName());
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                }

                                report(msg);
                            } catch (Throwable ignored) {}

                            return chain.proceed();
                        });

                    count++;
                    report("HOOK_OK " + signature);

                } catch (Throwable e) {
                    report("HOOK_FAILED " + signature + " " + e);
                }
            }

            report("CLASS_DONE " + className + " hooks=" + count);

        } catch (Throwable e) {
            report("CLASS_FAILED " + className + " " + e);
        }
    }
}
