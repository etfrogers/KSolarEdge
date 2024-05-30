package com.etfrogers.ksolaredge.serialisers

import kotlinx.datetime.LocalDateTime
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
        val rawMeters = value.map {
            RawMeter(it.key, it.value)
        }
        encoder.encodeSerializableValue(ListSerializer(RawMeter.serializer()), rawMeters)
    }

    override fun deserialize(decoder: Decoder): Meters {
        val rawMeters = decoder.decodeSerializableValue(ListSerializer(RawMeter.serializer()))
        return rawMeters.associate { it.type to it.values }
    }
}

typealias Meters = Map<String, List<MeterTimepoint>>

@Serializable
private data class RawMeter(
    val type: String,
    val values: List<MeterTimepoint>
)


@Serializable
data class MeterTimepoint(
    @Serializable(with = SEDateSerializer::class)
    val date: LocalDateTime,
    val value: Float? = null
)

//@Serializable(SEDateTimeSerialiser)
//data class SEDateTime: Lo