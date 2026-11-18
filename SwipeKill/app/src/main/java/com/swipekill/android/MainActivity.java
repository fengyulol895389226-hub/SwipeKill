package com.swipekill.android;
import android.app.Activity;import android.os.Bundle;import android.widget.TextView;
public final class MainActivity extends Activity { @Override public void onCreate(Bundle state){super.onCreate(state);TextView t=new TextView(this);t.setPadding(48,64,48,48);t.setTextSize(18);t.setText("SwipeKill V1.0 诊断版\n\nAndroid 16 · API 102\n\n本版本仅记录任务移除事件，不会强制停止应用。\n\n在 LSPosed 中启用模块，选择系统框架，重启后测试单独划卡和全部清理。\n\n使用 Root 终端执行：\nlogcat -d -s SwipeKill:I\n\n关闭模块并重启即可停用。");setContentView(t);} }
