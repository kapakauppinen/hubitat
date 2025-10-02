/*
 * Electricity price API driver for Hubitat
 * Author: Kari Kauppinen
 */

import java.text.SimpleDateFormat
import groovy.time.TimeCategory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId

metadata {
    definition(
        name: "Electricity price",
        namespace: "kapakauppinen",
        author: "Kari Kauppinen",
        importUrl: "https://raw.githubusercontent.com/kapakauppinen/hubitat/main/Drivers/ElectricityAPI/hubitatElectricityPrice.groovy"
    ) {
        capability "Sensor"
        capability "Initialize"
        capability "Polling"
        capability "Refresh"
        capability "EnergyMeter"

        attribute "PriceListToday", "HashMap"
        attribute "PriceList15M", "HashMap"
        attribute "CurrentPrice", "NUMBER"
        attribute "CurrentRank", "NUMBER"
        attribute "EVStartHour", "NUMBER"
        attribute "EVEndHour", "NUMBER"
        attribute "AveragePriceToday", "NUMBER"

        command "clearHours"
        command "clearStateVariables"
        command "setEVConsecutiveHours"
        
       
        
    }
}

preferences {
    section("Security") {
        input "securityToken", "text", title: "Entsoe Token", required: true
        input "WebAPIUrl", "text", title: "Entsoe WebAPI Url", required: true,
              defaultValue: "https://web-api.tp.entsoe.eu/api"
        input "areaCode", "text", title: "Area Code", required: true
        input "VAT", "number", title: "VAT %", required: true
        input "currencyFactor", "number", title: "CurrencyFactor", required: true, defaultvalue: 1

        input name: "EVRequiredHours", type: "number", title: "How many consecutive hours for EV", required: false
        input name: "EVChargingThresholdPrice", type: "number", title: "Threshold price (cents/kWh)", required: false

        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false

        input name: "EVTimeType", type: "enum", title: "Type of EVHour attributes",
              options: ["hour", "epoch"], defaultValue: "hour"
        input name: "PriceResolution", type: "enum", title: "Price resolution",
              options: ["PT15M", "PT60M"], defaultValue: "PT60M"

        input name: "TransferSummer", type: "number", title: "Transfer price summer", required: true
        input name: "TransferWinter", type: "number", title: "Transfer price winter", required: true
        input name: "Tax", type: "number", title: "Transfer tax", required: true
        
       
    }
}

def clearHours() {
    sendEvent(name: "EVStartHour", value: -1)
    sendEvent(name: "EVEndHour", value: -1)
    if (logEnable) log.debug "EV hours cleared"
}

def calculateTotalPrice(dateTimeString) {
    def dateFormat = new SimpleDateFormat("yyyyMMddHHmm")
    def dateTime = dateFormat.parse(dateTimeString)

    def currentYear = Calendar.getInstance().get(Calendar.YEAR)
    def isSunday = dateTime.format("E") == "Sun"

    def startDate = new Date(currentYear - 1900, 10, 1, 0, 0) // November
    def endDate = new Date(currentYear - 1900 + 1, 3, 1, 0, 0) // March

    if (endDate.getYear() + 1900 > currentYear && startDate > new Date()) {
        endDate = endDate - 365
        startDate = startDate - 365
    }

    def highPrice = Tax.toFloat() + TransferWinter.toFloat()
    def lowPrice  = Tax.toFloat() + TransferSummer.toFloat()

    if (isSunday) {
        return lowPrice
    } else if (dateTime.after(startDate) && dateTime.before(endDate) &&
               (dateTime.getHours() >= 7 && dateTime.getHours() < 22)) {
        return highPrice
    } else {
        return lowPrice
    }
}

def clearStateVariables() {
    device.deleteCurrentState('PriceListToday')
    device.deleteCurrentState('PriceList15M')
    device.deleteCurrentState('CurrentPrice')
    device.deleteCurrentState('CurrentRank')
    device.deleteCurrentState('EVStartHour')
    device.deleteCurrentState('EVEndHour')
    device.deleteCurrentState('AveragePriceToday')
    
    state.clear()
}

def logsOff() {
    log.warn "Debug logging disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def updated() {
    log.info "Updated"
    log.warn "Debug logging is: ${logEnable}"
    if (logEnable) runIn(1800, logsOff)
}

def getParams() {
    def date = new Date()
    def sdf = new SimpleDateFormat("yyyyMMdd")
    def start = sdf.format(date.plus(-1))
    def end   = sdf.format(date.plus(2))
    return "${WebAPIUrl}?securityToken=${securityToken}&documentType=A44&in_Domain=${areaCode}&out_Domain=${areaCode}&periodStart=${start}0000&periodEnd=${end}2300"
}

