package com.example.androidcodex

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import org.json.JSONObject
import androidx.security.crypto.MasterKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CodexHubApp()
        }
    }
}

enum class AuthMode(val label: String) {
    OAuth("Codex OAuth 导入"),
    ApiKey("Key 模式"),
    LocalBridge("本地桥接")
}

data class AiProvider(
    val id: String,
    val name: String,
    val tagline: String,
    val modes: List<AuthMode>,
    val defaultModel: String,
    val endpoint: String,
    val accent: String
)

data class ChatMessage(
    val sender: String,
    val text: String,
    val isAssistant: Boolean
)

data class WorkflowTemplate(
    val name: String,
    val description: String,
    val steps: List<String>
)

data class AuthImportResult(
    val mode: String,
    val displayName: String,
    val secret: String,
    val endpoint: String,
    val model: String,
    val metadata: String
)

private val supportedProviders = listOf(
    AiProvider(
        id = "codex",
        name = "Codex",
        tagline = "支持像 Sub2API 一样导入 Codex OAuth，也支持 OpenAI API Key 模式。",
        modes = listOf(AuthMode.OAuth, AuthMode.ApiKey),
        defaultModel = "gpt-5.5-codex",
        endpoint = "https://api.openai.com/v1",
        accent = "OpenAI"
    ),
    AiProvider(
        id = "claude-code",
        name = "Claude Code",
        tagline = "通过 Anthropic Key 或本机 Claude Code CLI 桥接编程代理。",
        modes = listOf(AuthMode.ApiKey, AuthMode.LocalBridge),
        defaultModel = "claude-sonnet",
        endpoint = "https://api.anthropic.com/v1",
        accent = "Anthropic"
    ),
    AiProvider(
        id = "deepseek",
        name = "DeepSeek",
        tagline = "兼容 DeepSeek Chat / Coder Key 模式。",
        modes = listOf(AuthMode.ApiKey),
        defaultModel = "deepseek-coder",
        endpoint = "https://api.deepseek.com",
        accent = "DeepSeek"
    )
)

private val workflowTemplates = listOf(
    WorkflowTemplate(
        name = "需求到补丁",
        description = "解析需求、定位文件、生成补丁并创建测试计划。",
        steps = listOf("理解需求", "检索代码", "生成实现", "运行测试", "输出变更摘要")
    ),
    WorkflowTemplate(
        name = "自动 Code Review",
        description = "对本地 diff 进行安全、性能、可维护性检查。",
        steps = listOf("读取 diff", "风险分级", "给出修复建议", "生成审查报告")
    ),
    WorkflowTemplate(
        name = "发行版构建",
        description = "配合 GitHub Actions 自动产出 Release APK / AAB。",
        steps = listOf("打 tag", "执行 Gradle", "上传构件", "生成 Release Notes")
    )
)

class SecureCredentialStore(context: Context) {
    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "codex_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (error: Exception) {
        context.getSharedPreferences("codex_secure_prefs_fallback", Context.MODE_PRIVATE)
    }

    fun read(providerId: String): String = prefs.getString(providerId, "").orEmpty()

    fun readField(providerId: String, field: String): String = prefs.getString("$providerId:$field", "").orEmpty()

    fun save(providerId: String, value: String) {
        prefs.edit().putString(providerId, value).apply()
    }

    fun saveAuthProfile(providerId: String, profile: AuthImportResult) {
        prefs.edit()
            .putString(providerId, profile.secret)
            .putString("$providerId:mode", profile.mode)
            .putString("$providerId:displayName", profile.displayName)
            .putString("$providerId:endpoint", profile.endpoint)
            .putString("$providerId:model", profile.model)
            .putString("$providerId:metadata", profile.metadata)
            .apply()
    }

    fun clear(providerId: String) {
        prefs.edit()
            .remove(providerId)
            .remove("$providerId:mode")
            .remove("$providerId:displayName")
            .remove("$providerId:endpoint")
            .remove("$providerId:model")
            .remove("$providerId:metadata")
            .apply()
    }
}



private fun JSONObject.findStringDeep(targetKeys: Set<String>): String? {
    for (key in targetKeys) {
        optString(key).takeIf { it.isNotBlank() }?.let { return it }
    }
    keys().asSequence().forEach { key ->
        optJSONObject(key)?.findStringDeep(targetKeys)?.let { return it }
    }
    return null
}

