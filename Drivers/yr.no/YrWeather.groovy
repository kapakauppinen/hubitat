import java.text.SimpleDateFormat

metadata {
    definition(name: "Yr Weather Full Driver", namespace: "kapakauppinen", author: "ChatGPT") {
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Pressure Measurement"
  
   
        capability "Sensor"
        capability "Refresh"

        attribute "temperatureTable", "HashMap"  // JSON-muotoinen lämpötilataulukko
        attribute "temperatureTableJson", "string"  // JSON-muotoinen lämpötilataulukko
        attribute "windspeed", "number"
        attribute "winddirection", "number"
        attribute "rain", "number"
        
        command "clearStateVariables"
    }

    preferences {
    
        input "pollInterval", "number", title: "Poll interval (minutes)", defaultValue: 10
    }
}

// Sisäinen HashMap lämpötiloille
def tempHashMap() {
    if (!state.tempMap) state.tempMap = [:]
    return state.tempMap
}


def clearStateVariables() {
    device.deleteCurrentState('temperatureTable')
    device.deleteCurrentState('temperatureTableJson')
    
    device.deleteCurrentState('windspeed')
    device.deleteCurrentState('winddirection')
    device.deleteCurrentState('rain')
    device.deleteCurrentState('humidity')
    device.deleteCurrentState('pressure')
    device.deleteCurrentState('temperature')

    
    state.clear()
}



def installed() { initialize() }
def updated() { initialize() }

def initialize() {
    unschedule()
    if (settings.pollInterval) {
        schedule("0 0/${settings.pollInterval} * * * ?", pollWeather)
    } else {
        runEvery5Minutes(pollWeather)
    }
    pollWeather()
}

def pollWeather() {
    if (!settings.latitude || !settings.longitude) {
        log.warn "Latitude or Longitude not set"
        return
    }

   // def lat = settings.latitude.toDouble()
   // def lon = settings.longitude.toDouble()
    
    def lat = location.latitude.toDouble()
	def lon = location.longitude.toDouble()
    
    
    def url = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=${lat}&lon=${lon}"

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
                
            } else {
                log.warn "HTTP error ${resp.status}"
            }
        }
    } catch (e) {
        log.error "Error fetching weather: ${e}"
    }
}

def parseWeatherData(data) {
     //log.debug "parseWeatherData"
    def timeseries = data.properties.timeseries
    if (!timeseries || timeseries.size() == 0) return
    HashMap<String, BigDecimal> map = [:]
    //def map = tempHashMap()

    timeseries.each { point ->
        def date = Date.parse("yyyy-MM-dd'T'HH:mm:ssX", point.time)
        def key = new SimpleDateFormat("yyyyMMddHHmm").format(date)
            //date.format("yyyyMMddHHmm")
        def temp = point.data.instant?.details?.air_temperature
        if (temp != null) 
        	map.put(key, temp)
        
        
        map = map.sort { it.key }
        //map[key] = temp
    }

    // Päivitetään UI attribuutti JSON-muodossa
    sendEvent(name: "temperatureTable", value:map)
    
    
    sendEvent(name: "temperatureTableJson", value: groovy.json.JsonOutput.toJson(map))
 

    // Viimeisin lämpötila
    def latest = timeseries[0].data.instant.details.air_temperature
    if (latest != null) sendEvent(name: "temperature", value: latest)

    // Paine
    def pressure = timeseries[0].data.instant.details.air_pressure_at_sea_level
    if (pressure != null) sendEvent(name: "pressure", value: pressure)

    // Kosteus
    def humidity = timeseries[0].data.instant.details.relative_humidity
    if (humidity != null) sendEvent(name: "humidity", value: humidity)

    // Tuuli
    def windSpeed = timeseries[0].data.instant.details.wind_speed
    def windDir = timeseries[0].data.instant.details.wind_from_direction
    if (windSpeed != null) sendEvent(name: "windspeed", value: windSpeed)
    if (windDir != null) sendEvent(name: "winddirection", value: windDir)

    // Sade seuraavalle tunnille
    def rainAmount = timeseries[0].data.next_1_hours?.details?.precipitation_amount
    if (rainAmount != null) sendEvent(name: "rain", value: rainAmount)
}

def refresh() { pollWeather() }
