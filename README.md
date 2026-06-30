# DailyTask

DailyTask 是一个 Android 原生示例项目，用于实践本地定时任务、前台服务、悬浮窗、通知监听、MediaProjection 截屏、Room 数据存储和消息通知等能力。项目采用 Kotlin + Java 混编，包含少量 C++ 本地库。

本项目仅用于 Android 技术学习、个人设备自动化流程验证和内部测试。使用前请确认符合所在组织、目标应用和设备系统的相关规则。

## 项目概览

| 项目 | 说明 |
| --- | --- |
| 应用类型 | Android 原生应用，单模块 `:app` |
| 主要语言 | Kotlin、Java、C++ |
| 最低系统 | Android 8.0，`minSdk 26` |
| 当前 SDK | `compileSdk 36`、`targetSdk 36` |
| 当前版本 | `2.4.1.0` |
| 数据存储 | Room、SharedPreferences |
| UI 技术 | AndroidX AppCompat、Material Components、RecyclerView、ViewBinding |
| 通信能力 | Retrofit、OkHttp、Gson、JavaMail、Webhook |
| 事件机制 | EventBus |

## 目录结构

| 路径 | 说明 |
| --- | --- |
| `app/` | Android 应用主模块 |
| `app/src/main/java/com/pengxh/daily/app/ui/` | 页面入口，包含主界面、设置、任务配置、消息渠道等 Activity |
| `app/src/main/java/com/pengxh/daily/app/service/` | 前台保活、倒计时、通知监听、截屏、悬浮窗等服务 |
| `app/src/main/java/com/pengxh/daily/app/utils/` | 任务调度、消息分发、节假日、日志、手势、遮罩等逻辑 |
| `app/src/main/java/com/pengxh/daily/app/sqlite/` | Room 数据库、DAO 和数据实体 |
| `app/src/main/res/` | 布局、主题、图片和字符串资源 |
| `app/src/main/cpp/` | C++ 本地库，使用 CMake 构建 |

## 启动前必要条件

1. 安装 JDK 17。
2. 安装 Android Studio 或 Android SDK。
3. 安装 Android SDK Platform 36、Build Tools、Platform Tools。
4. 安装 CMake 3.22.1 和 NDK 21.4.7075529。
5. 配置 Android SDK 路径，二选一：
   - 在项目根目录创建 `local.properties`，写入 `sdk.dir=/你的/Android/sdk`
   - 或配置环境变量 `ANDROID_HOME=/你的/Android/sdk`
6. 首次构建需要能访问 `google()`、`mavenCentral()`、`gradlePluginPortal()`、JitPack 和阿里云 Maven 仓库。
7. 真机运行建议使用 Android 8.0 及以上设备。

## Android Studio 启动

1. 使用 Android Studio 打开项目根目录。
2. 等待 Gradle Sync 完成。
3. 顶部运行配置选择 `app`。
4. 连接真机并确认设备已开启 USB 调试。
5. 点击 Run 或 Debug。

如果 Run 按钮是灰色，通常需要先完成 Gradle Sync、选择 `app` 运行配置，并连接可用设备。

## 命令行构建

构建 Debug 包：

```bash
./gradlew :app:assembleDebug
```

安装 Debug 包到已连接设备：

```bash
./gradlew :app:installDebug
```

构建 Release 包：

```bash
./gradlew :app:assembleRelease
```

如果 macOS/Linux 下提示 `permission denied`，先执行：

```bash
chmod +x gradlew
```

也可以使用：

```bash
sh gradlew :app:assembleDebug
```

## 运行配置

首次运行需要按业务场景授予以下权限或服务能力：

1. 悬浮窗权限：用于展示任务状态和倒计时。
2. 通知权限：用于前台服务和状态通知。
3. 通知监听：用于接收目标通知和远程指令。
4. 截屏授权：用于通过 MediaProjection 获取屏幕结果。
5. 电池与后台限制：建议关闭系统对本应用的后台限制，避免服务被系统回收。

基础流程：

1. 安装并打开 DailyTask。
2. 在设置页配置目标应用、结果来源、消息渠道和任务参数。
3. 如需截屏结果，先开启截屏服务并选择“整个屏幕”。
4. 添加任务时间。
5. 在主界面启动任务。

## 结果来源

