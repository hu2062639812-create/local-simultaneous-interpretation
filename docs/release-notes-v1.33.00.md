# Local 同传 v1.33.00 发布说明

发布日期：2026-10-21

完全离线的中英双向同声传译 Android 应用：实时双语字幕、逐句本地翻译、会后中文总结。所有推理在本机完成。

## 版本信息

| 项目 | 内容 |
|---|---|
| 版本号 | 1.33.00（versionCode 20261023） |
| 包名 | `com.localinterpreter.app` |
| 构建类型 | release（R8 混淆 + 资源压缩，本地密钥签名） |
| 系统要求 | Android 11+（API 30），arm64-v8a |
| APK 大小 | 63 MB |
| SHA-256 | `f6663551eeb31ed0e418424755ec16a1dd06f73f75cecd9ec3de336a3c359ba3` |
| 签名证书 SHA-256 | `5507ec701edeb2a980e9708cc944cd62ae786071905a61d0f4908ea3ee13200b` |

## 本次更新（相比 v1.32.03）

### 全新品牌
- 应用更名为 **Local 同传 / Local Interpreter**，包名由 `com.hearopilot.app` 变更为 `com.localinterpreter.app`。
- 全部 26 个语言资源的应用名与文案同步更新，新增简体中文本地化（`values-zh`）。
- 开源署名与 LICENSE 保留上游信息，与上游项目无隶属或背书关系。

### 界面语言
- 首次启动新增界面语言选择页：简体中文 / English，之后可在设置中随时更改。

### 同传体验
- 新增逐句离线翻译流程：每句识别完成后立即翻译，原文与译文对照显示。
- 新增暂停 / 继续：暂停时关闭麦克风与计时，模型保持加载；继续后计时累计。

### 会后总结
- 停止录音后在本地生成中文总结，包含标题、概览与待办事项。
- 总结卡片显示生成时间，支持复制与删除。

### 构建与发布
- 新增 release 签名配置：从本地 `keystore.properties` 读取密钥，密钥不入库；缺失该文件时回退为未签名构建。
- release 构建启用 R8 混淆与资源压缩，APK 体积相较 debug 版（136 MB）缩减至 63 MB。
- 移除仓库中预编译的 llama.cpp 系列 `.so`，改为构建时从源码编译。

## 安装与升级

v1.33.00 的包名与签名均与 v1.32.x 不同，**无法覆盖安装**：

1. 卸载旧版（旧版会话记录不会迁移）。
2. 如需保留模型，卸载前将 `/storage/emulated/0/Android/data/com.localinterpreter.app/files/models/` 备份到其他目录，重装后拷回；否则在首次启动时重新下载。
3. 安装 APK：

```bash
adb install Local-Interpreter-release-v1.33.00.apk
```

模型仅在首次使用时联网下载（语音识别约 228 MB，语言模型约 0.65–1 GB，视所选变体而定），之后录音、翻译、总结全程离线，无统计或崩溃上报。

## 已知限制

- 仅提供 arm64-v8a 版本，不支持 32 位设备。
- 本版本不含语音播报（TTS）与总结导出功能。
- 模拟器（ARM 转译环境）下推理速度明显低于真机：逐句翻译约 20–25 秒，长会话总结约 60–80 秒，字幕可能排队。

## 模型与许可

| 模型 / 组件 | 许可证 |
|---|---|
| SenseVoice Small（FunAudioLLM） | MIT |
| Silero VAD | MIT |
| Gemma 3 1B | Gemma Terms of Use |
| Qwen 3.5 0.8B | Apache License 2.0 |
| sherpa-onnx | Apache License 2.0 |
| llama.cpp | MIT |
| ONNX Runtime | MIT |

## 开源署名

本项目基于 [HearoPilot](https://github.com/Helldez/HearoPilot-App)（Copyright 2026 de.ai，Apache License 2.0）修改而来，遵循 Apache License 2.0 发布。上游名称与商标归其各自权利人所有。
