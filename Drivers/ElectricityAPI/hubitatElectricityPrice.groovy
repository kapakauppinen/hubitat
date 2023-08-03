/*
 * Electricity price api
 */
import java.text.SimpleDateFormat
import groovy.time.TimeCategory

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
		
//        attribute "EVStartHourEpoch", "NUMBER"
//        attribute "EVEndHourEpoch", "NUMBER"
		
	    command "clearStateVariables"
        command "setEVConsecutiveHours"
    }
}

preferences {
    section("Security") {
        input "securityToken", "text", title: "entsoe Token", required: true
        input "WebAPIUrl","text", title: "entsoe WebAPI Url", required: true, defaultValue:"https://web-api.tp.entsoe.eu/api"
        // Area code can be found at
        // https://transparency.entsoe.eu/content/static_content/Static%20content/web%20api/Guide.html#_areas
        // look for BZN + your zone
        // FI = 10YFI-1--------U
        // SE3 = 10Y1001A1001A46L
        input "areaCode", "text", title: "Area Code", required: true
	    input "VAT", "number", title:"VAT%", required: true
	    input "currencyFactor", "number", title:"CurrencyFactor", required: true, defaultvalue: 1
        input "timeZone", "text", title:"Timezone", required:true, defaultvalue:"GMT+5"
        
        input name: "EVRequiredHours", type: "number", title:"How many Consecutive hours is needed for EV", required:false
        input name: "EVChargingThresholdPrice", type: "number", title:"Threshold price to set the schedule (cents/kWh)", required:false
        
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "EVTimeType", type: "enum" , title:"Type of time of EVHour attributes",  options: ["hour","epoch"] ,defaultValue: "hour"
        
    }
}

def clearStateVariables(){
    device.deleteCurrentState('PriceListToday')
    device.deleteCurrentState('CurrentPrice')
    device.deleteCurrentState('CurrentRank')
    device.deleteCurrentState('EVStartHour')
    device.deleteCurrentState('EVEndHour')
	  device.deleteCurrentState('EVStartHourEpoch')
    device.deleteCurrentState('EVEndHourEpoch')
    
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
	    def start = sdf.format(date.plus(-1)).toString()
  	    def end = sdf.format(date.plus(1)).toString()

        //documentation can be found
        // https://transparency.entsoe.eu/content/static_content/Static%20content/web%20api/Guide.html
	    "${WebAPIUrl}?securityToken=${securityToken}&documentType=A44&in_Domain=${areaCode}&out_Domain=${areaCode}&periodStart=${start}0000&periodEnd=${end}2300"

    }

def poll () {


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
     //if(y)
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
    
    if (logEnable)
    log.debug getParams()

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
       
        if (logEnable)
            log.debug responseBody.TimeSeries
        
        
        //log.debug currencyFactor
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


            }
            catch (Exception ex) {
                  log.debug ex
            }
        }
        today = today.sort { it.key }

		sendEvent(name: "PriceListToday", value: today)
        
        //removed, because of the dealy in sendEvent.
        
        //set the hours to enable EV charger
        //if (EVRequiredHours!= null) {
        //    setEVConsecutiveHours()
        //}
        
	} 
	catch(Exception e) {
		log.debug "error occured calling httpget ${e}"
	}
}


    /* calculates the cheapest consecutive hours in the future */
    /* Can be used for example to charge EV */

  def setEVConsecutiveHours(){

        HashMap<String, String> today = new HashMap<String, Float>()
        HashMap<String, String> futurePrices = new HashMap<String, Float>()
      
        def limit=EVRequiredHours as Integer
        def limitPrice = EVChargingThresholdPrice as Double
  
     //remove {} from the PriceListToday
        def todayString =device.currentValue("PriceListToday").substring(1, device.currentValue("PriceListToday").length() - 1)

        //string to hasmap
        for(String keyValue : todayString.split(",")) {

            String[] pairs = keyValue.split("=", 2)
            today.put(pairs[0].trim(), pairs.length == 1 ? "" : Float.parseFloat(pairs[1].trim()));
        }
        
        //sort the today hashmap based on timestamp
        today = today.sort { it.key }
    
        //filter out timestamp that is in the past
        today.each { k, v ->
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
        def windowSum = values[0..(limit-1)].sum()
        def minSum = windowSum
    
        //calculate the lowest sum of required series
        for (int i = 1; i <= values.size() - limit; i++) {
            windowSum = windowSum - values[i-1] + values[i+(limit-1)]
            if (windowSum < minSum) {
                minSum = windowSum
                lowestStart = i       
            }
        }

    def lowestStartTimestamp = futurePrices.keySet()[lowestStart]
    def timestamp = new SimpleDateFormat("yyyyMMddHHmm").parse(lowestStartTimestamp)
 
    Calendar calendar = new GregorianCalendar();
    calendar.setTime (timestamp)
    calendar.add(Calendar.HOUR, limit);

        if (minSum/limit < limitPrice || limitPrice == null) {
            //change to epoch
            //log.debug (EVTimeType)
            
            switch(EVTimeType) { 
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

                        log.debug "Times not set,  current date " + new Date().getTime() + " is smaller than defined end date" +  device.currentValue("EVEndHour")
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

    
    
