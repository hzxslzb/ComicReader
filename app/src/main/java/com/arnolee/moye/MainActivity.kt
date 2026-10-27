package com.arnolee.moye

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity: ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val installPermission=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(packageManager.canRequestPackageInstalls()) installUpdate()
        else vm.updates.installError("尚未允许安装应用，可再次点击安装更新进行授权")
    }
    private var preparingInstall=false
    private fun installUpdate() {
        if(preparingInstall) return
        preparingInstall=true
        lifecycleScope.launch {
            try {
                val file=vm.updates.verifyForInstall()
                if(!packageManager.canRequestPackageInstalls()) {
                    installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")))
                } else startActivity(UpdateInstaller.intent(this@MainActivity,file))
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { vm.updates.installError("无法开始安装：${e.localizedMessage ?: "请重试"}") }
            finally { preparingInstall=false }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MoyeApp(vm) }
        if(savedInstanceState==null) { handleIntent(intent);vm.updates.check(manual=false) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                snapshotFlow { vm.updates.installRequested }.collect { requested ->
                    if(requested) { vm.updates.installRequested=false;installUpdate() }
                }
            }
        }
        if(Build.VERSION.SDK_INT>=33 && !vm.prefs.getBoolean("notificationAsked",false)) {
            vm.prefs.edit().putBoolean("notificationAsked",true).apply()
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    override fun onStart() { super.onStart();vm.readerVisibility(true) }
    override fun onStop() { vm.readerVisibility(false);super.onStop() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);handleIntent(intent) }
    private fun handleIntent(intent: Intent?) {
        if(intent?.getBooleanExtra("downloads",false)==true) { vm.tab=2;vm.screen="home" }
        intent?.dataString?.let { MangaSource.mangaId(it)?.let(vm::openBook) }
    }
}
