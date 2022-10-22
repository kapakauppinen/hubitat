/*
 * Electricity price api
 */
import java.text.SimpleDateFormat

metadata {
    definition(name: "Electricity price", namespace: "kapakauppinen", author: "Kari Kauppinen", importUrl: "") {
        capability "Sensor"
		capability "Polling"
		capability "Refresh"
		attribute "PriceListToday", "HashMap"
        attribute "CurrentPrice", "NUMBER"
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
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def clearStateVariables(){
    device.deleteCurrentState('PriceListToday')
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
    def currenttime = sdf.format(date).toString()
    HashMap<String, String> today = new HashMap<String, String>()

    //remove {}
    def todayString =device.currentValue("PriceListToday").substring(1, device.currentValue("PriceListToday").length() - 1)

    //string to hasmap
    for(String keyValue : todayString.split(",")) {

        String[] pairs = keyValue.split("=", 2)
        today.put(pairs[0].trim(), pairs.length == 1 ? "" : pairs[1].trim());
    }

    today = today.sort { it.key }

    def x = today.find{ it.key == currenttime }

    if(x)
        sendEvent(name: "CurrentPrice", value: x.value)
        if (logEnable)
            log.debug  x.value
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
        def outputTZ = TimeZone.getTimeZone('GMT+3')
        def date =  Date.parse(pattern,responseBody.TimeSeries[0].Period.timeInterval.start.text().toString())
        def convertedDate = date.format("yyyyMMddHHmm", outputTZ)
        def timeserieDate

        //def hmap
        HashMap<String, String> today = new HashMap<String, String>()

        Calendar calendar = new GregorianCalendar();
        def dateLabel

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
                    today.put(dateLabel, (Float.parseFloat(it.'price.amount'.text().toString())*1.103).round(2));
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
