# Changelog

本项目所有重要变更记录于此。版本号遵循 [Semantic Versioning](https://semver.org/)，正式 Release 仅由 `v*` Git Tag 触发，`versionName` 与 Tag 保持一致。

## [Unreleased]

**是否需要重启设备：是**（本轮包含 Xposed/system_server 代码与 system_server↔App 状态协议变更；安装后需重载或重启 system_server）。

### P0 修复

- system_server↔App 运行态主通道改为 UID 1000 校验的显式 Binder；旧广播仅作 API 34 共享发送方身份的降级通道
- 版本信息改为编译期写入模块 dex，并结合 libxposed `HookedTarget` 的实际加载 versionCode 判断是否需要重载
- 配置写入以 Remote Preferences `commit()` 成功为生效闸门；断线/失败时保留本地期望、显示等待同步，并在服务重连后自动 reconcile
- 诊断页增加 Binder ping、真实 `system_server` 进程/加载版本、独立 Hook 生命周期、状态通道、电源键匹配及三方配置状态
- 增加 App↔XposedService、system_server Hook、运行态 Binder、配置读取与 0x3F3 路由的分阶段 Debug 事件；异常至少记录 stage、错误类型与消息，诊断页支持复制最近 50 条完整记录
- 注册成功后等待 onServiceBind 增加 15 秒明确超时状态；Remote Preferences 增加打开开始事件
- system_server Hook 里程碑始终同步写入 LSPosed/Xposed 日志，诊断页提供独立日志 Tag 与事件检索说明
- 诊断页直接展示 Xposed listener 的注册阶段、`onServiceBind` 是否触发、注册到绑定的等待时长与最后异常，不再要求从日志猜测“已注册但未绑定”

### Added（V1，对应开发书 v1.0）

- libxposed API 102 最小工程：LSPosed 可识别，作用域固定 `system`（staticScope）
- ColorOS16 策略 Hook：system_server 内 `PhoneWindowManagerExtImpl$OplusSpeechHandler.handleMessage` 的 `0x3F3` 助手唤醒事件
- 调用流程：模块关闭走系统原逻辑；开启后消费事件并尝试启动所选第三方助手；失败前台静默、不回退小布
- AssistantResolver：ROLE_ASSISTANT + VoiceInteractionService 实时读取系统默认助手；按 Android 标准（VIS 服务 / BIND_VOICE_INTERACTION / ACTION_ASSIST）动态枚举第三方候选
- AssistantLauncher：标准 `ACTION_ASSIST` / `ACTION_VOICE_COMMAND` 入口，返回结构化 LaunchResult，不做回退与轮询
- RuntimeConfig：Remote Preferences 配置同步 + system_server 内缓存；读取失败视为未启用
- DiagnosticReporter：轻量日志事件异步广播上报，App 侧 Room 持久化；日志页支持查看、按失败筛选、复制单条、复制完整诊断、清空
- 版本一致性状态：首页与诊断页同时显示 App 版本、system_server 已加载模块版本；不一致时明确提示需要重载，避免把旧 Hook 状态误报为已生效
- App 五页面：首页 / 助手选择 / 日志 / 诊断信息 / 设置
- GitHub Actions：ci.yml（编译 + Lint + 单元测试 + Xposed 元数据门禁 + debug Artifact）、release.yml（v* Tag 触发、签名、SHA-256、版本一致性门禁、自动创建 Release）
