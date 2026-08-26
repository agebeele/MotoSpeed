package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// High Density Industrial Dark Theme Colors
val CockpitBackground = Color(0xFF0B0D12)
val CockpitSurface = Color(0xFF131722)
val CockpitSurfaceVariant = Color(0xFF1A202E)
val CockpitCardBorder = Color(0xFF242C3D)
val CockpitCardBorderHover = Color(0xFF38BDF8)

// High-Contrast Speed & Telemetry Accents
val NeonCyan = Color(0xFF00E5FF)
val NeonCyanGlow = Color(0x3300E5FF)
val ElectricOrange = Color(0xFFFF6D00)
val RacingRed = Color(0xFFFF2A4D)
val RacingGreen = Color(0xFF10B981)
val SpeedAmber = Color(0xFFF59E0B)
val DarkGraphite = Color(0xFF1E2532)
val SilverMuted = Color(0xFF64748B)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Material 3 Color Schemes - High Density Dark
val DarkMotoScheme = androidx.compose.material3.darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF001F24),
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = Color(0xFFE0F7FA),
    secondary = ElectricOrange,
    onSecondary = Color(0xFF2D1000),
    secondaryContainer = Color(0xFF5D2400),
    onSecondaryContainer = Color(0xFFFFDBC9),
    tertiary = RacingGreen,
    onTertiary = Color(0xFF00210E),
    tertiaryContainer = Color(0xFF005227),
    onTertiaryContainer = Color(0xFF98F7B5),
    background = CockpitBackground,
    onBackground = TextPrimary,
    surface = CockpitSurface,
    onSurface = TextPrimary,
    surfaceVariant = CockpitSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CockpitCardBorder,
    error = RacingRed,
    onError = Color.White
)

val LightMotoScheme = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFFEA580C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDD5),
    onSecondaryContainer = Color(0xFFC2410C),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color.White
)

