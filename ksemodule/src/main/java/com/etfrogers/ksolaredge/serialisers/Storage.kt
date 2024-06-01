package com.etfrogers.ksolaredge.serialisers

import com.etfrogers.ksolaredge.timestampDiff
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
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
data class StorageDataContainer(
    val storageData: StorageData,
)

@Serializable
data class StorageData(
    val batteryCount: Int,
    val batteries: List<Battery>
)

@Serializable
data class Battery(
    val serialNumber: String,
    val nameplate: Float,
    val modelNumber: String,
    val telemetryCount: Int,
    @Serializable(with = TelemetrySerializer::class) @SerialName("telemetries") val telemetry: Telemetry,
)

data class Telemetry(
    val timestamps: List<LocalDateTime>,
    val chargePowerFromGrid: List<Float>,
    val chargeEnergyFromGrid: List<Float>,
    val chargePowerFromSolar: List<Float>,
    val dischargePower: List<Float>,
    val chargePercentage: List<Float>,
    val storedEnergy: List<Float>,
//    val chargeFromGridEnergy: Float,
//    val dischargeEnergy: Float,
//    val chargeFromSolarEnergy: Float,
)

class TelemetrySerializer: KSerializer<Telemetry> {
    companion object {
        var timeZone: TimeZone? = null
    }

    override fun deserialize(decoder: Decoder): Telemetry {
        val rawTelemetries = decoder.decodeSerializableValue(
            ListSerializer(RawTelemetry.serializer()))
        val timestamps = rawTelemetries.map { it.timestamp }
//        val chargePowerFromGrid = rawTelemetries.map {
//            if ((it.power > 0) && it.acGridCharging > 0) it.power else 0
//        }
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
        return Telemetry(
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

    override fun serialize(encoder: Encoder, value: Telemetry) {
        throw NotImplementedError("Not yet implemented")
    }
}

/*
    output = {
        'timestamps': timestamps,
        'charge_power_from_grid': np.array(charge_power_from_grid),
        'discharge_power': np.array(discharge_power),
        'charge_power_from_solar': np.array(charge_power_from_solar),
        'charge_percentage': np.asarray(charge_percentage),
        'charge_from_grid_energy': sum([entry['ACGridCharging'] for entry in data['telemetries']]),
        'discharge_energy': self.integrate_power(timestamps, discharge_power),
        'charge_from_solar_energy': self.integrate_power(timestamps, charge_power_from_solar),
        'stored_energy': energy_stored
    }
    return output
    */

@Serializable
data class RawTelemetry(
    @Serializable(with = SEDateSerializer::class)
    @SerialName("timeStamp") val timestamp: LocalDateTime,
    val power: Float,
    val batteryState: Int,
    val lifeTimeEnergyCharged: Float,
    val lifeTimeEnergyDischarged: Float,
    val fullPackEnergyAvailable: Float,
    val internalTemp: Float,
    @SerialName("ACGridCharging") val acGridCharging: Float,
    val batteryPercentageState: Float,
)