| 来源 | 说明 |
| --- | --- |
| 通知监听 | 通过系统通知判断结果，适合目标应用会稳定产生通知的场景 |
| 截屏服务 | 通过 MediaProjection 截取屏幕结果，适合通知不可用或通知内容不足的场景 |

Android 对 MediaProjection 授权有较严格限制。如果系统回收截屏服务、设备真实锁屏或授权失效，需要重新开启截屏服务并授权。

## 远程指令

远程指令依赖通知监听服务。请使用测试账号或内部测试环境验证，不建议在含敏感消息的账号上开启通知监听。

| 指令 | 功能 |
| --- | --- |
| `启动任务` | 启动任务 |
| `停止任务` | 停止任务 |
| `开启循环` | 开启循环任务标志 |
| `关闭循环` | 关闭循环任务标志 |
| `息屏` | 开启低亮度遮罩模式 |
| `亮屏` | 退出低亮度遮罩模式 |
| `考勤记录` | 导出当天记录 |
| `状态查询` | 获取当前任务、服务、电量、版本和日期等状态 |
| `截屏` | 获取一张当前屏幕截图并通过消息渠道反馈 |

## 调试方式

1. 构建问题先执行：

```bash
./gradlew :app:assembleDebug
```

2. 如果提示 `SDK location not found`，检查 `local.properties` 或 `ANDROID_HOME`。
3. 页面和业务流程优先使用 Android Studio Debug，入口是 `MainActivity`。
4. Logcat 按包名 `com.pengxh.daily.app` 过滤。
5. 任务调度问题重点看：
   - `TaskScheduler`
   - `CountDownTimerService`
   - `AlarmScheduler`
   - `TaskResetReceiver`
   - `ForegroundRunningService`
6. 远程指令问题重点看：
   - `NotificationMonitorService`
   - `MessageDispatcher`
   - `MessageViewModel`
7. 截屏链路问题重点看：
   - `CaptureImageService`
   - `ProjectionSession`
   - 系统 MediaProjection 授权状态
8. 数据问题可使用 Android Studio App Inspection 查看 Room 数据库 `DailyTask.db`。

当前项目未提供 `test` 或 `androidTest` 目录，主要验证方式是 Gradle 构建和真机测试。

## 常见问题

### 为什么需要真机测试？

项目依赖悬浮窗、通知监听、前台服务、目标应用跳转和 MediaProjection。模拟器无法完整覆盖这些系统能力和厂商后台策略。

### 为什么截屏服务会失效？

常见原因包括系统回收前台服务、设备进入真实锁屏、MediaProjection 授权被系统终止、后台限制过严。重新进入设置页开启截屏服务并授权即可恢复。

### 为什么任务没有按预期执行？

优先检查：

1. 当前任务是否已启动。
2. 系统时间和任务时间是否正确。
3. 循环任务开关是否符合预期。
4. 目标应用是否能被正常打开。
5. 悬浮窗、通知监听、截屏服务是否正常。
6. 电池优化或后台限制是否影响前台服务。

## 合规与隐私说明

1. 项目不包含服务端，配置和任务数据默认保存在本机。
2. 通知监听会读取系统通知内容；请仅在明确了解影响后开启。
3. 截屏服务会获取屏幕画面；请避免在包含隐私内容的页面运行测试。
4. 请在合法、合规、已授权的环境中使用和二次开发。

## 版本记录

| 版本 | 说明 |
| --- | --- |
| 2.4.1.0 | 优化任务重置、通知发送、截图结果处理和截屏会话稳定性 |
| 2.4.0.0 | 优化截图服务、任务时间计算、倒计时服务、每日重置和低电量提醒 |
| 2.3.1.0 | 优化远程指令、截图服务和核心服务状态提示 |
| 2.3.0.0 | 支持多目标应用入口、图片消息、附件邮件、远程截屏和状态查询 |
| 2.2.x | 优化任务导入导出、消息渠道、悬浮窗和主界面 |
| 2.1.x | 优化前台服务、任务计时、任务通知和低亮度遮罩模式 |
| 2.0.x | 重构基础任务流程和每日循环能力 |

## 参考截图

![配置流程](screenshot/%E9%80%9A%E4%BF%A1%E6%9C%BA%E5%88%B6.jpg)
