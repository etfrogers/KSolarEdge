package com.etfrogers.ksolaredge.serialisers

import kotlinx.datetime.LocalDateTime
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
data class EnergyDetailsContainer(
    val energyDetails: EnergyDetails,
)

@Serializable
data class EnergyDetails(
    val timeUnit: String,
    val unit: String,
    @Serializable(with = MeterSerializer::class) val meters: Meters
) {
//    init {
//        val meters: Map<String, MeterValues> = metersList.associate { it.type to it.values }
//    }
}

internal object MeterSerializer : KSerializer<Meters> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "Meter",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: Meters) {
        val rawMeters = listOfNotNull(
            RawMeter.build("FeedIn", value.timestamps, value.feedIn),
            RawMeter.build("Production", value.timestamps, value.production),
            RawMeter.build("Purchased", value.timestamps, value.purchased),
            RawMeter.build("Consumption", value.timestamps, value.consumption),
        )
        encoder.encodeSerializableValue(ListSerializer(RawMeter.serializer()), rawMeters)
    }

    override fun deserialize(decoder: Decoder): Meters {
        val rawMeters = decoder.decodeSerializableValue(ListSerializer(RawMeter.serializer()))
        val allTimestamps = rawMeters.map { rawMeter ->  rawMeter.values.map{it.date} }
        val refTimestamps = allTimestamps[0]
        allTimestamps.forEach { ts ->
            if (ts != refTimestamps) {
                throw MismatchedTimestampsException("Deserialization of meters assumes all timestamps are equal.")
            }
        }
        val map = rawMeters.associate {meter -> meter.type to meter.values.map{it.value} }
        return Meters(refTimestamps,
            production = map["Production"],
            feedIn = map["FeedIn"],
            purchased = map["Production"],
            consumption = map["Production"],
            )
    }
}

class MismatchedTimestampsException(msg: String): SerializationException(msg)

data class Meters(
    val timestamps: List<LocalDateTime>,
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
