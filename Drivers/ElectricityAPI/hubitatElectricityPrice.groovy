/*
 * Electricity price api
 */
import java.text.SimpleDateFormat
import groovy.time.TimeCategory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId






metadata {
    definition(name: "Electricity price", namespace: "kapakauppinen", author: "Kari Kauppinen", importUrl: "https://raw.githubusercontent.com/kapakauppinen/hubitat/main/Drivers/ElectricityAPI/hubitatElectricityPrice.groovy") {
        capability "Sensor"
        capability "Initialize"
		capability "Polling"
		capability "Refresh"
        capability "EnergyMeter"
		attribute "PriceListToday", "HashMap"
        /* created for testing */
        //attribute "PriceRankList", "HashMap"
        
        attribute "CurrentPrice", "NUMBER"
        attribute "CurrentRank", "NUMBER"
        
        attribute "EVStartHour", "NUMBER"
        attribute "EVEndHour", "NUMBER"
		
        command "clearHours"
	    command "clearStateVariables"
        command "setEVConsecutiveHours"
    }
}

preferences {
    section("Security") {
        input "securityToken", "text", title: "entsoe Token", required: true
        input "WebAPIUrl", "text", title: "entsoe WebAPI Url", required: true, defaultValue: "https://web-api.tp.entsoe.eu/api"
        // Area code can be found at
        // https://transparency.entsoe.eu/content/static_content/Static%20content/web%20api/Guide.html#_areas
        // look for BZN + your zone
        // FI = 10YFI-1--------U
        // SE3 = 10Y1001A1001A46L
        input "areaCode", "text", title: "Area Code", required: true
	    input "VAT", "number", title: "VAT%", required: true
	    input "currencyFactor", "number", title: "CurrencyFactor", required: true, defaultvalue: 1
        input "timeZone", "text", title: "Timezone", required: true, defaultvalue: "GMT+5"
        
        input name: "EVRequiredHours", type: "number", title: "How many Consecutive hours is needed for EV", required: false
        input name: "EVChargingThresholdPrice", type: "number", title: "Threshold price to set the schedule (cents/kWh)", required: false
        
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "EVTimeType", type: "enum", title: "Type of time of EVHour attributes", options: ["hour", "epoch"], defaultValue: "hour"
        input name: "TransferSummer", type: "number", title: "Transfer price during summer", required: true
        input name: "TransferWinter", type: "number", title: "Transfer price during winter", required: true
        input name: "Tax", type: "number", title: "Transfer tax", required: true


    }
}


def clearHours() {

    sendEvent(name: "EVStartHour", value: -1)
    sendEvent(name: "EVEndHour", value: -1)


    if (logEnable)
        log.debug  "delete EVHours values"



}

def toLocaltime(utcTimestamp)
{

// Assume the UTC timestamp is a string in ISO 8601 format
//log.debug utcTimestamp
// Parse the UTC timestamp
def utcDateTime = ZonedDateTime.parse(utcTimestamp, DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("CET")))
//log.debug utcDateTime
// Convert to local time (e.g., Europe/Helsinki for Finland)
def localDateTime = utcDateTime.withZoneSameInstant(ZoneId.of(timeZone))

// Format the local time as a string if needed
def localTimeString = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

//log.debug localTimeString
   return localDateTime
}

