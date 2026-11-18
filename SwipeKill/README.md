# SwipeKill V1.0-probe
仅诊断，不杀后台。未编译或实机验证。
将本目录内容（包括隐藏的 .github）上传到 GitHub 仓库根目录，Actions → Build SwipeKill APK → Run workflow。完成后下载 Artifacts 中的 APK。
安装、LSPosed 启用系统框架作用域、重启。打开普通测试应用，上划任务；另测全部清理。Root 终端运行 `logcat -d -s SwipeKill:I`，提交日志（可能包含应用包名，可删去无关内容）。如异常，禁用模块并重启。若无法启动，使用已验证的 Root/LSPosed 救援方式停用框架。不要在没有救援方案时测试 system_server 模块。
