package com.etfrogers.ksolaredge.serialisers

import com.etfrogers.ksolaredge.timestampDiff
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
internal data class StorageDataContainer(
    val storageData: LocalStorageData,
)

data class StorageData(
    val batteryCount: Int,
    val batteries: List<Battery>
)

@Serializable
internal data class LocalStorageData(
    val batteryCount: Int,
    val batteries: List<LocalBattery>
){
    fun toStorageData(timezone: TimeZone): StorageData {
        return StorageData(
            this.batteryCount,
            this.batteries.map { it.toBattery(timezone) }
        )
    }
}

data class Battery(
    val serialNumber: String,
    val nameplate: Float,
    val modelNumber: String,
    val telemetryCount: Int,
    val telemetry: Telemetry,
)


@Serializable
internal data class LocalBattery(
    val serialNumber: String,
    val nameplate: Float,
    val modelNumber: String,
    val telemetryCount: Int,
    @Serializable(with = TelemetrySerializer::class) @SerialName("telemetries") val telemetry: LocalTelemetry,
) {
    fun toBattery(timezone: TimeZone): Battery {
        return Battery(
            serialNumber,
            nameplate,
            modelNumber,
            telemetryCount,
            telemetry.toTelemetry(timezone)
        )
    }
}

data class Telemetry(
    val timestamps: List<Instant> = listOf(),
    val chargePowerFromGrid: List<Float> = listOf(),
    val chargeEnergyFromGrid: List<Float> = listOf(),
    val chargePowerFromSolar: List<Float> = listOf(),
    val dischargePower: List<Float> = listOf(),
    val chargePercentage: List<Float> = listOf(),
    val storedEnergy: List<Float> = listOf(),
)

internal data class LocalTelemetry(
    val timestamps: List<LocalDateTime> = listOf(),
    val chargePowerFromGrid: List<Float> = listOf(),
    val chargeEnergyFromGrid: List<Float> = listOf(),
    val chargePowerFromSolar: List<Float> = listOf(),
    val dischargePower: List<Float> = listOf(),
    val chargePercentage: List<Float> = listOf(),
    val storedEnergy: List<Float> = listOf(),
) {
    fun toTelemetry(timezone: TimeZone): Telemetry {
        return Telemetry(
            timestamps.map { it.toInstant(timezone) },
            chargePowerFromGrid,
            chargeEnergyFromGrid,
            chargePowerFromSolar,
            dischargePower,
            chargePercentage,
            storedEnergy,
        )
    }
}

internal class TelemetrySerializer: KSerializer<LocalTelemetry> {
    companion object {
        var timeZone: TimeZone? = null
    }

    override fun deserialize(decoder: Decoder): LocalTelemetry {
        val rawTelemetries = decoder.decodeSerializableValue(
            ListSerializer(RawTelemetry.serializer()))
        val timestamps = rawTelemetries.map { it.timestamp }
        val dischargePower = rawTelemetries.map {
            if (it.power < 0) -it.power else 0f
        }
        val chargePercentage = rawTelemetries.map { it.batteryPercentageState }
        val storedEnergy = rawTelemetries.map {
            it.fullPackEnergyAvailable * it.batteryPercentageState / 100
        }
        val chargeEnergyFromGrid = rawTelemetries.map { it.acGridCharging }
        val chargePowerFromSolar = rawTelemetries.mapIndexed { i, it ->
            if ((it.power > 0)) it.power - chargeEnergyFromGrid[i] else 0f
        }
        val period = timestampDiff(timestamps, timeZone!!)
        val acGridChargingAveragePowerInPeriod = chargeEnergyFromGrid.map {
            it / period.inWholeSeconds }
        return LocalTelemetry(
            timestamps = timestamps,
            chargePowerFromGrid = acGridChargingAveragePowerInPeriod,
            chargeEnergyFromGrid = chargeEnergyFromGrid,
            chargePowerFromSolar = chargePowerFromSolar,
            dischargePower = dischargePower,
            chargePercentage = chargePercentage,
            storedEnergy = storedEnergy,
        )
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "Meter", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTelemetry) {
        throw NotImplementedError("Not yet implemented")
    }
}

@Serializable
data class RawTelemetry(
    @Serializable(with = SEDateSerializer::class)
    @SerialName("timeStamp") val timestamp: LocalDateTime,
    @SerialName("power") private val rawPower: Float?,
    val batteryState: Int,
    val lifeTimeEnergyCharged: Float,
    val lifeTimeEnergyDischarged: Float,
    val fullPackEnergyAvailable: Float,
    val internalTemp: Float,
    @SerialName("ACGridCharging") val acGridCharging: Float,
    val batteryPercentageState: Float,
){
    val power
        get() = rawPower ?: Float.NaN
}