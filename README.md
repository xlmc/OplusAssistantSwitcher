# 欧唤 · Oplus Assistant Switcher

面向 OPlus 系统（ColorOS / OxygenOS / realme UI）的**系统语音助手入口切换器**。

欧唤不是语音助手本体，也不做 AI 对话、ASR/TTS。它只做一件事：识别当前系统可用的第三方语音助手，把 ColorOS 原本约 0.5 秒电源键唤醒小布的入口，路由到用户选择的第三方语音助手。

> 本文与代码中的「短按」统一指 OPPO 官方约 0.5 秒电源键助手唤醒动作，不是普通单击。

## 功能边界（V1）

**做：**

- 实时识别当前系统默认语音助手（来自系统真实状态）
- 枚举当前设备中「已安装且具备语音助手资格」的第三方助手
- 将 0.5 秒电源键助手唤醒事件路由到所选第三方助手
- 调用失败时前台完全静默，后台记录可诊断日志

**不做：**

- 不做 AI 对话、ASR/TTS、任意应用启动器、Circle to Search、手势条接管
- 不在失败时回退小布、Gemini 或其他助手（失败策略：静默终止）
- 不弹 Toast、Dialog、通知或错误页打扰用户
- 不把普通 App 放进助手列表，不允许手填包名/Activity

## 工作原理

- Hook 点：`system_server` 内 `PhoneWindowManagerExtImpl$OplusSpeechHandler.handleMessage`，消息码 `what == 0x3F3`（ColorOS 16 已验证路径，来自公开技术文档）
- 模块关闭 → 执行系统原逻辑；模块开启 → 消费该事件并尝试启动所选助手；无有效目标 → 静默结束
- 配置通过 LSPosed Remote Preferences 从 App 写入、Hook 侧只读同步
- 日志由 Hook 侧异步广播上报，App 侧落库展示（热路径无数据库/网络/耗时操作）
- 作用域固定为 `system`（libxposed API 102，`staticScope=true`）

## 安装要求

1. 已 root 并安装 [LSPosed](https://github.com/LSPosed/LSPosed)（需支持现代 Xposed API 102 的版本）
2. 安装 Release APK，在 LSPosed 中启用模块——推荐作用域只有 `system`
3. 重启手机，打开欧唤：Hook 状态应显示「正常」
4. 在「选择助手」中挑选一个第三方语音助手

> 普通单击亮屏/熄屏、长按电源菜单、双击/SOS 等系统行为不在 Hook 范围内，不受影响。

## 兼容性

V1 仅锁定**已验证的 ColorOS 16** 链路。OxygenOS / realme UI 同源但实现存在差异，标记为待实机验证，不凭品牌名宣称完整支持。ROM 更新导致 Hook 类或消息码变化时，模块自动退化为「不接管」并在日志中记录 `HOOK_TARGET_NOT_FOUND` / `ROM_UNSUPPORTED`，绝不导致 system_server 崩溃。

## 构建

本项目**无需本地 Android 开发环境**，所有编译、签名与发布均由 GitHub Actions 完成：

- **CI**：Push / Pull Request 自动执行编译、Lint、单元测试与 Xposed 元数据校验，上传带版本号的 debug APK Artifact
- **Release**：仅 `v*` Git Tag 触发，产出 `OplusAssistantSwitcher-vX.Y.Z.apk` 与 `.sha256` 校验文件，`versionName` 与 Tag 强制一致

发布流程与签名 Secret 配置见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。

## 仓库结构

```
OplusAssistantSwitcher/
├── .github/workflows/        # ci.yml + release.yml（GitHub 原生构建/发布）
├── app/src/main/
│   ├── java/com/ouhuan/oplusassistant/
│   │   ├── app/              # App 侧：服务绑定、配置写入
│   │   ├── data/             # 日志 Room 持久化、广播接收、Hook 状态快照
│   │   ├── shared/           # Models / ErrorCodes / Constants（双端共用）
│   │   ├── system/           # 系统默认助手读取、第三方助手扫描
│   │   ├── ui/               # 首页 / 助手选择 / 日志 / 诊断 / 设置
│   │   └── xposed/           # MainModule、ColorOS16PowerAssistantHook、
│   │                         # AssistantResolver、AssistantLauncher、
│   │                         # RuntimeConfig、DiagnosticReporter
│   ├── res/
│   └── resources/META-INF/xposed/   # java_init.list / module.prop / scope.list
├── docs/DEVELOPMENT.md
├── CHANGELOG.md
└── README.md
```

## 许可与声明

本项目仅供学习研究与个人设备定制使用。使用本模块产生的任何系统行为变化由使用者自行承担。
