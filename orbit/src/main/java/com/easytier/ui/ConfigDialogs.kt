package com.easytier.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.ui.glass.GlassRadius
import com.easytier.ui.glass.GlassSpace
import com.easytier.ui.glass.components.GlassButton
import com.easytier.ui.glass.components.GlassButtonTone
import com.easytier.ui.glass.components.GlassDialog
import com.easytier.ui.glass.glassColors
import com.easytier.ui.glass.glassShape
import com.easytier.ui.theme.currentColors

/**
 * 「配置管理器」弹窗（原来是 MainActivity 里的 AlertDialog）。
 *
 * 逻辑与文案保持原样：点列表项 = 切换到该配置（[onSelect] 给的是下标，调用方负责
 * loadConfig 并关闭）；底部两个动作「删除」「新建…」。
 *
 * 外观统一走 [GlassDialog]（20dp 圆角乳白玻璃面板 + specular/描边 + 弹性浮现），
 * 按钮走 [GlassButton]：语义色 + spring 按压；列表只是换壳，选中项用 ✓ 标记。
 *
 * 「取消」按钮已去掉：Dialog 会把「点空白处 / 返回键」变成 onDismiss，少一个按钮更干净。
 */
@Composable
fun ConfigManagerDialog(
    names: List<String>,
    currentName: String,
    langZh: Boolean,
    onSelect: (Int) -> Unit,
    onNew: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val glass = glassColors()

    GlassDialog(
        visible = true,
        onDismiss = onDismiss,
        title = T("配置管理器", "Config Manager", langZh)
    ) {
        Text(
            text = T("当前: $currentName", "Current: $currentName", langZh),
            color = glass.textTertiary,
            fontSize = 12.sp
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            names.forEachIndexed { index, name ->
                val selected = name == currentName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(glassShape(GlassRadius.sm))
                        .background(if (selected) glass.accentSoft else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        color = if (selected) glass.accent else glass.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Text("✓", color = glass.accent, fontSize = 13.sp)
                    }
                }
            }
        }
        GlassDialogActionRow {
            GlassButton(
                text = T("删除", "Delete", langZh),
                tone = GlassButtonTone.SECONDARY,
                rounded = GlassRadius.md,
                minHeight = 40.dp,
                modifier = Modifier.weight(1f),
                onClick = onDelete
            )
            GlassButton(
                text = T("新建…", "New…", langZh),
                tone = GlassButtonTone.PRIMARY,
                rounded = GlassRadius.md,
                minHeight = 40.dp,
                modifier = Modifier.weight(1f),
                onClick = onNew
            )
        }
    }
}

/**
 * 「确认删除 <name>？」—— 替代原来的「先选要删哪一个」列表。
 *
 * 删除对象固定为**当前选中的配置**（由调用方传 name），所以整个流程只剩一个确认框：
 * 按钮「关闭」+「删除（红）」；点空白处 / 返回键同样取消（[GlassDialog] 的 onDismiss）。
 * 默认配置不可删的校验放在调用方（[com.easytier.MainActivity.showDeleteConfigDialog]）。
 */