def calculateTotalPrice(dateTimeString) {
    
    def electricityTax = Tax
    def electricityTransferSummer = TransferSummer
    def electricityTransferWinter = TransferWinter


    // Parse the input date string
    def dateFormat = new SimpleDateFormat("yyyyMMddHHmm")
    def dateTime = dateFormat.parse(dateTimeString)

    // Get the current year
    def currentYear = Calendar.getInstance().get(Calendar.YEAR)
    def isSunday = dateTime.format("E") == "Sun"

    // Define the start and end dates for the higher price period
    def startDate = new Date(currentYear - 1900, 10, 1, 0, 0) // 10 corresponds to November (0-indexed)
    def endDate = new Date(currentYear - 1900 + 1, 3, 1, 0, 0)  // 2 corresponds to March (0-indexed)
    
    
    //if the endDate year is next year startdate is greater than current date -> we are before march
    // subtract one year
    if (endDate.getYear()+1900 > currentYear && startDate > new Date())
    {
        endDate = endDate - 365
        startDate = startDate - 365
        // log.debug endDate
    }
    
    //log.debug startDate
   

    // Define the higher and lower prices

    //Float.parseFloat(currencyFactor.toString())
    
    def highPrice = (Float.parseFloat(electricityTax.toString()) + Float.parseFloat(electricityTransferWinter.toString()))
    def lowPrice = (Float.parseFloat(electricityTax.toString()) + Float.parseFloat(electricityTransferSummer.toString()))

    // Check if the given date is within the higher price period and the time is between 7:00 and 22:00
    if (isSunday) {
        return lowPrice
    } else if (dateTime.after(startDate) && dateTime.before(endDate) && (dateTime.getHours() >= 7 && dateTime.getHours() < 22)) {
        return highPrice
    } else {
        return lowPrice
    }
}


def clearStateVariables(){
    device.deleteCurrentState('PriceListToday')
    device.deleteCurrentState('CurrentPrice')
    device.deleteCurrentState('CurrentRank')
    device.deleteCurrentState('EVStartHour')
    device.deleteCurrentState('EVEndHour')


    state.clear()
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    if (logEnable) runIn(1800, logsOff)
}

def getParams()
{
        def date = new Date()
        def sdf = new SimpleDateFormat("yyyyMMdd")

    sdf.format(date)
	    def start = sdf.format(date.plus(-1)).toString()
  	    def end = sdf.format(date.plus(2)).toString()

    //documentation can be found
    // https://transparency.entsoe.eu/content/static_content/Static%20content/web%20api/Guide.html
    "${WebAPIUrl}?securityToken=${securityToken}&documentType=A44&in_Domain=${areaCode}&out_Domain=${areaCode}&periodStart=${start}0000&periodEnd=${end}2300"

}

def poll() {


    def date = new Date()
    def sdf = new SimpleDateFormat("yyyyMMddHH00")
    def dateFormat = new SimpleDateFormat("yyyyMMdd")
    def currenttime = sdf.format(date).toString()
    def currentdate = dateFormat.format(date).toString()
    HashMap <String, String> today = new HashMap <String, Float> ()
    HashMap <String, String> rank = new HashMap <String, Float> ()


    //remove {}
    def todayString = device.currentValue("PriceListToday").substring(1, device.currentValue("PriceListToday").length() - 1)

    //string to hasmap
    for (String keyValue : todayString.split(",")) {

        String[] pairs = keyValue.split("=", 2)
        today.put(pairs[0].trim(), pairs.length == 1 ? "" : Float.parseFloat(pairs[1].trim()));
    }

    today = today.sort { it.key }

    rank = today.sort { it.value }
    rank = rank.findAll { it.key.startsWith(currentdate) }
    //rank = rank.unique()

    //sendEvent(name: "PriceRankList", value: rank)
    def y = rank.findIndexOf{ it.key == currenttime }
    //if(y)
    sendEvent(name: "CurrentRank", value: y)
    
    

    def x = today.find{ it.key == currenttime }


    if (x)
        sendEvent(name: "CurrentPrice", value: x.value)
    if (logEnable)
        log.debug  x.value
}
def installed() {
    log.info "installed() called"
    sendEvent(name: "PriceListToday", value: "{}")
    updated()
}

def initialize() {

    log.info "initialize() called"
    sendEvent(name: "PriceListToday", value: "{}")
}

