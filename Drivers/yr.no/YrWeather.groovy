
/*
====================================================================
 YR WEATHER FULL DRIVER
====================================================================

Purpose
-------
This Hubitat driver retrieves weather forecast data from the
Norwegian Meteorological Institute (MET Norway / Yr) and provides:

  - Current temperature
  - Humidity
  - Atmospheric pressure
  - Wind speed
  - Wind direction
  - Precipitation
  - Full forecast temperature table
  - Cloudiness
  - Sun elevation
  - Sunrise
  - Sunset
  - Daylight / twilight / dark status

The driver is designed to provide both weather information and
a simple indication of whether indoor lighting should be allowed
to turn on automatically.

The existing temperature forecast table is preserved.


====================================================================
 DATA SOURCES
====================================================================

Weather forecast
----------------
MET Norway Locationforecast API:

  https://api.met.no/weatherapi/locationforecast/2.0/compact

The driver uses the Hubitat Location latitude and longitude.

Astronomical data
-----------------
MET Norway Sunrise API:

  https://api.met.no/weatherapi/sunrise/3.0/sun

The driver retrieves:

  - Sunrise
  - Solar noon
  - Sunset
  - Solar midnight

The current solar elevation is calculated from these values.


====================================================================
 POLLING
====================================================================

The driver polls the weather API according to the configured
"Poll interval (minutes)" setting.

Default:

  10 minutes

After each successful weather request, the driver also retrieves
the astronomical information from the Sunrise API.


====================================================================
 CONFIGURATION
====================================================================

Poll interval
-------------
Controls how often weather data is updated.

Default:

  10 minutes


Twilight below sun elevation
----------------------------
Default:

  8 degrees

When the sun is below this elevation, the driver considers the
situation to be within the twilight range.

Example:

  Sun elevation 12°  -> daylight
  Sun elevation  6°  -> twilight
  Sun elevation  2°  -> dark


Dark below sun elevation
------------------------
Default:

  4 degrees

When the sun is below this elevation, the driver considers the
situation dark regardless of cloudiness.

Example:

  Sun elevation 2° -> dark


Cloudiness threshold for dark
-----------------------------
Default:

  50 %

When:

  - sun elevation is below the twilight limit
  - AND cloudiness is equal to or above this threshold

the driver considers the situation dark for indoor lighting
automation.

Example with default settings:

  Sun elevation 6°
  Cloudiness 80 %

  -> twilight = true
  -> dark = true


====================================================================
 LIGHT STATE LOGIC
====================================================================

The driver provides three status attributes:

  daylight
  twilight
  dark


DAYLIGHT
--------
daylight = true when:

  sun elevation >= twilight elevation

With the default setting:

  sun elevation >= 8°

Example:

  15° -> daylight = true


TWILIGHT
--------
twilight = true when:

  dark elevation <= sun elevation < twilight elevation

With the default settings:

  4° <= sun elevation < 8°

Example:

  6° -> twilight = true


DARK
----
dark = true when either of these conditions is met:

  1. Sun elevation < dark elevation

OR

  2. Sun elevation < twilight elevation
     AND cloudiness >= cloud threshold


With the default settings:

  Sun elevation < 4°
      -> dark = true

OR

  Sun elevation < 8°
  AND cloudiness >= 50%
      -> dark = true


IMPORTANT
---------
"dark" is intended primarily as an indoor lighting automation
indicator. It is not a precise measurement of outdoor illuminance.

MET provides cloudiness and astronomical information, but this
driver does not measure actual lux.

For precise indoor lighting control, a physical illuminance/lux
sensor is more accurate.


====================================================================
 FINNISH SUMMER
====================================================================

The logic does not depend on clock time.

This is important in Finland because during summer the sun may
remain above the horizon throughout the night.

Example:

  Sun elevation: +5°
  Cloudiness: 10%

  daylight = false
  twilight = true
  dark = false

The kitchen light automation would therefore NOT consider the
condition dark.

If the same situation is heavily overcast:

  Sun elevation: +5°
  Cloudiness: 80%

  daylight = false
  twilight = true
  dark = true

This allows indoor lighting automation to react to very cloudy
summer evenings/nights.

If the sun is above the twilight limit, cloudiness does not by
itself make the situation dark.

Example:

  Sun elevation: +12°
  Cloudiness: 100%

  daylight = true
  twilight = false
  dark = false


====================================================================
 WEATHER ATTRIBUTES
====================================================================

temperature
-----------
Current air temperature from MET.


humidity
--------
Current relative humidity in percent.


pressure
--------
Current sea-level atmospheric pressure.


windspeed
---------
Current wind speed.


winddirection
-------------
Current wind direction in degrees.


rain
----
Precipitation amount for the next one-hour period, when supplied
by the MET forecast.


cloudiness
----------
Total cloud area fraction from MET.

Unit:

  percent

Example:

  0   = clear
  50  = 50% cloud cover
  100 = completely cloudy


====================================================================
 ASTRONOMICAL ATTRIBUTES
====================================================================

sunElevation
------------
Current calculated solar elevation in degrees.

Examples:

  +50° = high sun
  +10° = low sun
  +5°  = twilight
  +2°  = dark
  -5°  = sun below horizon


sunrise
-------
Local sunrise time in HH:mm format.


sunset
------
Local sunset time in HH:mm format.


====================================================================
 STATUS ATTRIBUTES
====================================================================

daylight
--------
String value:

  "true"
  "false"

Indicates whether the sun is at or above the configured twilight
elevation.


twilight
--------
String value:

  "true"
  "false"

Indicates that the sun is between the configured dark and
twilight elevations.

Note:

twilight can be true at the same time as dark.

This happens when the sun is in the twilight range but heavy
cloudiness causes the driver to classify the condition as dark
for indoor lighting automation.


dark
-----
String value:

  "true"
  "false"

This is the primary attribute intended for automatic indoor
lighting.

It indicates that the driver considers the environment dark
enough that automatic indoor lighting may be appropriate.


====================================================================
 TEMPERATURE FORECAST TABLE
====================================================================

temperatureTable
----------------
Contains the complete MET forecast temperature table.

Keys use:

  yyyyMMddHHmm

Example:

  202609231200

means:

  23 September 2026 at 12:00


temperatureTableJson
--------------------
Same forecast table represented as JSON.

The temperature table is intentionally preserved because other
Hubitat automations can use it to access future forecast
temperatures.


====================================================================
 EXAMPLE LIGHTING AUTOMATION
====================================================================

The driver does NOT turn lights on or off itself.

Instead, Rule Machine can use:

  Motion active
      +
  dark == true
      ->
  Turn kitchen light ON


For turning the light off:

  Motion inactive
      ->
  Wait 2 minutes
      ->
  Check that motion is still inactive
      ->
  Turn kitchen light OFF


The actual dimming level should preferably be handled separately
with Hubitat's "Dimmers per Mode" application.

For example:

  Day      -> 100%
  Evening  -> 60%
  Night    -> 20%

This keeps the responsibilities separate:

  Yr Weather driver
      = Is it dark?

  Motion sensor
      = Is somebody in the kitchen?

  Rule Machine
      = Should the light turn on/off?

  Dimmers per Mode
      = At what brightness should it turn on?


====================================================================
 CLEAR STATE VARIABLES
====================================================================

The command:

  clearStateVariables

removes the driver's current weather, astronomical and forecast
state values and clears the driver's internal state.

It does not change the driver configuration.


====================================================================
 IMPORTANT LIMITATIONS
====================================================================

1. "dark" is an estimate, not a lux measurement.
2. Cloudiness is total cloud area fraction from the MET forecast,
   not measured indoor or outdoor illuminance.
3. During Finnish summer, the sun may never reach the configured
   dark elevation. Heavy cloudiness can nevertheless cause
   "dark" to become true while the sun is still above the horizon.
4. The thresholds are configurable and can be adjusted according
   to the actual lighting conditions in the house.
5. The driver uses the Hubitat Location coordinates and timezone.


====================================================================
 DEFAULT LIGHTING THRESHOLDS
====================================================================

Twilight:
  8°
Dark:
  4°
Cloudiness for dark:
  50%
These values are intended as practical starting values for
automatic indoor lighting and can be adjusted without changing
the driver code.
====================================================================
*/