private fun parseCodexOAuthImport(rawInput: String): AuthImportResult? {
    val raw = rawInput.trim()
    if (raw.isBlank()) return null

    if (raw.startsWith("http://") || raw.startsWith("https://")) {
        val uri = Uri.parse(raw)
        val fragmentUri = uri.fragment?.let { Uri.parse("https://codex.local/?$it") }
        fun readParam(key: String): String? = uri.getQueryParameter(key)
            ?: fragmentUri?.getQueryParameter(key)

        val token = listOf("refresh_token", "access_token", "code", "session", "token")
            .firstNotNullOfOrNull { key -> readParam(key)?.takeIf { it.isNotBlank() } }
            ?: raw
        val account = readParam("account_id")
            ?: readParam("org_id")
            ?: readParam("email")
            ?: uri.host.orEmpty()
        return AuthImportResult(
            mode = "codex-oauth-url",
            displayName = account.ifBlank { "Codex OAuth URL" },
            secret = token,
            endpoint = readParam("base_url") ?: "https://api.openai.com/v1",
            model = readParam("model") ?: "gpt-5.5-codex",
            metadata = "Imported from OAuth callback URL"
        )
    }

    return runCatching {
        val json = JSONObject(raw)
        val token = json.findStringDeep(setOf("refresh_token", "access_token", "id_token", "api_key", "token"))
            ?: return null
        val display = json.findStringDeep(setOf("account_id", "org_id", "email", "user", "name"))
            ?: "Codex OAuth"
        AuthImportResult(
            mode = json.optString("mode", "codex-oauth-json"),
            displayName = display,
            secret = token,
            endpoint = json.findStringDeep(setOf("base_url", "endpoint", "openai_base_url")) ?: "https://api.openai.com/v1",
            model = json.findStringDeep(setOf("model", "default_model")) ?: "gpt-5.5-codex",
            metadata = "Imported JSON keys: ${json.keys().asSequence().joinToString()}"
        )
    }.getOrNull()
}

