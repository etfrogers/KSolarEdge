package com.etfrogers.ksolaredge.serialisers

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.char
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal val solarEdgeFormat = LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); dayOfMonth()
    char(' ')
    hour(); char(':'); minute(); char(':'); second();
}

internal val solarEdgeURLFormat = LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); dayOfMonth()
    chars("%20");
    hour(); char(':'); minute(); char(':'); second();
}

internal object SEDateSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "DateTime",
        PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(
        solarEdgeFormat.format(value))
    override fun deserialize(decoder: Decoder): LocalDateTime = solarEdgeFormat.parse(decoder.decodeString())
}