import java.text.SimpleDateFormat
import groovy.json.JsonOutput

metadata {
    definition(name: "Yr Weather Full Driver", namespace: "kapakauppinen", author: "ChatGPT") {
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Pressure Measurement"
        capability "Sensor"
        capability "Refresh"

        // Existing forecast table
        attribute "temperatureTable", "HashMap"
        attribute "temperatureTableJson", "string"

        attribute "windspeed", "number"
        attribute "winddirection", "number"
        attribute "rain", "number"

        // New weather/light attributes
        attribute "cloudiness", "number"
        attribute "sunElevation", "number"
        attribute "sunrise", "string"
        attribute "sunset", "string"

        attribute "daylight", "string"
        attribute "twilight", "string"
        attribute "dark", "string"

        command "clearStateVariables"
    }

    preferences {
        input "pollInterval",
            "number",
            title: "Poll interval (minutes)",
            defaultValue: 10

        input "twilightElevation",
            "decimal",
            title: "Twilight below sun elevation (degrees)",
            defaultValue: 8.0

        input "darkElevation",
            "decimal",
            title: "Dark below sun elevation (degrees)",
            defaultValue: 4.0

        input "cloudThreshold",
            "number",
            title: "Cloudiness threshold for dark (%)",
            defaultValue: 50
    }
}


