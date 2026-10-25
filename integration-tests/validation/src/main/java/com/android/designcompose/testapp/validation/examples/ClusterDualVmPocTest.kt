/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.designcompose.testapp.validation.examples

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.designcompose.DesignVariableModeValues
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

enum class IviVmState {
    RUNNING,
    FROZEN_ANR,
    CRASHED_REBOOTING,
}

enum class PlaneViewMode {
    COMPOSITE,
    HAR_ONLY,
    DRIVER_UI_ONLY,
}

@Composable
fun ClusterDualVmPocTest() {
    // Shared Figma Doc ID: CuF1b1eAIukB6YszX6B5OZ
    // Plane 1 (Safety HAR VM):  #HAR-stage via ClusterHARUIDoc
    // Plane 2 (Android IVI VM): #android-stage via ClusterDriverUIDoc

    var harVariant by remember { mutableStateOf(ClusterHARVariant.HarVariant.Retro) }
    var wrapperVariant by remember { mutableStateOf(ClusterHARVariant.WrapperVariant.Full) }

    var panel1 by remember { mutableStateOf(ClusterVariant.ComponentType.None) }
    var panel2 by remember { mutableStateOf(ClusterVariant.ComponentType.Call) }
    var panel3 by remember { mutableStateOf(ClusterVariant.ComponentType.None) }
    var mediaState by remember { mutableStateOf(ClusterVariant.MediaPlayerState.on) }
    var phoneState by remember { mutableStateOf(ClusterVariant.PhoneCallState.incall) }
    var viewMode by remember { mutableStateOf(ClusterVariant.ViewMode.normal) }

    var iviVmState by remember { mutableStateOf(IviVmState.RUNNING) }
    var planeViewMode by remember { mutableStateOf(PlaneViewMode.COMPOSITE) }
    var showPlaneWireframes by remember { mutableStateOf(true) }
    var autoTelemetry by remember { mutableStateOf(true) }
    var manualSpeed by remember { mutableStateOf(65f) }
    var darkThemeMode by remember { mutableStateOf(true) }
    var brandModeIndex by remember { mutableStateOf(0) }
    val brandModes = listOf("hyundai", "kia", "genesis")
    val sizeModes = listOf("Small", "Medium", "Large")

    // Continuous 60fps safety telemetry loop in the simulated HAR Safety VM (0 -> 130 MPH sweep)
    val infiniteTransition = rememberInfiniteTransition(label = "har_telemetry")
    val animatedSpeed by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 130f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(5500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "speed",
        )

    var blinkerTick by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(450)
            blinkerTick = !blinkerTick
        }
    }

    val currentSpeed = if (autoTelemetry) animatedSpeed else manualSpeed
    val speedInt = currentSpeed.roundToInt()

    // Realistic 4-speed gear & RPM simulation: RPM climbs 18 -> 88 inside each gear band
    val gearBand = (speedInt / 33).coerceIn(0, 3)
    val speedInGear = (currentSpeed - gearBand * 33f).coerceAtLeast(0f)
    val currentRpm =
        if (speedInt == 0) 8f else (18f + (speedInGear / 33f) * 70f).coerceIn(10f, 95f)
    val rpmInt = currentRpm.roundToInt()

    // Normalize speedGauge (0..100%) across 0..130 MPH
    val speedGaugePct = ((currentSpeed / 130f) * 100f).coerceIn(0f, 100f)
    val rpmGaugePct = currentRpm.coerceIn(0f, 100f)

    // Dynamic battery & range derived from speed sweep
    val batteryPct = (92 - (currentSpeed * 0.45f).roundToInt()).coerceIn(15, 95)
    val rangeMiles = (batteryPct * 2.8f).roundToInt()
    val overSpeedLimit = speedInt > 65

    // Key that forces child @DesignComponent replacements (#HAR-UI & #HAR-UI-Wrapper)
    // to re-merge LocalCustomizationContext on every telemetry tick
    val telemetryKey =
        "${speedInt}_${rpmInt}_${batteryPct}_${blinkerTick}_${iviVmState}_${darkThemeMode}_${brandModeIndex}"

    val currentBrand = brandModes[brandModeIndex % brandModes.size]
    val currentSize = sizeModes[brandModeIndex % sizeModes.size]
    val themeName = if (darkThemeMode) "Dark" else "Light"

    val variableModes =
        hashMapOf(
            "02 theme" to themeName,
            "Color" to themeName,
            "material-theme" to themeName,
            "custom-theme" to themeName,
            "local-material-theme" to themeName,
            "local-custom-theme" to themeName,
            "Typography" to currentBrand,
            "Component Size" to currentSize,
        )

    DesignVariableModeValues(variableModes) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0E1117)).padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Architecture & Live Telemetry Status Banner
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(Color(0xFF161B22), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text =
                            "AAOS SDV Dual-VM Simulator  |  DCF: CuF1b1eAIukB6YszX6B5OZ (Theme=$themeName, OEM=$currentBrand)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text =
                            "Live HAR Telemetry: ${speedInt} MPH (${speedGaugePct.roundToInt()}% arc)  |  Gear ${gearBand + 1} (${rpmInt}x100 RPM)  |  Battery ${batteryPct}% (${rangeMiles} MI)",
                        color = if (overSpeedLimit) Color(0xFFFF7B72) else Color(0xFF58A6FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier =
                            Modifier.background(Color(0xFF0D3B2E), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF2EA043), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "SAFETY VM (HAR): ${speedInt} MPH / ${rpmInt}00 RPM",
                            color = Color(0xFF3FB950),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    val (iviBg, iviBorder, iviText) =
                        when (iviVmState) {
                            IviVmState.RUNNING ->
                                Triple(
                                    Color(0xFF0C2D6B),
                                    Color(0xFF388BFD),
                                    "IVI VM (DriverUI): ONLINE",
                                )
                            IviVmState.FROZEN_ANR ->
                                Triple(
                                    Color(0xFF4B2E05),
                                    Color(0xFFD29922),
                                    "IVI VM (DriverUI): ANR",
                                )
                            IviVmState.CRASHED_REBOOTING ->
                                Triple(
                                    Color(0xFF490B0E),
                                    Color(0xFFF85149),
                                    "IVI VM (DriverUI): CRASHED",
                                )
                        }
                    Box(
                        modifier =
                            Modifier.background(iviBg, RoundedCornerShape(6.dp))
                                .border(1.dp, iviBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = iviText,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Center 1920x720 Hardware Display Compositor Viewport
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val scale = (maxWidth.value / 1920f).coerceAtMost(maxHeight.value / 720f)
                Box(
                    modifier =
                        Modifier.requiredSize(1920.dp * scale, 720.dp * scale)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .background(Color.Black, RoundedCornerShape(12.dp))
                            .border(
                                width = 2.dp,
                                color =
                                    if (showPlaneWireframes) Color(0xFF3FB950)
                                    else Color(0xFF30363D),
                                shape = RoundedCornerShape(12.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    // =========================================================================
                    // PLANE 1: Safety VM — High Availability Renderer (#HAR-stage)
                    // Pass telemetryKey to harMain, harUi, and harUiWrapper so child
                    // @DesignComponent replacements re-merge customizations on every frame!
                    // =========================================================================
                    if (planeViewMode != PlaneViewMode.DRIVER_UI_ONLY) {
                        ClusterHARUIDoc.harMain(
                            modifier =
                                Modifier.requiredSize(1920.dp, 720.dp).graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                },
                            key = telemetryKey,
                            harUi = { ctx ->
                                ClusterHARUIDoc.harUi(
                                    modifier = ctx.layoutModifier,
                                    key = telemetryKey,
                                    harVariant = harVariant,
                                )
                            },
                            harUiWrapper = { ctx ->
                                ClusterHARUIDoc.harUiWrapper(
                                    modifier = ctx.layoutModifier,
                                    key = telemetryKey,
                                    wrapperVariant = wrapperVariant,
                                )
                            },
                            speed = speedInt.toString(),
                            speedGauge = speedGaugePct,
                            speedNeedle = speedGaugePct,
                            rpm = rpmInt.toString(),
                            rpmGauge = rpmGaugePct,
                            rpmNeedle = rpmGaugePct,
                            speedLimit = "65",
                            speedLimitDisplayed = true,
                            temp = "${68 + (speedInt / 25)}°F",
                            date = "Gear ${gearBand + 1}  •  Oct 25",
                            remainingRange = "$rangeMiles MI",
                            batteryLevel = batteryPct.toString(),
                            seatbelt = overSpeedLimit,
                            lowTirePressure = overSpeedLimit,
                            airbag = false,
                            abs = iviVmState != IviVmState.RUNNING,
                            brake = speedInt < 15,
                            traction = overSpeedLimit,
                            fogLights = false,
                            parkLights = true,
                            hibeam = speedInt > 95,
                            lowbeam = true,
                            turnSignalLeft = blinkerTick,
                            turnSignalRight = !blinkerTick,
                            openDoors = false,
                            openTrunk = false,
                        )
                    }

                    // =========================================================================
                    // PLANE 2: Android IVI VM — DriverUI (#android-stage)
                    // =========================================================================
                    if (planeViewMode != PlaneViewMode.HAR_ONLY) {
                        if (iviVmState != IviVmState.CRASHED_REBOOTING) {
                            Box(
                                modifier =
                                    Modifier.requiredSize(1920.dp, 720.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                                            alpha =
                                                if (iviVmState == IviVmState.FROZEN_ANR) 0.45f
                                                else 1f
                                        }
                                        .drawWithContent {
                                            drawIntoCanvas { canvas ->
                                                val paint =
                                                    Paint().apply {
                                                        blendMode =
                                                            if (darkThemeMode) BlendMode.Lighten
                                                            else BlendMode.Darken
                                                    }
                                                canvas.saveLayer(Rect(Offset.Zero, size), paint)
                                                drawContent()
                                                canvas.restore()
                                            }
                                        }
                            ) {
                                ClusterDriverUIDoc.androidMain(
                                    modifier =
                                        Modifier.requiredSize(1920.dp, 720.dp)
                                            .then(
                                                if (showPlaneWireframes) {
                                                    Modifier.border(
                                                        3.dp,
                                                        Color(0xFF388BFD).copy(alpha = 0.7f),
                                                    )
                                                } else Modifier
                                            ),
                                    panel1Component = panel1,
                                    panel2Component = panel2,
                                    panel3Component = panel3,
                                    mediaPlayerState = mediaState,
                                    phoneState = phoneState,
                                    viewMode =
                                        if (overSpeedLimit) ClusterVariant.ViewMode.sport
                                        else viewMode,
                                )
                            }
                        } else {
                            Box(
                                modifier =
                                    Modifier.background(
                                            Color(0xCC3B0A0D),
                                            RoundedCornerShape(8.dp),
                                        )
                                        .border(1.dp, Color(0xFFF85149), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text =
                                        "ANDROID IVI VM OFFLINE (KERNEL PANIC / REBOOTING...)\nHAR Safety Plane (#HAR-stage) Continues Uninterrupted at 60 FPS",
                                    color = Color(0xFFFF7B72),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Interactive Dual-VM, Speed Sweep & DCF Live-Update Control Deck
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(Color(0xFF161B22), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Row 1: Continuous HAR Speed Telemetry & Stage Layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "1. HAR Speed & Layout:",
                        color = Color(0xFF3FB950),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    PocButton(
                        text =
                            if (autoTelemetry) "Auto Sweep: ON (${speedInt} MPH)"
                            else "Auto Sweep: PAUSED (${speedInt} MPH)",
                        selected = autoTelemetry,
                    ) {
                        if (autoTelemetry) {
                            manualSpeed = currentSpeed
                            autoTelemetry = false
                        } else {
                            autoTelemetry = true
                        }
                    }
                    PocButton(text = "-25 MPH", selected = false) {
                        autoTelemetry = false
                        manualSpeed = (currentSpeed - 25f).coerceIn(0f, 130f)
                    }
                    PocButton(text = "+25 MPH", selected = false) {
                        autoTelemetry = false
                        manualSpeed = (currentSpeed + 25f).coerceIn(0f, 130f)
                    }
                    PocButton(
                        text = "Retro Gauge",
                        selected = harVariant == ClusterHARVariant.HarVariant.Retro,
                    ) {
                        harVariant = ClusterHARVariant.HarVariant.Retro
                        wrapperVariant = ClusterHARVariant.WrapperVariant.Full
                        panel1 = ClusterVariant.ComponentType.None
                        panel2 = ClusterVariant.ComponentType.Call
                        panel3 = ClusterVariant.ComponentType.None
                    }
                    PocButton(
                        text = "Thin Arcs",
                        selected =
                            harVariant == ClusterHARVariant.HarVariant.Modern &&
                                wrapperVariant == ClusterHARVariant.WrapperVariant.Thin,
                    ) {
                        harVariant = ClusterHARVariant.HarVariant.Modern
                        wrapperVariant = ClusterHARVariant.WrapperVariant.Thin
                        panel1 = ClusterVariant.ComponentType.MediaThin
                        panel2 = ClusterVariant.ComponentType.CallThin
                        panel3 = ClusterVariant.ComponentType.NavigationThin
                    }
                    PocButton(
                        text = "Rear Cam",
                        selected = harVariant == ClusterHARVariant.HarVariant.ModernCamera,
                    ) {
                        harVariant = ClusterHARVariant.HarVariant.ModernCamera
                        wrapperVariant = ClusterHARVariant.WrapperVariant.Full
                    }
                }

                // Row 2: Dual-VM Fault Injection & Live DCF Variable Modes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "2. Dual-VM & DCF Vars:",
                        color = Color(0xFF58A6FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    PocButton(
                        text =
                            if (iviVmState == IviVmState.CRASHED_REBOOTING)
                                "Restore Android IVI VM"
                            else "Simulate IVI VM Crash",
                        selected = iviVmState == IviVmState.CRASHED_REBOOTING,
                        danger = true,
                    ) {
                        iviVmState =
                            if (iviVmState == IviVmState.CRASHED_REBOOTING) IviVmState.RUNNING
                            else IviVmState.CRASHED_REBOOTING
                    }
                    PocButton(text = "Swap IVI Panel", selected = false) {
                        panel2 =
                            when (panel2) {
                                ClusterVariant.ComponentType.Call ->
                                    ClusterVariant.ComponentType.Media
                                ClusterVariant.ComponentType.Media ->
                                    ClusterVariant.ComponentType.Navigation
                                else -> ClusterVariant.ComponentType.Call
                            }
                    }
                    PocButton(
                        text = "DCF Theme: $themeName",
                        selected = !darkThemeMode,
                    ) {
                        darkThemeMode = !darkThemeMode
                    }
                    PocButton(
                        text = "DCF OEM Var: $currentBrand/$currentSize",
                        selected = brandModeIndex > 0,
                    ) {
                        brandModeIndex = (brandModeIndex + 1) % 3
                    }
                    PocButton(
                        text = "Plane: ${planeViewMode.name}",
                        selected = planeViewMode != PlaneViewMode.COMPOSITE,
                    ) {
                        planeViewMode =
                            when (planeViewMode) {
                                PlaneViewMode.COMPOSITE -> PlaneViewMode.HAR_ONLY
                                PlaneViewMode.HAR_ONLY -> PlaneViewMode.DRIVER_UI_ONLY
                                PlaneViewMode.DRIVER_UI_ONLY -> PlaneViewMode.COMPOSITE
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun PocButton(
    text: String,
    selected: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val bgColor =
        when {
            danger && selected -> Color(0xFFDA3633)
            danger -> Color(0xFF6E1C1A)
            selected -> Color(0xFF1F6FEB)
            else -> Color(0xFF21262D)
        }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = bgColor, contentColor = Color.White),
    ) {
        Text(text = text, fontSize = 11.sp)
    }
}
