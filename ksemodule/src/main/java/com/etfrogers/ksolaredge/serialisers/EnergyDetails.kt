package com.etfrogers.ksolaredge.serialisers

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
internal data class EnergyDetailsContainer(
    val energyDetails: LocalMeterDetails,
)

@Serializable
internal data class PowerDetailsContainer(
    val powerDetails: LocalMeterDetails,
)

@Serializable
internal data class LocalMeterDetails(
    val timeUnit: String,
    val unit: String,
    @Serializable(with = MeterSerializer::class) val meters: LocalMeters
) {
    fun toMeterDetails(timezone: TimeZone): MeterDetails {
        return MeterDetails(
            this.timeUnit,
            this.unit,
            this.meters.toMeters(timezone)
        )
    }
}

data class MeterDetails(
    val timeUnit: String,
    val unit: String,
    val meters: Meters
)

internal object MeterSerializer : KSerializer<LocalMeters> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "Meter",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: LocalMeters) {
        val rawMeters = listOfNotNull(
            RawMeter.build("FeedIn", value.timestamps, value.feedIn),
            RawMeter.build("Production", value.timestamps, value.production),
            RawMeter.build("Purchased", value.timestamps, value.purchased),
            RawMeter.build("Consumption", value.timestamps, value.consumption),
        )
        encoder.encodeSerializableValue(ListSerializer(RawMeter.serializer()), rawMeters)
    }

    override fun deserialize(decoder: Decoder): LocalMeters {
        val rawMeters = decoder.decodeSerializableValue(ListSerializer(RawMeter.serializer()))
        val allTimestamps = rawMeters.map { rawMeter ->  rawMeter.values.map{it.date} }
        val refTimestamps = allTimestamps[0]
        allTimestamps.forEach { ts ->
            if (ts != refTimestamps) {
                throw MismatchedTimestampsException("Deserialization of meters assumes all timestamps are equal.")
            }
        }
        val map = rawMeters.associate {meter -> meter.type to meter.values.map{it.value} }
        return LocalMeters(refTimestamps,
            production = map["Production"],
            feedIn = map["FeedIn"],
            purchased = map["Purchased"],
            consumption = map["Consumption"],
            )
    }
}

class MismatchedTimestampsException(msg: String): SerializationException(msg)

internal class LocalMeters(
    val timestamps: List<LocalDateTime>,
    val production: List<Float?>? = listOf(),
    val feedIn: List<Float?>? = listOf(),
    val purchased: List<Float?>? = listOf(),
    val consumption: List<Float?>? = listOf(),
) {
    fun toMeters(timezone: TimeZone): Meters{
        return Meters(
            this.timestamps.map { it.toInstant(timezone) },
            this.production,
            this.feedIn,
            this.purchased,
            this.consumption
        )
    }
}

class Meters(
    val timestamps: List<Instant>,
    val production: List<Float?>? = listOf(),
    val feedIn: List<Float?>? = listOf(),
    val purchased: List<Float?>? = listOf(),
    val consumption: List<Float?>? = listOf(),
)

@Serializable
private data class RawMeter(
    val type: String,
    val values: List<MeterTimepoint>,
){
    companion object Factory{
        fun build(type: String, timestamps: List<LocalDateTime>, values: List<Float?>?) : RawMeter? {
            if (values == null) return null
            val meterPoints = timestamps.zip(values).map { (ts, value) -> MeterTimepoint(ts, value) }
            return RawMeter(type, meterPoints)
        }
    }
}


@Serializable
data class MeterTimepoint(
    @Serializable(with = SEDateSerializer::class)
    val date: LocalDateTime,
    val value: Float? = null
)