// ============================================================
// STATE
// ============================================================

def tempHashMap() {
    if (!state.tempMap) state.tempMap = [:]
    return state.tempMap
}


// ============================================================
// CLEAR
// ============================================================

def clearStateVariables() {

    device.deleteCurrentState('temperatureTable')
    device.deleteCurrentState('temperatureTableJson')

    device.deleteCurrentState('windspeed')
    device.deleteCurrentState('winddirection')
    device.deleteCurrentState('rain')

    device.deleteCurrentState('humidity')
    device.deleteCurrentState('pressure')
    device.deleteCurrentState('temperature')

    device.deleteCurrentState('cloudiness')
    device.deleteCurrentState('sunElevation')
    device.deleteCurrentState('sunrise')
    device.deleteCurrentState('sunset')

    device.deleteCurrentState('daylight')
    device.deleteCurrentState('twilight')
    device.deleteCurrentState('dark')

    state.clear()
}


// ============================================================
// LIFECYCLE
// ============================================================

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def initialize() {

    unschedule()

    if (settings.pollInterval) {

        schedule(
            "0 0/${settings.pollInterval} * * * ?",
            pollWeather
        )

    } else {

        runEvery5Minutes(pollWeather)
    }

    pollWeather()
}


// ============================================================
// WEATHER
// ============================================================

def pollWeather() {

    if (!settings.latitude || !settings.longitude) {
        log.warn "Latitude or Longitude not set"
        return
    }

    def lat = settings.latitude.toDouble()
    def lon = settings.longitude.toDouble()

    def url =
        "https://api.met.no/weatherapi/locationforecast/2.0/compact" +
        "?lat=${lat}&lon=${lon}"

    def params = [
        uri: url,
        headers: [
            "User-Agent": "HubitatYrDriver/1.0 (kari.kauppinen@iki.fi)",
            "Accept": "application/json"
        ],
        contentType: "application/json",
        requestContentType: "application/json"
    ]

    try {

        httpGet(params) { resp ->

            if (resp.status == 200) {

                parseWeatherData(resp.data)

                // Get astronomical information
                pollSunData()

            } else {

                log.warn "HTTP error ${resp.status}"
            }
        }

    } catch (e) {

        log.error "Error fetching weather: ${e}"
    }
}


// ============================================================
// WEATHER DATA
// ============================================================

