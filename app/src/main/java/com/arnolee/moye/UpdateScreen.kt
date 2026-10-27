package com.arnolee.moye

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable fun UpdateSettings(updates: AppUpdateController) {
    SectionLabel("应用更新")
    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.testTag("update-settings")) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("ComicReader ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.titleMedium)
            Text(updates.status.ifBlank { "从 GitHub 获取官方正式版本" },style=MaterialTheme.typography.bodySmall)
            Row {
                Column(Modifier.weight(1f)) { Text("启动时检查更新");Text("每天最多检查一次，不自动下载",style=MaterialTheme.typography.bodySmall) }
                Switch(updates.autoCheck,updates::updateAutoCheck,modifier=Modifier.testTag("update-auto"))
            }
            OutlinedButton({ updates.check() },enabled=!updates.checking && !updates.downloading,modifier=Modifier.testTag("check-update")) {
                Text(if(updates.checking) "正在检查…" else "检查更新")
            }
            updates.release?.let { release ->
                TextButton({ updates.showDialog=true },modifier=Modifier.testTag("show-update")) { Text("${release.version} · 查看更新与安装") }
            }
        }
    }
}

@Composable fun UpdateDialog(updates: AppUpdateController,allowed: Boolean) {
    val release=updates.release ?: return
    if(!updates.showDialog || !allowed) return
    AlertDialog(onDismissRequest={ updates.showDialog=false },title={ Text("发现新版本 ${release.version}") },
        text={ Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("当前 ${BuildConfig.VERSION_NAME} · 安装包 ${String.format(Locale.US,"%.1f",release.size/1024.0/1024.0)} MB")
            Text(release.notes.ifBlank { "本次更新包含体验优化与问题修复。" },style=MaterialTheme.typography.bodySmall)
            Text(updates.status,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("update-status"))
            if(updates.downloading) {
                LinearProgressIndicator(progress={ (updates.bytes.toFloat()/release.size).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                Text("${updates.bytes*100/release.size}%",style=MaterialTheme.typography.labelMedium)
            }
            Text("直接覆盖安装，保留书架、历史与离线下载。请勿卸载旧版。下载可能使用移动流量，安装需在系统页面确认。",style=MaterialTheme.typography.bodySmall)
        } },
        confirmButton={ if(updates.downloading) TextButton(updates::cancelDownload,modifier=Modifier.testTag("cancel-update-download")) { Text("取消下载") }
            else Button({ if(updates.downloaded) updates.requestInstall() else updates.download() },modifier=Modifier.testTag("download-update")) { Text(if(updates.downloaded) "安装更新" else "下载并安装") } },
        dismissButton={ TextButton({ updates.showDialog=false }) { Text(if(updates.downloading) "稍后查看" else "稍后再说") } })
}
