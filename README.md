# Local 同传 / Local Interpreter

**完全离线的中英双向同声传译 Android 应用**：实时双语字幕、逐句本地翻译、会后总结。

本仓库基于开源项目 [HearoPilot](https://github.com/Helldez/HearoPilot-App)（Copyright 2026 de.ai，Apache License 2.0）修改而来，与上游项目无隶属或背书关系。详见[开源署名](#开源署名)。

---

## 功能

- **中英双向同传** — 自动识别中英文并翻译为另一种语言，也可固定译入中文或英文
- **实时双语字幕** — 每句话识别完成后立即在本机翻译，原文与译文对照显示
- **会后总结** — 停止录音后在本地生成总结（标题、概览、待办），显示生成时间，支持复制与删除
- **暂停 / 继续** — 暂停时停止麦克风与计时，模型保持加载，继续后计时累计
- **首次启动语言选择** — 首次进入可选简体中文或 English 界面，之后可在设置中修改
- **会话管理** — 历史记录、重命名、全文搜索、字幕与总结编辑
- **息屏录音** — 前台服务保证锁屏/息屏期间持续收音
- **断点续传** — STT / LLM 模型下载支持从断点恢复
- **完全离线推理** — 录音、翻译、总结全部在本机完成；除首次下载模型外无需网络，无统计或崩溃上报

界面为 Material Design 3，支持深色模式。

---

## AI 模型

| 用途 | 模型 | 大小 |
|---|---|---|
| STT | SenseVoice Small int8（sherpa-onnx） | 约 228 MB |
| VAD | Silero VAD | 约 0.6 MB（内置） |
| LLM（默认 Q8\_0） | Gemma 3 1B Q8\_0（GGUF） | 约 1 GB |
| LLM（低内存 IQ4\_NL） | Gemma 3 1B IQ4\_NL（GGUF） | 约 650 MB |
| LLM（可选） | Qwen 3.5 0.8B Q8\_0（GGUF） | 约 870 MB |

首次启动时按设备内存推荐并下载 LLM 变体；Qwen 3.5 0.8B 需在设置中手动下载。模型保存在应用专属外部存储，可随时删除重下。STT 模型支持中文、英文、日文、韩文与粤语识别。

| 模型 / 组件 | 许可证 |
|---|---|
| SenseVoice Small（FunAudioLLM） | MIT |
| Qwen 3.5 0.8B | Apache License 2.0 |
| Gemma 3 1B | Gemma Terms of Use |
| sherpa-onnx | Apache License 2.0 |
| llama.cpp | MIT |
| ONNX Runtime | MIT |

应用内“开源许可”页面列出了完整许可文本与出处。

---

## 技术栈

Kotlin + Jetpack Compose（Material 3）、Hilt、Room、DataStore、Coroutines/Flow。

```
app/                 # Compose UI、导航、Hilt 入口
presentation/        # ViewModel、UiState、StateFlow
domain/              # 纯 Kotlin：模型、仓库接口、用例
data/                # 仓库实现、Room、DataStore、模型下载
feature-stt/         # Sherpa-ONNX STT + AudioRecord 管线
feature-llm/         # llama.cpp 推理封装
lib-sherpa-onnx/     # Sherpa-ONNX JNI
lib-llama-android/   # llama.cpp Android 库（CMake 源码编译）
```

只打包 `arm64-v8a`，最低支持 Android 11（API 30）。

---

## 构建

### 环境要求

| 工具 | 版本 |
|---|---|
| JDK | 17 |
| Android SDK | compile / target 35，min 30 |
| Android NDK | 29.0.13113456 |
| CMake | 3.31+ |
| Git LFS | 用于 `*.so` 与 `*.onnx` 资源 |

### 步骤

```bash
git clone https://github.com/hu2062639812-create/local-simultaneous-interpretation.git
cd local-simultaneous-interpretation

# local.properties 中配置 sdk.dir（Android Studio 会自动生成）

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

本项目不依赖 Firebase 或 Google Play 服务。`app-debug.apk` 使用调试签名，仅供测试；正式分发请配置自己的 release 签名。

### 首次启动

1. 选择界面语言（简体中文 / English）
2. 下载 STT 模型（约 228 MB）
3. 下载推荐的 LLM 模型（约 650 MB–1 GB）
4. 授予录音权限，点按麦克风开始同传

---

## 开源署名

本项目修改自 [HearoPilot](https://github.com/Helldez/HearoPilot-App)：

- 原始代码 Copyright 2026 de.ai (Decentralized AI)，以 [Apache License 2.0](LICENSE) 发布
- 本仓库保留原始 `LICENSE` 文件与版权声明，修改内容同样以 Apache License 2.0 发布
- 未使用上游项目的名称、Logo 或商标暗示官方关联；`HearoPilot` 相关权利归其权利人所有

相对上游的主要修改：

- STT 由 NeMo Parakeet 替换为 SenseVoice Small（中英日韩粤），并重做双语翻译管线
- 精简为同传优先的单一使用流程，移除会议模式等入口
- 新增首次启动界面语言选择（中文 / English）
- 新增会话暂停 / 继续
- 总结支持时间显示、复制、删除，并将“中文总结”更名为“总结”
- 移除 Firebase 与 Google Play 相关依赖，模型改为本地下载与离线推理

上游架构与部分设计说明仍适用于本项目，可参阅上游文档。

---

## License

Copyright 2026 de.ai (Decentralized AI)

Licensed under the [Apache License, Version 2.0](LICENSE).
You may not use this project except in compliance with the License.
