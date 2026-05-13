package com.example.androidcodex

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.security.crypto.MasterKey
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val oauthCallbackUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oauthCallbackUri.value = intent?.data
        enableEdgeToEdge()
        setContent {
            CodexHubApp(callbackUri = oauthCallbackUri.value)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        oauthCallbackUri.value = intent.data
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

    fun savePendingCodexOAuth(verifier: String, state: String) {
        prefs.edit()
            .putString("codex:oauth:verifier", verifier)
            .putString("codex:oauth:state", state)
            .apply()
    }

    fun readPendingCodexVerifier(): String = prefs.getString("codex:oauth:verifier", "").orEmpty()

    fun readPendingCodexState(): String = prefs.getString("codex:oauth:state", "").orEmpty()

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
            .remove("$providerId:oauth:verifier")
            .remove("$providerId:oauth:state")
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



private const val CODEX_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
private const val CODEX_REDIRECT_URI = "http://localhost:1455/auth/callback"

private fun randomBase64Url(byteCount: Int = 32): String {
    val bytes = ByteArray(byteCount)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

private fun codeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
    return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

private fun buildCodexOAuthUrl(credentialStore: SecureCredentialStore): String {
    val verifier = randomBase64Url(64)
    val state = randomBase64Url(24)
    credentialStore.savePendingCodexOAuth(verifier, state)
    return Uri.Builder()
        .scheme("https")
        .authority("auth.openai.com")
        .appendPath("oauth")
        .appendPath("authorize")
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("client_id", CODEX_CLIENT_ID)
        .appendQueryParameter("redirect_uri", CODEX_REDIRECT_URI)
        .appendQueryParameter("scope", "openid profile email offline_access")
        .appendQueryParameter("code_challenge", codeChallenge(verifier))
        .appendQueryParameter("code_challenge_method", "S256")
        .appendQueryParameter("id_token_add_organizations", "true")
        .appendQueryParameter("codex_cli_simplified_flow", "true")
        .appendQueryParameter("originator", "codex_android")
        .appendQueryParameter("state", state)
        .build()
        .toString()
}

private fun formEncode(values: Map<String, String>): String = values.entries.joinToString("&") { (key, value) ->
    "${URLEncoder.encode(key, "UTF-8") }=${URLEncoder.encode(value, "UTF-8") }"
}

private suspend fun exchangeCodexOAuthCallback(
    callbackUrl: String,
    credentialStore: SecureCredentialStore
): AuthImportResult = withContext(Dispatchers.IO) {
    val uri = Uri.parse(callbackUrl.trim())
    val code = uri.getQueryParameter("code") ?: error("回调 URL 缺少 code 参数。")
    val state = uri.getQueryParameter("state").orEmpty()
    val expectedState = credentialStore.readPendingCodexState()
    if (expectedState.isNotBlank() && state.isNotBlank() && state != expectedState) {
        error("OAuth state 不匹配，请重新生成授权链接。")
    }
    val verifier = credentialStore.readPendingCodexVerifier().ifBlank {
        error("缺少本次授权的 PKCE verifier，请先点击生成授权链接。")
    }
    val body = formEncode(
        mapOf(
            "grant_type" to "authorization_code",
            "client_id" to CODEX_CLIENT_ID,
            "code" to code,
            "redirect_uri" to CODEX_REDIRECT_URI,
            "code_verifier" to verifier
        )
    )
    val connection = (URL("https://auth.openai.com/oauth/token").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20_000
        readTimeout = 60_000
        doOutput = true
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    }
    OutputStreamWriter(connection.outputStream).use { it.write(body) }
    val responseCode = connection.responseCode
    val response = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
        .bufferedReader()
        .use { it.readText() }
    if (responseCode !in 200..299) error("Token 交换失败 HTTP $responseCode：$response")
    val json = JSONObject(response)
    val accessToken = json.optString("access_token").takeIf { it.isNotBlank() }
        ?: error("Token 响应中没有 access_token。")
    AuthImportResult(
        mode = "codex-oauth-browser",
        displayName = "Codex OAuth",
        secret = accessToken,
        endpoint = "https://api.openai.com/v1",
        model = "gpt-5.5-codex",
        metadata = "refresh_token=${json.optString("refresh_token")}; expires_in=${json.optString("expires_in") }"
    )
}

private suspend fun callAiProvider(
    provider: AiProvider,
    credentialStore: SecureCredentialStore,
    prompt: String
): String = withContext(Dispatchers.IO) {
    val token = credentialStore.read(provider.id)
    require(token.isNotBlank()) { "请先在模型页完成 ${provider.name} 授权或保存 API Key。" }
    val endpoint = credentialStore.readField(provider.id, "endpoint").ifBlank { provider.endpoint }
    val model = credentialStore.readField(provider.id, "model").ifBlank { provider.defaultModel }
    val url = if (provider.id == "claude-code") {
        URL("${endpoint.trimEnd('/')}/messages")
    } else {
        URL("${endpoint.trimEnd('/')}/chat/completions")
    }
    val body = if (provider.id == "claude-code") {
        JSONObject()
            .put("model", model)
            .put("max_tokens", 1200)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
    } else {
        JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "You are a mobile coding agent. Answer concisely with implementation steps and code guidance."))
                    .put(JSONObject().put("role", "user").put("content", prompt))
            )
    }

    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20_000
        readTimeout = 60_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        if (provider.id == "claude-code") {
            setRequestProperty("x-api-key", token)
            setRequestProperty("anthropic-version", "2023-06-01")
        } else {
            setRequestProperty("Authorization", "Bearer $token")
        }
    }
    OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }
    val responseCode = connection.responseCode
    val response = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
        .bufferedReader()
        .use { it.readText() }
    if (responseCode !in 200..299) return@withContext "请求失败 HTTP $responseCode：$response"
    val json = JSONObject(response)
    if (provider.id == "claude-code") {
        json.optJSONArray("content")?.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
            ?: response
    } else {
        json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }
            ?: response
    }
}