@Composable
fun CodexHubApp() {
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        androidx.compose.material3.dynamicLightColorScheme(context)
    } else {
        lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colors
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            CodexHubHome()
        }
    }
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    Chat("对话", Icons.Default.Chat),
    Providers("模型", Icons.Default.SmartToy),
    Workflows("工作流", Icons.Default.Timeline),
    Settings("设置", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodexHubHome() {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Chat) }
    val context = LocalContext.current
    val credentialStore = remember { SecureCredentialStore(context) }
    val connectedProviders = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        supportedProviders.forEach { provider ->
            connectedProviders[provider.id] = credentialStore.read(provider.id).isNotBlank()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Codex Hub", fontWeight = FontWeight.Bold)
                        Text("多模型移动编程代理", style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = {
                    IconButton(onClick = { selectedTab = AppTab.Providers }) {
                        Icon(Icons.Default.Key, contentDescription = "配置密钥")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                )
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            label = "tab-transition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { tab ->
            when (tab) {
                AppTab.Chat -> ChatScreen(connectedProviders)
                AppTab.Providers -> ProviderScreen(credentialStore, connectedProviders)
                AppTab.Workflows -> WorkflowScreen()
                AppTab.Settings -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun ChatScreen(connectedProviders: Map<String, Boolean>) {
    var selectedProvider by rememberSaveable { mutableStateOf(supportedProviders.first().id) }
    var prompt by rememberSaveable { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            ChatMessage("Codex Hub", "选择一个模型，输入需求，我会以编程代理方式拆解任务。", true)
        )
    }
    val provider = supportedProviders.first { it.id == selectedProvider }
    val isConnected = connectedProviders[selectedProvider] == true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroCard(
                title = "面向代码任务的 AI 工作台",
                subtitle = "统一 Codex、Claude Code、DeepSeek，保留授权登录与 Key 模式入口。",
                icon = Icons.Default.Code
            )
        }
        item {
            ProviderSelector(selectedProvider, onSelected = { selectedProvider = it })
        }
        items(messages) { message ->
            MessageBubble(message)
        }
        item {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("描述你的代码任务") },
                placeholder = { Text("例如：帮我给登录页增加 OAuth 和 API Key 两种入口") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                trailingIcon = {
                    IconButton(
                        enabled = prompt.isNotBlank(),
                        onClick = {
                            val userPrompt = prompt.trim()
                            messages += ChatMessage("你", userPrompt, false)
                            messages += ChatMessage(
                                provider.name,
                                buildAssistantPreview(provider, userPrompt, isConnected),
                                true
                            )
                            prompt = ""
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isConnected) "${provider.name} 已配置，可接入真实后端。" else "${provider.name} 尚未配置，当前展示离线任务规划预览。",
                style = MaterialTheme.typography.labelMedium,
                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun buildAssistantPreview(provider: AiProvider, prompt: String, isConnected: Boolean): String {
    val mode = if (isConnected) "已连接" else "未连接"
    return "[$mode · ${provider.defaultModel}] 我将把“$prompt”拆成：1) 分析上下文；2) 生成补丁；3) 运行测试；4) 汇总风险。"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderSelector(selectedProvider: String, onSelected: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        supportedProviders.forEach { provider ->
            FilterChip(
                selected = selectedProvider == provider.id,
                onClick = { onSelected(provider.id) },
                label = { Text(provider.name) },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isAssistant) Arrangement.Start else Arrangement.End
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.isAssistant) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(0.86f)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message.sender, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ProviderScreen(
    credentialStore: SecureCredentialStore,
    connectedProviders: MutableMap<String, Boolean>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroCard(
                title = "模型与授权",
                subtitle = "Codex 授权登录、Key 模式、Claude Code 与 DeepSeek 统一配置。",
                icon = Icons.Default.Security
            )
        }
        items(supportedProviders) { provider ->
            ProviderCard(provider, credentialStore, connectedProviders)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderCard(
    provider: AiProvider,
    credentialStore: SecureCredentialStore,
    connectedProviders: MutableMap<String, Boolean>
) {
    var keyValue by rememberSaveable(provider.id) { mutableStateOf("") }
    var revealKey by rememberSaveable(provider.id) { mutableStateOf(false) }
    val connected = connectedProviders[provider.id] == true

    Card(colors = CardDefaults.elevatedCardColors(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(provider.tagline, style = MaterialTheme.typography.bodyMedium)
                }
                StatusChip(connected)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                provider.modes.forEach { mode -> AssistChip(onClick = {}, label = { Text(mode.label) }) }
                ElevatedAssistChip(onClick = {}, label = { Text(provider.defaultModel) })
            }
            Text("Endpoint：${provider.endpoint}", style = MaterialTheme.typography.labelMedium)
            if (provider.id == "codex") {
                CodexOAuthImportPanel(provider, credentialStore, connectedProviders)
            }
            OutlinedTextField(
                value = keyValue,
                onValueChange = { keyValue = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("${provider.name} API Key") },
                placeholder = { Text("粘贴 Key 后点保存") },
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { revealKey = !revealKey }) {
                        Text(if (revealKey) "隐藏" else "显示")
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = keyValue.isNotBlank(),
                    onClick = {
                        credentialStore.saveAuthProfile(
                            provider.id,
                            AuthImportResult(
                                mode = "api-key",
                                displayName = "${provider.name} API Key",
                                secret = keyValue.trim(),
                                endpoint = provider.endpoint,
                                model = provider.defaultModel,
                                metadata = "Manual API key entry"
                            )
                        )
                        connectedProviders[provider.id] = true
                        keyValue = ""
                    }
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("保存 Key")
                }
                TextButton(onClick = {
                    credentialStore.clear(provider.id)
                    connectedProviders[provider.id] = false
                }) {
                    Text("断开")
                }
            }
            ProviderRuntimeCard(provider, credentialStore)
        }
    }
}


@Composable
private fun ProviderRuntimeCard(provider: AiProvider, credentialStore: SecureCredentialStore) {
    val mode = credentialStore.readField(provider.id, "mode").ifBlank { "未配置" }
    val endpoint = credentialStore.readField(provider.id, "endpoint").ifBlank { provider.endpoint }
    val model = credentialStore.readField(provider.id, "model").ifBlank { provider.defaultModel }
    val snippet = when (provider.id) {
        "codex" -> "OPENAI_BASE_URL=$endpoint\nCODEX_AUTH_MODE=$mode\nCODEX_MODEL=$model"
        "claude-code" -> "ANTHROPIC_BASE_URL=$endpoint\nANTHROPIC_MODEL=$model\nCLAUDE_CODE_USE_BEDROCK=0"
        "deepseek" -> "OPENAI_BASE_URL=$endpoint\nOPENAI_MODEL=$model\nDEEPSEEK_COMPATIBLE=true"
        else -> "AI_BASE_URL=$endpoint\nAI_MODEL=$model"
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("运行配置", fontWeight = FontWeight.Bold)
            Text("认证模式：$mode", style = MaterialTheme.typography.bodySmall)
            Text("Endpoint：$endpoint", style = MaterialTheme.typography.bodySmall)
            Text("Model：$model", style = MaterialTheme.typography.bodySmall)
            Text(snippet, style = MaterialTheme.typography.labelSmall)
        }
    }
}


@Composable
private fun CodexOAuthImportPanel(
    provider: AiProvider,
    credentialStore: SecureCredentialStore,
    connectedProviders: MutableMap<String, Boolean>
) {
    var oauthPayload by rememberSaveable(provider.id) { mutableStateOf("") }
    var importMessage by rememberSaveable(provider.id) { mutableStateOf(credentialStore.readField(provider.id, "displayName")) }
    val activeMode = credentialStore.readField(provider.id, "mode")
    val activeModel = credentialStore.readField(provider.id, "model").ifBlank { provider.defaultModel }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Codex OAuth / Sub2API 导入", fontWeight = FontWeight.Bold)
                    Text(
                        "粘贴 Codex OAuth JSON、auth.json 片段或 OAuth 回调 URL，App 会提取 token、账号、模型和兼容接口地址。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            OutlinedTextField(
                value = oauthPayload,
                onValueChange = { oauthPayload = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text("OAuth JSON / 回调 URL / Sub2API 导出") },
                placeholder = { Text("例如：{\"refresh_token\":\"...\",\"account_id\":\"...\",\"model\":\"gpt-5.5-codex\"}") },
                visualTransformation = PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = oauthPayload.isNotBlank(),
                    onClick = {
                        val profile = parseCodexOAuthImport(oauthPayload)
                        if (profile == null) {
                            importMessage = "导入失败：未找到 refresh_token、access_token、code 或 token 字段。"
                        } else {
                            credentialStore.saveAuthProfile(provider.id, profile)
                            connectedProviders[provider.id] = true
                            importMessage = "已导入 ${profile.displayName} · ${profile.model}"
                            oauthPayload = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("导入 OAuth")
                }
                TextButton(onClick = {
                    oauthPayload = ""
                    importMessage = "可重新粘贴最新 OAuth 导出内容。"
                }) {
                    Text("清空输入")
                }
            }
            Text(
                text = if (importMessage.isNotBlank()) "状态：$importMessage" else "当前：${activeMode.ifBlank { "未导入 OAuth" }} · $activeModel",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "兼容调用：Authorization: Bearer <导入的 OAuth token>，Base URL 默认为 ${provider.endpoint}，也可由导入内容覆盖。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatusChip(connected: Boolean) {
    AssistChip(
        onClick = {},
        leadingIcon = {
            Icon(
                if (connected) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = { Text(if (connected) "已连接" else "未配置") }
    )
}

@Composable
private fun WorkflowScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroCard(
                title = "自动化工作流",
                subtitle = "App 内置代码任务模板；仓库同时提供 GitHub Actions 自动构建发行版。",
                icon = Icons.Default.Sync
            )
        }
        items(workflowTemplates) { workflow -> WorkflowCard(workflow) }
    }
}

@Composable
private fun WorkflowCard(workflow: WorkflowTemplate) {
    Card {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(workflow.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(workflow.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.45f))
            workflow.steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Text(step)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    var telemetryEnabled by rememberSaveable { mutableStateOf(false) }
    var dynamicColor by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroCard(
                title = "安全默认值",
                subtitle = "默认不上传遥测，密钥不参与系统备份；后续可接入企业代理与私有网关。",
                icon = Icons.Default.Settings
            )
        }
        item {
            SettingsRow(
                title = "启用动态色彩",
                description = "遵循 Material Design 3 的系统调色板。",
                checked = dynamicColor,
                onCheckedChange = { dynamicColor = it }
            )
        }
        item {
            SettingsRow(
                title = "匿名诊断",
                description = "关闭时不会发送崩溃以外的使用数据。",
                checked = telemetryEnabled,
                onCheckedChange = { telemetryEnabled = it }
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String, icon: ImageVector) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
