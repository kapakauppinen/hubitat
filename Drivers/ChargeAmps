
import groovyx.net.http.ContentType
import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import groovy.json.JsonOutput


metadata {
    definition (name: "Charge Amps", namespace: "kapakauppinen", author: "Kari Kauppinen") {
		attribute "chargePointId", "STRING"
        attribute "LastTokenSync", "NUMBER"

        
        attribute "lastResult", "String"
   
		capability "Refresh"
		capability "Initialize"
        capability "Switch"
        //command "scheduleChild" , ["Number","Number"]

    }
	
	preferences {
	section("Charge Amps") {
	       input "ApiUrl", "text", title: "Charge Amps Api Url", required: true

    
            input name: "Username", type: "string", title: "Username", required: true
	        input name: "Password", type: "string", title: "Password", required: true
	        input name: "ApiKey", type: "string", title: "ApiKey", required: true
            input name: "ApiPath", type: "string", title: "Api path and version", required: true
            input name: "SwitchType", type: "enum", title: "SwitchType", required: true, multiple: true,options:[[1:"Charger"],[2:"Schuko"]], defaultValue: 1
            input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
            input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
            input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: false
	    }
    }
	
	
}

private getApiAddress() {
  return settings.ApiUrl
}


def sendCommand(int deviceId, String command, String level=16) {

   // def level="11"
    switch (command) {
		case "on":
		  on(deviceId,level);
		  break;
		case "off":
		  off (deviceId);
		  break;
        
        case "remoteStart":
		  remoteStart(deviceId);
		  break;
		case "remoteStop":
		  remoteStop (deviceId);
		  break;
		  
		default:
            logDebug(command)
		   break;
	}

	//logDebug (deviceId)

}

def logTrace(msg) {
  if (traceLogEnable) log.trace msg
}

private logInfo(msg) {
  if (descriptionTextEnable) log.info msg
}

def logDebug(msg) {
  if (logEnable) log.debug msg
}


def updated() {
 
  addChildDevices()
}



def uninstalled() {
  removeChildDevices()
}

 
  
def getBaseURI() 
	{ 
       
        return "${ApiUrl}${ApiPath}"
	}


def installed() {
    log.warn "installed..."
   
}

def scheduleChild (Number startSchedule, Number endSchedule)
{
  
      getChildDevices().each {
        it.resetSchedule()
        it.setStartSchedule(startSchedule)
        it.setStopSchedule(endSchedule)
    }
    
    
}

 

private updateSubDevices(evt) {
  logDebug "updateZoneDevices: ${evt.toString()}"
  if (evt.name() == "System") {
    logDebug "Update all Devices"
    childDevices *.zone(evt)
    return
  }

  def zonedevice = getChildDevice(getDeviceId(evt.name()))
  if (zonedevice) {
    zonedevice.zone(evt)
  }

}


private addChildDevices() {
 

  //temporary workaround to add Strings to lists
  def selectedZones = []
  if (settings.SwitchType instanceof java.lang.String) {
    selectedZones = [settings.SwitchType]
  }
  else {
    selectedZones = settings.SwitchType
  }
  selectedZones.each {
    def deviceId = getDeviceId(it)
    if (!getChildDevice(deviceId)) {
      addChildDevice("kapakauppinen", "Charge Amps Switch", deviceId, [name: "Charge Amps Switch ${it}", label: "${device.name} - Switch ${it}", isComponent: false])
      logInfo "Added Charge Amps Switch: ${deviceId}"
    }
  }

  childDevices *.refresh()
}


private removeChildDevices() {
	getChildDevices().each {
    log.warn "deleting ${it}"
    deleteChildDevice(it.deviceNetworkId)
  }
}

private getDeviceId(zone) {
  return "ChargeAmps|${zone}".toString()
}


def parse(String description) {
}




def refresh() {
    logDebug("Refresh: ${device.name} ${getBaseURI()}")
    getRefreshToken()
    getId("${getBaseURI()}chargepoints/owned")  
    def chargePointId = device.currentValue("chargePointId")
  
    
}
    

def initialize() {
     logDebug( "Initialize")
     GetToken()
     getId("${getBaseURI()}chargepoints/owned")
    
     schedule("0 0 * * * ?", "getRefreshToken")// ("${ApiUrl}/api/v5/auth/refreshtoken"))
	schedule("0 0/1 * * * ?", "childRunner")
}


def childRunner() {
    getChildDevices().each {
        it.scheduleRunner()
    }
}




def setStatus(String uri, String status, String connectorId, String maxCurrent) {

    def chargePointId = device.currentValue("chargePointId")

        def body = [

        chargePointId: chargePointId,
        connectorId: connectorId,
        mode: status,
        maxCurrent: maxCurrent,
        rfidLock: true,
        cableLock: true
    ]

    log.debug body
    
    apiRequest("PUT", uri, body)
}


def getChargerStatus () {
     
      def chargePointId = device.currentValue("chargePointId")
    def p_uri ="${getBaseURI()}chargepoints/${chargePointId}/status"
    def responseBody 
    def httpheaders = [:]
    
    httpheaders.put("Authorization", "Bearer ${state.Token}")
     def getParams = [
        uri: p_uri,
        headers : httpheaders
	]
    try {
        
         
        httpGet(getParams) { resp ->
           responseBody = resp.getData()
        }        
       
               
	} 
	catch(Exception e) {
		log.debug "error occured calling httpget ${e}"
	}
    
    
}



def getId (String p_uri) {

    def resp = apiRequest("GET","/chargepoints/owned")

    if(resp) {
        sendEvent(name: "chargePointId", value: resp.first().id)
    }
}