@Composable
fun CodexHubApp(callbackUri: Uri? = null) {
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
            CodexHubHome(callbackUri)
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
private fun CodexHubHome(callbackUri: Uri?) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Chat) }
    val context = LocalContext.current
    val credentialStore = remember { SecureCredentialStore(context) }
    val connectedProviders = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        supportedProviders.forEach { provider ->
            connectedProviders[provider.id] = credentialStore.read(provider.id).isNotBlank()
        }
    }
    LaunchedEffect(callbackUri) {
        callbackUri?.let { uri ->
            parseCodexOAuthImport(uri.toString())?.let { profile ->
                credentialStore.saveAuthProfile("codex", profile)
                connectedProviders["codex"] = true
            }
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
    val context = LocalContext.current
    val credentialStore = remember { SecureCredentialStore(context) }
    val scope = rememberCoroutineScope()
    var isSending by rememberSaveable { mutableStateOf(false) }

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
                            prompt = ""
                            if (!isConnected) {
                                messages += ChatMessage(provider.name, buildAssistantPreview(provider, userPrompt, false), true)
                            } else {
                                isSending = true
                                scope.launch {
                                    val answer = runCatching { callAiProvider(provider, credentialStore, userPrompt) }
                                        .getOrElse { error -> "调用失败：${error.message ?: error::class.java.simpleName}" }
                                    messages += ChatMessage(provider.name, answer, true)
                                    isSending = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isSending) "${provider.name} 正在回复…" else if (isConnected) "${provider.name} 已配置，正在使用真实接口聊天。" else "${provider.name} 尚未配置，当前展示离线任务规划预览。",
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var oauthPayload by rememberSaveable(provider.id) { mutableStateOf("") }
    var importMessage by rememberSaveable(provider.id) { mutableStateOf(credentialStore.readField(provider.id, "displayName")) }
    var generatedLink by rememberSaveable(provider.id) { mutableStateOf("") }
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
                        "使用 Codex 官方客户端 ID 与 PKCE 生成链接；登录后浏览器会跳到 localhost，复制地址栏回调 URL 上传即可交换 token。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    generatedLink = buildCodexOAuthUrl(credentialStore)
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(generatedLink))) }
                        .onFailure { importMessage = "无法打开浏览器，请复制下方授权链接。" }
                }) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("生成链接并打开浏览器")
                }
                TextButton(onClick = { generatedLink = buildCodexOAuthUrl(credentialStore) }) {
                    Text("仅生成链接")
                }
            }
            if (generatedLink.isNotBlank()) {
                Text("授权链接：$generatedLink", style = MaterialTheme.typography.bodySmall)
                Text("如果浏览器最后显示 localhost 无法打开，这是正常的：复制地址栏完整 URL，粘贴到下方上传回调。", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = oauthPayload,
                onValueChange = { oauthPayload = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text("上传回调 URL / OAuth JSON / Sub2API 导出") },
                placeholder = { Text("例如：{\"refresh_token\":\"...\",\"account_id\":\"...\",\"model\":\"gpt-5.5-codex\"}") },
                visualTransformation = PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = oauthPayload.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val profileResult = if (oauthPayload.contains("code=") && oauthPayload.contains("localhost:1455")) {
                                runCatching { exchangeCodexOAuthCallback(oauthPayload, credentialStore) }
                            } else {
                                runCatching { parseCodexOAuthImport(oauthPayload) ?: error("未找到 refresh_token、access_token、code 或 token 字段。") }
                            }
                            profileResult
                                .onSuccess { profile ->
                                    credentialStore.saveAuthProfile(provider.id, profile)
                                    connectedProviders[provider.id] = true
                                    importMessage = "已导入 ${profile.displayName} · ${profile.model}"
                                    oauthPayload = ""
                                }
                                .onFailure { error ->
                                    importMessage = "导入失败：${error.message ?: error::class.java.simpleName}"
                                }
                        }
                    }
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("上传回调并导入")
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
