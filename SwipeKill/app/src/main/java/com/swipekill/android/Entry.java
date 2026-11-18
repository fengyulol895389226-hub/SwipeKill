package com.swipekill.android;
import android.os.Binder;
import android.os.Build;
import android.util.Log;
import android.content.Intent;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;
public final class Entry extends XposedModule {
 private static final String TAG="SwipeKill";
 @Override public void onSystemServerStarting(SystemServerStartingParam param) {
  if(Build.VERSION.SDK_INT!=36)return;
  try {
   ClassLoader cl=param.getClassLoader();
   Class<?> atms=Class.forName("com.android.server.wm.ActivityTaskManagerService",false,cl);
   Class<?> sup=Class.forName("com.android.server.wm.ActivityTaskSupervisor",false,cl);
   Class<?> task=Class.forName("com.android.server.wm.Task",false,cl);
   Method base=task.getDeclaredMethod("getBaseIntent"); base.setAccessible(true);
   ThreadLocal<String> event=new ThreadLocal<>();
   for(String name:new String[]{"removeTask","removeAllVisibleRecentTasks"}) {
    Method m=name.equals("removeTask")?atms.getDeclaredMethod(name,int.class):atms.getDeclaredMethod(name);
    hook(m).setId("probe-"+name).intercept(chain->{
     String previous=event.get();
     event.set(name+" uid="+Binder.getCallingUid());
     try { Log.i(TAG,"BEGIN "+event.get()+" args="+chain.getArgs()); Object out=chain.proceed(); Log.i(TAG,"END "+event.get()+" result="+out); return out; }
     finally { if(previous==null)event.remove();else event.set(previous); }
    });
   }
   for(Method m:sup.getDeclaredMethods()) {
    if(!m.getName().equals("removeTask")||m.getParameterCount()<4||m.getParameterTypes()[0]!=task)continue;
    hook(m).setId("probe-supervisor-"+m.getParameterCount()).intercept(chain->{
     try { if(event.get()!=null){ Intent intent=(Intent)base.invoke(chain.getArgs().get(0)); String pkg=intent==null?"unknown":intent.getComponent()!=null?intent.getComponent().getPackageName():intent.getPackage(); Log.i(TAG,"TASK "+event.get()+" package="+pkg+" reason="+chain.getArgs().get(3)); } } catch(Exception e){Log.w(TAG,"probe failed",e);}
     return chain.proceed();
    });
   }
   Log.i(TAG,"Probe installed; no force-stop enabled");
  } catch(Throwable e){Log.e(TAG,"Probe setup failed",e);}
 }
}
