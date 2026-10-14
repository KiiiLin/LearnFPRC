# LearnFPRC · 每日学习助手

> 本项目使用GPT-6.1sol构建

运行在 Android 手机本机的学习任务助手，通过系统无障碍服务识别和操作页面，内置中文 OCR 模型。支持 Android 11（API 30）及以上。

当前版本：**1.2.4**。应用名称为“每日学习助手”，包名为 `io.github.autotaskrun`。

## 功能

- 文章阅读：地区频道未读文章，默认 6 篇，每篇 60 秒。
- 视听学习：百灵未读视频，优先选择至少 90 秒的内容，默认 6 个，每个计时 60 秒。
- 每日答题：支持选择、判断和填空题，根据提示作答，以积分中心 5/5 核验。
- 订阅：记录点击和历史，每天最多订阅两个新账号。
- 本地频道：进入地区学习平台并核验积分。
- 补充积分：可选，固定队尾，仅补本批勾选的项目。
- 支持任务勾选、拖动排序、执行日志、暂停和停止；每项执行前后核验积分，结束后返回助手显示收益。

## 安装与使用

1. 在 [Releases](https://github.com/KiiiLin/LearnFPRC/releases/latest) 下载“稳定版”的 `LearnFPRC-stable.apk`，传到手机并安装。系统询问时，允许当前文件管理器或浏览器安装该 APK。
2. 已安装旧版时直接覆盖安装，避免卸载导致本机设置和订阅记录丢失。覆盖安装要求签名一致。
3. 打开助手，按内置图文指南启用“每日学习助手 · 屏幕操作”无障碍服务。若系统提示受限设置，先在本助手应用信息中允许受限设置。
4. 安装并登录学习强国，勾选任务、调整顺序，点击“开始执行”。
5. 执行期间保持手机解锁，避免手动切换目标页面。结束后在助手查看任务进度、积分和日志。

权限指南使用小米手机示例，其他品牌菜单名称可能不同。小米上滑清除助手后台可能触发强行停止并关闭无障碍开关，需要重新手动开启；可通过返回桌面退出，或手动设置后台锁减少被清理的机会。

助手不申请联网权限，OCR、进度和订阅历史只保存在本机；学习强国本身需要网络。积分以目标应用实际显示为准。

## 从源码构建

准备 **JDK 17**、**Android SDK Platform 35** 和 **Build Tools 35.0.0**。可通过 Android Studio 的 SDK Manager 安装。仓库内含 Gradle Wrapper，首次构建需要联网下载 Gradle 和依赖。

```powershell
git clone https://github.com/KiiiLin/LearnFPRC.git
cd LearnFPRC
$env:JAVA_HOME = '你的 JDK 17 目录'
$env:ANDROID_HOME = '你的 Android SDK 目录'
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

脚本默认执行单元测试、Lint 和 Debug 构建，输出到 `dist/LearnFPRC-v1.2.4-debug.apk`，可直接安装。设置好相同环境变量后，也可使用 Wrapper：

```powershell
cd android_app
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Linux/macOS 使用 `./gradlew`。原始 APK 位于 `android_app/app/build/outputs/apk/debug/app-debug.apk`。

## 构建签名稳定包

准备自己的签名文件，将密码仅设置在本机环境变量 `LEARNFPRC_STORE_PASSWORD` 和 `LEARNFPRC_KEY_PASSWORD` 中，执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 `
  -Release -SigningStore 'D:\keys\LearnFPRC.jks' -SigningAlias '你的签名别名'
```

脚本执行 Release 单元测试、Lint、构建、签名及签名核验，输出 `dist/LearnFPRC-stable.apk` 和 SHA-256 文件。自行构建的签名通常与发布包不同，不能直接覆盖发布包；已有安装需要升级时，应使用相同签名。签名文件和密码不要提交到仓库。

## 源码结构

```text
android_app/       Android 核心代码、界面资源、测试和 Gradle Wrapper
build.ps1          Windows 构建、签名与校验入口
README.md          构建、安装和使用说明
```

构建缓存、安装包、签名和个人运行数据均不纳入 Git。公开安装包不包含个人历史，覆盖升级继续使用手机上已有的数据。
