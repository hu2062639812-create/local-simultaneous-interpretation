# Local 同传 v1.35.00 发布说明

发布日期：2026-10-21

完全离线的多语同声传译 Android 应用：实时双语字幕、逐句本地翻译、会后中文总结。本版新增 **中文 ⇄ Luganda（乌干达语）完整语音同传** 与应用内 **GitHub Release 更新提示**。所有推理在本机完成。

## 版本信息

| 项目 | 内容 |
|---|---|
| 版本号 | 1.35.00（versionCode 20261025） |
| 包名 | `com.localinterpreter.app` |
| 构建类型 | release（R8 混淆 + 资源压缩，本地密钥签名） |
| 系统要求 | Android 11+（API 30），arm64-v8a |
| APK 大小 | 63 MB（65,889,633 字节） |
| SHA-256 | `4281255a284a9b93914fb7e672af65c76d1e8e7fd5e2ebf222cb5a81d742eeda` |
| 签名证书 SHA-256 | `5507ec701edeb2a980e9708cc944cd62ae786071905a61d0f4908ea3ee13200b` |

## 本次更新（相比 v1.33.00，v1.34.00 未发布合并入本版）

### Luganda 语音同传（新）
- 新增翻译方向 **中文 ⇄ Luganda（乌干达语）**，可在新建会话时选择：Luganda 语音自动译为中文，中文语音自动译为 Luganda。
- Luganda 语音识别基于 sherpa-onnx **Omnilingual ASR 1600 语言 CTC 300M（int8）** 模型（Apache 2.0），与中英 SenseVoice 模型按会话自动切换，全部本地运行。
- Luganda 模型（约 366 MB）在设置页一键下载，断点续传；仅需下载一次，之后全程离线。
- 双语字幕、语言徽标（橙 LG / 绿 中）、面对面双屏均支持 Luganda 方向。

### 应用内更新提示（新）
- 应用启动后检查 GitHub Release 最新版本，发现新版本时弹窗提示并引导下载 APK。
- 仅做版本号对比，不自动下载、不收集任何数据；检查失败静默忽略。

### 面对面双屏大字
- 面对面模式：上下分屏、对方画面旋转 180°，双人各自持机面对面对话。
- 支持每侧按住说话、自动听音、沉浸模式与常亮，字号大适合户外与弱光环境。

### 按住说话
- 普通会话中支持按住说话：按住录音、松开停止，与自动模式可随时切换。

### 离线常用短语本
- 内置 48 条中英常用短语，支持搜索与分类；点击卡片全屏大字展示，支持翻转与一键复制。

### 界面与可读性
- 设置中新增字体缩放（100% / 115% / 130% / 150%），全部界面生效。
- 锁定竖屏方向。

## 安装与升级

1. 已安装 v1.33.00 及以上版本（同包名同签名）可直接覆盖安装，会话记录保留。
2. 首次使用 Luganda 同传前，需在设置页下载 Luganda 语音识别模型（约 366 MB）；中英模型（约 228 MB）与语言模型（约 0.65–1 GB）仅在首次使用时下载。
3. 安装 APK：

```bash
adb install Local-Interpreter-release-v1.35.00.apk
```

模型仅在首次使用时联网下载，之后录音、翻译、总结全程离线，无统计或崩溃上报。

## 已知限制

- 仅提供 arm64-v8a 版本，不支持 32 位设备。
- 本版本不含语音播报（TTS）与总结导出功能。
- Luganda 翻译由 0.8B 本地小模型完成，译文质量与速度弱于云端大模型，长句可能出现用词偏差；建议短句交流。
- 模拟器（ARM 转译环境）下推理速度明显低于真机：逐句翻译约 20–25 秒，长会话总结约 60–80 秒，字幕可能排队。

## 模型与许可

| 模型 / 组件 | 许可证 |
|---|---|
| SenseVoice Small（FunAudioLLM） | MIT |
| Omnilingual ASR 1600 语言 CTC 300M（Meta 开源团队） | Apache License 2.0 |
| Silero VAD | MIT |
| Qwen 3.5 0.8B | Apache License 2.0 |
| sherpa-onnx | Apache License 2.0 |
| llama.cpp | MIT |
| ONNX Runtime | MIT |

## 开源署名

本项目基于 [HearoPilot](https://github.com/Helldez/HearoPilot-App)（Copyright 2026 de.ai，Apache License 2.0）修改而来，遵循 Apache License 2.0 发布。上游名称与商标归其各自权利人所有。
