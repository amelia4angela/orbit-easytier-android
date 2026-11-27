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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.easytier.ui.theme.currentColors

/**
 * 「配置管理器」弹窗（原来是 MainActivity 里的 AlertDialog）。
 *
 * 逻辑与文案保持原样：点列表项 = 切换到该配置（[onSelect] 给的是下标，调用方负责
 * loadConfig 并关闭）；底部三个动作「新建…」「删除」「取消」。外观统一走 [HmDialog]
 * —— 20dp 圆角玻璃卡 + 统一标题 + 统一按钮层级；列表只是换壳，选中项用 √ 标记，
 * 与原 AlertDialog「名字 + ✓」一致。
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
    val colors = currentColors()

    HmDialog(
        visible = true,
        onDismiss = onDismiss,
        title = T("配置管理器", "Config Manager", langZh)
    ) {
        Text(
            text = T("当前: $currentName", "Current: $currentName", langZh),
            color = colors.textTertiary,
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) colors.accentContainer else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        color = if (selected) colors.accentLight else colors.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Text("✓", color = colors.accentLight, fontSize = 13.sp)
                    }
                }
            }
        }
        HmDialogActionRow {
            HmDialogSecondaryButton(
                text = T("删除", "Delete", langZh),
                modifier = Modifier.weight(1f),
                onClick = onDelete
            )
            HmDialogPrimaryButton(
                text = T("新建…", "New…", langZh),
                modifier = Modifier.weight(1f),
                onClick = onNew
            )
        }
        HmDialogTextButton(
            text = T("取消", "Cancel", langZh),
            modifier = Modifier.fillMaxWidth(),
            onClick = onDismiss
        )
    }
}

/**
 * 「配置服务器」弹窗（原来是 AlertDialog + EditText）。
 *
 * 逻辑与文案保持原样：输入 URL → 「获取」回调 [onFetch]（空串仍交给调用方忽略），
 * 「取消」关闭。外框/标题/按钮统一走 [HmDialog]。
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

    HmDialog(
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
        HmDialogActionRow {
            HmDialogSecondaryButton(
                text = T("取消", "Cancel", langZh),
                modifier = Modifier.weight(1f),
                onClick = onDismiss
            )
            HmDialogPrimaryButton(
                text = T("获取", "Fetch", langZh),
                modifier = Modifier.weight(1f),
                onClick = { onFetch(url) }
            )
        }
    }
}
