package com.etfrogers.ksolaredge.serialisers

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EnergyDetailsContainer(
    val energyDetails: EnergyDetails,
)

@Serializable
data class EnergyDetails(
    val timeUnit: String,
    val unit: String,
    @SerialName("meters") private val metersList: List<RawMeter>
) {
    init {
        val meters: Map<String, MeterValues> = metersList.associate { it.type to it.values }
    }
}

typealias MeterValues = List<MeterTimepoint>

@Serializable
data class RawMeter(
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