def parseWeatherData(data) {

    def timeseries = data.properties.timeseries

    if (!timeseries || timeseries.size() == 0) {
        return
    }

    // ========================================================
    // YOUR ORIGINAL FORECAST TABLE
    // ========================================================

    HashMap<String, BigDecimal> map = [:]

    timeseries.each { point ->

        def date =
            Date.parse(
                "yyyy-MM-dd'T'HH:mm:ssX",
                point.time
            )

        def key =
            new SimpleDateFormat("yyyyMMddHHmm")
                .format(date)

        def temp =
            point.data.instant?.details?.air_temperature

        if (temp != null) {
            map.put(key, temp)
        }
    }

    map = map.sort { it.key }

    sendEvent(
        name: "temperatureTable",
        value: map
    )

    sendEvent(
        name: "temperatureTableJson",
        value: JsonOutput.toJson(map)
    )


    // ========================================================
    // CURRENT TEMPERATURE
    // ========================================================

    def latest =
        timeseries[0].data.instant.details

    def temperature =
        latest.air_temperature

    if (temperature != null) {

        sendEvent(
            name: "temperature",
            value: temperature
        )
    }


    // ========================================================
    // PRESSURE
    // ========================================================

    def pressure =
        latest.air_pressure_at_sea_level

    if (pressure != null) {

        sendEvent(
            name: "pressure",
            value: pressure
        )
    }


    // ========================================================
    // HUMIDITY
    // ========================================================

    def humidity =
        latest.relative_humidity

    if (humidity != null) {

        sendEvent(
            name: "humidity",
            value: humidity
        )
    }


    // ========================================================
    // WIND
    // ========================================================

    def windSpeed =
        latest.wind_speed

    def windDir =
        latest.wind_from_direction

    if (windSpeed != null) {

        sendEvent(
            name: "windspeed",
            value: windSpeed
        )
    }

    if (windDir != null) {

        sendEvent(
            name: "winddirection",
            value: windDir
        )
    }


    // ========================================================
    // RAIN
    // ========================================================

    def rainAmount =
        timeseries[0]
            .data
            .next_1_hours
            ?.details
            ?.precipitation_amount

    if (rainAmount != null) {

        sendEvent(
            name: "rain",
            value: rainAmount
        )
    }


    // ========================================================
    // CLOUDINESS
    // ========================================================

    def cloudiness =
        latest.cloud_area_fraction

    if (cloudiness != null) {

        sendEvent(
            name: "cloudiness",
            value: cloudiness
        )
    }
}


// ============================================================
// SUNRISE API
// ============================================================

def pollSunData() {

    def lat = location.latitude.toDouble()
    def lon = location.longitude.toDouble()

    def tz =
        location.timeZone ?: TimeZone.getDefault()

    def calendar =
        Calendar.getInstance(tz)

    // Current UTC offset including daylight saving time
    def offsetMillis =
        calendar.get(Calendar.ZONE_OFFSET) +
        calendar.get(Calendar.DST_OFFSET)

    def offsetMinutes =
        (int)(offsetMillis / 60000)

    def sign =
        offsetMinutes >= 0 ? "+" : "-"

    def absoluteMinutes =
        Math.abs(offsetMinutes)

    def offsetHours =
        (int)(absoluteMinutes / 60)

    def offsetMins =
        absoluteMinutes % 60

    def offset =
        String.format(
            "%s%02d:%02d",
            sign,
            offsetHours,
            offsetMins
        )

    def dateFormatter =
        new SimpleDateFormat("yyyy-MM-dd")

    dateFormatter.timeZone = tz

    def date =
        dateFormatter.format(new Date())

    def url =
        "https://api.met.no/weatherapi/sunrise/3.0/sun" +
        "?lat=${lat}" +
        "&lon=${lon}" +
        "&date=${date}" +
        "&offset=${offset}"

    def params = [
        uri: url,
        headers: [
            "User-Agent": "HubitatYrDriver/1.0 (kari.kauppinen@iki.fi)",
            "Accept": "application/json"
        ],
        contentType: "application/json",
        requestContentType: "application/json"
    ]

    try {

        httpGet(params) { resp ->

            if (resp.status == 200) {

                parseSunData(
                    resp.data,
                    tz
                )

            } else {

                log.warn "Sunrise API HTTP error ${resp.status}"
            }
        }

    } catch (e) {

        log.error "Error fetching sunrise data: ${e}"
    }
}


