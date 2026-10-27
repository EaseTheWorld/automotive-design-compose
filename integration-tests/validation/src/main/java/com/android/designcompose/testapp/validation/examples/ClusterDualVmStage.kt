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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.designcompose.DesignVariableModeValues

/**
 * Pure DesignCompose (Squoosh) stage bindings for the AAOS SDV Dual-VM Cluster.
 *
 * Notice there are ZERO regular Jetpack Compose UI widgets (no Box, Row, Column, Text, Image, or
 * Canvas) in this file. Both stages are rendered directly from the single Figma .dcf bundle
 * (CuF1b1eAIukB6YszX6B5OZ) via the Rust layout engine (dc_layout) and Squoosh single-pass renderer:
 * - Plane 1 (Safety VM): #HAR-stage -> ClusterHARUIDoc.harMain(...)
 * - Plane 2 (Android IVI VM): #android-stage -> ClusterDriverUIDoc.androidMain(...)
 */
data class HarTelemetryState(
    val speedMph: Int = 65,
    val speedArcPercent: Float = 50f,
    val rpmHundreds: Int = 32,
    val rpmArcPercent: Float = 32f,
    val gearLabel: String = "Gear 3  •  Oct 25",
    val tempLabel: String = "68°F",
    val rangeLabel: String = "240 MI",
    val batteryPercent: Int = 85,
    val overSpeedLimit: Boolean = false,
    val iviFaultActive: Boolean = false,
    val blinkerLeft: Boolean = false,
    val blinkerRight: Boolean = false,
)

@Composable
fun HarSafetyStage(
    telemetry: HarTelemetryState,
    harVariant: ClusterHARVariant.HarVariant,
    wrapperVariant: ClusterHARVariant.WrapperVariant,
    modifier: Modifier = Modifier,
) {
    ClusterHARUIDoc.harStage(
        modifier = modifier,
        harVariant = harVariant,
        wrapperVariant = wrapperVariant,
        // Native Dual-VM See-Through Control:
        // - false (Android Heartbeat ON): Hides "BackgroundLayer" (#heartbeat=off) so #HAR-stage
        //   is 100% transparent, letting #android-stage show through from the bottom plane.
        // - true (Android Heartbeat OFF / Crash): Enables "BackgroundLayer" so #HAR-stage
        //   renders its own opaque safety background over the dead/frozen Android plane.
        showFallbackBackground = telemetry.iviFaultActive,
        speed = telemetry.speedMph.toString(),
        speedGauge = telemetry.speedArcPercent,
        speedNeedle = telemetry.speedArcPercent,
        rpm = telemetry.rpmHundreds.toString(),
        rpmGauge = telemetry.rpmArcPercent,
        rpmNeedle = telemetry.rpmArcPercent,
        speedLimit = "65",
        speedLimitDisplayed = true,
        temp = telemetry.tempLabel,
        date = telemetry.gearLabel,
        remainingRange = telemetry.rangeLabel,
        batteryLevel = telemetry.batteryPercent.toString(),
        seatbelt = telemetry.overSpeedLimit,
        lowTirePressure = telemetry.overSpeedLimit,
        airbag = false,
        abs = telemetry.iviFaultActive,
        brake = telemetry.speedMph < 15,
        traction = telemetry.overSpeedLimit,
        fogLights = false,
        parkLights = true,
        hibeam = telemetry.speedMph > 95,
        lowbeam = true,
        turnSignalLeft = telemetry.blinkerLeft,
        turnSignalRight = telemetry.blinkerRight,
        openDoors = false,
        openTrunk = false,
    )
}

@Composable
fun DriverUiIviStage(
    panel1: ClusterVariant.ComponentType,
    panel2: ClusterVariant.ComponentType,
    panel3: ClusterVariant.ComponentType,
    mediaState: ClusterVariant.MediaPlayerState,
    phoneState: ClusterVariant.PhoneCallState,
    viewMode: ClusterVariant.ViewMode,
    modifier: Modifier = Modifier,
) {
    ClusterDriverUIDoc.androidMain(
        modifier = modifier,
        panel1Component = panel1,
        panel2Component = panel2,
        panel3Component = panel3,
        mediaPlayerState = mediaState,
        phoneState = phoneState,
        viewMode = viewMode,
    )
}

@Composable
fun DcfVariableThemeScope(
    themeName: String,
    brandName: String,
    componentSize: String,
    content: @Composable () -> Unit,
) {
    val variableModes =
        hashMapOf(
            "02 theme" to themeName,
            "Color" to themeName,
            "material-theme" to themeName,
            "custom-theme" to themeName,
            "local-material-theme" to themeName,
            "local-custom-theme" to themeName,
            "Typography" to brandName,
            "Component Size" to componentSize,
        )
    DesignVariableModeValues(variableModes, content)
}
