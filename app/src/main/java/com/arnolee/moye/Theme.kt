package com.arnolee.moye

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val Paper=Color(0xFFF6F5EF)
val Pine=Color(0xFF356653)
private val Light=lightColorScheme(primary=Pine,onPrimary=Color.White,primaryContainer=Color(0xFFDDE9DF),onPrimaryContainer=Color(0xFF193C2F),secondary=Color(0xFF9A7446),secondaryContainer=Color(0xFFF1E7D5),background=Paper,surface=Paper,surfaceContainer=Color(0xFFEDEDE5),surfaceContainerLow=Color(0xFFFCFCF8),surfaceVariant=Color(0xFFE9EBE2),onSurface=Color(0xFF252E28),onSurfaceVariant=Color(0xFF727C73),outline=Color(0xFFADB6AB),outlineVariant=Color(0xFFE0E4D9))
private val Dark=darkColorScheme(primary=Color(0xFFB4D4BA),onPrimary=Color(0xFF193C2F),primaryContainer=Color(0xFF294B3D),onPrimaryContainer=Color(0xFFD8EBDD),secondary=Color(0xFFD6B98B),secondaryContainer=Color(0xFF4C4030),background=Color(0xFF141B17),surface=Color(0xFF141B17),surfaceContainer=Color(0xFF202B24),surfaceContainerLow=Color(0xFF1C251F),surfaceVariant=Color(0xFF2B372F),onSurface=Color(0xFFE5E9DE),onSurfaceVariant=Color(0xFFA4B0A4),outline=Color(0xFF778779),outlineVariant=Color(0xFF344238))
@Composable fun MoyeTheme(mode: String,content: @Composable ()->Unit) {
    val dark=mode=="dark" || (mode=="system" && isSystemInDarkTheme())
    val view=LocalView.current
    SideEffect { (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it,view).apply { isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark } } }
    MaterialTheme(colorScheme=if(dark) Dark else Light,typography=Typography(
        headlineLarge=TextStyle(fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,fontSize=32.sp,lineHeight=40.sp),
        headlineMedium=TextStyle(fontWeight=FontWeight.Bold,fontSize=26.sp,lineHeight=34.sp),
        titleLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=21.sp,lineHeight=29.sp),
        titleMedium=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=24.sp),
        bodyLarge=TextStyle(fontSize=16.sp,lineHeight=26.sp),
        bodyMedium=TextStyle(fontSize=14.sp,lineHeight=22.sp),
        labelSmall=TextStyle(fontSize=11.sp,lineHeight=16.sp,letterSpacing=1.sp)
    ),content=content)
}