// ============================================================
// SUN DATA
// ============================================================

def parseSunData(data, TimeZone tz) {

    def props =
        data?.properties

    if (!props) {

        log.warn "Sunrise API returned no properties"
        return
    }


    def sunrise =
        parseSunEvent(props.sunrise)

    def solarNoon =
        parseSunEvent(props.solarnoon)

    def sunset =
        parseSunEvent(props.sunset)

    def solarMidnight =
        parseSunEvent(props.solarmidnight)


    // ========================================================
    // SUNRISE
    // ========================================================

    if (props.sunrise?.time) {

        sendEvent(
            name: "sunrise",
            value: formatLocalTime(
                props.sunrise.time,
                tz
            )
        )
    }


    // ========================================================
    // SUNSET
    // ========================================================

    if (props.sunset?.time) {

        sendEvent(
            name: "sunset",
            value: formatLocalTime(
                props.sunset.time,
                tz
            )
        )
    }


    // ========================================================
    // CURRENT SUN ELEVATION
    // ========================================================

    def elevation =
        calculateSunElevation(
            new Date(),
            sunrise,
            solarNoon,
            sunset,
            solarMidnight,
            props
        )


    if (elevation != null) {

        elevation =
            Math.round(
                elevation * 10
            ) / 10.0

        sendEvent(
            name: "sunElevation",
            value: elevation
        )

        updateLightState(
            elevation
        )
    }
}


// ============================================================
// PARSE SUN EVENT
// ============================================================

def parseSunEvent(event) {

    if (!event?.time) {
        return null
    }

    try {

        /*
         * Sunrise API returns ISO timestamps such as:
         *
         * 2026-09-23T06:42+03:00
         */

        return Date.parse(
            "yyyy-MM-dd'T'HH:mmXXX",
            event.time
        )

    } catch (e) {

        try {

            return Date.parse(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                event.time
            )

        } catch (ignored) {

            log.warn(
                "Unable to parse sun event: ${event.time}"
            )

            return null
        }
    }
}


// ============================================================
// LOCAL TIME
// ============================================================

def formatLocalTime(
    String value,
    TimeZone tz
) {

    try {

        Date date

        try {

            date =
                Date.parse(
                    "yyyy-MM-dd'T'HH:mmXXX",
                    value
                )

        } catch (ignored) {

            date =
                Date.parse(
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    value
                )
        }

        def formatter =
            new SimpleDateFormat("HH:mm")

        formatter.timeZone = tz

        return formatter.format(date)

    } catch (e) {

        return value
    }
}


// ============================================================
// SUN ELEVATION
// ============================================================