@Composable
fun DeleteConfigConfirmDialog(
    name: String?,
    langZh: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = glassColors()
    // 退出动画期间 name 已经变 null，正文还得显示原名字 —— 记住最后一次非空值。
    var lastShown by remember { mutableStateOf("") }
    LaunchedEffect(name) { if (name != null) lastShown = name }
    val display = name ?: lastShown

    GlassDialog(
        visible = name != null,
        onDismiss = onDismiss,
        title = T("删除配置", "Delete Config", langZh)
    ) {
        Text(
            text = T("确认删除「$display」？", "Delete \"$display\"?", langZh),
            color = glass.textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Text(
            text = T(
                "该配置会从本机配置列表中移除，无法撤回。",
                "The config file is removed from this device. This cannot be undone.",
                langZh
            ),
            color = glass.textTertiary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        GlassDialogActionRow {
            GlassButton(
                text = T("关闭", "Close", langZh),
                tone = GlassButtonTone.SECONDARY,
                rounded = GlassRadius.md,
                minHeight = 40.dp,
                modifier = Modifier.weight(1f),
                onClick = onDismiss
            )
            GlassButton(
                text = T("删除", "Delete", langZh),
                tone = GlassButtonTone.DANGER,
                rounded = GlassRadius.md,
                minHeight = 40.dp,
                modifier = Modifier.weight(1f),
                onClick = { if (display.isNotEmpty()) onConfirm(display) }
            )
        }
    }
}

/**
 * 「新建配置」—— 替代原来的 AlertDialog + EditText。
 *
 * 两层：输入名字（同名时提示会覆盖）→ 覆盖确认。整个流程都在 [GlassDialog] 外壳里，
 * 与配置管理器 / 删除确认的圆角、乳白玻璃底、按钮层级完全一致。
 */
@Composable
fun NewConfigDialog(
    visible: Boolean,
    langZh: Boolean,
    existingNames: List<String>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = currentColors()
    val glass = glassColors()
    var name by remember { mutableStateOf("") }
    var askOverwrite by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            name = ""
            askOverwrite = false
        }
    }

    val trimmed = name.trim()
    val exists = trimmed.isNotEmpty() && existingNames.any { it == trimmed }

    GlassDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = if (askOverwrite) {
            T("覆盖确认", "Overwrite?", langZh)
        } else {
            T("新建配置", "New Config", langZh)
        }
    ) {
        if (askOverwrite) {
            Text(
                text = T("配置「$trimmed」已存在，覆盖吗？", "Config \"$trimmed\" exists. Overwrite?", langZh),
                color = glass.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            GlassDialogActionRow {
                GlassButton(
                    text = T("返回", "Back", langZh),
                    tone = GlassButtonTone.SECONDARY,
                    rounded = GlassRadius.md,
                    minHeight = 40.dp,
                    modifier = Modifier.weight(1f),
                    onClick = { askOverwrite = false }
                )
                GlassButton(
                    text = T("覆盖", "Overwrite", langZh),
                    tone = GlassButtonTone.PRIMARY,
                    rounded = GlassRadius.md,
                    minHeight = 40.dp,
                    modifier = Modifier.weight(1f),
                    onClick = { onSave(trimmed) }
                )
            }
        } else {
            HmTextField(
                value = name,
                onValueChange = { name = it },
                label = "",
                placeholder = T("输入新配置名称", "Enter config name", langZh),
                colors = colors,
                hint = if (exists) {
                    T("已存在同名配置，保存会覆盖", "A config with this name exists; saving overwrites it", langZh)
                } else null,
                // 弹窗里永远可编辑（不受运行中锁定影响）
                enabled = true
            )
            GlassDialogActionRow {
                GlassButton(
                    text = T("关闭", "Close", langZh),
                    tone = GlassButtonTone.SECONDARY,
                    rounded = GlassRadius.md,
                    minHeight = 40.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                )
                GlassButton(
                    text = T("保存", "Save", langZh),
                    tone = GlassButtonTone.PRIMARY,
                    rounded = GlassRadius.md,
                    minHeight = 40.dp,
                    modifier = Modifier.weight(1f),
                    enabled = trimmed.isNotEmpty(),
                    onClick = {
                        if (exists) askOverwrite = true else onSave(trimmed)
                    }
                )
            }
        }
    }
}

/**
 * 「配置服务器」弹窗（原来是 AlertDialog + EditText）。
 *
 * 逻辑与文案保持原样：输入 URL → 「获取」回调 [onFetch]（空串仍交给调用方忽略），
 * 「关闭」/ 点空白关闭。外框/标题/按钮统一走 [GlassDialog] + [GlassButton]。
 */
@Composable
fun ConfigServerDialog(
    visible: Boolean,
    langZh: Boolean,
    onFetch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = currentColors()
    var url by remember { mutableStateOf("") }
    LaunchedEffect(visible) { if (visible) url = "" }

    GlassDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = T("连接配置服务器", "Config Server", langZh)
    ) {
        HmTextField(
            value = url,
            onValueChange = { url = it },
            label = "",
            placeholder = T("输入配置服务器 URL", "Enter config server URL", langZh),
            colors = colors
        )
        GlassDialogActionRow {
            GlassButton(
                text = T("关闭", "Close", langZh),
                tone = GlassButtonTone.SECONDARY,
                rounded = GlassRadius.md,
                minHeight = 40.dp,
                modifier = Modifier.weight(1f),
                onClick = onDismiss
            )
            GlassButton(
                text = T("获取", "Fetch", langZh),
                tone = GlassButtonTone.PRIMARY,
                rounded = GlassRadius.md,
                minHeight = 40.dp,
                modifier = Modifier.weight(1f),
                onClick = { onFetch(url) }
            )
        }
    }
}

/** 弹窗底部按钮行：每个按钮等分宽度，间距 [GlassSpace.sm]。 */
@Composable
private fun GlassDialogActionRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GlassSpace.sm),
        content = content
    )
}
