/*
 * Electricity price api
 */
import java.text.SimpleDateFormat

metadata {
    definition(name: "Electricity price", namespace: "kapakauppinen", author: "Kari Kauppinen", importUrl: "") {
        capability "Sensor"
        capability "Initialize"
		capability "Polling"
		capability "Refresh"
		attribute "PriceListToday", "HashMap"
        /* created for testing */
        //attribute "PriceRankList", "HashMap"
        
        attribute "CurrentPrice", "NUMBER"
        attribute "CurrentRank", "NUMBER"
	    command "clearStateVariables"
    }
}

preferences {
    section("Security") {
        input "securityToken", "text", title: "entsoe Token", required: true
        // Area code can be found at
        // https://transparency.entsoe.eu/content/static_content/Static%20content/web%20api/Guide.html#_areas
        // look for BZN + your zone
        // FI = 10YFI-1--------U
        // SE3 = 10Y1001A1001A46L
        input "areaCode", "text", title: "Area Code", required: true
	    input "VAT", "number", title:"VAT%", required: true
	    input "currencyFactor", "number", title:"CurrencyFactor", required: true, defaultvalue: 1
        input "timeZone", "text", title:"Timezone", required:true, defaultvalue:"GMT+5"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def clearStateVariables(){
    device.deleteCurrentState('PriceListToday')
    
    //device.deleteCurrentState('PriceRankList')
    
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
	def start = sdf.format(date).toString()
  	def end = sdf.format(date.plus(1)).toString()

        //documentation can be found
        // https://transparency.entsoe.eu/content/static_content/Static%20content/web%20api/Guide.html
	    "https://transparency.entsoe.eu/api?securityToken=${securityToken}&documentType=A44&in_Domain=${areaCode}&out_Domain=${areaCode}&periodStart=${start}0000&periodEnd=${end}2300"
	}

def poll () {

    //refresh()

    def date = new Date()
    def sdf = new SimpleDateFormat("yyyyMMddHH00")
    def dateFormat = new SimpleDateFormat("yyyyMMdd")
    def currenttime = sdf.format(date).toString()
    def currentdate = dateFormat.format(date).toString()
    HashMap<String, String> today = new HashMap<String, Float>()
    HashMap<String, String> rank = new HashMap<String, Float>()
    

    //remove {}
    def todayString =device.currentValue("PriceListToday").substring(1, device.currentValue("PriceListToday").length() - 1)

    //string to hasmap
    for(String keyValue : todayString.split(",")) {
        String[] pairs = keyValue.split("=", 2)
        today.put(pairs[0].trim(), pairs.length == 1 ? "" : Float.parseFloat(pairs[1].trim()));
    }

    today = today.sort { it.key }
    
    rank = today.sort { it.value}
    rank = rank.findAll {it.key.startsWith(currentdate)}
    //rank = rank.unique()
    
    //sendEvent(name: "PriceRankList", value: rank)
    def y = rank.findIndexOf{ it.key == currenttime }
     if(y)
        sendEvent(name: "CurrentRank", value: y)
    
    

    def x = today.find{ it.key == currenttime }

    
    if(x)
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

	def responseBody

	try {
		
		httpGet([uri: getParams(),contentType: "text/xml"]) { resp ->
			responseBody =  resp.getData() /*.TimeSeries[0].Period.Point[13].'price.amount'*/
	    }
        //2022-10-13T22:00Z
        def pattern = "yyyy-MM-dd'T'HH:mm'Z'"
        def outputTZ = TimeZone.getTimeZone(timeZone)
        def date =  Date.parse(pattern,responseBody.TimeSeries[0].Period.timeInterval.start.text().toString())
        def convertedDate = date.format("yyyyMMddHHmm", outputTZ)
        def timeserieDate

        //def hmap
        HashMap<String, String> today = new HashMap<String, String>()

        Calendar calendar = new GregorianCalendar();
        def dateLabel
        
        //Price is in MegaWatts, we want it to kilowatts
        Double vatMultiplier =  (1+((VAT/100)))/10
       
log.debug currencyFactor
        //get the Timeseries from the Data
        responseBody.TimeSeries.each {

            try {

                timeserieDate = Date.parse(pattern,it.Period.timeInterval.start.text().toString())
                calendar.setTime (timeserieDate)

                //get the price (inc vat for each hour)
                it.Period.Point.each {
                    calendar.add(Calendar.HOUR, it.position.text().toString() as Integer);
                    dateLabel = calendar.getTime().format("yyyyMMddHHmm", outputTZ)
                    calendar.setTime (timeserieDate)
                  
                    // replace number in row below with your currancy factor
			today.put(dateLabel, (Float.parseFloat(it.'price.amount'.text().toString())*Float.parseFloat(currencyFactor.toString())*vatMultiplier).round(2));
                }

                //log.debug

            }
            catch (Exception ex) {
                  log.debug ex
            }
        }
        today = today.sort { it.key }

		sendEvent(name: "PriceListToday", value: today)
	} 
	catch(Exception e) {
		log.debug "error occured calling httpget ${e}"
	}
}