def calculateSunElevation(
    Date now,
    Date sunrise,
    Date solarNoon,
    Date sunset,
    Date solarMidnight,
    def props
) {

    /*
     * MET defines sunrise/sunset at approximately
     * -0.8333 degrees for the centre of the sun.
     */

    final double HORIZON =
        -0.8333


    def noonElevation =
        props.solarnoon?.disc_centre_elevation

    def midnightElevation =
        props.solarmidnight?.disc_centre_elevation


    // ========================================================
    // DAYLIGHT: SUNRISE -> SOLAR NOON
    // ========================================================

    if (
        sunrise &&
        solarNoon &&
        now.time >= sunrise.time &&
        now.time <= solarNoon.time
    ) {

        double fraction =
            (now.time - sunrise.time) /
            (double)(
                solarNoon.time -
                sunrise.time
            )

        if (noonElevation != null) {

            return HORIZON +
                (
                    noonElevation.toDouble() -
                    HORIZON
                ) *
                Math.sin(
                    fraction *
                    Math.PI /
                    2
                )
        }
    }


    // ========================================================
    // DAYLIGHT: SOLAR NOON -> SUNSET
    // ========================================================

    if (
        solarNoon &&
        sunset &&
        now.time > solarNoon.time &&
        now.time <= sunset.time
    ) {

        double fraction =
            (now.time - solarNoon.time) /
            (double)(
                sunset.time -
                solarNoon.time
            )

        if (noonElevation != null) {

            return HORIZON +
                (
                    noonElevation.toDouble() -
                    HORIZON
                ) *
                Math.cos(
                    fraction *
                    Math.PI /
                    2
                )
        }
    }


    // ========================================================
    // NIGHT: SOLAR MIDNIGHT -> SUNRISE
    // ========================================================

    if (
        solarMidnight &&
        sunrise &&
        now.time >= solarMidnight.time &&
        now.time < sunrise.time
    ) {

        if (midnightElevation != null) {

            double fraction =
                (now.time - solarMidnight.time) /
                (double)(
                    sunrise.time -
                    solarMidnight.time
                )

            return midnightElevation.toDouble() +
                (
                    HORIZON -
                    midnightElevation.toDouble()
                ) *
                Math.sin(
                    fraction *
                    Math.PI /
                    2
                )
        }
    }


    // ========================================================
    // NIGHT: SUNSET -> SOLAR MIDNIGHT
    // ========================================================

    if (
        sunset &&
        solarMidnight &&
        now.time > sunset.time &&
        now.time <= solarMidnight.time
    ) {

        if (midnightElevation != null) {

            double fraction =
                (now.time - sunset.time) /
                (double)(
                    solarMidnight.time -
                    sunset.time
                )

            return HORIZON +
                (
                    midnightElevation.toDouble() -
                    HORIZON
                ) *
                Math.sin(
                    fraction *
                    Math.PI /
                    2
                )
        }
    }


    // ========================================================
    // POLAR NIGHT / MIDNIGHT SUN
    // ========================================================

    if (!sunrise && !sunset) {

        if (props.solarnoon?.visible == true &&
            noonElevation != null) {

            return noonElevation.toDouble()
        }

        if (midnightElevation != null) {

            return midnightElevation.toDouble()
        }

        return -90.0
    }


    return null
}



// ============================================================
// LIGHT STATE
// ============================================================

def updateLightState(
    BigDecimal elevation
) {

    double twilightLimit =
        settings.twilightElevation != null
            ? settings.twilightElevation.toDouble()
            : 8.0

    double darkLimit =
        settings.darkElevation != null
            ? settings.darkElevation.toDouble()
            : 4.0

    double cloudLimit =
        settings.cloudThreshold != null
            ? settings.cloudThreshold.toDouble()
            : 50.0


    Double cloudiness = null

    def cloudValue =
        device.currentValue("cloudiness")

    if (cloudValue != null) {

        try {
            cloudiness = cloudValue.toDouble()
        } catch (ignored) {
        }
    }


    boolean daylight = false
    boolean twilight = false
    boolean dark = false


    // ========================================================
    // DAYLIGHT
    // ========================================================

    if (elevation >= twilightLimit) {

        daylight = true
    }


    // ========================================================
    // TWILIGHT
    // ========================================================

    else if (elevation >= darkLimit) {

        twilight = true

        /*
         * Sun is still in twilight range.
         *
         * If the sun is below the twilight limit and
         * cloudiness is high enough, consider it dark
         * for indoor lighting automation.
         */

        if (
            cloudiness != null &&
            cloudiness >= cloudLimit
        ) {

            dark = true
        }
    }


    // ========================================================
    // DARK
    // ========================================================

    else {

        dark = true
    }


    sendEvent(
        name: "daylight",
        value: daylight ? "true" : "false"
    )

    sendEvent(
        name: "twilight",
        value: twilight ? "true" : "false"
    )

    sendEvent(
        name: "dark",
        value: dark ? "true" : "false"
    )
}



// ============================================================
// REFRESH
// ============================================================

def refresh() {
    pollWeather()
}
