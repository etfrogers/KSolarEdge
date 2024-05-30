package com.etfrogers.ksolaredge.serialisers

import android.health.connect.datatypes.PowerRecord.PowerRecordSample
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SitePowerFlowContainer(
    val siteCurrentPowerFlow: SitePowerFlow
)

@Serializable
data class SitePowerFlow (
    val updateRefreshRate: Int,
    val unit: String,
    val connections: List<Connection>,
    @SerialName("GRID") val grid: ConnectionData,
    @SerialName("LOAD") val load: ConnectionData,
    @SerialName("PV") val pv: ConnectionData,
    @SerialName("STORAGE") val storage: BatteryData,
)

@Serializable
data class Connection(
    val from: String,
    val to: String
)

@Serializable
data class ConnectionData(
    val status: String,
    val currentPower: Float,
)

@Serializable
data class BatteryData(
    val status: String,
    val currentPower: Float,
    val chargeLevel: Int,
    val critical: Boolean,
)