package com.etfrogers.ksolaredge

import com.etfrogers.ksolaredge.serialisers.EnergyDetailsContainer
import com.etfrogers.ksolaredge.serialisers.MeterDetails
import com.etfrogers.ksolaredge.serialisers.PowerDetailsContainer
import com.etfrogers.ksolaredge.serialisers.SitePowerFlow
import com.etfrogers.ksolaredge.serialisers.SitePowerFlowContainer
import com.etfrogers.ksolaredge.serialisers.StorageData
import com.etfrogers.ksolaredge.serialisers.StorageDataContainer
import com.etfrogers.ksolaredge.serialisers.TelemetrySerializer
import com.etfrogers.ksolaredge.serialisers.solarEdgeURLFormat
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.http.Query
import java.io.File
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

private const val API_URL = "https://monitoringapi.solaredge.com"

@Serializable
data class SolarEdgeConfig(
    @SerialName("site-api-key") val siteApiKey: String,
    @SerialName("account-api-key") val accountApiKey: String = "",
    @SerialName("site-id") val siteID: String,
    @SerialName("account-id") val accountID: String,
    @SerialName("storage-profile-name") val storageProfileName: String,
    )


interface SolarEdgeApiService {
    @GET("currentPowerFlow")
    suspend fun getPowerFlow(): SitePowerFlowContainer

    @GET("energyDetails")
    suspend fun getEnergyDetails(@Query("startTime", encoded = true) startTime: String,
                                 @Query("endTime", encoded = true) endTime: String,
                                 @Query("timeUnit") timeUnit: String,
                                 ): EnergyDetailsContainer

    @GET("powerDetails")
    suspend fun getPowerDetails(@Query("startTime", encoded = true) startTime: String,
                                 @Query("endTime", encoded = true) endTime: String,
                                 @Query("timeUnit") timeUnit: String,
    ): PowerDetailsContainer

    @GET("storageData")
    suspend fun getStorageData(@Query("startTime", encoded = true) startTime: String,
                                @Query("endTime", encoded = true) endTime: String,
    ): StorageDataContainer

}


class SolarEdgeApi(siteID: String,
                   apiKey: String,
                   private val timezone: TimeZone = TimeZone.UTC) { //TimeZone.of("Europe/London")) {

    init {
        if (TelemetrySerializer.timeZone != null && TelemetrySerializer.timeZone != timezone) {
            throw Exception("Telemetry Serializer can only be set once")
        }
        TelemetrySerializer.timeZone = timezone
    }

    private val siteUrl = "$API_URL/site/${siteID}/"
    private val client = OkHttpClient.Builder()
        .addInterceptor(APIKeyInterceptor(apiKey))
        .build()

    private val retrofit = Retrofit.Builder()
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
        .baseUrl(siteUrl)
        .client(client)
        .build()

    private val retrofitService: SolarEdgeApiService by lazy {
        retrofit.create(SolarEdgeApiService::class.java)
    }

    suspend fun getPowerFlow() = retrofitService.getPowerFlow().siteCurrentPowerFlow

    suspend fun getEnergyDetails(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        timeUnit: String = "DAY"
    ): MeterDetails {
        val data = retrofitService.getEnergyDetails(
            startTime.format(solarEdgeURLFormat),
            endTime.format(solarEdgeURLFormat),
            timeUnit)
        return data.energyDetails
    }

    suspend fun getEnergyForDay(date: LocalDate): MeterDetails {
        val (start, end) = dayStartEndTimes(date)
        return getEnergyDetails(start, end)
    }


    suspend fun getPowerHistoryForDay(date: LocalDate): MeterDetails {
        val (start, end) = dayStartEndTimes(date)
        val data = getPowerDetails(
            start, end, timeUnit = "QUARTER_OF_AN_HOUR"
        )
        assert(data.timeUnit == "QUARTER_OF_AN_HOUR")
        assert(data.unit == "W")
        return data
    }


    suspend fun getPowerDetails(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        timeUnit: String = "DAY"
    ): MeterDetails {
        val data = retrofitService.getPowerDetails(
            startTime.format(solarEdgeURLFormat),
            endTime.format(solarEdgeURLFormat),
            timeUnit)
        return data.powerDetails
    }



    suspend fun getBatteryHistoryForDay(date: LocalDate): StorageData {
        val (start, end) = dayStartEndTimes(date)
        return getBatteryHistory(start, end)
    }

/*
    private fun integratePower(timestamps, powers): {
        dt = np.diff(timestamps)
        # assume first entry is the standard 5-minute interval.
        dt = np.concatenate(([datetime.timedelta(minutes = 5)], dt))
        dt_seconds = np.array([t.total_seconds() for t in dt])
        dt_hours = dt_seconds / (60 * 60)
        dt_hours = dt_hours
        return np.sum(dt_hours * powers)
    }
*/
    suspend fun getBatteryHistory(startTime: LocalDateTime, endTime: LocalDateTime): StorageData {
        val data = retrofitService.getStorageData(
            startTime.format(solarEdgeURLFormat),
            endTime.format(solarEdgeURLFormat),
            )
        return data.storageData
    }


    private fun dayStartEndTimes(day: LocalDate): Pair<LocalDateTime, LocalDateTime> {
        val start = LocalDateTime(day, LocalTime(0, 0, 0))
        // period is inclusive, so if we don't subtract one second, we ge the first period of the next day too.
        val end = (start.toInstant(timeZone = timezone) + 1.days - 1.seconds).toLocalDateTime(timezone)
        return Pair(start, end)
    }

}

