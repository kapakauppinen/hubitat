
import groovyx.net.http.ContentType
import groovy.json.JsonSlurper

metadata {
		definition (name: "Charge Amps Switch", namespace: "kapakauppinen", author: "Kari Kauppinen") {
		capability "Switch"
		capability "Actuator"
        capability "EnergyMeter"
		capability "Refresh"
		capability "Initialize"
		attribute "mode", "STRING"
        attribute "level", "NUMBER"    
        attribute "energy", "number"
        attribute "power", "number"
        
            
        attribute "ScheduleStart", "NUMBER"
        attribute "ScheduleStop", "NUMBER"
        
        command "setStartSchedule", ["NUMBER"]
        command "setStopSchedule", ["NUMBER"]
        command "resetSchedule"
            
    
            
        command "remoteStart"
        command "remoteStop"
          
        command "low"
        command "medium"
        command "high"
        command "setLevel", ["NUMBER"]
              
    }
	
	preferences {
	    section("userInfo") {       
		    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	    }
    }
 
}

def logDebug(msg) {
    if(logEnable) {
        log.debug msg
    }
}


private short toDisplayLevel(short level) {
    level = Math.max(6, Math.min(16, level))
    (level == (short) 15) ? 16 : level
}

def setLevel(value){
    
    
    short level = toDisplayLevel(value as short)
    
     sendEvent(name: "level", value: level)
     runIn(5,on)
    //device.currentValue("level") = value
}

def allowReschedule (){
    
    def startEpoch = new Date(device.currentValue("ScheduleStart").toLong())
    
    def currentMode = device.currentValue("mode")
    
    switch (currentMode) {
        case "Charging":
		  return false;
		  break;
        case "SuspendedEV":
        	return false;
        	break;
		case "Preparing":
		 if (startEpoch > new Date())
        	{	
                return false
            }
        else {
            return true
        }
		  break;
        
 
		  
		default:
            return true;
		   break;
	}
    
 
}



def setStartSchedule(Number value) {
    
    if (value != -1 && allowReschedule()) {
    	logDebug("Executing setStartSchedule ${value}")
    	sendEvent(name: "ScheduleStart", value: value)
    }
    else {
        logDebug ("Reschedule not allowed")
    }
}

def setStopSchedule(Number value){
    
    if (value != -1 && allowReschedule()) {
    	logDebug ("Executing setStartSchedule ${value}")
     	sendEvent(name: "ScheduleStop", value: value)
    }
    else {
        logDebug ("Reschedule not allowed")
    }

}

def resetSchedule()
{
    //estetään unscheduled, mikäli Kaapli on kytketty
    if (allowReschedule()) {
		unschedule()   
    }
        else {
        logDebug ("Reschedule not allowed")
    }
        
}



private Map buildEvent(name, value, unit=null) {
    Map eventMap = [name: name, value: value, unit: unit, isStateChange: true]
    return eventMap
}


def low() {
    setLevel(6)
}

def medium() {
    setLevel(12)
}

def high() {
    setLevel(16)
}


def installed() {
    log.warn "installed..."
   
}

def updated() {
    log.info "updated..."
    log.warn "description logging is: ${txtEnable == true}"
}

def parse(String description) {
}

def refresh() {
    log.debug "Refresh: ${device.name}"

   
    
}
    

def initialize() {
     log.debug "Initialize"
    
     
    
}

def scheduleRunner() {

    def now = new Date()  // nykyhetki millisekunneissa
    
    if (device.currentValue("switch")=="on") {
    
		def startEpoch = new Date(device.currentValue("ScheduleStart").toLong())
    	def endEpoch   = new Date(device.currentValue("ScheduleStop").toLong())

   		def chargedata = parent.getChargerStatus()
        
        
        ///log.debug chargedata
		//def json = new JsonSlurper().parseText(chargedata)
		def thisConnector = chargedata.connectorStatuses.find { it.connectorId == getZone() }
        def chargerstatus = thisConnector?.status
        
        sendEvent(name: "mode", value: chargerstatus )
        
		
		def isCharging = chargerstatus == "Charging" || chargerstatus ==  "SuspendedEV"
        
        def power = thisConnector?.chargingPowerKw
        def energy = thisConnector?.totalConsumptionKwh
		//log.debug isCharging

        
        if (!isCharging) {
        
    		if(now < startEpoch) {
        	// Haluttu lataus alkaa tulevaisuudessa
        		logDebug("Charging scheduled in the future from ${startEpoch} to ${endEpoch}")
        		runOnce(startEpoch, "startCharging")
        		runOnce(endEpoch, "stopCharging")

    		} else if(now >= startEpoch && now < endEpoch) {
        		// Halvin tunti on meneillään → aloita lataus heti
        		logDebug("Charging window active now, starting immediately until ${endEpoch}")
        		startCharging()
        		runOnce(endEpoch, "stopCharging")

    		} else {
        		// Halvin tunti on jo ohi → ei tehdä mitään
        		logDebug("Charging window already passed, skipping")
    		}
    	}
        else {
        sendEvent(name: "power", value: power, unit: "kW" )
            sendEvent(name: "energy", value: energy, unit: "kWh" )
        	logDebug ("Already Charging")
    	}
    }
   
}





def startCharging() {
    logDebug("Starting charging")
    parent.sendCommand (getZone(),remoteStart)
}

def stopCharging() {
    logDebug("Stopping charging")
    parent.sendCommand (getZone(),remoteStop)
}
    



private sendCommand(int Id, String Command) {
    
    def level=device.currentValue("level")
	parent.sendCommand(Id, Command,level.toString())
}

private getZone() {
   
  return new String(device.deviceNetworkId).tokenize('|')[1].toInteger()
}

def SetOff() {
sendEvent(name: "switch", value: "off", isStateChange: true)
}

def SetOn() {
	sendEvent(name: "switch", value: "on", isStateChange: true)
    //runIn(2,remoteStop)
}

def on() {
    sendCommand(getZone(),"on" )     
}

def off() {
	sendCommand(getZone(),"off" )     
    
    
}


def remoteStart() {
    sendCommand(	getZone(),"remoteStart" )     
}

def remoteStop() {
	sendCommand(getZone(),"remoteStop" )     
    
    
}
 
	
    
    

