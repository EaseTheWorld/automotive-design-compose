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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Interactive test bench harness for the AAOS SDV Dual-VM Cluster POC.
 *
 * All standard Jetpack Compose UI (top status bar, bottom control deck, 60fps telemetry generator,
 * and simulated DRM hardware plane blending) lives here, while the actual cluster rendering is
 * delegated to the pure DesignCompose Squoosh stages in [ClusterDualVmStage.kt]:
 * - [HarSafetyStage] (#HAR-stage)
 * - [DriverUiIviStage] (#android-stage)
 */
@Composable
fun ClusterDualVmSimulator() {
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

    // Continuous 60fps safety telemetry loop in the simulated HAR Safety VM (0 -> 98 MPH sweep)
    val infiniteTransition = rememberInfiniteTransition(label = "har_telemetry")
    val animatedSpeed by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 98f,
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

    val speedGaugePct = ((currentSpeed / 130f) * 100f).coerceIn(0f, 100f)
    val rpmGaugePct = currentRpm.coerceIn(0f, 100f)
    val batteryPct = (92 - (currentSpeed * 0.45f).roundToInt()).coerceIn(15, 95)
    val rangeMiles = (batteryPct * 2.8f).roundToInt()
    val overSpeedLimit = speedInt > 65

    val telemetryState =
        HarTelemetryState(
            speedMph = speedInt,
            speedArcPercent = speedGaugePct,
            rpmHundreds = rpmInt,
            rpmArcPercent = rpmGaugePct,
            gearLabel = "Gear ${gearBand + 1}  •  Oct 25",
            tempLabel = "${68 + (speedInt / 25)}°F",
            rangeLabel = "$rangeMiles MI",
            batteryPercent = batteryPct,
            overSpeedLimit = overSpeedLimit,
            iviFaultActive = iviVmState != IviVmState.RUNNING,
            blinkerLeft = blinkerTick,
            blinkerRight = !blinkerTick,
        )

    val currentBrand = brandModes[brandModeIndex % brandModes.size]
    val currentSize = sizeModes[brandModeIndex % sizeModes.size]
    val themeName = if (darkThemeMode) "Dark" else "Light"

    DcfVariableThemeScope(
        themeName = themeName,
        brandName = currentBrand,
        componentSize = currentSize,
    ) {
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
                    // PLANE 1 (Bottom DRM Plane): Android IVI VM — DriverUI (#android-stage)
                    // Stays mounted in VRAM even during a crash; when heartbeat is lost, Plane 2
                    // (#HAR-stage) enables its own "BackgroundLayer" (#heartbeat=off) to occlude it.
                    if (planeViewMode != PlaneViewMode.HAR_ONLY) {
                        DriverUiIviStage(
                            panel1 = panel1,
                            panel2 = panel2,
                            panel3 = panel3,
                            mediaState = mediaState,
                            phoneState = phoneState,
                            viewMode =
                                if (overSpeedLimit) ClusterVariant.ViewMode.sport else viewMode,
                            modifier =
                                Modifier.requiredSize(1920.dp, 720.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                    }
                                    .then(
                                        if (showPlaneWireframes) {
                                            Modifier.border(
                                                3.dp,
                                                Color(0xFF388BFD).copy(alpha = 0.7f),
                                            )
                                        } else Modifier
                                    ),
                        )
                    }

                    // PLANE 2 (Top See-Through DRM Overlay Plane): Safety VM — HAR (#HAR-stage)
                    // Because "BackgroundLayer" (#heartbeat=off) inside #HAR-stage is hidden while
                    // the Android IVI VM heartbeat is healthy, #HAR-stage is natively 100%
                    // see-through over #android-stage without any BlendMode tricks!
                    if (planeViewMode != PlaneViewMode.DRIVER_UI_ONLY) {
                        HarSafetyStage(
                            telemetry =
                                telemetryState.copy(
                                    // Show fallback BackgroundLayer if Android IVI VM is offline
                                    // OR when viewing HAR_ONLY plane in isolation
                                    iviFaultActive =
                                        iviVmState != IviVmState.RUNNING ||
                                            planeViewMode == PlaneViewMode.HAR_ONLY
                                ),
                            harVariant = harVariant,
                            wrapperVariant = wrapperVariant,
                            modifier =
                                Modifier.requiredSize(1920.dp, 720.dp).graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                },
                        )
                    }

                    // Optional visual indicator when simulating Android IVI VM crash
                    if (
                        planeViewMode != PlaneViewMode.HAR_ONLY &&
                            iviVmState == IviVmState.CRASHED_REBOOTING
                    ) {
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
                                    "ANDROID IVI VM OFFLINE (HEARTBEAT LOST -> HAR BackgroundLayer ACTIVE)\nHAR Safety Plane (#HAR-stage) Continues Uninterrupted at 60 FPS",
                                color = Color(0xFFFF7B72),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
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
                    SimulatorButton(
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
                    SimulatorButton(text = "-25 MPH", selected = false) {
                        autoTelemetry = false
                        manualSpeed = (currentSpeed - 25f).coerceIn(0f, 130f)
                    }
                    SimulatorButton(text = "+25 MPH", selected = false) {
                        autoTelemetry = false
                        manualSpeed = (currentSpeed + 25f).coerceIn(0f, 130f)
                    }
                    SimulatorButton(
                        text = "Retro Gauge",
                        selected = harVariant == ClusterHARVariant.HarVariant.Retro,
                    ) {
                        harVariant = ClusterHARVariant.HarVariant.Retro
                        wrapperVariant = ClusterHARVariant.WrapperVariant.Full
                        panel1 = ClusterVariant.ComponentType.None
                        panel2 = ClusterVariant.ComponentType.Call
                        panel3 = ClusterVariant.ComponentType.None
                    }
                    SimulatorButton(
                        text = "Modern Arcs",
                        selected = harVariant == ClusterHARVariant.HarVariant.Modern,
                    ) {
                        harVariant = ClusterHARVariant.HarVariant.Modern
                        wrapperVariant = ClusterHARVariant.WrapperVariant.Full
                        panel1 = ClusterVariant.ComponentType.MediaThin
                        panel2 = ClusterVariant.ComponentType.CallThin
                        panel3 = ClusterVariant.ComponentType.NavigationThin
                    }
                    SimulatorButton(
                        text = "Compact HUD",
                        selected = harVariant == ClusterHARVariant.HarVariant.Compact,
                    ) {
                        harVariant = ClusterHARVariant.HarVariant.Compact
                        wrapperVariant = ClusterHARVariant.WrapperVariant.Full
                        panel1 = ClusterVariant.ComponentType.None
                        panel2 = ClusterVariant.ComponentType.None
                        panel3 = ClusterVariant.ComponentType.Navigation
                    }
                    SimulatorButton(
                        text = "Rear Cam",
                        selected = harVariant == ClusterHARVariant.HarVariant.ModernCamera,
                    ) {
                        harVariant = ClusterHARVariant.HarVariant.ModernCamera
                        wrapperVariant = ClusterHARVariant.WrapperVariant.Full
                    }
                }

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
                    SimulatorButton(
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
                    SimulatorButton(text = "Swap IVI Panel", selected = false) {
                        panel2 =
                            when (panel2) {
                                ClusterVariant.ComponentType.Call ->
                                    ClusterVariant.ComponentType.Media
                                ClusterVariant.ComponentType.Media ->
                                    ClusterVariant.ComponentType.Navigation
                                else -> ClusterVariant.ComponentType.Call
                            }
                    }
                    SimulatorButton(
                        text = "DCF Theme: $themeName",
                        selected = !darkThemeMode,
                    ) {
                        darkThemeMode = !darkThemeMode
                    }
                    SimulatorButton(
                        text = "DCF OEM Var: $currentBrand/$currentSize",
                        selected = brandModeIndex > 0,
                    ) {
                        brandModeIndex = (brandModeIndex + 1) % 3
                    }
                    SimulatorButton(
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
private fun SimulatorButton(
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