def refresh()
{
	refresh_new()  
}


def poll ()
{
	poll_new()
}


/**
 * Luo HashMap nykyisestä PriceList-atribuutista.
 * @param attributeName nimi attribuutille, esim. "PriceListToday" tai "PriceList15M"
 * @return HashMap<String, Float> jossa avaimet ovat aikaleimoja ja arvot hinnat
 */
def getPriceMap(String attributeName) {
    HashMap<String, Float> priceMap = [:]
    def priceString = device.currentValue(attributeName)
     
    if (!priceString) return priceMap

    // Poistetaan ympäröivät aaltosulkeet, jos ne ovat
    priceString = priceString.substring(1, priceString.length() - 1)

    for (String keyValue : priceString.split(",")) {
        String[] pairs = keyValue.split("=", 2)
        def key = pairs[0].trim()
        def value = (pairs.length == 1) ? 0f : Float.parseFloat(pairs[1].trim())
        priceMap.put(key, value)
    }
    return priceMap.sort { it.key } as HashMap
}

def poll_new () {
    if (logEnable) log.debug "Poll start"
    def date = new Date()
    def calendar = Calendar.getInstance()
    calendar.setTime(date)

    def keyMinute
    def priceMap
    if (PriceResolution == "PT15M") {
        // pyöristetään minuutit lähimpään 15 min väliin
        def minutes = calendar.get(Calendar.MINUTE)
        def roundedMinutes = (minutes / 15 as int) * 15
        calendar.set(Calendar.MINUTE, roundedMinutes)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        priceMap = getPriceMap("PriceList15M")   ?: [:]
       // priceMap = device.currentValue("PriceList15M") ?: [:]
    } else { // PT60M
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        priceMap = getPriceMap("PriceListToday")   ?: [:]
        //priceMap = device.currentValue("PriceListToday") ?: [:]
    }

    keyMinute = calendar.format("yyyyMMddHHmm")
    
    if (!priceMap.containsKey(keyMinute)) {
        if (logEnable) log.debug "Price not found for key $keyMinute"
        sendEvent(name: "CurrentPrice", value: -1)
        sendEvent(name: "CurrentRank", value: -1)
        return
    }

    def currentPrice = priceMap[keyMinute]
    sendEvent(name: "CurrentPrice", value: currentPrice)

    // Rank lasketaan vain samoista tunnista/15min-periodista
    def sortedEntries = priceMap.sort { it.key }
    def rankList = sortedEntries.findAll { it.key.startsWith(keyMinute[0..7]) } // saman päivän hinnat
    def rank = rankList.findIndexOf { it.key == keyMinute }
    sendEvent(name: "CurrentRank", value: rank)

    if (logEnable) log.debug "Poll $PriceResolution → key=$keyMinute, price=$currentPrice, rank=$rank"
}


def installed() {
    log.info "Installed"
    sendEvent(name: "PriceListToday", value: "{}")
    updated()
}

def initialize() {
    log.info "Initialize"
    sendEvent(name: "PriceListToday", value: "{}")
}


