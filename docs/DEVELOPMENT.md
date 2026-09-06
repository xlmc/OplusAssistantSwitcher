# 开发指南

本文描述欧唤（Oplus Assistant Switcher）的工程约定、架构细节与发布流程。产品边界以《欧唤开发书 v1.0》为准，任何改动不得超出该文档定义的范围。

## 1. 环境要求

**本地开发不需要 Android 开发环境**——所有编译、签名、发布均由 GitHub Actions 在全新 Runner 上从零完成。仓库自带 Gradle Wrapper，不依赖任何开发者本机的 Gradle 或 Android SDK 配置。

如需本地构建（可选）：

- JDK 17（Temurin 推荐）
- Android Studio 或命令行 Android SDK（compileSdk 37，随 AGP 自动下载）
- `./gradlew assembleDebug`

技术栈基线：

| 项 | 值 |
| --- | --- |
| 语言 | Java（纯 Java，无 Kotlin 插件） |
| Gradle | 8.10.2（Wrapper） |
| AGP | 8.7.3 |
| libxposed API | `io.github.libxposed:api:102.0.0`（compileOnly） |
| libxposed service | `io.github.libxposed:service:102.0.0`（App 侧远程配置写入） |
| minSdk / targetSdk / compileSdk | 29 / 35 / 37 |
| 日志持久化 | Room 2.6.1（仅 App 侧） |

**红线**：API 102 项目禁止混用 legacy Xposed API。出现 `XposedHelpers`、`XSharedPreferences`、`XposedBridge.log` 等 `de.robv.android.xposed` 引用视为阻塞问题。

## 2. 架构与模块职责

```
xposed 层（运行于 system_server）
├── MainModule                     libxposed 入口：识别进程、安装策略、非 system_server 即 detach
├── ColorOS16PowerAssistantHook    仅匹配 0x3F3 助手唤醒事件并进入路由
├── AssistantResolver              读取系统默认助手；验证所选目标；解析可启动入口
├── AssistantLauncher              以系统上下文按标准 Assistant 入口发起调用
├── RuntimeConfig                  Remote Preferences 同步 + 进程内缓存
├── DiagnosticReporter             轻量日志事件缓冲与异步上报（禁止热路径写库）
└── ContextProvider                system_server Context 捕获（内部支撑）

app 层（模块 App）
├── app/AssistApp + ConfigStore    XposedService 绑定；Remote Preferences 写入 + 本地镜像
├── data/                          Room 日志库、广播接收器、Hook 状态快照
├── system/                        系统默认助手读取、第三方助手扫描、设备信息
└── ui/                            首页 / 助手选择 / 日志 / 诊断信息 / 设置

shared 层（双端共用，纯 Java）
└── Constants / ErrorCodes / LogEvent / LaunchResult / SystemAssistantState / …
```

### 2.1 Hook 点（ColorOS16 策略）

- 进程：`system_server`
- 类：`com.android.server.policy.PhoneWindowManagerExtImpl$OplusSpeechHandler`
- 方法：`handleMessage(Message)`
- 触发：`Message.what == 0x3F3`（约 0.5 秒电源键助手唤醒）

该类名、内部类结构与消息码属于厂商实现细节，ROM 更新可能变化。因此它被封装在 `ColorOS16PowerAssistantHook` 中：类/方法找不到时记录 `HOOK_TARGET_NOT_FOUND`，非 OPlus ROM 记录 `ROM_UNSUPPORTED`，任何安装失败退化为「不接管」，绝不崩 system_server。

### 2.2 调用流程（开发书 2.2）

```
0x3F3 事件命中
  → 模块未启用（或偏好读取失败） → chain.proceed() 执行系统原逻辑
  → 已启用：消费原调用（不执行小布、不提示）
      → Resolver 验证目标（已安装 / 启用 / 资格 / 角色一致）
        失败 → 静默结束 + 终态日志（TARGET_NOT_FOUND / RESOLVE_FAILED / …）
      → Launcher 以 ACTION_ASSIST / ACTION_VOICE_COMMAND 启动
        成功 → LAUNCH_ACCEPTED（SUCCESS = 系统接受了启动请求）
        异常 → SECURITY_EXCEPTION / LAUNCH_EXCEPTION（前台完全静默）
```

失败策略为「静默终止」：目标启动失败后不调用原小布逻辑，也不自动切换其他助手。

