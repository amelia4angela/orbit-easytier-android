package com.easytier

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.easytier.ui.ConfigForm
import com.easytier.ui.ConfigState
import com.easytier.ui.drawOrbitBackdrop
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.GlassSpace
import com.easytier.ui.glass.GlassTheme
import com.easytier.ui.glass.components.GlassBottomSheet
import com.easytier.ui.glass.components.GlassButton
import com.easytier.ui.glass.components.GlassButtonTone
import com.easytier.ui.glass.components.GlassDialog
import com.easytier.ui.glass.components.GlassSurface
import com.easytier.ui.glass.components.GlassTextField
import com.easytier.ui.glass.components.GlassTopBar
import com.easytier.ui.glass.effects.GlassSky
import com.easytier.ui.glass.glassColors
import com.easytier.ui.glass.glassShape
import com.easytier.ui.glass.rememberGlassLevel
import com.easytier.ui.theme.EasyTierTheme
import com.easytier.ui.theme.ProvideThemeMode
import com.easytier.ui.theme.currentColors
import org.json.JSONObject
import java.io.File

/**
 * 配置编辑 Activity（Compose + 液态玻璃）
 *
 * 对标官方 <RemoteManagement> 组件：配置列表 + 编辑器 + 导入导出
 *
 * 外观：GlassSky 页面底 + 玻璃顶栏 + 悬浮玻璃动作条 + 玻璃弹窗/底部面板
 * （材质基准 = ProxyLaunchCard 的乳白玻璃）。
 *
 * 返回结果：
 *   RESULT_OK → intent 包含 EXTRA_CONFIG_NAME（用户选择的配置名）
 *   RESULT_CANCELED → 用户取消
 */
class ConfigEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CURRENT_CONFIG = "current_config_name"
        const val EXTRA_CONFIG_NAME = "selected_config_name"
        private const val REQUEST_IMPORT_TOML = 2001
        /** 导出 .toml：ACTION_CREATE_DOCUMENT 的返回 */
        private const val REQUEST_EXPORT_TOML = 2002
    }

    private var currentConfigName = "default"
    private var langZh = true
    private var isDarkTheme = false
    /** 待写入的 .toml 内容（SAF 选完位置后才写） */
    private var pendingExportToml: String? = null

    /**
     * 导入结果提示（原来用 `android.app.AlertDialog.Builder` 直接弹平台弹窗，是整页最扎眼的
     * 一块 Material）。改为 Compose 状态 → [GlassDialog] 玻璃壳，文案/回调不变。
     *
     * @property applyName 非空时，「好的」= 带着这个名字回传 RESULT_OK 并关闭本页
     * （只有「导入成功」报告是这样；「无法识别的格式」只是提示）。
     */
    private data class ImportNotice(
        val title: String,
        val message: String,
        val applyName: String? = null
    )

    private val importNotice = mutableStateOf<ImportNotice?>(null)

    // ── Lifecycle ──

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        langZh = getSharedPreferences("easytier_lang", MODE_PRIVATE)
            .getBoolean("lang_zh", true)
        isDarkTheme = getSharedPreferences("easytier_theme", MODE_PRIVATE)
            .getBoolean("dark_theme", false)
        currentConfigName = intent?.getStringExtra(EXTRA_CURRENT_CONFIG) ?: "default"

        // Load initial state from disk
        val initialJson = loadConfigFromDisk(currentConfigName)
        val initialConfig = TomlUtils.jsonToConfigState(initialJson)

        setContent {
            // 独立的 Activity 也有自己的组合：同样在顶层读一次用户选择（<33 恒为 UNIVERSAL）。
            ProvideThemeMode {
            EasyTierTheme(darkTheme = isDarkTheme) {
            // 液态玻璃作用域：只提供调色板 + 质量档位，ConfigForm 的卡片/分组容器
            // （flGlassCard → glassCard）按档位渲染；编辑逻辑与此无关，一行未动。
            GlassTheme(level = rememberGlassLevel().value) {
                // GlassSky = 整页的玻璃采样源（顶栏/底栏的模糊采的就是它），
                // 底色与 MainScreen 同一套「主题渐变 + 两团柔光」，所以这一页看起来
                // 和主界面是同一个 App；页内任何布局/业务逻辑都不受影响。
                val skyColors = glassColors()
                val appColors = currentColors()
                GlassSky(
                    modifier = Modifier.fillMaxSize(),
                    colors = skyColors,
                    backdrop = { size -> drawOrbitBackdrop(appColors, size) }
                ) {
                    ConfigEditScreen(
                        initialConfig = initialConfig,
                        currentConfigName = currentConfigName,
                        langZh = langZh,
                        onApply = { name -> onApplyConfig(name) },
                        onBack = { finish() },
                        onImport = { importToml() },
                        onNewConfig = { name ->
                            currentConfigName = name
                            saveConfigToDisk(name, TomlUtils.jsonToConfigState(loadConfigFromDisk("default")))
                        },
                        onDeleteConfig = { name ->
                            deleteConfigFromDisk(name)
                            // After delete, load "default" if was current
                            if (currentConfigName == name) {
                                currentConfigName = "default"
                            }
                        },
                        onLoadConfig = { name ->
                            val json = loadConfigFromDisk(name)
                            TomlUtils.jsonToConfigState(json)
                        },
                        onSaveConfig = { name, state ->
                            currentConfigName = name
                            saveConfigToDisk(name, state)
                        },
                        onExportToml = { state -> exportToml(state) }
                    )
                }
            } // end GlassTheme
            } // end EasyTierTheme
            } // end ProvideThemeMode
        }
    }

    // ── ConfigEditScreen (Compose) ──

    @Composable
    private fun ConfigEditScreen(
        initialConfig: ConfigState,
        currentConfigName: String,
        langZh: Boolean,
        onApply: (String) -> Unit,
        onBack: () -> Unit,
        onImport: () -> Unit,
        onNewConfig: (String) -> Unit,
        onDeleteConfig: (String) -> Unit,
        onLoadConfig: (String) -> ConfigState,
        onSaveConfig: (String, ConfigState) -> Unit,
        onExportToml: (ConfigState) -> Unit
    ) {
        val tr: (String, String) -> String = { zh, en -> if (langZh) zh else en }
        // 玻璃调色板：页面内所有颜色都从这里取，浅色/深色自动成立。
        val glass = glassColors()

        // ── State ──
        var configState by remember { mutableStateOf(initialConfig) }
        var configName by remember { mutableStateOf(currentConfigName) }
        var showConfigPicker by remember { mutableStateOf(false) }
        var showNewConfigDialog by remember { mutableStateOf(false) }
        var showDeleteConfirm by remember { mutableStateOf(false) }
        var showExportSheet by remember { mutableStateOf(false) }

        // Share: write the generated .toml into filesDir and hand it to any app
        val shareCtx = LocalContext.current
        fun shareToml() {
            try {
                val toml = TomlUtils.buildToml(configState)
                val dir = File(shareCtx.filesDir, "share").apply { mkdirs() }
                val safeName = configName.trim().ifEmpty { "config" }.replace(Regex("[^\\w.-]"), "_")
                val file = File(dir, "$safeName.toml")
                file.writeText(toml)
                val uri = FileProvider.getUriForFile(shareCtx, shareCtx.packageName + ".fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                shareCtx.startActivity(Intent.createChooser(send, "Share config"))
            } catch (e: Exception) {
                Toast.makeText(shareCtx, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        var exportTomlContent by remember { mutableStateOf("") }
        val configNames = remember { mutableStateListOf<String>() }
        // Refresh config list
        LaunchedEffect(configName) {
            configNames.clear()
            configNames.addAll(listConfigFiles())
        }

        Scaffold(
            // Scaffold 只负责布局与 insets；底色交给 GlassSky，所以容器必须透明。
            containerColor = Color.Transparent,
            topBar = {
                // 玻璃顶栏：与二级页（SettingsSubPage）同一套写法 —— 悬浮玻璃面板 +
                // 返回 + 居中标题；返回仍走 finish()。
                GlassTopBar(
                    title = tr("编辑网络", "Edit Network"),
                    onBack = { finish() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            },
            bottomBar = {
                // 悬浮玻璃动作条：导入 / 导出 / 分享 / 应用（原 Material Button 组）。
                // 语义映射——三个原 OutlinedButton → SECONDARY，原 Button → PRIMARY；
                // onClick / 顺序 / 横向滚动与原来完全一致。
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    radius = GlassRadius.md,
                    // 乳白一档：填充比默认玻璃更实，底面渐变上按钮才站得住。
                    intensity = 1.15f,
                    shadowElevation = 10.dp,
                    contentPadding = PaddingValues(
                        horizontal = GlassSpace.sm,
                        vertical = GlassSpace.xs
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassButton(
                            text = tr("导入", "Import"),
                            leadingIcon = Icons.Default.FileOpen,
                            tone = GlassButtonTone.SECONDARY,
                            rounded = GlassRadius.md,
                            minHeight = 44.dp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            onClick = { onImport() }
                        )

                        GlassButton(
                            text = tr("导出", "Export"),
                            leadingIcon = Icons.Default.Save,
                            tone = GlassButtonTone.SECONDARY,
                            rounded = GlassRadius.md,
                            minHeight = 44.dp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            onClick = {
                                exportTomlContent = TomlUtils.buildToml(configState)
                                showExportSheet = true
                            }
                        )

                        // Share — hand the generated .toml to any app via ACTION_SEND
                        GlassButton(
                            text = tr("分享", "Share"),
                            leadingIcon = Icons.Default.Share,
                            tone = GlassButtonTone.SECONDARY,
                            rounded = GlassRadius.md,
                            minHeight = 44.dp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            onClick = { shareToml() }
                        )

                        GlassButton(
                            text = tr("应用", "Apply"),
                            leadingIcon = Icons.Default.Check,
                            tone = GlassButtonTone.PRIMARY,
                            rounded = GlassRadius.md,
                            minHeight = 44.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            onClick = {
                                onSaveConfig(configName, configState)
                                onApply(configName)
                            }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // 与 MainScreen 的 ConfigTab 一样留 16dp 页边，卡片不再贴边。
                    .padding(horizontal = 16.dp)
                    .padding(top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 8.dp)
            ) {
                ConfigForm(
                    config = configState,
                    langZh = langZh,
                    onConfigChange = { configState = it }
                )
            }
        }

        // ── Dialogs ──
        // 全部换成玻璃壳（GlassDialog）：20dp 圆角 + 乳白玻璃填充 + 顶部高光/0.5dp 描边，
        // 弹性浮现（GlassMotion.appear spring）。规则沿用既有约定：确认类弹窗不放「取消」，
        // 点空白处 / 返回键即取消；删除类用 danger 红钮。业务分支（保存、切换、删除、导入导出）
        // 与原 AlertDialog 完全一致。

        // Config Picker —— 点一项 = 选中该配置（先自动保存当前配置，再载入所选项）
        GlassDialog(
            visible = showConfigPicker,
            onDismiss = { showConfigPicker = false },
            title = tr("选择配置", "Select Config")
        ) {
            if (configNames.isEmpty()) {
                Text(
                    tr("暂无已保存的配置", "No saved configs"),
                    color = glass.textSecondary,
                    fontSize = 13.sp
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    configNames.forEach { name ->
                        val isCurrent = name == configName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(glassShape(GlassRadius.sm))
                                .background(if (isCurrent) glass.accentSoft else Color.Transparent)
                                .clickable {
                                    // Auto-save before switching
                                    onSaveConfig(configName, configState)
                                    configState = onLoadConfig(name)
                                    configName = name
                                    showConfigPicker = false
                                }
                                .padding(horizontal = 10.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                color = if (isCurrent) glass.accent else glass.textPrimary,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCurrent) {
                                Text("✓", color = glass.accent, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // New Config Dialog
        GlassDialog(
            visible = showNewConfigDialog,
            onDismiss = { showNewConfigDialog = false },
            title = tr("新建配置", "New Config")
        ) {
            var newName by remember { mutableStateOf("") }

            GlassTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = tr("配置名称", "Config name"),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GlassSpace.sm)
            ) {
                GlassButton(
                    text = tr("关闭", "Close"),
                    tone = GlassButtonTone.SECONDARY,
                    rounded = GlassRadius.md,
                    minHeight = 44.dp,
                    modifier = Modifier.weight(1f),
                    onClick = { showNewConfigDialog = false }
                )
                GlassButton(
                    text = tr("保存", "Save"),
                    tone = GlassButtonTone.PRIMARY,
                    rounded = GlassRadius.md,
                    minHeight = 44.dp,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val name = newName.trim()
                        if (name.isEmpty()) {
                            Toast.makeText(
                                this@ConfigEditActivity,
                                tr("名称不能为空", "Name cannot be empty"),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // Save current first, then save as new
                            onSaveConfig(configName, configState)
                            onNewConfig(name)
                            configName = name
                            showNewConfigDialog = false
                        }
                    }
                )
            }
        }

        // Delete Confirm Dialog —— 删除是破坏性操作，确认钮走 DANGER 红
        GlassDialog(
            visible = showDeleteConfirm,
            onDismiss = { showDeleteConfirm = false },
            title = tr("删除配置", "Delete Config")
        ) {
            Text(
                text = tr("确定删除「$configName」？", "Delete '$configName'?"),
                color = glass.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GlassSpace.sm)
            ) {
                GlassButton(
                    text = tr("关闭", "Close"),
                    tone = GlassButtonTone.SECONDARY,
                    rounded = GlassRadius.md,
                    minHeight = 44.dp,
                    modifier = Modifier.weight(1f),
                    onClick = { showDeleteConfirm = false }
                )
                GlassButton(
                    text = tr("删除", "Delete"),
                    tone = GlassButtonTone.DANGER,
                    rounded = GlassRadius.md,
                    minHeight = 44.dp,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDeleteConfig(configName)
                        configState = onLoadConfig("default")
                        configName = "default"
                        showDeleteConfirm = false
                        Toast.makeText(
                            this@ConfigEditActivity,
                            tr("已删除", "Deleted"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }

        // Export TOML Bottom Sheet —— 玻璃面板（原来 Material ModalBottomSheet）；
        // 预览区用 stableFill 做内嵌不透明块，等宽正文压在乳白玻璃上也可读。
        GlassBottomSheet(
            visible = showExportSheet,
            onDismiss = { showExportSheet = false },
            title = tr("导出配置 (TOML)", "Export Config (TOML)")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clip(glassShape(GlassRadius.sm))
                    .background(glass.stableFill, glassShape(GlassRadius.sm))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = exportTomlContent,
                    color = glass.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }

            GlassButton(
                text = tr("保存为 .toml 文件", "Save as .toml file"),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    showExportSheet = false
                    onExportToml(configState)
                }
            )
        }

        // 导入结果 / 无法识别（原 android.app.AlertDialog）—— 同一套玻璃壳；
        // 「好的」：报告型（applyName != null）回传 RESULT_OK 并关闭本页，提示型只关弹窗。
        val noticeNow = importNotice.value
        var lastNotice by remember { mutableStateOf<ImportNotice?>(null) }
        LaunchedEffect(noticeNow) { if (noticeNow != null) lastNotice = noticeNow }
        val notice = noticeNow ?: lastNotice
        GlassDialog(
            visible = noticeNow != null,
            onDismiss = { importNotice.value = null },
            title = notice?.title
        ) {
            Text(
                text = notice?.message ?: "",
                color = glass.textSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            GlassButton(
                text = tr("好的", "OK"),
                modifier = Modifier.fillMaxWidth(),
                rounded = GlassRadius.sm,
                minHeight = 44.dp,
                onClick = {
                    val applyName = notice?.applyName
                    importNotice.value = null
                    if (applyName != null) {
                        val res = Intent()
                        res.putExtra(EXTRA_CONFIG_NAME, applyName)
                        setResult(RESULT_OK, res)
                        finish()
                    }
                }
            )
        }
    }

    // ── Apply ──

    private fun onApplyConfig(name: String) {
        val result = Intent()
        result.putExtra(EXTRA_CONFIG_NAME, name)
        setResult(RESULT_OK, result)
        finish()
    }

    // ── Config Persistence ──

    private fun getConfigsDir(): File {
        return File(filesDir, "configs").also { it.mkdirs() }
    }

    private fun getExternalConfigsDir(): File {
        return if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            getConfigsDir()
        } else if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).parentFile,
                "EasyTier/configs"
            ).also { it.mkdirs() }
        } else getConfigsDir()
    }

    private fun sanitizeName(name: String): String {
        return name.replace(Regex("[/\\\\:*?\"<>|]"), "_").take(64)
    }

    private fun listConfigFiles(): List<String> {
        return getConfigsDir().listFiles()
            ?.filter { it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted() ?: emptyList()
    }

    private fun loadConfigFromDisk(name: String): JSONObject {
        var file = File(getConfigsDir(), "${sanitizeName(name)}.json")
        if (!file.exists()) {
            val extFile = File(getExternalConfigsDir(), "${sanitizeName(name)}.json")
            if (extFile.exists()) {
                try {
                    extFile.copyTo(file, overwrite = true)
                    AppLogger.i("ConfigEdit", "Config restored from external: $name")
                } catch (_: Throwable) {}
            }
        }
        return if (file.exists()) {
            try {
                JSONObject(file.readText())
            } catch (t: Throwable) {
                AppLogger.e("ConfigEdit", "Config parse error: $name", t)
                JSONObject()
            }
        } else {
            JSONObject()
        }
    }

    /**
     * Save config state to disk (internal + external).
     */
    private fun saveConfigToDisk(name: String, state: ConfigState) {
        val json = TomlUtils.configStateToJson(state).toString()
        val sanitized = sanitizeName(name)
        File(getConfigsDir(), "$sanitized.json").writeText(json)
        try {
            File(getExternalConfigsDir(), "$sanitized.json").writeText(json)
        } catch (t: Throwable) {
            AppLogger.w("ConfigEdit", "External config save failed: ${t.message}")
        }

        AppLogger.i("ConfigEdit", "Config saved: $name")
    }

    private fun deleteConfigFromDisk(name: String) {
        File(getConfigsDir(), "${sanitizeName(name)}.json").delete()
        try {
            File(getExternalConfigsDir(), "${sanitizeName(name)}.json").delete()
        } catch (_: Throwable) {}
    }

    // ── Import / Export ──

    private fun exportToml(state: ConfigState) {
        pendingExportToml = TomlUtils.buildToml(state)
        try {
            startActivityForResult(
                TomlFileExport.createDocumentIntent(TomlFileExport.fileName(currentConfigName)),
                REQUEST_EXPORT_TOML
            )
        } catch (t: Throwable) {
            pendingExportToml = null
            AppLogger.e("ConfigEdit", "Cannot launch document picker", t)
            Toast.makeText(
                this,
                tr("无法打开文件保存器: ${t.message}", "Cannot open file picker: ${t.message}"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun importToml() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, tr("选择配置文件 (TOML)", "Select config file (TOML)"))
        }
        startActivityForResult(intent, REQUEST_IMPORT_TOML)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMPORT_TOML && resultCode == RESULT_OK && data?.data != null) {
            handleTomlImport(data.data!!)
            return
        }
        if (requestCode == REQUEST_EXPORT_TOML) {
            val toml = pendingExportToml
            pendingExportToml = null
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null && toml != null) {
                val err = TomlFileExport.write(this, uri, toml)
                if (err == null) {
                    AppLogger.i("ConfigEdit", "Config exported: $uri")
                    Toast.makeText(this, tr("已导出 .toml 文件", ".toml file exported"),
                        Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, tr("导出失败: $err", "Export failed: $err"),
                        Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleTomlImport(uri: android.net.Uri) {
        try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            val fileName = cursor?.use {
                val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && it.moveToFirst()) it.getString(nameIdx) else "unknown.toml"
            } ?: "unknown.toml"

            val inputStream = contentResolver.openInputStream(uri) ?: run {
                Toast.makeText(this, tr("无法打开文件", "Cannot open file"), Toast.LENGTH_SHORT).show()
                return
            }
            val text = inputStream.bufferedReader().use { it.readText() }
            // Lenient parse: structure/field-name/stray content doesn't matter.
            val result = TomlUtils.parseLenient(text)
            if (!result.recognized) {
                AppLogger.w("ConfigEdit", "Import: nothing recognized in $fileName")
                // 玻璃提示弹窗（原来是 android.app.AlertDialog），只有「好的」= 关掉弹窗。
                importNotice.value = ImportNotice(
                    title = tr("无法识别的格式", "Unrecognized format"),
                    message = buildString {
                        append(tr("没能从「$fileName」中提取到任何配置信息。\n",
                            "Could not extract any config from \"$fileName\".\n"))
                        if (result.warnings.isNotEmpty()) {
                            append(tr("\n提示:\n", "\nNotes:\n"))
                            result.warnings.forEach { append("• ").append(it).append('\n') }
                        }
                    }.trim()
                )
                return
            }

            val autoName = TomlUtils.deriveNameFromState(result.state)
            saveConfigToDisk(autoName, result.state)
            AppLogger.i("ConfigEdit",
                "Imported config: $autoName nodes=${result.nodesFound} fields=${result.fieldsFound.size}")

            val report = buildString {
                append(tr("已导入并保存: $autoName\n\n", "Imported & saved: $autoName\n\n"))
                append(tr("识别到 ${result.nodesFound} 个节点、${result.fieldsFound.size} 个字段\n",
                    "Found ${result.nodesFound} node(s) and ${result.fieldsFound.size} field(s)\n"))
                if (result.fieldsFound.isNotEmpty()) {
                    append(tr("字段: ", "Fields: "))
                    append(result.fieldsFound.joinToString(tr("、", ", ")))
                    append('\n')
                }
                if (result.warnings.isNotEmpty()) {
                    append(tr("\n提示:\n", "\nNotes:\n"))
                    result.warnings.forEach { append("• ").append(it).append('\n') }
                }
            }.trim()
            // 报告型提示：「好的」= 带名字回传 RESULT_OK 并关闭本页（业务结果不变）。
            importNotice.value = ImportNotice(
                title = tr("导入结果", "Import result"),
                message = report,
                applyName = autoName
            )
        } catch (t: Throwable) {
            Toast.makeText(this, tr("导入错误: ${t.message}", "Import error: ${t.message}"), Toast.LENGTH_LONG).show()
            AppLogger.e("ConfigEdit", "TOML import error", t)
        }
    }

    private fun tr(zh: String, en: String): String = if (langZh) zh else en
}