internal fun timestampDiff(timestamps: List<LocalDateTime>, timezone: TimeZone): Duration {
    val instants = timestamps.map { it.toInstant(timezone) }
    val diff = instants[1] - instants[0]
    for (i in 2..<timestamps.size){
        val loopDiff = instants[i] - instants[i-1]
        if ((loopDiff - diff).inWholeSeconds > 1)
            throw TimestampParseError("Error finding timestamp differences. " +
                    "First diff: {$diff}, this diff (at index $i): $loopDiff")
    }
    return diff
}

class TimestampParseError(msg: String): Exception(msg)

class APIKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val currentUrl = chain.request().url
        val newUrl = currentUrl.newBuilder().addQueryParameter("api_key", apiKey).build()
        val currentRequest = chain.request().newBuilder()
        val newRequest = currentRequest.url(newUrl).build()
        return chain.proceed(newRequest)
    }
}


fun main(){
    val text = File("config.json").readText()
    val config = Json.decodeFromString<SolarEdgeConfig>(text)

    val client = SolarEdgeApi(config.siteID, config.accountApiKey)
    var powerFlow: SitePowerFlow
    var energy: MeterDetails
    var power: MeterDetails
    var battery: StorageData
    val day = LocalDate(2024, 5, 29)
    runBlocking {
        val def = async { client.getPowerFlow() }
        powerFlow = def.await()

        val def2 = async {
            client.getEnergyForDay(day)
        }
        energy = def2.await()

        val def3 = async {
            client.getPowerHistoryForDay(day)
        }
        power = def3.await()

        val def4 = async {
        client.getBatteryHistoryForDay(day)
        }
        battery = def4.await()
    }
    println(powerFlow)
    println(energy)
    println(power)
    println(battery)
}

/*

API_URL = 'https://monitoringapi.solaredge.com'
API_DATE_FORMAT = "%Y-%m-%d"
API_TIME_FORMAT = (API_DATE_FORMAT + " %H:%M:%S")
logger = logging.getLogger(__name__)
CACHEDIR = pathlib.Path(os.path.dirname(inspect.getsourcefile(lambda: 0))) / '..' / '..' / 'cache' / 'solaredge'


class BatteryNotFoundError(Exception):
    pass


class SolarEdgeClient:
    def __init__(self, api_key, site_id, timezone=None):
        self.api_key = api_key
        self.site_id = site_id
        # Note all times are returned in the timezone of the site
        self.timezone = timezone


    def get_site_dates(self) -> Tuple[datetime.datetime, datetime.datetime]:
        date_range_data = self.api_request('dataPeriod')
        start_date = datetime.datetime.strptime(date_range_data['dataPeriod']['startDate'], API_DATE_FORMAT)
        end_date = datetime.datetime.strptime(date_range_data['dataPeriod']['endDate'], API_DATE_FORMAT)
        return start_date, end_date

    def get_power_history_for_site(self):
        site_start_date, site_end_date = self.get_site_dates()
        start_date = site_start_date
        end_date = _end_of_month(start_date)
        while start_date < site_end_date:
            data = self.get_power_details(start_date, end_date)
            month_label = start_date.strftime('%Y-%m')
            with open(CACHEDIR / f'power_details_{month_label}.json', 'w') as file:
                json.dump(data, file, indent=4)
            start_date = _start_of_next_month(start_date)
            end_date = _end_of_month(start_date)


    def get_battery_history_for_site(self):
        site_start_date, site_end_date = self.get_site_dates()
        one_week = datetime.timedelta(days=7)
        start_date = site_start_date
        end_date = start_date + one_week - datetime.timedelta(hours=1)
        while start_date < site_end_date:
            # loop over weeks
            battery_data = self.get_battery_history(start_date, end_date)
            with open(CACHEDIR / f'battery_details_{start_date.strftime(API_DATE_FORMAT)}.json', 'w') as file:
                json.dump(battery_data, file, indent=4)
            start_date = start_date + one_week
            end_date = end_date + one_week


def _start_of_next_month(date):
    if date.month == 12:
        new_date = date.replace(year=date.year+1, month=1, day=1, hour=0, minute=0, second=0)
    else:
        new_date = date.replace(month=date.month + 1, day=1, hour=0, minute=0, second=0)
    return new_date


def _end_of_month(start_date):
    _, last_day_of_month = calendar.monthrange(start_date.year, start_date.month)
    end_of_month = start_date.replace(day=last_day_of_month)
    end_of_month = end_of_month.combine(end_of_month.date(), datetime.time(23, 59))
    return end_of_month


def _format_if_datetime(value):
    if isinstance(value, datetime.datetime):
        return value.strftime(API_TIME_FORMAT)
    else:
        return value




 */