def refresh_new() {
    if (logEnable) log.debug "Refresh"
    if (logEnable) log.debug getParams()

    try {
        def responseBody
        httpGet([uri: getParams(), contentType: "text/xml"]) { resp ->
            responseBody = resp.getData()
        }

        HashMap<String, BigDecimal> today = [:]
        HashMap<String, BigDecimal> today15m = [:]
        Calendar calendar = new GregorianCalendar()
        Double vatMultiplier = (1 + (VAT.toDouble() / 100.0)) / 10.0

        //Set default value, since there are no other values anymore
        def detectedResolution = "PT15M"
            
        def resMinutes = (detectedResolution == "PT15M") ? 15 : 60

        responseBody.TimeSeries.each {
            try {
                it.Period.each { period ->
                    DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME
                    ZonedDateTime utcStartTime = ZonedDateTime.parse(period.timeInterval.start.text().toString(), formatter)
                    ZonedDateTime localTime = utcStartTime.withZoneSameInstant(ZoneId.of(timeZone))

                    calendar.setTime(Date.from(localTime.toInstant()))
                    def previousPrice = 0.0
                    def previousDateLabel = calendar.getTime().format("yyyyMMddHHmm")

                    period.Point.each { point ->
                        def position = point.position.text().toInteger()
                        def currentPrice = point.'price.amount'.text().toDouble()

                        def pointTime = new Date(calendar.time.time + ((position - 1) * resMinutes * 60 * 1000))
                        def dateLabel = new SimpleDateFormat("yyyyMMddHHmm").format(pointTime)

                        // --- PT15M tallennus erilliseen map:iin ---
                        if (detectedResolution == "PT15M") {
                            def totalPrice15m = (currentPrice * currencyFactor.toDouble() * vatMultiplier).round(10) +
                                                calculateTotalPrice(dateLabel).round(10)
                            today15m.put(dateLabel, totalPrice15m.round(2))
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug "Period parse error: ${ex}"
            }
        }

        // Jos PT15M, laske tuntikeskiarvot erikseen
        if (detectedResolution == "PT15M") {
            HashMap<String, BigDecimal> hourly = [:]
            def grouped = today15m.groupBy { it.key.substring(0, 10) } // yyyyMMddHH
            grouped.each { hourKey, values ->
                def avg = values.values().sum() / values.size()
                hourly.put(hourKey + "00", avg.round(2))
            }

            today = hourly.sort { it.key }
            today15m = today15m.sort { it.key }
            sendEvent(name: "PriceList15M", value: today15m)
        }

        today = today.sort { it.key }
        sendEvent(name: "PriceListToday", value: today)
        
        
        // Laske koko päivän keskiarvo
		def currentDate = new SimpleDateFormat("yyyyMMdd").format(new Date())
		def todaysValues = today.findAll { k, v -> k.startsWith(currentDate) }.values()

		if (todaysValues && todaysValues.size() > 0) {
    		def dailyAvg = (todaysValues.sum() / todaysValues.size()).round(2)
    		sendEvent(name: "AveragePriceToday", value: dailyAvg)
		}

    } catch (Exception e) {
        log.debug "Error in httpGet ${e}"
    }
}



	

def setEVConsecutiveHours(int p_limit = EVRequiredHours) {
    HashMap<String, Float> today = [:]
    HashMap<String, Float> futurePrices = [:]

    def limit = p_limit as Integer
    def limitPrice = EVChargingThresholdPrice as Double

    def todayString = device.currentValue("PriceListToday").substring(1, device.currentValue("PriceListToday").length() - 1)
    for (String keyValue : todayString.split(",")) {
        String[] pairs = keyValue.split("=", 2)
        today.put(pairs[0].trim(), pairs.length == 1 ? 0f : Float.parseFloat(pairs[1].trim()))
    }

    today = today.sort { it.key }
    today.each { k, v ->
        def timestamp = new SimpleDateFormat("yyyyMMddHHmm").parse(k)
        if (timestamp.after(new Date())) futurePrices.put(k, v)
    }
    futurePrices = futurePrices.sort { it.key }

    def values = futurePrices.values() as List
    if (values.size() < limit) return

    def windowSum = values[0..(limit - 1)].sum()
    def minSum = windowSum
    def lowestStart = 0

    for (int i = 1; i <= values.size() - limit; i++) {
        windowSum = windowSum - values[i - 1] + values[i + (limit - 1)]
        if (windowSum < minSum) {
            minSum = windowSum
            lowestStart = i
        }
    }

    def lowestStartTimestamp = futurePrices.keySet()[lowestStart]
    def timestamp = new SimpleDateFormat("yyyyMMddHHmm").parse(lowestStartTimestamp)
    Calendar calendar = new GregorianCalendar()
    calendar.setTime(timestamp)
    calendar.add(Calendar.HOUR, limit)

    if (minSum / limit < limitPrice || limitPrice == null) {
        switch (EVTimeType) {
            case "hour":
                sendEvent(name: "EVStartHour", value: timestamp.getHours())
                sendEvent(name: "EVEndHour", value: calendar.getTime().getHours())
                break
            case "epoch":
                if (new Date().getTime() > device.currentValue("EVEndHour")) {
                    sendEvent(name: "EVStartHour", value: timestamp.getTime())
                    sendEvent(name: "EVEndHour", value: calendar.getTime().getTime())
                } else if (logEnable) {
                    log.debug "Times not set, current date < EVEndHour"
                }
                break
            default:
                sendEvent(name: "EVStartHour", value: timestamp.getHours())
                sendEvent(name: "EVEndHour", value: calendar.getTime().getHours())
        }
    } else {
        sendEvent(name: "EVStartHour", value: -1)
        sendEvent(name: "EVEndHour", value: -1)
    }
}