def on(int connectorId, String level) {
    def chargePointId = device.currentValue("chargePointId")
    setStatus("/chargepoints/${chargePointId}/connectors/${connectorId}/settings","On",connectorId.toString(),level)
   
   
     def children = childDevices
     def childDevice = children.find{it.deviceNetworkId.endsWith(connectorId.toString())}
	 childDevice.SetOn()
   
  
   
}

def off(int connectorId) {
 
    def chargePointId = device.currentValue("chargePointId")
    
  
    setStatus("/chargepoints/${chargePointId}/connectors/${connectorId}/settings","Off",connectorId.toString(),"0")
   
	 def children = childDevices
     def childDevice = children.find{it.deviceNetworkId.endsWith(connectorId.toString())}
	 childDevice.SetOff()
	
	
    //sendEvent(name: "switch", value: "off", isStateChange: true)
  
    
}



def remoteStart(int connectorId) {

    def chargePointId = device.currentValue("chargePointId")

    def body = [
        rfidLength: 4,
        rfidFormat: "Hex",
        rfid: "0482D92AAE1391",
        externalTransactionId: "1"
    ]

    apiRequest(
        "PUT",
        "/chargepoints/${chargePointId}/connectors/${connectorId}/remotestart",
        body
    )
}

def remoteStop (int connectorId)
{
    def chargePointId = device.currentValue("chargePointId")

    def body = [
        rfidLength: 4,
        rfidFormat: "Hex",
        rfid: "0482D92AAE1391",
        externalTransactionId: "1"
    ]

    apiRequest(
        "PUT",
        "/chargepoints/${chargePointId}/connectors/${connectorId}/remotestop",
        body
    )
}



     
def getRefreshToken () {
 
    p_uri="${getBaseURI()}/auth/refreshtoken"   
       def body =  JsonOutput.toJson([
        token: state.Token,
        refreshToken: state.refreshToken
       ])

    def responseBody
    def httpheaders = [:]  
    httpheaders.put("host", getHostAddress())
    httpheaders.put("Content-Type", "application/json")
    
    def postParams = [
        uri: p_uri,
        body : body, 
        headers : httpheaders
	]
    
     try {
        httpPost(postParams) { resp ->
           responseBody = resp.getData()
        }        
         state.Token = responseBody.token
         state.refreshToken = responseBody.refreshToken
         
         sendEvent(name: "LastTokenSync", value: new Date().getTime())
                       sendEvent(name: 'lastResult', value: "Success", descriptionText: message, type: 'API call')
        
    } catch(Exception e) {
        log.debug "error occured calling httpPost ${e}"
        state.refreshToken=""
        state.Token=""
        sendEvent(name: "LastTokenSync", value: 0)
        GetToken()
   
    }
    
}

def GetToken() {
 
def p_uri = "${getBaseURI()}auth/login"
    
    
       def body = JsonOutput.toJson([
    email: Username,
    password: Password
])
    
    def responseBody
    def httpheaders = [:] 
  
    httpheaders.put("apiKey", "${ApiKey}")
    httpheaders.put("Content-Type", "application/json")
    httpheaders.put("accept", "application/json")
      
    def postParams = [
        uri: p_uri,
        body : body, //'{"email": ${Username}, "password": ${Password}}',
        headers : httpheaders
	]

     try 
        {
         
            httpPost(postParams) { resp ->
                   responseBody = resp.getData()
                }
         
             state.Token = responseBody.token
             state.refreshToken = responseBody.refreshToken
             sendEvent(name: "LastTokenSync", value: new Date().getTime())
             sendEvent(name: 'lastResult', value: "Success", descriptionText: message, type: 'API call')

        } 
     catch(Exception e) 
         {
            sendEvent(name: "LastTokenSync", value: 0)
            state.refreshToken=""
            state.Token=""
            log.debug "error occured calling httpPost ${e}"
                           sendEvent(name: 'lastResult', value: "Failure", descriptionText: message, type: 'API call')
         }
  
}


private apiRequest(String method, String path, Map body = null) {

    
      def httpheaders = [:]

    httpheaders.put("Authorization", "Bearer ${state.Token}")
    httpheaders.put("host", getHostAddress())


    def params = [
        uri: "${getBaseURI()}${path}",
        //path: path,
        headers: httpheaders,
        //contentType: "application/json"
    ]

    if(body != null) {
        params.body = body
        params.contentType= "application/json"
    }

    try {

        switch(method) {
            case "POST":
                httpPost(params) { resp -> return resp.data }
                break

            case "PUT":
                httpPut(params) { resp -> return resp.data }
                break

            case "GET":
                httpGet(params) { resp -> return resp.data }
                break
        }

    } catch(e) {
        log.error "API ${method} ${ApiUrl}${path}  failed: ${e}"
    }
}




private baseURL() {
  return 'https://my.goabode.com'
}

def getHostAddress(){
	return "eapi.charge.space:443"
}

private driverUserAgent() {
  return 'AbodeAlarm/0.7.0 Hubitat Elevation driver'
}

def getDateString (int DayOfWeek) {
    
    def datestring
     switch(DayOfWeek) {
        case 1:
            datestring = "\"sunday\": true,"
            break
        case 2:
            datestring ="\"monday\": true ,"
            break
         case 3:
            datestring ="\"tuesday\": true ,"
            break
         case 4:
            datestring = "\"wednesday\": true,"
            break
        case 5:
            datestring ="\"thursday\": true ,"
            break
         case 6:
            datestring ="\"friday\": true ,"
            break
         case 7:
            datestring ="\"saturday\": true ,"
            break
     }
         return datestring
    
    
}

	
    
    
