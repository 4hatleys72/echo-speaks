/**
 *	Echo Speaks WebSocket
 *
 *  Copyright 2018, 2019 Anthony Santilli
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

import groovy.json.*
import java.util.*
import java.text.SimpleDateFormat
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.security.MessageDigest
String devVersion()  { return "3.1.0.0"}
String devModified() { return "2019-09-25" }
Boolean isBeta()     { return false }
Boolean isST()       { return (getPlatform() == "SmartThings") }
Boolean isWS()       { return true }

metadata {
    definition (name: "Echo Speaks WS", namespace: "tonesto7", author: "Anthony Santilli", importUrl: "https://raw.githubusercontent.com/tonesto7/echo-speaks/master/devicetypes/tonesto7/echo-speaks-ws.src/echo-speaks-ws.groovy") {
        capability "Initialize"
        capability "Refresh"
        capability "Actuator"
        //command "sendMsg", ["String"]
        attribute "Activity","String"
    }
}

preferences {
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
}

void updateDeviceStatus(Map devData) {
    Boolean isOnline = false
    if(devData?.size()) {

    }
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def refresh() {
    log.info "refresh() called"
}

def triggerInitialize() {}
def resetQueue() {}

def installed() {
    log.info "installed() called"
    updated()
}

def updated() {
    log.info "updated() called"
    unschedule()
    initialize()
}

def initialize() {
    log.info "initialize() called"
    close()
    state?.amazonDomain = parent?.getAmazonDomain()
    state?.cookie = parent?.getCookieVal()
    def serArr = state?.cookie =~ /ubid-[a-z]+=([^;]+);/
    state?.wsSerial = serArr?.find() ? serArr[0..-1][0][1] : null
    state?.wsDomain = (state?.amazonDomain == "amazon.com") ? "-js.amazon.com" : ".${state?.amazonDomain}"
    // def msgId = 0Math.floor(1E9 * Math.random()) as BigInteger;
    // log.debug "messageId: ${msgId}"
    state?.messageId = state?.messageId ?: 0
    state?.messageInitCnt = 0
    connect()
}

def connect() {
    //Connect to Alexa API WebSocket
    try {
        def ts = now()
        String url = "https://dp-gw-na${state?.wsDomain}/?x-amz-device-type=ALEGCNGL9K0HM&x-amz-device-serial=${state?.wsSerial}-${ts}"
        log.debug "url: ${url}"
        Map headers = [
            "Connection": "keep-alive, Upgrade",
            "Upgrade": "websocket",
            "Host": "dp-gw-na.${state?.amazonDomain}",
            "Origin": "https://alexa.${state?.amazonDomain}",
            "Pragma": "no-cache",
            "Cache-Control": "no-cache",
            // "Accept": 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            // "Accept-Language": "de,en-US;q=0.7,en;q=0.3",
            // "Sec-WebSocket-Version": 13,
            // "Sec-WebSocket-Extensions": "permessage-deflate",
            // "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 11_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15G77 PitanguiBridge/2.2.219248.0-[HARDWARE=iPhone10_4][SOFTWARE=11.4.1]",
            "Cookie": state?.cookie
        ]
        interfaces.webSocket.connect(url, byteInterface: "true", pingInterval: 45, headers: headers)
    }
    catch(e) {
        log.error "WebSocket connect failed"
    }
}

def close() {
    state?.connectionActive = false;
    interfaces.webSocket.close()
}

def reconnectWebSocket() {
    // first delay is 2 seconds, doubles every time
    state.reconnectDelay = (state.reconnectDelay ?: 1) * 2
    // don't let the delay get too crazy, max it out at 10 minutes
    if(state.reconnectDelay > 600) state.reconnectDelay = 600

    runIn(state?.reconnectDelay, initialize)
}

def sendWsMsg(String s) {
    interfaces.webSocket.sendMessage(s)
}

def webSocketStatus(String status){
    log.debug "WS Status Event | ${status}"

    if(status.startsWith('failure: ')) {
        log.warn("failure message from web socket ${status}")
        reconnectWebSocket()
    } else if(status == 'status: open') {
        logInfo("Alexa WS Connection is Open")
        // success! reset reconnect delay
        pauseExecution(1000)
        state.reconnectDelay = 1
        log.trace("Connection Initiation (Step 1)")
        sendWsMsg(strToHex("0x99d4f71a 0x0000001d A:HTUNE")?.toString())
    } else if (status == "status: closing"){
        log.warn "WebSocket connection closing."
    } else if(status?.startsWith("send error: ")) {
        log.error("send error: $status")

    } else {
        log.warn "WebSocket error, reconnecting."
        reconnectWebSocket()
    }
}

def parse(message) {
    log.debug "parsed ${message}"
    def newMsg = strFromHex(message)
    log.debug "decodedMsg: ${newMsg}"
    if(newMsg) {
        switch(newMsg) {
            case """0xbafef3f3 0x000000cd {"protocolName":"A:H","parameters":{"AlphaProtocolHandler.supportedEncodings":"GZIP","AlphaProtocolHandler.maxFragmentSize":"16000","AlphaProtocolHandler.receiveWindowSize":"16"}}TUNE""":
                sendWsMsg(strToHex("""0xa6f6a951 0x0000009c {"protocolName":"A:H","parameters":{"AlphaProtocolHandler.receiveWindowSize":"16","AlphaProtocolHandler.maxFragmentSize":"16000"}}TUNE""")?.toString())
                pauseExecution(1000)
                def gwHsMsg = encodeGWHandshake()
                log.debug "gwHsMsg: $gwHsMsg"
                sendWsMsg(strToHex(gwHsMsg))

                def gwRegMsg = encodeGWRegister()
                log.debug "gwRegMsg: $gwRegMsg"
                sendWsMsg(strToHex(gwRegMsg))
                log.trace("Connection Messages Sent (Step 2+3)")

                def pingMsg = encodePing()
                log.debug "pingMsg: ${pingMsg}"
                // sendWsMsg(strToHex(pingMsg))
                log.trace("Connection Messages Sent (Step 4 | Encoded Ping)")
                break
        }
    }
}

def encodePing() {
    state.messageId++;
    String msg = 'MSG 0x00000065 '; // Message-type and Channel = CHANNEL_FOR_HEARTBEAT;
    msg += encodeNumber(state?.messageId) + ' f 0x00000001 ';
    Integer idx1 = msg.length();
    msg += '0x00000000 '; // Checksum!
    Integer idx2 = msg.length();
    msg+= '0x00000062 '; // length content

    byte[] buffer = new byte[98]
    buffer = copyArrRange(buffer, 0, msg?.getBytes("ASCII"));
    log.debug "buffer(${buffer?.size()}): ${buffer}"
    String header = 'PIN';
    String payload = 'Regular';

    byte[] n = new byte[header?.length() + 4 + 8 + 4 + (2 * payload?.length())]

    Integer idx = header?.length();

    byte[] u = header?.getBytes("UTF-8");
    n = copyArrRange(n, 0, u)
    Integer l = 0;

    n = encode(n, l, idx, 4);
    // log.debug "n2(${n?.size()}): $n"
    idx += 4;
    n = encode(n, now(), idx, 8);
    idx += 8;
    n = encode(n, payload?.length(), idx, 4);
    idx += 4;
    n = encodePayload(n, payload, idx, payload?.length())
    log.debug "n(${n?.size()}): $n"

    buffer = copyArrRange(buffer, msg?.length(), n)

    def buf2End = "FABE"?.getBytes("ASCII")
    // log.debug "len: ${msg?.length() + n?.size()}"

    buffer = copyArrRange(buffer, msg?.length() + n?.size(), buf2End)

    def checksum = computeRFC1071Checksum(new String(buffer), idx1, idx2);
    // log.debug "checksum: ${checksum}"

    def checksumBuf = encodeNumber(checksum)?.getBytes("UTF-8")
    buffer = copyArrRange(buffer, 39, checksumBuf)
    // log.debug "buffer1: $buffer"
    def out = new String(buffer)
    return out

    // "MSG 0x00000065 0x0e414e47 f 0x00000001 0xbc2fbb5f 0x00000062 PIN" + 30 + "FABE"
}


def encode(arr, b, pos, len, logs=false) {
    // Integer i = 0
    def u = new byte[len]
    // def arrItems = arr[pos..(pos+len)]
    // if(logs) log.debug "arrItems: $arrItems"
    // arrItems?.each {
    //      arr[it] = b >> (8 * (len - 1 - i)) & 255;
    //      i++;
    // }
    for (c = 0; c < len; c++) u[c] = b >> 8 * (len - 1 - c) & 255;

    return copyArrRange(arr, pos, u)
}

def encodePayload(arr, pay, pos, len) {
    // byte[] pb = pay?.getBytes("ASCII");
    byte[] u = new byte[len*2]
    for (def q = 0; q < pay?.length(); q++) { u[q * 2] = 0; u[(q * 2) + 1] = pay?.charAt(q); }
    // log.debug "u: $u"
    return copyArrRange(arr, pos, u)
}

def copyArrRange(arrSrc, Integer arrSrcStrt=0, arrIn) {
    if(arrSrc?.size() < arrSrcStrt) { log.error "Array Start Index is larger than Array Size..."; return arrSrc; }
    Integer s = 0
    // log.debug "arrIn: $arrIn"
    (arrSrcStrt..(arrSrcStrt+arrIn?.size()-1))?.each { arrSrc[it] = arrIn[s]; s++; }
    return arrSrc
}

def computeRFC1071Checksum(a, f, k) {
    if (k < f) throw "Invalid checksum exclusion window!";
    if(a instanceof String) { a = a?.getBytes(); }
    def h = 0
    def l = 0
    def t = 0
    for (def e = 0; e < a?.size(); e++) {
        if(e != f) { t = a[e] << ((e & 3 ^ 3) << 3); l += c(t); h += b(l, 32); l = c(l & 4294967295); }
        else { e = k - 1 }
    }
    for (; h;) {
        l += h; h = b(l, 32); l &= 4294967295;
    }
    return c(l);
}





String encodeGWHandshake() {
    //pubrelBuf = new Buffer('MSG 0x00000361 0x0e414e45 f 0x00000001 0xd7c62f29 0x0000009b INI 0x00000003 1.0 0x00000024 ff1c4525-c036-4942-bf6c-a098755ac82f 0x00000164d106ce6b END FABE');
    try {
        state?.messageId++;
        def msg = 'MSG 0x00000361 '; // Message-type and Channel = GW_HANDSHAKE_CHANNEL;
        msg += encodeNumber(state?.messageId) + ' f 0x00000001 ';
        def idx1 = msg?.length();
        msg += '0x00000000 '; // Checksum!
        def idx2 = msg?.length();
        msg += '0x0000009b '; // length content
        msg += 'INI 0x00000003 1.0 0x00000024 '; // content part 1
        msg += generateUUID();
        msg += ' ';
        msg += encodeNumber(now(), 16);
        msg += ' END FABE';
        // log.debug "msg: ${msg}"
        byte[] completeBuffer = msg?.getBytes("ASCII")
        def checksum = computeRFC1071Checksum(msg, idx1, idx2);
        def checksumBuf = encodeNumber(checksum)?.getBytes("UTF-8")
        completeBuffer = copyArrRange(completeBuffer, 39, checksumBuf)
        def out = new String(completeBuffer)
        // log.debug "out: $out"
        return out
    } catch (ex) { log.error "encodeGWHandshake Exception: ${ex}" }
}

def encodeGWRegister() {
    //pubrelBuf = new Buffer('MSG 0x00000362 0x0e414e46 f 0x00000001 0xf904b9f5 0x00000109 GWM MSG 0x0000b479 0x0000003b urn:tcomm-endpoint:device:deviceType:0:deviceSerialNumber:0 0x00000041 urn:tcomm-endpoint:service:serviceName:DeeWebsiteMessagingService {"command":"REGISTER_CONNECTION"}FABE');
    state?.messageId++;
    def msg = 'MSG 0x00000362 '; // Message-type and Channel = GW_CHANNEL;
    msg += encodeNumber(state?.messageId) + ' f 0x00000001 ';
    def idx1 = msg?.length();
    msg += '0x00000000 '; // Checksum!
    def idx2 = msg?.length();
    msg += '0x00000109 '; // length content
    msg += 'GWM MSG 0x0000b479 0x0000003b urn:tcomm-endpoint:device:deviceType:0:deviceSerialNumber:0 0x00000041 urn:tcomm-endpoint:service:serviceName:DeeWebsiteMessagingService {"command":"REGISTER_CONNECTION"}FABE';
    byte[] buffer = msg?.getBytes("ASCII")
    def checksum = computeRFC1071Checksum(msg, idx1, idx2);
    def checksumBuf = encodeNumber(checksum)?.getBytes("UTF-8")
    buffer = copyArrRange(buffer, 39, checksumBuf)
    def out = new String(buffer)
    return out
}



def readHex(str, index, length) {
    def s = str?.toString('ascii', index, index + length);
    if (s?.startsWith('0x')) s = s?.substr(2);
    return parseInt(s, 16);
}

def readString(str, index, length) {
    return str?.toString('ascii', index, index + length);
}

String encodeNumber(val, byteLen=null) {
    if (!byteLen) byteLen = 8;
    def str = Integer.toString(val as Integer, 16);
    while (str?.toString()?.length() < byteLen) {
        str = '0' + str;
        log.debug "str:"
    }
    return '0x' +str;
}

def b(a, b) { for (a = c(a); 0 != b && 0 != a;) a = Math.floor(a / 2); b--; return a; }
def c(a) { return (0 > a) ? (4294967295 + a + 1) : a; }

String generateUUID() {
    def a = []
    for (def b = 0; 36 > b; b++) {
        def c = "rrrrrrrr-rrrr-4rrr-srrr-rrrrrrrrrrrr"?.charAt(b);
        if ("r" == c || "s" == c) {
            def d = Math.floor(16 * Math.random());
            if("s" == c) d = d ? 3 : 8;
            a?.push(Integer.toString(d as Integer, 16));
        } else a?.push(c);
    }
    return a?.join("");
}

String strToHex(String arg, charset="UTF-8") { return String.format("%x", new BigInteger(1, arg.getBytes(charset))); }
String strFromHex(str, charset="UTF-8") { return new String(str?.decodeHex()) }
String getCookieVal() { return (state?.cookie && state?.cookie?.cookie) ? state?.cookie?.cookie as String : null }
String getCsrfVal() { return (state?.cookie && state?.cookie?.csrf) ? state?.cookie?.csrf as String : null }

Integer stateSize() { def j = new groovy.json.JsonOutput().toJson(state); return j?.toString().length(); }
Integer stateSizePerc() { return (int) ((stateSize() / 100000)*100).toDouble().round(0); }
private addToLogHistory(String logKey, msg, statusData, Integer max=10) {
    Boolean ssOk = (stateSizePerc() > 70)
    List eData = state?.containsKey(logKey as String) ? state[logKey as String] : []
    if(eData?.find { it?.message == msg }) { return; }
    if(status) { eData.push([dt: getDtNow(), message: msg, status: statusData]) }
    else { eData.push([dt: getDtNow(), message: msg]) }
	if(!ssOK || eData?.size() > max) { eData = eData?.drop( (eData?.size()-max) ) }
	state[logKey as String] = eData
}
private logDebug(msg) { if(settings?.logDebug == true) { log.debug "Echo (v${devVersion()}) | ${msg}" } }
private logInfo(msg) { if(settings?.logInfo != false) { log.info " Echo (v${devVersion()}) | ${msg}" } }
private logTrace(msg) { if(settings?.logTrace == true) { log.trace "Echo (v${devVersion()}) | ${msg}" } }
private logWarn(msg, noHist=false) { if(settings?.logWarn != false) { log.warn " Echo (v${devVersion()}) | ${msg}"; }; if(!noHist) { addToLogHistory("warnHistory", msg, null, 15); } }
private logError(msg, noHist=false) { if(settings?.logError != false) { log.error "Echo (v${devVersion()}) | ${msg}"; }; if(noHist) { addToLogHistory("errorHistory", msg, null, 15); } }

Map getLogHistory() {
    return [ warnings: state?.warnHistory ?: [], errors: state?.errorHistory ?: [], speech: state?.speechHistory ?: [] ]
}
public clearLogHistory() {
    state?.warnHistory = []
    state?.errorHistory = []
    state?.speechHistory = []
}

private incrementCntByKey(String key) {
	long evtCnt = state?."${key}" ?: 0
	evtCnt++
	state?."${key}" = evtCnt?.toLong()
}

String getObjType(obj) {
	if(obj instanceof String) {return "String"}
	else if(obj instanceof GString) {return "GString"}
	else if(obj instanceof Map) {return "Map"}
    else if(obj instanceof LinkedHashMap) {return "LinkedHashMap"}
    else if(obj instanceof HashMap) {return "HashMap"}
	else if(obj instanceof List) {return "List"}
	else if(obj instanceof ArrayList) {return "ArrayList"}
	else if(obj instanceof Integer) {return "Integer"}
	else if(obj instanceof BigInteger) {return "BigInteger"}
	else if(obj instanceof Long) {return "Long"}
	else if(obj instanceof Boolean) {return "Boolean"}
	else if(obj instanceof BigDecimal) {return "BigDecimal"}
	else if(obj instanceof Float) {return "Float"}
	else if(obj instanceof Byte) {return "Byte"}
	else { return "unknown"}
}

public Map getDeviceMetrics() {
    Map out = [:]
    def cntItems = state?.findAll { it?.key?.startsWith("use_") }
    def errItems = state?.findAll { it?.key?.startsWith("err_") }
    if(cntItems?.size()) {
        out["usage"] = [:]
        cntItems?.each { k,v -> out?.usage[k?.toString()?.replace("use_", "") as String] = v as Integer ?: 0 }
    }
    if(errItems?.size()) {
        out["errors"] = [:]
        errItems?.each { k,v -> out?.errors[k?.toString()?.replace("err_", "") as String] = v as Integer ?: 0 }
    }
    return out
}

private getPlatform() {
    String p = "SmartThings"
    if(state?.hubPlatform == null) {
        try { [dummy: "dummyVal"]?.encodeAsJson(); } catch (e) { p = "Hubitat" }
        // if (location?.hubs[0]?.id?.toString()?.length() > 5) { p = "SmartThings" } else { p = "Hubitat" }
        state?.hubPlatform = p
        logDebug("hubPlatform: (${state?.hubPlatform})")
    }
    return state?.hubPlatform
}