### 2.3 配置与日志链路

- **配置**：App 通过 `XposedService.getRemotePreferences("ouhuan_config")` 写入；Hook 侧 `XposedInterface.getRemotePreferences()` 只读同步，每次触发刷新一次快照并缓存。读取失败一律视为「模块未启用」。
- **日志**：Hook 侧生成轻量 LogEvent（字段见开发书 8.3）→ 显式组件广播送达 App 的 `LogEntryReceiver`（`exported=true` + signature 级权限；API 34+ 再校验 system/module 发送方）→ Room 落库。systemReady 之前广播通道不可用，事件先入内存缓冲（上限 64 条）由 AMS.systemReady 后补发。热路径内无 Room/SQLite、无网络、无 sleep/轮询。
- **敏感数据**：日志不存储语音正文、屏幕内容、账户信息、Token；详细诊断模式只追加类名、方法名、Intent、ComponentName 与异常摘要。

## 3. Xposed 元数据

`app/src/main/resources/META-INF/xposed/`：

- `java_init.list`：`com.ouhuan.oplusassistant.xposed.MainModule`
- `module.prop`：`minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`、`exceptionMode=protective`、`autoHotReload=false`
- `scope.list`：仅 `system`（V1 锁定；扩充作用域必须先经实机验证并在开发书后续版本记录原因、风险与回滚方式）

CI 与 Release 均运行 `.github/scripts/verify_xposed_meta.sh` 校验以上内容与入口类在 dex 中的有效性。

## 4. GitHub Actions

### 4.1 ci.yml（Push / PR）

1. `./gradlew assembleDebug lintDebug testDebugUnitTest`
2. Xposed 元数据门禁脚本
3. 上传 debug APK Artifact（命名含版本号）

### 4.2 release.yml（仅 `v*` Tag）

1. 从 Tag 解析 `versionName`（如 `v0.1.0` → `0.1.0`）与 `versionCode`（`major*10000 + minor*100 + patch`），经 `-PversionName/-PversionCode` 注入构建——**Tag 是版本号唯一来源，不存在两套可漂移的版本号**
2. 校验四个签名 Secret 齐备，缺失即失败（禁止用 debug keystore 出正式包）
3. 临时恢复 keystore 至 `$RUNNER_TEMP`（随 Runner 生命周期销毁）
4. 构建签名 Release APK `OplusAssistantSwitcher-vX.Y.Z.apk`
5. 门禁：`apksigner verify`、aapt2 校验 `versionName/versionCode` 与 Tag 一致、Xposed 元数据脚本
6. 生成 `.sha256`，用 `gh release create` 发布 APK + 校验文件

### 4.3 首次发布前的签名配置

生成一份专用于本项目的 keystore（本地执行，**不要提交仓库**）：

```bash
keytool -genkeypair -v \
  -keystore ouhuan-release.jks -alias ouhuan -keyalg RSA -keysize 4096 \
  -validity 10000
```

在仓库 Settings → Secrets → Actions 配置：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 ouhuan-release.jks` 的输出 |
| `KEYSTORE_PASSWORD` | keystore 口令 |
| `KEY_ALIAS` | `ouhuan` |
| `KEY_PASSWORD` | key 口令 |

> keystore 与恢复信息必须长期安全备份（离线多地留存）。GitHub Secret 只是 CI 存储，不是唯一副本。正式发布后签名必须长期保持稳定，否则无法作为同一应用覆盖升级。

## 5. 开发顺序与禁止事项（摘自开发书 15）

推荐顺序：最小 Hook 工程 → Hook 状态日志 → 0x3F3 触发验证 → Resolver → RuntimeConfig 与选择页 → Launcher → 诊断/日志页 → CI → 签名发布 → P0 实机测试后才打 `v0.1.0` Tag。

禁止：先加大量兼容代码再验证最小 Hook 链路；扩大作用域碰运气；热路径数据库/网络/长耗时任务；失败回退小布；写死第三方助手包名；把普通 App 放进助手列表；debug keystore 作正式签名。

## 6. 实机验收

发布前必须通过开发书 13 的 V1 验收标准与 14 的 P0 实机测试矩阵（普通单击/长按/双击不受影响、卸载目标后静默结束且日志明确、ROM Hook 失效不崩 system_server 等）。测试矩阵与预期日志见开发书对应章节。
