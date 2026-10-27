package com.arnolee.moye

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity: ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MoyeApp(vm) }
        if(savedInstanceState==null) handleIntent(intent)
        if(Build.VERSION.SDK_INT>=33 && !vm.prefs.getBoolean("notificationAsked",false)) {
            vm.prefs.edit().putBoolean("notificationAsked",true).apply()
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);handleIntent(intent) }
    private fun handleIntent(intent: Intent?) {
        if(intent?.getBooleanExtra("downloads",false)==true) { vm.tab=2;vm.screen="home" }
        intent?.dataString?.let { MangaSource.mangaId(it)?.let(vm::openBook) }
    }
}
