package com.swipekill.android;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

public final class Entry extends XposedModule {
    private static final String TAG = "SwipeKill";
    private static final String LAUNCHER = "com.android.launcher3";
    // Conservative first functional test: only explicitly allowed packages.
    private static final Set<String> ALLOWED = Set.of("org.breezyweather", "com.android.chrome", "com.google.android.calculator", "com.android.calculator2");
    private static final class Batch {
        final Set<String> packages = new HashSet<>();
    }
    private final ThreadLocal<Batch> active = new ThreadLocal<>();
    private void report(String s) {
        Log.i(TAG, "V1.1 " + s);
        try { log(Log.INFO, TAG, "V1.1 " + s); } catch (Throwable ignored) {}
    }
    @Override public void onModuleLoaded(ModuleLoadedParam p) { report("MODULE_LOADED"); }
    private Context context() throws Exception {
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object t = at.getMethod("currentActivityThread").invoke(null);
        return (Context) at.getMethod("getSystemContext").invoke(t);
    }
    private boolean trustedCaller(int uid) throws Exception {
        if (uid < 10000 || uid >= 100000) return false;
        PackageManager pm = context().getPackageManager();
        String[] pkgs = pm.getPackagesForUid(uid);
        if (pkgs == null || pkgs.length != 1 || !LAUNCHER.equals(pkgs[0])) return false;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && LAUNCHER.equals(ri.activityInfo.packageName);
    }
    @Override public void onSystemServerStarting(SystemServerStartingParam p) {
        if (Build.VERSION.SDK_INT != 36) return;
        try {
            ClassLoader cl = p.getClassLoader();
            Class<?> atms = Class.forName("com.android.server.wm.ActivityTaskManagerService", false, cl);
            Class<?> sup = Class.forName("com.android.server.wm.ActivityTaskSupervisor", false, cl);
            Class<?> task = Class.forName("com.android.server.wm.Task", false, cl);
            Method base = task.getMethod("getBaseIntent");
            Field user = task.getDeclaredField("mUserId"); user.setAccessible(true);
            int count = 0;
            for (Method m : sup.getDeclaredMethods()) {
                if (!m.getName().equals("removeTask") || m.getParameterCount() == 0 || m.getParameterTypes()[0] != task) continue;
                try {
                    hook(m).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Batch b = active.get(); String pkg = null;
                        if (b != null) try {
                            Object t = chain.getArgs().get(0);
                            if (user.getInt(t) == 0) {
                                Intent i = (Intent) base.invoke(t);
                                if (i != null && i.getComponent() != null) pkg = i.getComponent().getPackageName();
                            }
                        } catch (Throwable e) { report("TASK_SKIP " + e); }
                        Object result = chain.proceed();
                        if (b != null && pkg != null && !Boolean.FALSE.equals(result)) b.packages.add(pkg);
                        return result;
                    }); count++; report("HOOK_OK " + m);
                } catch (Throwable e) { report("HOOK_FAILED " + e); }
            }
            if (count == 0) { report("NO_TASK_HOOK; disabled"); return; }
            Method entry = atms.getDeclaredMethod("removeTask", int.class);
            hook(entry).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                boolean trusted = false;
                try { trusted = trustedCaller(Binder.getCallingUid()); } catch (Throwable e) { report("CALLER_SKIP " + e); }
                if (!trusted || active.get() != null) return chain.proceed();
                Batch b = new Batch(); active.set(b);
                Object result;
                try { result = chain.proceed(); } finally { active.remove(); }
                if (Boolean.TRUE.equals(result)) {
                    for (String pkg : b.packages) {
                        report("REMOVED " + pkg);
                        if (ALLOWED.contains(pkg)) new Handler(Looper.getMainLooper()).postDelayed(() -> stop(pkg), 300);
                        else report("SKIP_NOT_ALLOWED " + pkg);
                    }
                }
                return result;
            });
            report("READY single swipe only; test allowlist=" + ALLOWED);
        } catch (Throwable e) { report("SETUP_FAILED " + e); }
    }
    private void stop(String pkg) {
        long identity = Binder.clearCallingIdentity();
        try {
            Context c = context(); PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if (!ALLOWED.contains(pkg) || ai.uid < 10000 || ai.uid >= 100000 || (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP | ApplicationInfo.FLAG_PERSISTENT)) != 0) { report("SKIP_PROTECTED " + pkg); return; }
            String[] peers = pm.getPackagesForUid(ai.uid);
            if (peers == null || peers.length != 1 || !pkg.equals(peers[0])) return;
            ActivityManager am = c.getSystemService(ActivityManager.class);
            // Fail closed if another task of this package still exists.
            java.util.List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(Integer.MAX_VALUE);
            if (tasks == null || tasks.isEmpty()) { report("SKIP_TASK_CHECK " + pkg); return; }
            for (ActivityManager.RunningTaskInfo t : tasks) {
                if ((t.baseActivity != null && pkg.equals(t.baseActivity.getPackageName())) || (t.topActivity != null && pkg.equals(t.topActivity.getPackageName()))) { report("SKIP_ACTIVE_TASK " + pkg); return; }
            }
            Method force = ActivityManager.class.getDeclaredMethod("forceStopPackage", String.class);
            force.setAccessible(true); force.invoke(am, pkg);
            report("FORCE_STOP_OK " + pkg);
        } catch (Throwable e) { report("FORCE_STOP_FAILED " + pkg + " " + e); }
        finally { Binder.restoreCallingIdentity(identity); }
    }
}
