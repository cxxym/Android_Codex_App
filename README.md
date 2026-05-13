# Codex Hub Android

Codex Hub 是一个 Material Design 3 风格的 Android 编程代理客户端雏形，用于统一管理 Codex、Claude Code、DeepSeek 等主流 AI 代码模型。

## 功能

- **Codex OAuth 浏览器授权 + 回调上传**：App 使用 Codex 官方客户端 ID 与 PKCE 生成授权链接并打开浏览器；登录后如果跳到 `localhost:1455` 无法打开，只需复制地址栏完整回调 URL 上传，App 会交换 token；也支持 OAuth JSON、`auth.json` 片段或 Sub2API 导出。
- **Claude Code / DeepSeek 配置**：支持 Key 模式、本地桥接模式、模型与 endpoint 展示。
- **真实聊天入口**：配置 Codex/OpenAI-compatible、Claude Code/Anthropic 或 DeepSeek 后，聊天页会调用对应接口；未配置时才展示离线规划预览。
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
