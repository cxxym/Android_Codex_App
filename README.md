# Codex Hub Android

Codex Hub 是一个 Material Design 3 风格的 Android 编程代理客户端雏形，用于统一管理 Codex、Claude Code、DeepSeek 等主流 AI 代码模型。

## 功能

- **Codex OAuth 导入 + Key 模式**：支持像 Sub2API 一样粘贴 OAuth JSON、`auth.json` 片段或回调 URL 导入，也支持 API Key 保存。
- **Claude Code / DeepSeek 配置**：支持 Key 模式、本地桥接模式、模型与 endpoint 展示。
- **MD3 Compose UI**：使用 Jetpack Compose Material 3、动态色彩、底部导航和响应式卡片。
- **工作流模板**：内置“需求到补丁”“自动 Code Review”“发行版构建”等代码任务流。
- **自动发行版构建**：GitHub Actions 在 tag 或手动触发时生成 Release APK / AAB 并上传构件。

## 本地构建

```bash
gradle assembleDebug
```

## 发行版构建

```bash
gradle assembleRelease bundleRelease
```

如果 CI 中配置了 `ANDROID_KEYSTORE_FILE`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`，release 包会使用对应签名；否则使用 debug 签名，便于预览构件。