def refresh() {
    if (logEnable)
        log.debug "Refresh"

    if (logEnable)
        log.debug getParams()

	def responseBody

    try {

        httpGet([uri: getParams(), contentType: "text/xml"]) {
            resp ->
                responseBody =  resp.getData() /*.TimeSeries[0].Period.Point[13].'price.amount'*/
        }
        //2022-10-13T22:00Z
        def pattern = "yyyy-MM-dd'T'HH:mm'Z'"
        def outputTZ = TimeZone.getTimeZone("CET")
        def date = Date.parse(pattern, responseBody.TimeSeries[0].Period.timeInterval.start.text().toString())
        def convertedDate = date.format("yyyyMMddHHmm", outputTZ)
        def timeserieDate
        def totalPrice
        def convertedDateISO = date.format("yyyy-MM-dd'T'HH:mm")
        def localDateTime = toLocaltime(convertedDateISO)
        def position = 0
        def previousposition = 0
        
        //log.debug getParams()
        

        //def hmap
        HashMap <String, String> today = new HashMap <String, String> ()

        Calendar calendar = new GregorianCalendar();
        def dateLabel


        //Price is in MegaWatts, we want it to kilowatts
        Double vatMultiplier = (1 + ((VAT.toDouble() / 100.00))) / 10.00

        if (logEnable)
            log.debug responseBody.TimeSeries


        //log.debug currencyFactor
        //get the Timeseries from the Data
        responseBody.TimeSeries.each {

            try {
                it.Period.each {
                    timeserieDate = Date.parse(pattern, it.timeInterval.start.text().toString())
                    convertedDateISO = timeserieDate.format("yyyy-MM-dd'T'HH:mm")
                    localDateTime = toLocaltime(convertedDateISO)
                    
                    //log.debug localDateTime
                    //log.debug Date.from(localDateTime.toInstant())
                    calendar.setTime(Date.from(localDateTime.toInstant()))
                    def currentposition=1
                    def currentprice=0.0
                    def previousprice=0.0

                    
                   

                    //get the price (inc vat for each hour)
                    it.Point.each {
                        position = it.position.text().toString() as Integer
                        currentprice = it.'price.amount'.text().toString()
                        
                        // entso-E has changed the xml structure
                        // if the price in next hour is the same, the data is not present anymore                       
                        currentposition = position - previousposition    
                   
                        //if there are gaps fill them
                        if (currentposition > 1) {                                              
                            for (int i = 1; i <= currentposition-1; i++) {                        
                                calendar.add(Calendar.HOUR, previousposition+i);
                                dateLabel = calendar.getTime().format("yyyyMMddHHmm")                   
                                calendar.setTime(Date.from(localDateTime.toInstant()))
                                totalPrice = (Float.parseFloat(previousprice) * Float.parseFloat(currencyFactor.toString()) * vatMultiplier).round(10) + calculateTotalPrice(dateLabel).round(10)
                                today.put(dateLabel, totalPrice.round(2));               
                            }
                            
                        }
                                
                        calendar.add(Calendar.HOUR, position);
                        dateLabel = calendar.getTime().format("yyyyMMddHHmm")                   
                        calendar.setTime(Date.from(localDateTime.toInstant()))
                       
                        totalPrice = (Float.parseFloat(currentprice) * Float.parseFloat(currencyFactor.toString()) * vatMultiplier).round(10) + calculateTotalPrice(dateLabel).round(10)

                        // replace number in row below with your currancy factor
                        today.put(dateLabel, totalPrice.round(2));
                        previousposition = position
                        previousprice = currentprice
                        
                    }
                     
                    //if lastposition is not 24 add prices at the end                      
                    if (previousposition < 24) {
                        for (int i = previousposition; i < 24; i++) {
                            calendar.add(Calendar.HOUR, i);
                            dateLabel = calendar.getTime().format("yyyyMMddHHmm")  ;
                            calendar.setTime(Date.from(localDateTime.toInstant()))
                            totalPrice = (Float.parseFloat(previousprice) * Float.parseFloat(currencyFactor.toString()) * vatMultiplier).round(10) + calculateTotalPrice(dateLabel).round(10)
                            today.put(dateLabel, totalPrice.round(2));
                           
                        }
                    }
                }

            }
            catch (Exception ex) {
                log.debug ex
            }
        }
        today = today.sort { it.key }

        sendEvent(name: "PriceListToday", value: today)

       
    }
    catch (Exception e) {
        log.debug "error occured calling httpget ${e}"
    }
}


    /* calculates the cheapest consecutive hours in the future */
    /* Can be used for example to charge EV */

  def setEVConsecutiveHours(int p_limit = EVRequiredHours){

    HashMap <String, String> today = new HashMap <String, Float> ()
    HashMap <String, String> futurePrices = new HashMap <String, Float> ()
      
        def limit = p_limit as Integer
    if (logEnable)
        log.debug limit
        def limitPrice = EVChargingThresholdPrice as Double

     //remove {} from the PriceListToday
        def todayString = device.currentValue("PriceListToday").substring(1, device.currentValue("PriceListToday").length() - 1)

    //string to hasmap
    for (String keyValue : todayString.split(",")) {

        String[] pairs = keyValue.split("=", 2)
        today.put(pairs[0].trim(), pairs.length == 1 ? "" : Float.parseFloat(pairs[1].trim()));
    }

    //sort the today hashmap based on timestamp
    today = today.sort { it.key }

    //filter out timestamp that is in the past
    today.each {
        k, v ->
            def timestamp = new SimpleDateFormat("yyyyMMddHHmm").parse(k)
        if (timestamp.after(new Date())) {
            futurePrices.put(k, v)
        }
    }


    //sort the Hashmap
    futurePrices = futurePrices.sort { it.key }

        def lowestSum = Double.MAX_VALUE
        def lowestStart = 0

        def values = futurePrices.values() as List
        def windowSum = values[0..(limit - 1)].sum()
        def minSum = windowSum

    //calculate the lowest sum of required series
    for (int i = 1; i <= values.size() - limit; i++) {
        windowSum = windowSum - values[i - 1] + values[i + (limit - 1)]
        if (windowSum < minSum) {
            minSum = windowSum
            lowestStart = i
        }
    }

    def lowestStartTimestamp = futurePrices.keySet()[lowestStart]
    def timestamp = new SimpleDateFormat("yyyyMMddHHmm").parse(lowestStartTimestamp)
 
    Calendar calendar = new GregorianCalendar();
    calendar.setTime(timestamp)
    calendar.add(Calendar.HOUR, limit);

    if (minSum / limit < limitPrice || limitPrice == null) {
        //change to epoch
        //log.debug (EVTimeType)

        switch (EVTimeType) {
            case "hour":
                sendEvent(name: "EVStartHour", value: timestamp.getHours())
                sendEvent(name: "EVEndHour", value: calendar.getTime().getHours())
                break
            case "epoch":
                //in this case we have find out, if the current date is smaller than the endDate.
                //if so we don't set the date 
                if (new Date().getTime() > device.currentValue("EVEndHour")) {
                    sendEvent(name: "EVStartHour", value: timestamp.getTime())
                    sendEvent(name: "EVEndHour", value: calendar.getTime().getTime())
                }
                else {
                    if (logEnable) {

                        log.debug "Times not set,  current date " + new Date().getTime() + " is smaller than defined end date" + device.currentValue("EVEndHour")
                        //log.debug new Date().getTime()
                        //log.debug device.currentValue("EVEndHour")
                    }
                }
                break
            default:
                sendEvent(name: "EVStartHour", value: timestamp.getHours())
                sendEvent(name: "EVEndHour", value: calendar.getTime().getHours())
                break

        }



    }
    else {
        sendEvent(name: "EVStartHour", value: -1)
        sendEvent(name: "EVEndHour", value: -1)
    }

}



