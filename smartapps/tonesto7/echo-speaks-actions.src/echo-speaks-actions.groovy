/**
 *  Echo Speaks Actions
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
 *
 */

import groovy.json.*
import java.text.SimpleDateFormat

String appVersion()	 { return "3.0.0" }
String appModified()  { return "2019-08-08" }
String appAuthor()	 { return "Anthony S." }
Boolean isBeta()     { return false }
Boolean isST()       { return (getPlatform() == "SmartThings") }

definition(
    name: "Echo Speaks - Actions",
    namespace: "tonesto7",
    author: "Anthony Santilli",
    description: "DO NOT INSTALL FROM MARKETPLACE\n\nAllow you to create echo device actions based on Events in your SmartThings home",
    category: "My Apps",
    parent: "tonesto7:Echo Speaks",
    iconUrl: "https://raw.githubusercontent.com/tonesto7/echo-speaks/master/resources/icons/es_actions.png",
    iconX2Url: "https://raw.githubusercontent.com/tonesto7/echo-speaks/master/resources/icons/es_actions.png",
    iconX3Url: "https://raw.githubusercontent.com/tonesto7/echo-speaks/master/resources/icons/es_actions.png",
    importUrl  : "https://raw.githubusercontent.com/tonesto7/echo-speaks/master/smartapps/tonesto7/echo-speaks-actions.src/echo-speaks-actions.groovy",
    pausable: true)

preferences {
    page(name: "startPage")
    page(name: "codeUpdatePage")
    page(name: "mainPage")
    page(name: "uhOhPage")
    page(name: "namePage")
    page(name: "triggersPage")
    page(name: "conditionsPage")
    page(name: "notifPrefPage")
    page(name: "actionsPage")
    page(name: "searchTuneInResultsPage")
    page(name: "condTimePage")
    page(name: "uninstallPage")
    page(name: "sequencePage")
}

def startPage() {
    if(parent != null) {
        if(!state?.isInstalled && parent?.childInstallOk() != true) {
            uhOhPage()
        } else {
            state?.isParent = false
            if(checkMinVersion()) {
                codeUpdatePage()
            } else { mainPage() }
        }
    } else { uhOhPage() }
}

def codeUpdatePage () {
    return dynamicPage(name: "codeUpdatePage", title: "Update is Required", install: false, uninstall: false) {
        section() { paragraph "Looks like your Action App needs an update\n\nPlease make sure all app and device code is updated to the most current version\n\nOnce updated your actions will resume normal operation.", required: true, state: null, image: getAppImg("exclude") }
    }
}

def uhOhPage () {
    return dynamicPage(name: "uhOhPage", title: "This install Method is Not Allowed", install: false, uninstall: true) {
        section() {
            paragraph "HOUSTON WE HAVE A PROBLEM!\n\nEcho Speaks - Actions can't be directly installed from the Marketplace.\n\nPlease use the Echo Speaks SmartApp to configure them.", required: true,
            state: null, image: getAppImg("exclude")
        }
        if(isST()) { remove("Remove this invalid Action", "WARNING!!!", "This is a BAD install of an Action SHOULD be removed") }
    }
}

def appInfoSect(sect=true)	{
    section() { href "empty", title: pTS("${app?.name}", getAppImg("es_actions", true)), description: "(V${appVersion()})", image: getAppImg("es_actions") }
}

List cleanedTriggerList() {
    List newList = []
    settings?.triggerTypes?.each {
        newList?.push(it?.toString()?.split("::")[0] as String)
    }
    return newList?.unique()
}

String selTriggerTypes(type) {
    return settings?.triggerTypes?.findAll { it?.startsWith(type as String) }?.collect { it?.toString()?.split("::")[1] }?.join(", ")
}

private def buildTriggerEnum() {
    List enumOpts = []
    Map buildItems = [:]
    buildItems["Date/Time"] = ["scheduled":"Scheduled Time"]?.sort{ it?.key }
    buildItems["Location"] = ["mode":"Modes", "routineExecuted":"Routines"]?.sort{ it?.key }
    if(!isST()) {
        buildItems?.Location?.remove("routineExecuted")
        //TODO: Once I can find a reliable method to list the scenes and subscribe to events on Hubitat I will re-activate
        // buildItems?.Location?.scene = "Scenes"
    }
    // buildItems["Weather Events"] = ["Weather":"Weather"]
    buildItems["Safety & Security"] = ["alarm": "${getAlarmSystemName()}", "fire":"Carbon Monoxide & Smoke"]?.sort{ it?.key }
    buildItems["Actionable Devices"] = ["lock":"Locks", "switch":"Outlets/Switches", "level":"Dimmers/Level", "door":"Garage Door Openers", "valve":"Valves", "shade":"Window Shades", "button":"Buttons"]?.sort{ it?.key }
    // buildItems["Sensor Device"] = ["Acceleration":"Acceleration", "Contacts, Doors, Windows":"Contacts, Doors, Windows", "Motion":"Motion", "Presence":"Presence", "Temperature":"Temperature", "Humidity":"Humidity", "Water":"Water", "Power":"Power"]?.sort{ it?.key }
    buildItems["Sensor Device"] = ["contact":"Contacts, Doors, Windows", "motion":"Motion", "presence":"Presence", "temperature":"Temperature", "humidity":"Humidity", "water":"Water", "power":"Power"]?.sort{ it?.key }
    if(isST()) {
        buildItems?.each { key, val-> addInputGrp(enumOpts, key, val) }
        // log.debug "enumOpts: $enumOpts"
        return enumOpts
    } else { return buildItems?.collectEntries { it?.value } }
}

def mainPage() {
    Boolean newInstall = !state?.isInstalled
    return dynamicPage(name: "mainPage", nextPage: (!newInstall ? "" : "namePage"), uninstall: newInstall, install: !newInstall) {
        appInfoSect()
        if(!isPaused()) {
            Boolean trigConf = triggersConfigured()
            Boolean condConf = conditionsConfigured()
            Boolean actConf = actionsConfigured()
            section ("Configuration: Part 1") {
                href "triggersPage", title: "Action Triggers", description: getTriggersDesc(), state: (trigConf ? "complete" : ""), image: getAppImg("trigger")
            }

            section("Configuration: Part 2:") {
                if(trigConf) {
                    href "conditionsPage", title: "Condition/Restrictions\n(Optional)", description: getConditionsDesc(), state: (condConf ? "complete": ""), image: getAppImg("conditions")
                } else { paragraph "More Options will be shown once triggers are configured" }
            }
            section("Configuration: Part 3") {
                if(trigConf) {
                    href "actionsPage", title: "Actions Tasks", description: getActionDesc(), state: (actConf ? "complete" : ""), image: getAppImg("es_actions")
                } else { paragraph "More Options will be shown once triggers are configured" }
            }

            section("Preferences") {
                input (name: "appDebug", type: "bool", title: "Show Debug Logs in the IDE?", description: "Only enable when required", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("debug"))
            }
        } else {
            paragraph "This Action is currently in a paused state...  To edit the configuration please un-pause", required: true, state: null, image: getAppImg("pause_orange")
        }

        if(state?.isInstalled) {
            section ("Place this action on hold:") {
                input "actionPause", "bool", title: "Pause this Actions from Running?", defaultValue: false, submitOnChange: true, image: getAppImg("pause_orange")
            }
            section("Name this Automation:") {
                input "appLbl", "text", title:"Label this Action", description: "", required:true, submitOnChange: true, image: getAppImg("name_tag")
            }
            section("Remove Action:") {
                href "uninstallPage", title: "Remove this Action", description: "Tap to Remove...", image: getAppImg("uninstall")
            }
        }
    }
}

def namePage() {
    return dynamicPage(name: "namePage", install: true, uninstall: true) {
        section("Name this Automation:") {
            input "appLbl", "text", title:"Label this Action", description: "", required:true, submitOnChange: true, image: getAppImg("name_tag")
        }
    }
}

def triggersPage() {
    return dynamicPage(name: "triggersPage", uninstall: false, install: false) {
        List stRoutines = getLocationRoutines() ?: []
        Boolean showSpeakEvtVars = false
        section ("Select Capabilities") {
            if(isST()) {
                input "triggerEvents", "enum", title: "Select Trigger Event(s)", groupedOptions: buildTriggerEnum(), multiple: true, required: true, submitOnChange: true, image: getAppImg("trigger")
            } else {
                input "triggerEvents", "enum", title: "Select Trigger Event(s)", options: buildTriggerEnum(), multiple: true, required: true, submitOnChange: true, image: getAppImg("trigger")
            }
        }
        if (settings?.triggerEvents?.size()) {
            if(!(settings?.triggerEvents in ["Scheduled", "Weather"])) { showSpeakEvtVars = true }
            if (valTrigEvt("scheduled")) {
                section("Time Based Events", hideable: true) {
                    if(!settings?.trig_scheduled_time) {
                        input "trig_scheduled_sunState", "enum", title: "Sunrise or Sunset...", options: ["Sunrise", "Sunset"], multiple: false, required: false, submitOnChange: true, image: getPublicImg("sun")
                        if(settings?.trig_scheduled_sunState) {
                            input "trig_scheduled_sunState_offset", "number", range: "*..*", title: "Offset event this number of minutes (+/-)", required: true, image: getPublicImg(settings?.trig_scheduled_sunState?.toString()?.toLowerCase() + "")
                        }
                    }
                    if(!settings?.trig_scheduled_sunState) {
                        input "trig_scheduled_time", "time", title: "Time of Day?", required: false, submitOnChange: true, image: getPublicImg("clock")
                        if(settings?.trig_scheduled_time || settings?.trig_scheduled_sunState) {
                            input "trig_scheduled_recurrence", "enum", title: "Recurrence?", description: "(Optional)", multiple: false, required: false, submitOnChange: true, options: ["Once", "Daily", "Weekly", "Monthly"], defaultValue: "Once", image: getPublicImg("day_calendar2")
                            Boolean dayReq = (settings?.trig_scheduled_recurrence in ["Weekly", "Monthly"])
                            Boolean weekReq = (settings?.trig_scheduled_recurrence in ["Weekly", "Monthly"])
                            Boolean monReq = (settings?.trig_scheduled_recurrence in ["Monthly"])
                            if(settings?.trig_scheduled_recurrence) {
                                input "trig_scheduled_days", "enum", title: "Day(s) of the week", description: (!dayReq ? "(Optional)" : ""), multiple: true, required: dayReq, submitOnChange: true, options: weekDaysEnum(), image: getPublicImg("day_calendar2")
                                input "trig_scheduled_weeks", "enum", title: "Weeks(s) of the month", description: (!weekReq ? "(Optional)" : ""), multiple: true, required: weekReq, submitOnChange: true, options: ["1", "2", "3", "4", "5"], image: getPublicImg("day_calendar2")
                                input "trig_scheduled_months", "enum", title: "Month(s) of the year", description: (!monReq ? "(Optional)" : ""), multiple: true, required: monReq, submitOnChange: true, options: monthEnum(), image: getPublicImg("day_calendar2")
                            }
                        }
                    }
                }
            }

            if (valTrigEvt("alarm")) {
                section ("${getAlarmSystemName()} (${getAlarmSystemName(true)}) Events", hideable: true) {
                    input "trig_alarm", "enum", title: "${getAlarmSystemName()} Modes", options: getAlarmTrigOpts(), multiple: true, required: true, submitOnChange: true, image: getAppImg("alarm_home")
                    // if("alerts" in trig_alarm) {
                    //     input "trig_alarm_alerts_clear", "bool", title: "Send the update when Alerts are cleared.", required: false, defaultValue: false, submitOnChange: true
                    // }
                }
            }

            if (valTrigEvt("mode")) {
                section ("Mode Events", hideable: true) {
                    input "trig_mode", "mode", title: "Location Modes", multiple: true, required: true, submitOnChange: true, image: getAppImg("mode")
                    if(settings?.trig_mode) {
                        input "trig_mode_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                    }
                }
            }

            if(valTrigEvt("routineExecuted")) {
                section("Routine Events", hideable: true) {
                    input "trig_routineExecuted", "enum", title: "Routines", options: stRoutines, multiple: true, required: true, submitOnChange: true, image: getAppImg("routine")
                    if(settings?.trig_routineExecuted) {
                        input "trig_routineExecuted_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                    }
                }
            }

            if(valTrigEvt("scene")) {
                section("Scene Events", hideable: true) {
                    input "trig_scene", "device.sceneActivator", title: "Scene Devices", multiple: true, required: true, submitOnChange: true, image: getAppImg("routine")
                    if(settings?.trig_scene) {
                        input "trig_scene_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                    }
                }
            }

            if (valTrigEvt("weather")) {
                section ("Weather Events", hideable: true) {
                    paragraph "Weather Events are not configured to take actions yet.", state: null, image: getAppImg("weather")
                    //TODO: Buildout weather alerts
                    // input "trig_WeatherAlert", "enum", title: "Weather Alerts", required: false, multiple: true, submitOnChange: true, image: getAppImg("weather"),
                    //         options: [
                    //             "TOR":	"Tornado Warning",
                    //             "TOW":	"Tornado Watch",
                    //             "WRN":	"Severe Thunderstorm Warning",
                    //             "SEW":	"Severe Thunderstorm Watch",
                    //             "WIN":	"Winter Weather Advisory",
                    //             "FLO":	"Flood Warning",
                    //             "WND":	"High Wind Advisoryt",
                    //             "HEA":	"Heat Advisory",
                    //             "FOG":	"Dense Fog Advisory",
                    //             "FIR":	"Fire Weather Advisory",
                    //             "VOL":	"Volcanic Activity Statement",
                    //             "HWW":	"Hurricane Wind Warning"
                    //         ]
                    // input "trig_WeatherHourly", "enum", title: "Hourly Weather Forecast Updates", required: false, multiple: false, submitOnChange: true, options: ["Weather Condition Changes", "Chance of Precipitation Changes", "Wind Speed Changes", "Humidity Changes", "Any Weather Updates"], image: "blank"
                    // input "trig_WeatherEvents", "enum", title: "Weather Elements", required: false, multiple: false, submitOnChange: true, options: ["Chance of Precipitation (in/mm)", "Wind Gust (MPH/kPH)", "Humidity (%)", "Temperature (F/C)"], image: "blank"
                    // if (settings?.trig_WeatherEvents) {
                    //     input "trig_WeatherEventsCond", "enum", title: "Notify when Weather Element changes...", options: ["above", "below"], required: false, submitOnChange: true, image: getAppImg("trigger")
                    // }
                    // if (settings?.trig_WeatherEventsCond) {
                    //     input "trig_WeatherThreshold", "decimal", title: "Weather Variable Threshold...", required: false, submitOnChange: true, image: getAppImg("trigger")
                    //     if (settings?.trig_WeatherThreshold) {
                    //         input "trig_WeatherCheckSched", "enum", title: "How Often to Check for Weather Changes...", required: true, multiple: false, submitOnChange: true, image: getPublicImg("day_calendar2"),
                    //             options: [
                    //                 "runEvery1Minute": "Every Minute",
                    //                 "runEvery5Minutes": "Every 5 Minutes",
                    //                 "runEvery10Minutes": "Every 10 Minutes",
                    //                 "runEvery15Minutes": "Every 15 Minutes",
                    //                 "runEvery30Minutes": "Every 30 Minutes",
                    //                 "runEvery1Hour": "Every Hour",
                    //                 "runEvery3Hours": "Every 3 Hours"
                    //             ]
                    //     }
                    // }
                }
            }

            if (valTrigEvt("switch")) {
                section ("Outlets, Switches", hideable: true) {
                    input "trig_switch", "capability.switch", title: "Outlets/Switches", multiple: true, submitOnChange: true, required: true, image: getAppImg("switch")
                    if (settings?.trig_switch) {
                        input "trig_switch_cmd", "enum", title: "are turned...", options:["on", "off", "any"], multiple: false, required: true, submitOnChange: true, image: getAppImg("command")
                        if (settings?.trig_switch?.size() > 1 && settings?.trig_switch_cmd && settings?.trig_switch_cmd != "any") {
                            input "trig_switch_all", "bool", title: "Require ALL Switches to be (${settings?.trig_switch_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                        }
                        input "trig_switch_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                        input "trig_switch_wait", "number", title: "Wait between each report", required: false, defaultValue: null, submitOnChange: true, image: getAppImg("delay_time")
                    }
                }
            }

            if (valTrigEvt("level")) {
                section ("Dimmers/Levels", hideable: true) {
                    input "trig_level", "capability.switchLevel", title: "Dimmers/Level", multiple: true, submitOnChange: true, required: true, image: getPublicImg("speed_knob")
                    if(settings?.trig_level) {
                        input "trig_level_cmd", "enum", title: "Level is...", options: ["between", "below", "above", "equals"], required: true, multiple: false, submitOnChange: true, image: getAppImg("command")
                        if (settings?.trig_level_cmd) {
                            if (settings?.trig_level_cmd in ["between", "below"]) {
                                input "trig_level_low", "number", title: "a ${trig_level_cmd == "between" ? "Low " : ""}Level of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_level_cmd in ["between", "above"]) {
                                input "trig_level_high", "number", title: "${trig_level_cmd == "between" ? "and a high " : "a "}Level of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_level_cmd == "equals") {
                                input "trig_level_equal", "number", title: "a Level of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_level?.size() > 1) {
                                input "trig_level_all", "bool", title: "Require ALL devices to be (${settings?.trig_level_cmd}) values?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                            }
                            input "trig_level_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                            input "trig_level_wait", "number", title: "Wait between each report", required: false, defaultValue: 120, submitOnChange: true, image: getAppImg("delay_time")
                        }
                    }
                }
            }

            if (valTrigEvt("motion")) {
                section ("Motion Sensors", hideable: true) {
                    input "trig_motion", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: true, submitOnChange: true, image: getAppImg("motion")
                    if (settings?.trig_motion) {
                        input "trig_motion_cmd", "enum", title: "become...", options: ["active", "inactive", "any"], multiple: false, required: true, submitOnChange: true, image: getAppImg("command")
                        if (settings?.trig_motion?.size() > 1 && settings?.trig_motion_cmd && settings?.trig_motion_cmd != "any") {
                            input "trig_motion_all", "bool", title: "Require ALL Motion Sensors to be (${settings?.trig_motion_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                        }
                        input "trig_motion_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                        input "trig_motion_wait", "number", title: "Wait between each report", required: false, defaultValue: null, submitOnChange: true, image: getAppImg("delay_time")
                    }
                }
            }

            if (valTrigEvt("presence")) {
                section ("Presence Events", hideable: true) {
                    input "trig_presence", "capability.presenceSensor", title: "Presence Sensors", multiple: true, required: true, submitOnChange: true, image: getAppImg("presence")
                    if (settings?.trig_presence) {
                        input "trig_presence_cmd", "enum", title: "changes to?", options: ["present", "not present", "any"], multiple: false, required: true, submitOnChange: true, image: getAppImg("command")
                        if (settings?.trig_presence?.size() > 1 && settings?.trig_presence_cmd && settings?.trig_presence_cmd != "any") {
                            input "trig_presence_all", "bool", title: "Require ALL Presence Sensors to be (${settings?.trig_presence_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                        }
                        input "trig_presence_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                        input "trig_presence_wait", "number", title: "Wait between each report", required: false, defaultValue: null, submitOnChange: true, image: getAppImg("delay_time")
                    }
                }
            }

            if (valTrigEvt("contact")) {
                section ("Contacts, Doors, Windows", hideable: true) {
                    input "trig_contact", "capability.contactSensor", title: "Contacts, Doors, Windows", multiple: true, required: true, submitOnChange: true, image: getAppImg("contact")
                    if (settings?.trig_contact) {
                        input "trig_contact_cmd", "enum", title: "changes to?", options: ["open", "closed", "any"], multiple: false, required: true, submitOnChange: true, image: getAppImg("command")
                        if (settings?.trig_contact?.size() > 1 && settings?.trig_contact_cmd && settings?.trig_contact_cmd != "any") {
                            input "trig_contact_all", "bool", title: "Require ALL Contact to be (${settings?.trig_contact_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                        }
                        input "trig_contact_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                        input "trig_contact_wait", "number", title: "Wait between each report", required: false, defaultValue: null, submitOnChange: true, image: getAppImg("delay_time")
                    }
                }
            }

            if (valTrigEvt("door")) {
                section ("Garage Door Openers", hideable: true) {
                    input "trig_door", "capability.garageDoorControl", title: "Garage Doors", multiple: true, required: true, submitOnChange: true, image: getAppImg("garage_door")
                    if (settings?.trig_door) {
                        input "trig_door_cmd", "enum", title: "changes to?", options: ["open", "closed", "opening", "closing", "any"], multiple: false, required: true, submitOnChange: true, image: getAppImg("command")
                        if (settings?.trig_door?.size() > 1 && trig_door_cmd && (trig_door_cmd == "open" || trig_door_cmd == "close")) {
                            input "trig_door_all", "bool", title: "Require ALL Garage Doors to be (${settings?.trig_door_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                        }
                        input "trig_door_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                        input "trig_door_wait", "number", title: "Wait between each report", required: false, defaultValue: null, submitOnChange: true, image: getAppImg("delay_time")
                    }
                }
            }

            if (valTrigEvt("lock")) {
                section ("Locks", hideable: true) {
                    input "trig_lock", "capability.lock", title: "Smart Locks", multiple: true, required: true, submitOnChange: true, image: getAppImg("lock")
                    if (settings?.trig_lock) {
                        input "trig_lock_cmd", "enum", title: "changes to?", options: ["locked", "unlocked", "any"], multiple: false, required: true, submitOnChange: true, image: getAppImg("command")
                        if (settings?.trig_lock?.size() > 1 && settings?.trig_lock_cmd && settings?.trig_lock_cmd != "any") {
                            input "trig_lock_all", "bool", title: "Require ALL Locks to be (${settings?.trig_lock_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                        }
                        input "trig_lock_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                        input "trig_lock_wait", "number", title: "Wait between each report", required: false, defaultValue: null, submitOnChange: true, image: getAppImg("delay_time")

                    }
                }
            }

            // if ("Keypads" in settings?.triggerEvents) {
            //     section ("Keypads", hideable: true) {
            //         input "trig_Keypads", "capability.lockCodes", title: "Select Keypads", multiple: true, required: true, submitOnChange: true, image: getPublicImg("door_control")
            //         if (settings?.trig_Keypads) {
            //             input "trig_KeyCode", "number", title: "Code (4 digits)", required: true, submitOnChange: true
            //             input "trig_KeyButton", "enum", title: "Which button?", options: ["on":"On", "off":"Off", "partial":"Partial", "panic":"Panic"], multiple: false, required: true, submitOnChange: true
            //         }
            //     }
            // }

            if (valTrigEvt("temperature")) {
                section ("Temperature Sensor Events", hideable: true) {
                    input "trig_temperature", "capability.temperatureMeasurement", title: "Temperature", required: true, multiple: true, submitOnChange: true, image: getPublicImg("temperature")
                    if(trig_temperature) {
                        input "trig_temperature_cmd", "enum", title: "Temperature is...", options: ["between", "below", "above", "equals"], required: true, multiple: false, submitOnChange: true, image: getAppImg("command")
                        if (settings?.trig_temperature_cmd) {
                            if (settings?.trig_temperature_cmd in ["between", "below"]) {
                                input "trig_temperature_low", "number", title: "a ${trig_temperature_cmd == "between" ? "Low " : ""}Temperature of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_temperature_cmd in ["between", "above"]) {
                                input "trig_temperature_high", "number", title: "${trig_temperature_cmd == "between" ? "and a high " : "a "}Temperature of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_temperature_cmd == "equals") {
                                input "trig_temperature_equal", "number", title: "a Temperature of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_temperature?.size() > 1) {
                                input "trig_temperature_all", "bool", title: "Require ALL devices to be (${settings?.trig_temperature_cmd}) values?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                            }
                            input "trig_temperature_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                            input "trig_temperature_wait", "number", title: "Wait between each report", required: false, defaultValue: 120, submitOnChange: true, image: getAppImg("delay_time")
                        }
                    }
                }
            }

            if (valTrigEvt("humidity")) {
                section ("Humidity Sensor Events", hideable: true) {
                    input "trig_humidity", "capability.relativeHumidityMeasurement", title: "Relative Humidity", required: true, multiple: true, submitOnChange: true, image: getPublicImg("humidity")
                    if (settings?.trig_humidity) {
                        input "trig_humidity_cmd", "enum", title: "Relative Humidity (%) is...", options: ["between", "above", "below", "equals"], required: false, submitOnChange: true, image: getAppImg("command")
                        if(settings?.trig_humidity_cmd) {
                            if (settings?.trig_humidity_cmd in ["between", "below"]) {
                                input "trig_humidity_low", "number", title: "a ${trig_power_cmd == "between" ? "Low " : ""}Relative Humidity (%) of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_humidity_cmd in ["between", "above"]) {
                                input "trig_humidity_high", "number", title: "${trig_power_cmd == "between" ? "and a high " : "a "}Relative Humidity (%) of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_humidity_cmd == "equals") {
                                input "trig_humidity_equal", "number", title: "a Relative Humidity (%) of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_humidity?.size() > 1) {
                                input "trig_humidity_all", "bool", title: "Require ALL devices to be (${settings?.trig_humidity_cmd}) values?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                            }
                            input "trig_humidity_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                            input "trig_humidity_wait", "number", title: "Wait between each report", required: false, defaultValue: 120, submitOnChange: true, image: getAppImg("delay_time")
                        }
                    }
                }
            }

            // if ("Acceleration" in settings?.triggerEvents) {
            //     section ("Acceleration Sensor Events", hideable: true) {
            //         input "trig_Acceleration", "capability.accelerationSensor", title: "Acceleration Sensors", required: true, multiple: true, submitOnChange: true, image: getPublicImg("humidity")
            //         if (settings?.trig_Acceleration) {
            //             input "trig_AccelerationCond", "enum", title: "Relative Humidity (%) is...", options: ["active", "inactive", "any"], required: false, submitOnChange: true, image: getAppImg("command")
            //             if (settings?.trig_Acceleration?.size() > 1 && settings?.trig_AccelerationCmd && settings?.trig_AccelerationCmd != "any") {
            //                 input "trig_AccelerationAll", "bool", title: "Require ALL Acceleration Sensors to be (${settings?.trig_AccelerationCmd})?", required: false, defaultValue: false, submitOnChange: true
            //             }
            //         }
            //     }
            // }

            if (valTrigEvt("water") in settings?.triggerEvents) {
                section ("Water Sensor Events", hideable: true) {
                    input "trig_water", "capability.waterSensor", title: "Water/Moisture Sensors", required: true, multiple: true, submitOnChange: true, image: getPublicImg("water")
                    if (settings?.trig_water) {
                        input "trig_water_cmd", "enum", title: "changes to?", options: ["wet", "dry", "any"], required: false, submitOnChange: true, image: getAppImg("command")
                        if (settings?.trig_water?.size() > 1 && settings?.trig_water_cmd && settings?.trig_water_cmd != "any") {
                            input "trig_water_all", "bool", title: "Require ALL Sensors to be (${settings?.trig_water_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                        }
                        input "trig_water_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                        input "trig_water_wait", "number", title: "Wait between each report", required: false, defaultValue: null, submitOnChange: true, image: getAppImg("delay_time")
                    }
                }
            }

            if (valTrigEvt("power")) {
                section ("Power Events", hideable: true) {
                    input "trig_power", "capability.powerMeter", title: "Power Meters", required: true, multiple: true, submitOnChange: true, image: getPublicImg("power")
                    if (settings?.trig_power) {
                        input "trig_power_cmd", "enum", title: "Power Level (W) is...", options: ["between", "below", "above", "equals"], required: true, multiple: false, submitOnChange: true, image: getAppImg("command")
                        if (settings?.trig_power_cmd) {
                            if (settings?.trig_power_cmd in ["between", "below"]) {
                                input "trig_power_low", "number", title: "a ${trig_power_cmd == "between" ? "Low " : ""}Power Level (W) of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_power_cmd in ["between", "above"]) {
                                input "trig_power_high", "number", title: "${trig_power_cmd == "between" ? "and a high " : "a "}Power Level (W) of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_power_cmd == "equals") {
                                input "trig_power_equal", "number", title: "a Power Level (W) of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_power?.size() > 1) {
                                input "trig_power_all", "bool", title: "Require ALL devices to be (${settings?.trig_power_cmd}) values?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                            }
                            input "trig_power_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                            input "trig_power_wait", "number", title: "Wait between each alert", required: false, defaultValue: 120, submitOnChange: true, image: getAppImg("delay_time")
                        }
                    }
                }
            }

            if (valTrigEvt("fire")) {
                // section ("CO\u00B2 Events", hideable: true) {
                //     input "trig_CO2", "capability.carbonMonoxideDetector", title: "Carbon Dioxide (CO\u00B2)", required: !(settings?.trig_smoke), multiple: true, submitOnChange: true, image: getPublicImg("co2_warn_status")
                //     if (settings?.trig_CO2) {
                //         input "trig_CO2Cmd", "enum", title: "changes to?", options: ["above", "below", "equals"], required: false, submitOnChange: true, image: getAppImg("command")
                //         if (settings?.trig_CO2Cmd) {
                //             input "trig_CO2Level", "number", title: "CO\u00B2 Level...", required: true, description: "number", submitOnChange: true
                //             input "trig_CO2Once", "bool", title: "Perform this check only once", required: false, defaultValue: false, submitOnChange: true
                //         }
                //     }
                // }
                section ("Carbon Monoxide Events", hideable: true) {
                    input "trig_carbonMonoxide", "capability.carbonMonoxideDetector", title: "Carbon Monoxide Sensors", required: !(settings?.trig_smoke), multiple: true, submitOnChange: true
                    if (settings?.trig_carbonMonoxide) {
                        input "trig_carbonMonoxide_cmd", "enum", title: "changes to?", options: ["detected", "clear", "any"], required: false, submitOnChange: true, image: getAppImg("command")
                        if (settings?.trig_carbonMonoxide?.size() > 1 && settings?.trig_carbonMonoxide_cmd && settings?.trig_carbonMonoxide_cmd != "any") {
                            input "trig_carbonMonoxide_all", "bool", title: "Require ALL Smoke Detectors to be (${settings?.trig_carbonMonoxide_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                        }
                    }
                }

                section ("Smoke Events", hideable: true) {
                    input "trig_smoke", "capability.smokeDetector", title: "Smoke Detectors", required: !(settings?.trig_carbonMonoxide), multiple: true, submitOnChange: true
                    if (settings?.trig_smoke) {
                        input "trig_smoke_cmd", "enum", title: "changes to?", options: ["detected", "clear", "any"], required: false, submitOnChange: true, image: getAppImg("command")
                        if (settings?.trig_smoke?.size() > 1 && settings?.trig_smoke_cmd && settings?.trig_smoke_cmd != "any") {
                            input "trig_smoke_all", "bool", title: "Require ALL Smoke Detectors to be (${settings?.trig_smoke_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                        }
                    }
                }
            }

            if (valTrigEvt("illuminance")) {
                section ("Illuminance Events", hideable: true) {
                    input "trig_illuminance", "capability.illuminanceMeasurement", title: "Lux Level", required: true, submitOnChange: true
                    if (settings?.trig_illuminance) {
                        input "trig_illuminance_cmd", "enum", title: "Power Level (W) is...", options: ["between", "below", "above", "equals"], required: true, multiple: false, submitOnChange: true, image: getAppImg("command")
                        if (settings?.trig_illuminance_cmd) {
                            if (settings?.trig_illuminance_cmd in ["between", "below"]) {
                                input "trig_illuminance_low", "number", title: "a ${trig_power_cmd == "between" ? "Low " : ""}Power Level (W) of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_illuminance_cmd in ["between", "above"]) {
                                input "trig_illuminance_high", "number", title: "${trig_power_cmd == "between" ? "and a high " : "a "}Power Level (W) of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_illuminance_cmd == "equals") {
                                input "trig_illuminance_equal", "number", title: "a Power Level (W) of...", required: true, submitOnChange: true
                            }
                            if (settings?.trig_illuminance?.size() > 1) {
                                input "trig_illuminance_all", "bool", title: "Require ALL devices to be (${settings?.trig_illuminance_cmd}) values?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                            }
                            input "trig_illuminance_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                            input "trig_illuminance_wait", "number", title: "Wait between each alert", required: false, defaultValue: 120, submitOnChange: true, image: getAppImg("delay_time")
                        }
                    }
                }
            }

            if (valTrigEvt("shade")) {
                section ("Window Shades", hideable: true) {
                    input "trig_shade", "capability.windowShades", title: "Window Shades", multiple: true, required: true, submitOnChange: true, image: getPublicImg("window_shade")
                    if (settings?.trig_shade) {
                        input "trig_shade_cmd", "enum", title: "changes to?", options:["open", "closed", "any"], multiple: false, required: true, submitOnChange:true, image: getAppImg("command")
                        if (settings?.trig_shade?.size() > 1 && settings?.trig_shade_cmd && settings?.trig_shade_cmd != "any") {
                            input "trig_shade_all", "bool", title: "Require ALL Window Shades to be (${settings?.trig_shade_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                        }
                        input "trig_shade_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                        input "trig_shade_wait", "number", title: "Wait between each report", required: false, defaultValue: 120, submitOnChange: true, image: getAppImg("delay_time")
                    }
                }
            }

            if (valTrigEvt("valve")) {
                section ("Valves", hideable: true) {
                    input "trig_valve", "capability.valve", title: "Valves", multiple: true, required: true, submitOnChange: true, image: getPublicImg("valve")
                    if (settings?.trig_valve) {
                        input "trig_valve_cmd", "enum", title: "changes to?", options:["open", "closed", "any"], multiple: false, required: true, submitOnChange:true, image: getAppImg("command")
                        if (settings?.trig_valve?.size() > 1 && settings?.trig_valve_cmd && settings?.trig_valve_cmd != "any") {
                            input "trig_valve_all", "bool", title: "Require ALL Valves to be (${settings?.trig_valve_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                        }
                        input "trig_valve_once", "bool", title: "only alert once a day?\n(per device)", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("question")
                        input "trig_valve_wait", "number", title: "Wait between each report", required: false, defaultValue: 120, submitOnChange: true, image: getAppImg("delay_time")
                    }
                }
            }
        }
        state?.showSpeakEvtVars = showSpeakEvtVars
    }
}

Boolean scheduleTriggers() {
    return (settings?.trig_scheduled_time || settings?.trig_scheduled_sunState)
}

Boolean locationTriggers() {
    return (settings?.trig_mode || settings?.trig_alarm || settings?.trig_routineExecuted || settings?.trig_scene)
}

Boolean deviceTriggers() {
    return (settings?.trig_Buttons || (settings?.trig_shade && settings?.trig_shade_cmd) || (settings?.trig_door && settings?.trig_door_cmd) || (settings?.trig_valve && settings?.trig_valve_cmd) ||
            (settings?.trig_switch && settings?.trig_switch_cmd) || (settings?.trig_level && settings?.trig_level_cmd) || (settings?.trig_lock && settings?.trig_lock_cmd))
}

Boolean sensorTriggers() {
    return (
        (settings?.trig_temperature && settings?.trig_temperature_cmd) || (settings?.trig_carbonMonoxide && settings?.trig_carbonMonoxide_cmd) || (settings?.trig_humidity && settings?.trig_humidity_cmd) ||
        (settings?.trig_water && settings?.trig_water_cmd) || (settings?.trig_smoke && settings?.trig_smoke_cmd) || (settings?.trig_presence && settings?.trig_presence_cmd) || (settings?.trig_motion && settings?.trig_motion_cmd) ||
        (settings?.trig_contact && settings?.trig_contact_cmd) || (settings?.trig_power && settings?.trig_power_cmd) || (settings?.trig_illuminance && settings?.trig_illuminance_low && settings?.trig_illuminance_high)
    )
}

Boolean weatherTriggers() {
    return (settings?.trig_Weather || settings?.myWeather || settings?.myWeatherAlert)
}

Boolean triggersConfigured() {
    Boolean sched = scheduleTriggers()
    Boolean loc = locationTriggers()
    Boolean dev = deviceTriggers()
    Boolean sen = sensorTriggers()
    Boolean weath = weatherTriggers()
    // log.debug "sched: $sched | loc: $loc | dev: $dev | sen: $sen | weath: $weath"
    return (sched || loc || dev || sen || weath)
}

/******************************************************************************
    CONDITIONS SELECTION PAGE
******************************************************************************/

def conditionsPage() {
    return dynamicPage(name: "conditionsPage", title: "Only when these device, location conditions are True...", install: false, uninstall: false) {
        section("Time/Date") {
            href "condTimePage", title: "Time Schedule", description: getTimeCondDesc(false), state: (timeCondConfigured() ? "complete" : null), image: getPublicImg("clock")
            input "cond_days", "enum", title: "Days of the week", multiple: true, required: false, submitOnChange: true, options: weekDaysEnum(), image: getPublicImg("day_calendar")
            input "cond_months", "enum", title: "Months of the year", multiple: true, required: false, submitOnChange: true, options: monthEnum(), image: getPublicImg("day_calendar")
        }

        section ("Location Conditions") {
            input "cond_mode", "mode", title: "Location Mode is...", multiple: true, required: false, submitOnChange: true, image: getAppImg("mode")
            input "cond_alarm", "enum", title: "${getAlarmSystemName()} is...", options: getAlarmTrigOpts(), multiple: false, required: false, submitOnChange: true, image: getAppImg("alarm_home")
        }

        section ("Switches/Outlets Conditions") {
            input "cond_switch", "capability.switch", title: "Switches/Outlets", multiple: true, submitOnChange: true, required:false, image: getAppImg("switch")
            if (settings?.cond_switch) {
                input "cond_switch_cmd", "enum", title: "are...", options:["on","off"], multiple: false, required: true, submitOnChange: true, image: getAppImg("command")
                if (settings?.cond_switch?.size() > 1 && settings?.cond_switch_cmd) {
                    input "cond_switch_all", "bool", title: "ALL Switches must be (${settings?.cond_switch_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                }
            }
        }
        section ("Motion and Presence Conditions") {
            input "cond_motion", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false, submitOnChange: true, image: getAppImg("motion")
            if (settings?.cond_motion) {
                input "cond_motion_cmd", "enum", title: "are...", options: ["active", "inactive"], multiple: false, required: true, submitOnChange: true, image: getAppImg("command")
                if (settings?.cond_motion?.size() > 1 && settings?.cond_motion_cmd) {
                    input "cond_motion_all", "bool", title: "ALL Motion Sensors must be (${settings?.cond_motion_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                }
            }

            input "cond_presence", "capability.presenceSensor", title: "Presence Sensors", multiple: true, required: false, submitOnChange: true, image: getAppImg("presence")
            if (settings?.cond_presence) {
                input "cond_presence_cmd", "enum", title: "are...", options: ["present", "not present"], multiple: false, required: true, submitOnChange: true, image: getAppImg("command")
                if (settings?.cond_presence?.size() > 1 && settings?.cond_presence_cmd) {
                    input "cond_presence_all", "bool", title: "Presence Sensors must be (${settings?.cond_presence_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                }
            }
        }
        section ("Door, Window, Contact Sensors Conditions") {
            input "cond_contact", "capability.contactSensor", title: "Contact Sensors", multiple: true, required: false, submitOnChange: true, image: getAppImg("contact")
            if (settings?.cond_contact) {
                input "cond_contact_cmd", "enum", title: "that are...", options: ["open","closed"], multiple: false, required: true, submitOnChange: true, image: getAppImg("command")
                if (settings?.cond_contact?.size() > 1 && settings?.cond_contact_cmd) {
                    input "cond_contact_all", "bool", title: "ALL Contacts must be (${settings?.cond_contact_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                }
            }
        }

        section ("Garage Door and Lock Conditions") {
            input "cond_lock", "capability.lock", title: "Smart Locks", multiple: true, required: false, submitOnChange: true, image: getAppImg("lock")
            if (settings?.cond_lock) {
                input "cond_lock_cmd", "enum", title: "are...", options:["locked", "unlocked"], multiple: false, required: true, submitOnChange:true, image: getAppImg("command")
                if (settings?.cond_lock?.size() > 1 && settings?.cond_lock_cmd) {
                    input "cond_lock_all", "bool", title: "ALL Locks must be (${settings?.cond_lock_cmd})?", required: false, defaultValue: false, submitOnChange: true
                }
            }
            input "cond_door", "capability.garageDoorControl", title: "Garage Doors", multiple: true, required: false, submitOnChange: true, image: getAppImg("garage_door")
            if (settings?.cond_door) {
                input "cond_door_cmd", "enum", title: "are...", options:["open", "closed"], multiple: false, required: true, submitOnChange: true, image: getAppImg("command")
                if (settings?.cond_door?.size() > 1 && settings?.cond_door_cmd) {
                    input "cond_door_all", "bool", title: "ALL Garages must be (${settings?.cond_door_cmd})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("checkbox")
                }
            }
        }

        // section ("Environmental Conditions") {
        //     input "cond_Humidity", "capability.relativeHumidityMeasurement", title: "Relative Humidity", required: false, submitOnChange: true, image: getPublicImg("humidity")
        //     if (settings?.cond_Humidity) {
        //         input "cond_Humidity_level", "enum", title: "Only when the Humidity is...", options: ["above", "below", "equal"], required: false, submitOnChange: true, image: getAppImg("command")
        //         if (settings?.cond_Humidity_level) {
        //             input "cond_Humidity_percent", "number", title: "this level...", required: true, description: "percent", submitOnChange: true
        //         }
        //         if (settings?.cond_Humidity_percent && settings?.cond_Humidity_level != "equal") {
        //             input "cond_Humidity_stop", "number", title: "...but not ${settings?.cond_Humidity_level} this percentage", required: false, description: "humidity"
        //         }
        //     }
        //     input "cond_Temperature", "capability.temperatureMeasurement", title: "Temperature", required: false, multiple: true, submitOnChange: true, image: getPublicImg("temperature")
        //     if (settings?.cond_Temperature) {
        //         input "cond_Temperature_level", "enum", title: "When the temperature is...", options: ["above", "below", "equal"], required: false, submitOnChange: true, image: getAppImg("command")
        //         if (settings?.cond_Temperature_level) {
        //             input "cond_Temperature_degrees", "number", title: "Temperature...", required: true, description: "degrees", submitOnChange: true
        //         }
        //         if (settings?.cond_Temperature_degrees && settings?.cond_Temperature_level != "equal") {
        //             input "cond_Temperature_stop", "number", title: "...but not ${settings?.cond_Temperature_level} this temperature", required: false, description: "degrees"
        //         }
        //     }
        // }
    }
}

private Map devsSupportVolume(devs) {
    List noSupport = []
    List supported = []
    if(devs instanceof List && devs?.size()) {
        devs?.each { dev->
            if(dev?.hasAttribute("permissions") && dev?.currentPermissions?.toString()?.contains("volumeControl")) {
                supported?.push(dev?.label)
            } else { noSupport?.push(dev?.label) }
        }
    }
    return [s:supported, n:noSupport]
}

private executeAction(evt = null, frc=false, custText=null, src=null) {
    def startTime = now()
    log.trace "executeAction${src ? "($src)" : ""}${frc ? " | [Forced]" : ""}..."
    Boolean condOk = conditionsOk()
    Boolean actOk = actionsConfigured()
    Map actMap = state?.actionExecMap ?: null
    def actDevices = parent?.getDevicesFromList(settings?.act_EchoDevices)
    String actType = settings?.actionType
    if(actOk && actType) {
        if(!condOk) { log.warn "Skipping Execution because set conditions have not been met"; return; }
        if(!actMap || !actMap?.size()) { log.error "executeAction Error | The ActionExecutionMap is not found or is empty"; return; }
        if(!actDevices?.size()) { log.error "executeAction Error | No Echo Device List is not found or is empty"; return; }
        if(!actMap?.actionType) { log.error "executeAction Error | The ActionType is not found or is empty"; return; }
        Map actConf = actMap?.config
        Integer actDelay = actMap?.delay ?: 0
        Integer actDelayMs = actMap?.delay ? (actMap?.delay*1000) : 0
        Integer changeVol = actMap?.volume?.change ?: null
        Integer restoreVol = actMap?.volume?.restore ?: null
        Integer alarmVol = actMap?.volume?.alarm ?: null

        switch(actType) {
            //Speak Command Logic
            case "speak":
                if(actConf[actType]) {
                    String txt = null
                    if(actConf[actType]?.text) {
                        txt = evt ? (decodeVariables(evt, actConf[actType]?.text)) : actConf[actType]?.text
                    } else {
                        if(evt && custText && actConf[actType]?.evtText) { txt = custText }
                        else { txt = "Invalid Text Received... Please verify Action configuration..." }
                    }
                    if((changeVol || restoreVol)) {
                        actDevices?.each { dev-> dev?.setVolumeSpeakAndRestore(changeVol, txt, restoreVol) }
                    } else {
                        actDevices?.each { dev-> dev?.speak(txt) }
                    }
                    log.debug "Sending Speak Command: (${txt}) to ${actDevices} | Volume: ${changeVol} | Restore Volume: ${restoreVol}"
                }
                break

            //Announcement Command Logic
            case "announcement":
                if(actConf[actType] && actConf[actType]?.text) {
                    String txt = null
                    if(actConf[actType]?.text) {
                        txt = evt ? (decodeVariables(evt, actConf[actType]?.text)) : actConf[actType]?.text
                    } else {
                        if(evt && custText && actConf[actType]?.evtText) { txt = custText }
                        else { txt = "Invalid Text Received... Please verify Action configuration..." }
                    }
                    if(actDevices?.size() > 1 && actConf[actType]?.deviceObjs && actConf[actType]?.deviceObjs?.size()) {
                        //NOTE: Only sends command to first device in the list | We send the list of devices to announce one and then Amazon does all the processing
                        def devJson = new groovy.json.JsonOutput().toJson(actConf[actType]?.deviceObjs)
                        actDevices[0]?.sendAnnouncementToDevices(txt, (app?.getLabel() ?: "Echo Speaks Action"), devJson, changeVol, restoreVol, [delay: actDelayMs])
                        log.debug "Sending Announcement Command: (${txt}) to ${actDevices}${actDelay ? " | Delay: (${actDelay})" : ""}${changeVol ? " | Volume: ${changeVol}" : ""}${restoreVol ? " | Restore Volume: ${restoreVol}" : ""}"
                    } else {
                        actDevices?.each { dev-> dev?.playAnnouncement(txt, (app?.getLabel() ?: "Echo Speaks Action"), changeVol, restoreVol, [delay: actDelayMs]) }
                        log.debug "Sending Announcement Command: (${txt}) to ${actDevices}${actDelay ? " | Delay: (${actDelay})" : ""}${changeVol ? " | Volume: ${changeVol}" : ""}${restoreVol ? " | Restore Volume: ${restoreVol}" : ""}"
                    }
                }
                break

            case "sequence":
                if(actConf[actType] && actConf[actType]?.text) {
                    actDevices?.each { dev-> dev?.executeSequenceCommand(actConf[actType]?.text as String, [delay: actDelayMs]) }
                    log.debug "Sending Sequence Command: (${actConf[actType]?.text}) to ${actDevices}${actDelay ? " | Delay: (${actDelay})" : ""}"
                }
                break

            case "playback":
            case "dnd":
                if(actConf[actType] && actConf[actType]?.cmd) {
                    actDevices?.each { dev-> dev?."${actConf[actType]?.cmd}"([delay: actDelayMs]) }
                    log.debug "Sending ${actType?.toString()?.capitalize()} Command: (${actConf[actType]?.cmd}) to ${actDevices}${actDelay ? " | Delay: (${actDelay})" : ""}"
                }
                break

            case "builtin":
            case "calendar":
            case "weather":
                if(actConf[actType] && actConf[actType]?.cmd) {
                    if(changeVol || restoreVol) {
                        actDevices?.each { dev-> dev?."${actConf[actType]?.cmd}"(changeVolume, restoreVol, [delay: actDelayMs]) }
                        log.debug "Sending ${actType?.toString()?.capitalize()} Command: (${actConf[actType]?.cmd}) to ${actDevices}${actDelay ? " | Delay: (${actDelay})" : ""}${changeVol ? " | Volume: ${changeVol}" : ""}${restoreVol ? " | Restore Volume: ${restoreVol}" : ""}"
                    } else {
                        actDevices?.each { dev-> dev?."${actConf[actType]?.cmd}"([delay: actDelayMs]) }
                        log.debug "Sending ${actType?.toString()?.capitalize()} Command: (${actConf[actType]?.cmd}) to ${actDevices}${actDelay ? " | Delay: (${actDelay})" : ""}"
                    }
                }
                break

            case "alarm":
            case "reminder":
                if(actConf[actType] && actConf[actType]?.cmd && actConf[actType]?.label && actConf[actType]?.date && actConf[actType]?.time) {
                    actDevices?.each { dev-> dev?."${actConf[actType]?.cmd}"(actConf[actType]?.label, actConf[actType]?.date, actConf[actType]?.time, [delay: actDelayMs]) }
                    log.debug "Sending ${actType?.toString()?.capitalize()} Command: (${actConf[actType]?.cmd}) to ${actDevices} | Label: ${actConf[actType]?.label} | Date: ${actConf[actType]?.date} | Time: ${actConf[actType]?.time}"
                }
                break

            case "music":
                if(actConf[actType] && actConf[actType]?.cmd && actConf[actType]?.provider && actConf[actType]?.search) {
                    actDevices?.each { dev-> dev?."${actConf[actType]?.cmd}"(actConf[actType]?.search, convMusicProvider(actConf[actType]?.provider), changeVol, restoreVol, [delay: actDelayMs]) }
                    log.debug "Sending ${actType?.toString()?.capitalize()} | Provider: ${actConf[actType]?.provider} | Search: ${actConf[actType]?.search} | Command: (${actConf[actType]?.cmd}) to ${actDevices}${actDelay ? " | Delay: (${actDelay})" : ""}${changeVol ? " | Volume: ${changeVol}" : ""}${restoreVol ? " | Restore Volume: ${restoreVol}" : ""}"
                }
                break

            case "wakeword":
                if(actConf[actType] && actConf[actType]?.devices && actConf[actType]?.devices?.size()) {
                    actConf[actType]?.devices?.each { d->
                        def aDev = actDevices?.find { it?.id == d?.device }
                        aDev?."${d?.cmd}"(d?.wakeword, [delay: actDelayMs])
                        log.debug "Sending WakeWord: (${d?.wakeword}) | Command: (${d?.cmd}) to ${aDev}${actDelay ? " | Delay: (${actDelay})" : ""}"
                    }
                }
                break

            case "bluetooth":
                if(actConf[actType] && actConf[actType]?.devices && actConf[actType]?.devices?.size()) {
                    actConf[actType]?.devices?.each { d->
                        def aDev = actDevices?.find { it?.id == d?.device }
                        if(d?.cmd == "disconnectBluetooth") {
                            aDev?."${d?.cmd}"([delay: actDelayMs])
                        } else { aDev?."${d?.cmd}"(d?.btDevice, [delay: actDelayMs]) }
                        log.debug "Sending ${d?.cmd} | Bluetooth Device: ${d?.btDevice} to ${aDev}${actDelay ? " | Delay: (${actDelay})" : ""}"
                    }
                }
                break
        }
    }

    log.trace "ExecuteAction Finished | ProcessTime: (${now()-startTime}ms)"
}

def actionsPage() {
    return dynamicPage(name: "actionsPage", title: (settings?.actionType ? "Action | (${settings?.actionType})" : "Actions to perform..."), install: false, uninstall: false) {
        Boolean done = false
        Map actionExecMap = [configured: false]
        Map actionOpts = [
            "speak":"Speak", "announcement":"Announcement", "sequence":"Execute Sequence", "weather":"Weather Report", "playback":"Playback Control",
            "builtin":"Sing, Jokes, Story, etc.", "music":"Play Music", "calendar":"Calendar Events", "alarm":"Create Alarm", "reminder":"Create Reminder", "dnd":"Do Not Disturb",
            "bluetooth":"Bluetooth Control", "wakeword":"Wake Word"
        ]
        section("Configure Actions to Take:", hideable: true, hidden: (settings?.act_EchoDevices?.size())) {
            input "actionType", "enum", title: "Actions Type", description: "", options: actionOpts, multiple: false, required: true, submitOnChange: true, image: getAppImg("list")
        }

        if(actionType) {
            actionExecMap?.actionType = actionType
            actionExecMap?.config = [:]
            List devices = parent?.getDevicesFromList(settings?.act_EchoDevices)
            switch(actionType) {
                case "speak":
                    // TODO: Maybe add a custom text input for every trigger type?!?!?
                    // TODO: Make new Web Link URL icon
                    String ssmlTestUrl = "https://topvoiceapps.com/ssml"
                    String ssmlDocsUrl = "https://developer.amazon.com/docs/custom-skills/speech-synthesis-markup-language-ssml-reference.html"
                    String ssmlSoundsUrl = "https://developer.amazon.com/docs/custom-skills/ask-soundlibrary.html"
                    String ssmlSpeechConsUrl = "https://developer.amazon.com/docs/custom-skills/speechcon-reference-interjections-english-us.html"
                    echoDevicesInputByPerm("TTS")
                    if(settings?.act_EchoDevices) {
                        section("SSML Info:", hideable: true, hidden: true) {
                            paragraph title: "What is SSML?", "SSML allows for changes in tone, speed, voice, emphasis. As well as using MP3, and access to the Sound Library", state: "complete", image: getAppImg("info")
                            href url: ssmlDocsUrl, style: "external", required: false, title: "Amazon SSML Docs", description: "Tap to open browser", image: getPublicImg("www")
                            href url: ssmlSoundsUrl, style: "external", required: false, title: "Amazon Sound Library", description: "Tap to open browser", image: getPublicImg("www")
                            href url: ssmlSpeechConsUrl, style: "external", required: false, title: "Amazon SpeechCons", description: "Tap to open browser", image: getPublicImg("www")
                            href url: ssmlTestUrl, style: "external", required: false, title: "SSML Designer and Tester", description: "Tap to open browser", image: getPublicImg("www")
                        }
                        section("Speech Tips:") {
                            paragraph "To make beep tones use: 'wop, wop, wop' (equals 3 beeps)"
                        }

                        section("Action Config:") {
                            variableDesc()
                            input "act_speak_txt", "text", title: "Enter Text/SSML", description: "If entering SSML make sure to include <speak></speak>", submitOnChange: true, required: false, image: getAppImg("text")
                        }
                        actionVolumeInputs(devices)
                        actionExecMap?.config?.speak = [text: settings?.act_speak_txt, evtText: (state?.showSpeakEvtVars && !settings?.act_speak_txt)]
                        if(state?.showSpeakEvtVars || act_speak_txt) { done = true } else { done = false }
                    } else { done = false }
                    break

                case "announcement":
                    section("Action Description:") {
                        paragraph "Plays a brief tone and speaks the message you define. If you select multiple devices it will be a synchronized broadcast.", state: "complete", image: getAppImg("info")
                    }
                    echoDevicesInputByPerm("announce")
                    if(settings?.act_EchoDevices) {
                        section("Action Config:") {
                            variableDesc()
                            input "act_announcement_txt", "text", title: "Enter Text to announce", submitOnChange: true, required: false, image: getAppImg("text")
                        }
                        actionVolumeInputs(devices)
                        actionExecMap?.config?.announcement = [text: settings?.act_announcement_txt, evtText: (state?.showSpeakEvtVars && !settings?.act_speak_txt)]
                        if(settings?.act_EchoDevices?.size() > 1) {
                            List devObj = []
                            devices?.each { devObj?.push([deviceTypeId: it?.currentValue("deviceType"), deviceSerialNumber: it?.deviceNetworkId?.toString()?.tokenize("|")[2]]) }
                            log.debug "devObj: $devObj"
                            actionExecMap?.config?.announcement?.deviceObjs = devObj
                        }
                        if(state?.showSpeakEvtVars || act_announcement_txt) { done = true } else { done = false }
                    } else { done = false }
                    break

                case "sequence":
                    section("Action Description:") {
                        paragraph "Sequences are a custom command where you can string different alexa actions which are sent to Amazon as a single command.  The command is then processed by amazon sequentially or in parallel.", state: "complete", image: getAppImg("info")
                    }
                    echoDevicesInputByPerm("TTS")
                    if(settings?.act_EchoDevices) {
                        section(sTS("Sequence Options Legend:"), hideable: true, hidden: false) {
                            String str1 = "Sequence Options:"
                            seqItemsAvail()?.other?.sort()?.each { k, v->
                                str1 += "${bulletItem(str1, "${k}${v != null ? "::${v}" : ""}")}"
                            }
                            String str4 = "DoNotDisturb Options:"
                            seqItemsAvail()?.dnd?.sort()?.each { k, v->
                                str4 += "${bulletItem(str4, "${k}${v != null ? "::${v}" : ""}")}"
                            }
                            String str2 = "Music Options:"
                            seqItemsAvail()?.music?.sort()?.each { k, v->
                                str2 += "${bulletItem(str2, "${k}${v != null ? "::${v}" : ""}")}"
                            }
                            String str3 = "Canned TTS Options:"
                            seqItemsAvail()?.speech?.sort()?.each { k, v->
                                def newV = v
                                if(v instanceof List) { newV = ""; v?.sort()?.each { newV += "     ${dashItem(newV, "${it}", true)}"; } }
                                str3 += "${bulletItem(str3, "${k}${newV != null ? "::${newV}" : ""}")}"
                            }
                            paragraph str1, state: "complete"
                            // paragraph str4, state: "complete"
                            paragraph str2, state: "complete"
                            paragraph str3, state: "complete"
                            paragraph "Enter the command in a format exactly like this:\nvolume::40,, speak::this is so silly,, wait::60,, weather,, cannedtts_random::goodbye,, traffic,, amazonmusic::green day,, volume::30\n\nEach command needs to be separated by a double comma `,,` and the separator between the command and value must be command::value.", state: "complete"
                        }
                        section("Action Config:") {
                            input "act_sequence_txt", "text", title: "Enter sequence text", submitOnChange: true, required: false, image: getAppImg("text")
                        }
                        actionExecMap?.config?.sequence = [text: settings?.act_sequence_txt]
                        if(settings?.act_sequence_txt) { done = true } else { done = false }
                    } else { done = false }
                    break

                case "weather":
                    section("Action Description:") {
                        paragraph "Plays a very basic weather report.", state: "complete", image: getAppImg("info")
                    }
                    echoDevicesInputByPerm("TTS")
                    if(settings?.act_EchoDevices) {
                        actionVolumeInputs(devices)
                        done = true
                        actionExecMap?.config?.weather = [cmd: "playWeather"]
                    } else { done = false }
                    break

                case "playback":
                    section("Action Description:") {
                        paragraph "Builtin items are things like Sing a Song, Tell a Joke, Say Goodnight, etc.", state: "complete", image: getAppImg("info")
                    }
                    echoDevicesInputByPerm("mediaPlayer")
                    if(settings?.act_EchoDevices) {
                        Map playbackOpts = [
                            "pause":"Pause", "stop":"Stop", "play": "Play", "nextTrack": "Next Track", "previousTrack":"Previous Track",
                            "mute":"Mute"
                        ]
                        section("Playback Config:") {
                            input "act_playback_cmd", "enum", title: "Select Playback Action", description: "", options: playbackOpts, required: true, submitOnChange: true, image: getAppImg("command")
                        }
                        actionExecMap?.config?.playback = [cmd: settings?.act_playback_cmd]
                        if(settings?.act_playback_cmd) { done = true } else { done = false }
                    } else { done = false }
                    break

                case "builtin":
                    section("Action Description:") {
                        paragraph "Builtin items are things like Sing a Song, Tell a Joke, Say Goodnight, etc.", state: "complete", image: getAppImg("info")
                    }
                    echoDevicesInputByPerm("TTS")
                    if(settings?.act_EchoDevices) {
                        Map builtinOpts = [
                            "playSingASong":"Sing a Song", "playFlashBrief":"Flash Briefing", "playFunFact": "Fun Fact", "playTraffic": "Traffic", "playJoke":"Joke",
                            "playTellStory":"Tell Story", "sayGoodbye": "Say Goodbye", "sayGoodNight": "Say Goodnight", "sayBirthday": "Happy Birthday",
                            "sayCompliment": "Give Compliment", "sayGoodMorning": "Good Morning", "sayWelcomeHome": "Welcome Home"
                        ]
                        section("BuiltIn Speech Config:") {
                            input "act_builtin_cmd", "enum", title: "Select Builtin Speech Type", description: "", options: builtinOpts, required: true, submitOnChange: true, image: getAppImg("command")
                        }
                        actionVolumeInputs(devices)
                        actionExecMap?.config?.builtin = [cmd: settings?.act_builtin_cmd]
                        if(settings?.act_builtin_cmd) { done = true } else { done = false }
                    } else { done = false }
                    break

                case "music":
                    section("Action Description:") {
                        paragraph "Allow playback of various Songs/Radio using any connected music provider", state: "complete", image: getAppImg("info")
                    }
                    echoDevicesInputByPerm("mediaPlayer")
                    if(settings?.act_EchoDevices) {
                        List musicProvs = devices[0]?.hasAttribute("supportedMusic") ? devices[0]?.currentValue("supportedMusic")?.split(",")?.collect { "${it?.toString()?.trim()}"} : []
                        log.debug "Music Providers: ${musicProvs}"
                        if(musicProvs) {
                            section("Music Providers:") {
                                input "act_music_provider", "enum", title: "Select Music Provider", description: "", options: musicProvs, multiple: false, required: true, submitOnChange: true, image: getAppImg("music")
                            }
                            if(settings?.act_music_provider) {
                                if(settings?.act_music_provider == "TuneIn") {
                                    section(sTS("TuneIn Search Results:")) {
                                        paragraph "Enter a search phrase to query TuneIn to help you find the right search term to use in searchTuneIn() command.", state: "complete"
                                        input "tuneinSearchQuery", "text", title: inTS("Enter search phrase for TuneIn", getAppImg("tunein", true)), defaultValue: null, required: false, submitOnChange: true, image: getAppImg("tunein")
                                        if(settings?.tuneinSearchQuery) {
                                            href "searchTuneInResultsPage", title: inTS("View search results!", getAppImg("search", true)), description: "Tap to proceed...", image: getAppImg("search")
                                        }
                                    }
                                }
                                section("Action Config:") {
                                    input "act_music_txt", "text", title: "Enter Music Search text", submitOnChange: true, required: false, image: getAppImg("text")
                                }
                                actionVolumeInputs(devices)
                            }
                        }
                        actionExecMap?.config?.music = [cmd: "searchMusic", provider: settings?.act_music_provider, search: settings?.act_music_txt]
                        if(settings?.act_music_provider && settings?.act_music_txt) { done = true } else { done = false }
                    } else { done = false }
                    break

                case "calendar":
                    section("Action Description:") {
                        paragraph "This will read out events in your calendar (Requires accounts to be configured in the alexa app. Must not have PIN.)", state: "complete", image: getAppImg("info")
                    }
                    echoDevicesInputByPerm("TTS")
                    if(settings?.act_EchoDevices) {
                        section("Action Config:") {
                            input "act_calendar_cmd", "enum", title: "Select Calendar Action", description: "", options: ["playCalendarToday":"Today", "playCalendarTomorrow":"Tomorrow", "playCalendarNext":"Next Events"],
                                    required: true, submitOnChange: true, image: getAppImg("command")
                        }
                        actionVolumeInputs(devices)
                        actionExecMap?.config?.calendar = [cmd: settings?.act_calendar_cmd]
                        if(act_calendar_cmd) { done = true } else { done = false }
                    } else { done = false }
                    break

                case "alarm":
                    //TODO: Offer to remove alarm after event.
                    section("Action Description:") {
                        paragraph "This will allow you to alexa alarms based on triggers", state: "complete", image: getAppImg("info")
                    }
                    echoDevicesInputByPerm("alarms")
                    if(settings?.act_EchoDevices) {
                        section("Action Config:") {
                            input "act_alarm_label", "text", title: "Alarm Label", submitOnChange: true, required: true, image: getAppImg("name_tag")
                            input "act_alarm_date", "text", title: "Alarm Date\n(yyyy-mm-dd)", submitOnChange: true, required: true, image: getAppImg("day_calendar")
                            input "act_alarm_time", "time", title: "Alarm Time", submitOnChange: true, required: true, image: getPublicImg("clock")
                            // input "act_alarm_remove", "bool", title: "Remove Alarm when done", defaultValue: true, submitOnChange: true, required: false, image: getPublicImg("question")
                        }
                        actionVolumeInputs(devices, true)
                        actionExecMap?.config?.alarm = [cmd: "createAlarm", label: settings?.act_alarm_label, date: settings?.act_alarm_date, time: settings?.act_alarm_time, remove: settings?.act_alarm_remove]
                        if(act_alarm_label && act_alarm_date && act_alarm_time) { done = true } else { done = false }
                    } else { done = false }

                    break

                case "reminder":
                    //TODO: Offer to remove reminder after event.
                    section("Action Description:") {
                        paragraph "This will allow you to alexa reminders based on triggers", state: "complete", image: getAppImg("info")
                    }
                    echoDevicesInputByPerm("reminders")
                    if(settings?.act_EchoDevices) {
                        section("Action Config:") {
                            input "act_reminder_label", "text", title: "Reminder Label", submitOnChange: true, required: true, image: getAppImg("name_tag")
                            input "act_reminder_date", "text", title: "Reminder Date\n(yyyy-mm-dd)", submitOnChange: true, required: true, image: getAppImg("day_calendar")
                            input "act_reminder_time", "time", title: "Reminder Time", submitOnChange: true, required: true, image: getPublicImg("clock")
                            // input "act_reminder_remove", "bool", title: "Remove Reminder when done", defaultValue: true, submitOnChange: true, required: false, image: getPublicImg("question")
                        }
                        actionVolumeInputs(devices, true)
                        actionExecMap?.config?.reminder = [cmd: "createReminder", label: settings?.act_reminder_label, date: settings?.act_reminder_date, time: settings?.act_reminder_time, remove: settings?.act_reminder_remove]
                        if(act_reminder_label && act_reminder_date && act_reminder_time) { done = true } else { done = false }
                    } else { done = false }

                    break
                case "dnd":
                    echoDevicesInputByPerm("doNotDisturb")
                    if(settings?.act_EchoDevices) {
                        Map dndOpts = ["doNotDisturbOn":"Enable", "doNotDisturbOff":"Disable"]
                        section("Action Description:") {
                            paragraph "This will allow you to enable/disable Do Not Disturb based on triggers", state: "complete"
                        }
                        section("Action Config:") {
                            input "act_dnd_cmd", "enum", title: "Select Do Not Disturb Action", description: "", options: dndOpts, required: true, submitOnChange: true, image: getAppImg("command")
                        }
                        actionExecMap?.config?.dnd = [cmd: settings?.act_dnd_cmd]
                        if(settings?.act_dnd_cmd) { done = true } else { done = false }
                    } else { done = false }

                    break
                case "wakeword":
                    echoDevicesInputByPerm("wakeWord")
                    if(settings?.act_EchoDevices) {
                        Integer devsCnt = settings?.act_EchoDevices?.size() ?: 0
                        List devsObj = []
                        section("Action Description:") {
                            paragraph "This will allow you to change the Wake Word of your Echo's based on triggers", state: "complete", image: getAppImg("info")
                        }
                        if(devsCnt >= 1) {
                            List wakeWords = devices[0]?.hasAttribute("wakeWords") ? devices[0]?.currentValue("wakeWords")?.replaceAll('"', "")?.split(",") : []
                            // log.debug "WakeWords: ${wakeWords}"
                            devices?.each { cDev->
                                section("${cDev?.getLabel()}:") {
                                    if(wakeWords?.size()) {
                                        paragraph "Current Wake Word: ${cDev?.hasAttribute("alexaWakeWord") ? cDev?.currentValue("alexaWakeWord") : "Unknown"}"
                                        input "act_wakeword_device_${cDev?.id}", "enum", title: "New Wake Word", description: "", options: wakeWords, required: true, submitOnChange: true, image: getAppImg("list")
                                        devsObj?.push([device: cDev?.id, wakeword: settings?."act_wakeword_device_${cDev?.id}", cmd: "setWakeWord"])
                                    } else { paragraph "Oops...\nNo Wake Words have been found!  Please Remove the device from selection.", state: null, required: true }
                                }
                            }
                        }
                        actionExecMap?.config?.wakeword = [ devices: devsObj]
                        def aCnt = settings?.findAll { it?.key?.startsWith("act_wakeword_device_") && it?.value }
                        log.debug "aCnt: ${aCnt} | devsCnt: ${devsCnt}"
                        if(settings?.findAll { it?.key?.startsWith("act_wakeword_device_") && it?.value }?.size() == devsCnt) { done = true } else { done = false }
                    } else { done = false }
                    break
                case "bluetooth":
                    echoDevicesInputByPerm("bluetoothControl")
                    if(settings?.act_EchoDevices) {
                        Integer devsCnt = settings?.act_EchoDevices?.size() ?: 0
                        List devsObj = []
                        section("Action Description:") {
                            paragraph "This will allow you to connect or disconnect bluetooth based on triggers", state: "complete", image: getAppImg("info")
                        }
                        if(devsCnt >= 1) {
                            devices?.each { cDev->
                                List btDevs = cDev?.hasAttribute("btDevicesPaired") ? cDev?.currentValue("btDevicesPaired")?.split(",") : []
                                // log.debug "btDevs: $btDevs"
                                section("${cDev?.getLabel()}:") {
                                    if(btDevs?.size()) {
                                        input "act_bluetooth_device_${cDev?.id}", "enum", title: "BT device to use", description: "", options: btDevs, required: true, submitOnChange: true, image: getAppImg("bluetooth")
                                        input "act_bluetooth_action_${cDev?.id}", "enum", title: "BT action to take", description: "", options: ["connectBluetooth":"connect", "disconnectBluetooth":"disconnect"], required: true, submitOnChange: true, image: getAppImg("command")
                                        devsObj?.push([device: cDev?.id, btDevice: settings?."act_bluetooth_device_${cDev?.id}", cmd: settings?."act_bluetooth_action_${cDev?.id}"])
                                    } else { paragraph "Oops...\nNo Bluetooth devices are paired to this Echo Device!  Please Remove the device from selection.", state: null, required: true }
                                }
                            }
                        }
                        actionExecMap?.config?.bluetooth = [devices: devsObj]
                        if(settings?.findAll { it?.key?.startsWith("act_bluetooth_device_") && it?.value }?.size() == devsCnt &&
                            settings?.findAll { it?.key?.startsWith("act_bluetooth_action_") && it?.value }?.size() == devsCnt) { done = true } else { done = false }
                    } else { done = false }
                    break
            }
            if(done) {
                section("Delay Config:") {
                    input "act_delay", "number", title: "Delay Action in Seconds\n(Optional)", required: false, submitOnChange: true, image: getAppImg("delay_time")
                }
                section("Simulate Action") {
                    paragraph "Run the test to see if the action is what you what to occur"
                    input "actTestRun", "bool", title: "Test this action?", description: "", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("testing")
                    if(actTestRun) { executeActTest() }
                }
                section("") {
                    paragraph "You're all done with this step.  Press Done", state: "complete", image: getAppImg("info")
                }
                actionExecMap?.config?.volume = [change: settings?.act_set_volume, restore: settings?.act_restore_volume, alarm: settings?.act_alarm_volume]
                // devices = parent?.getDevicesFromList(settings?.act_EchoDevices)
                actionExecMap?.delay = settings?.act_delay
                actionExecMap?.configured = true

                //TODO: Add Cleanup of non selected inputs
            } else { actionExecMap = [configured: false] }
        }
        atomicState?.actionExecMap = (done && actionExecMap?.configured == true) ? actionExecMap : [configured: false]
        log.debug "actionExecMap: ${atomicState?.actionExecMap}"

    }
}

def cleanupDevSettings(prefix) {
    List cDevs = settings?.act_EchoDevices
    List sets = settings?.findAll { it?.key?.startsWith(prefix) }?.collect { it?.key as String }
    log.debug "cDevs: $cDevs | sets: $sets"
    List rem = []
    if(sets?.size()) {
        if(cDevs?.size()) {
            cDevs?.each {
                if(!sets?.contains("${prefix}${it}")) {
                    rem?.push("${prefix}${it}")
                }
            }
        } else { rem = rem + sets }
    }
    log.debug "rem: $rem"
    // rem?.each { sI-> if(settings?.containsKey(sI as String)) { settingRemove(sI as String) } }
}

def variableDesc() {
    if(state?.showSpeakEvtVars) {
        paragraph "You are using device/location triggers.\nSo you can choose to leave the text empty and text will be generated for each event."
        String varStr = "You can also use variables with your text"
        varStr += "\n • %type% = Event Type"
        varStr += "\n • %value% = Event Value"
        varStr += "\n • %name% = Event Name"
        varStr += "\n • %date% = Event Date"
        varStr += "\n • %time% = Event Time"
        varStr += "\n • %datetime% = Event Date/Time"
        varStr += "\nContact example: %name% has been %open%"
        paragraph varStr
    }
}

def updateActionExecMap(data) {
    // log.trace "updateActionExecMap..."
    atomicState?.actionExecMap = (data && data?.configured == true) ? data : [configured: false]
    // log.debug "actionExecMap: ${state?.actionExecMap}"
}

Boolean actionsConfigured() {
    Boolean type = (settings?.actionType)
    Boolean opts = (state?.actionExecMap && state?.actionExecMap?.configured == true)
    Boolean devs = (settings?.act_EchoDevices)
    // log.debug "type: $type | Options: $opts | devs: $devs"
    return (type || opts || devs)
}

private echoDevicesInputByPerm(type) {
    List echoDevs = parent?.getChildDevicesByCap(type as String)
    section("Alexa Devices: ") {
        if(echoDevs?.size()) {
            input "act_EchoDevices", "enum", title: "Echo Speaks Device(s) to Use", description: "Select the devices", options: echoDevs?.collectEntries { [(it?.getId()): it?.getLabel()] }?.sort { it?.value }, multiple: true, required: true, submitOnChange: true, image: getAppImg("echo_gen1")
        } else { paragraph "No devices were found with support for ($type)"}
    }
}

private actionVolumeInputs(devices, showAlrmVol=false) {
    if(showAlrmVol) {
        section("Volume Options:") {
            input "act_alarm_volume", "number", title: "Alarm Volume\n(Optional)", range: "0..100", required: false, submitOnChange: true, image: getAppImg("speed_knob")
        }
    } else {
        if(devices && settings?.actionType in ["speak", "announcement", "weather", "builtin", "music", "calendar"]) {
            Map volMap = devsSupportVolume(devices)
            section("Volume Options:") {
                if(volMap?.n?.size() > 0 && volMap?.n?.size() < devices?.size()) { paragraph "Some of the selected devices do not support volume control" }
                else if(devices?.size() == volMap?.n?.size()) { paragraph "Some of the selected devices do not support volume control"; return; }
                input "act_set_volume", "number", title: "Volume Level\n(Optional)", range: "0..100", required: false, submitOnChange: true, image: getAppImg("speed_knob")
                input "act_restore_volume", "number", title: "Restore Volume\n(Optional)", range: "0..100", required: false, submitOnChange: true, image: getAppImg("speed_knob")
            }
        }
    }
}

def condTimePage() {
    return dynamicPage(name:"condTimePage", title: "", uninstall: false) {
        Boolean timeReq = (settings["cond_time_start"] || settings["cond_time_stop"])
        section("Start Time: ") {
            input "cond_time_start_type", "enum", title: "Starting at...", options: ["A specific time", "Sunrise", "Sunset"], required: false , submitOnChange: true, image: getAppImg("start_time")
            if(cond_time_start_type in ["A specific time"]) {
                input "cond_time_start", "time", title: "Start time", required: timeReq, submitOnChange: true, image: getAppImg("start_time")
            } else if(cond_time_start_type in ["Sunrise", "Sunrise"]) {
                input "cond_time_start_offset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false, submitOnChange: true, image: getAppImg("threshold")
            }
        }
        section("Stop Time:") {
            input "cond_time_stop_type", "enum", title: "Stopping at...", options: ["A specific time", "Sunrise", "Sunset"], required: false , submitOnChange: true, image: getAppImg("stop_time")
            if(cond_time_stop_type in ["A specific time"]) {
                input "cond_time_stop", "time", title: "Stop time", required: timeReq, submitOnChange: true, image: getAppImg("stop_time")
            } else if(cond_time_stop_type in ["Sunrise", "Sunrise"]) {
                input "cond_time_stop_offset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false, submitOnChange: true, image: getAppImg("threshold")
            }
        }
    }
}

def uninstallPage() {
    return dynamicPage(name: "uninstallPage", title: "Uninstall", uninstall: true) {
        remove("Remove this Action!", "WARNING!!!", "Last Chance to Stop!\nThis action is not reversible\n\nThis Automation will be removed")
    }
}

Boolean wordInString(String findStr, String fullStr) {
    List parts = fullStr?.split(" ")?.collect { it?.toString()?.toLowerCase() }
    return (findStr in parts)
}

def notifPrefPage() {
    return dynamicPage(name: "notifPrefPage", title: "Notifications", uninstall: false) {
        section ("Push Messages:") {
            input "usePush", "bool", title: "Send Push Notifications...", required: false, defaultValue: false, submitOnChange: true
            input "pushTimeStamp", "bool", title: "Add timestamp to Push Messages...", required: false, defaultValue: false, submitOnChange: true
        }
        section ("Text Messages:", hideWhenEmpty: true) {
            paragraph "To send to multiple numbers separate the number by a comma\nE.g. 8045551122,8046663344"
            input "smsNumbers", "text", title: "Send SMS Text to...", required: false, submitOnChange: true, image: getAppImg("sms_phone")
        }
        section ("Alexa Mobile Notification:") {
            paragraph "This will send a push notification the Alexa Mobile app."
            input "alexaMobileMsg", "text", title: "Send this message to Alexa App", required: false, submitOnChange: true, image: getAppImg("sms_phone")
        }
        section("Pushover Support:") {
            input ("pushoverEnabled", "bool", title: "Use Pushover Integration", required: false, submitOnChange: true, image: getAppImg("pushover_icon"))
            if(settings?.pushoverEnabled == true) {
                def poDevices = parent?.getPushoverDevices()
                if(!poDevices) {
                    parent?.pushover_init()
                    paragraph "If this is the first time enabling Pushover than leave this page and come back if the devices list is empty"
                } else {
                    input "pushoverDevices", "enum", title: "Select Pushover Devices", description: "Tap to select", groupedOptions: poDevices, multiple: true, required: false, submitOnChange: true, image: getAppImg("select_icon")
                    if(settings?.pushoverDevices) {
                        def t0 = [(-2):"Lowest", (-1):"Low", 0:"Normal", 1:"High", 2:"Emergency"]
                        input "pushoverPriority", "enum", title: "Notification Priority (Optional)", description: "Tap to select", defaultValue: 0, required: false, multiple: false, submitOnChange: true, options: t0, image: getAppImg("priority")
                        input "pushoverSound", "enum", title: "Notification Sound (Optional)", description: "Tap to select", defaultValue: "pushover", required: false, multiple: false, submitOnChange: true, options: parent?.getPushoverSounds(), image: getAppImg("sound")
                    }
                }
                // } else { paragraph "New Install Detected!!!\n\n1. Press Done to Finish the Install.\n2. Goto the Automations Tab at the Bottom\n3. Tap on the SmartApps Tab above\n4. Select ${app?.getLabel()} and Resume configuration", state: "complete" }
            }
        }
        if(settings?.smsNumbers?.toString()?.length()>=10 || settings?.usePush || (settings?.pushoverEnabled && settings?.pushoverDevices)) {
            if((settings?.usePush || (settings?.pushoverEnabled && settings?.pushoverDevices)) && !state?.pushTested && state?.pushoverManager) {
                if(sendMsg("Info", "Push Notification Test Successful. Notifications Enabled for ${app?.label}", true)) {
                    state.pushTested = true
                }
            }
            section("Notification Restrictions:") {
                def t1 = getNotifSchedDesc()
                href "setNotificationTimePage", title: "Notification Restrictions", description: (t1 ?: "Tap to configure"), state: (t1 ? "complete" : null), image: getAppImg("restriction")
            }
        } else { state.pushTested = false }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state?.isInstalled = true
    state?.setupComplete = true
    if(settings?.appLbl && app?.getLabel() != "${settings?.appLbl} (Action)") { app?.updateLabel("${settings?.appLbl} (Action)") }
    runIn(3, "actionCleanup")
    runIn(7, "subscribeToEvts")
    appCleanup()
}

private appCleanup() {
    // State Cleanup
    List items = []
    items?.each { si-> if(state?.containsKey(si as String)) { state?.remove(si)} }
    // Settings Cleanup
    List setItems = ["tuneinSearchQuery", "performBroadcast", "performMusicTest"]
    settings?.each { si-> if(si?.key?.startsWith("broadcast") || si?.key?.startsWith("musicTest") || si?.key?.startsWith("announce") || si?.key?.startsWith("sequence") || si?.key?.startsWith("speechTest")) { setItems?.push(si?.key as String) } }
    // Performs the Setting Removal
    setItems?.each { sI-> if(settings?.containsKey(sI as String)) { settingRemove(sI as String) } }
}

private actionCleanup() {
    //Cleans up unused action setting items
    List setItems = []
    List setIgn = ["act_delay", "act_set_volume", "act_restore_volume", "act_EchoDevices"]
    if(settings?.actionType) { settings?.each { si-> if(si?.key?.startsWith("act_") && !si?.key?.startsWith("act_${settings?.actionType}") && !(si?.key in setIgn)) { setItems?.push(si?.key as String) } } }
    // if(settings?.actionType in ["bluetooth", "wakeword"]) { cleanupDevSettings("act_${settings?.actionType}_device_") }
    // TODO: Cleanup unselected trigger types
    // Performs the Setting Removal
    setItems?.each { sI-> if(settings?.containsKey(sI as String)) { settingRemove(sI as String) } }
}

public triggerInitialize() {
    runIn(3, "initialize")
}

Boolean isPaused() {
    return (settings?.actionPause == true)
}

private valTrigEvt(key) {
    return (key in settings?.triggerEvents)
}

def scheduleTrigEvt() {
    Map dateMap = getDateMap()
    log.debug "dateMap: ${dateMap}"
    Map sTrigMap = atomicState?.schedTrigMap ?: [:]
    String recur = settings?.trig_scheduled_recurrence
    def days = settings?.trig_scheduled_days
    def weeks = settings?.trig_scheduled_weeks
    def months = settings?.trig_scheduled_months
    Boolean dOk = settings?.trig_scheduled_days ? (isDayOfWeek(days)) : true
    Boolean wOk = (recur && weeks && recur in ["Weekly"]) ? (dateMap?.wm in weeks && sTrigMap?.lastRun?.wm != dateMap?.wm) : true
    Boolean mOk = (recur && months && recur in ["Weekly", "Monthly"]) ? (dateMap?.m in months && sTrigMap?.lastRun?.m != dateMap?.m) : true
    // Boolean yOk = (recur && recur in ["Yearly"]) ? (sTrigMap?.lastRun?.y != dateMap?.y) : true
    log.debug "scheduleTrigEvt | dayOk: $dOk | weekOk: $wOk | monthOk: $mOk"
    if(dOk && wOk && mOk) {
        sTripMap?.lastRun = dateMap
        atomicState?.schedTrigMap = sTrigMap
        executeAction(evt, false, null, "scheduleTrigEvt")
    }
}

private subscribeToEvts() {
    if(checkMinVersion()) { log.error "CODE UPDATE required to RESUME operation.  No events will be monitored."; return; }
    //SCHEDULING
    if (valTrigEvt("scheduled") && (settings?.trig_scheduled_time || settings?.trig_scheduled_sunState)) {
        if(settings?.trig_scheduled_sunState) {
            if (settings?.trig_scheduled_sunState == "Sunset") { subscribe(location, "sunsetTime", sunsetTimeHandler) }
            if (settings?.trig_scheduled_sunState == "Sunrise") { subscribe(location, "sunriseTime", sunriseTimeHandler) }
        }
        if(settings?.trig_scheduled_recurrence) {
            if(settings?.trig_scheduled_recurrence == "Once") {
                runOnce(settings?.trig_scheduled_time, "scheduleTrigEvt")
            } else {
                schedule(settings?.trig_scheduled_time, "scheduleTrigEvt")
            }
        }
    }

    // Location Events
    if(valTrigEvt("alarm")) {
        if(settings?.trig_alarm) { subscribe(location, !isST() ? "hsmStatus" : "alarmSystemStatus", alarmEvtHandler) }
        if(!isST() && settings?.trig_alarm == "Alerts") { subscribe(location, "hsmAlert", alarmEvtHandler) }
    }

    if(valTrigEvt("mode") && settings?.trig_mode) { subscribe(location, "mode", modeEvtHandler) }

    // Routines (ST Only)
    if(valTrigEvt("routineExecuted") && settings?.trig_routineExecuted) { subscribe(location, "routineExecuted", routineEvtHandler) }

    // Scene (Hubitat Only)
    if(valTrigEvt("scene") && settings?.trig_scene) { subscribe(settings?.trig_scene, "switch", sceneEvtHandler) }

    // ENVIRONMENTAL Sensors
    if(valTrigEvt("presence") && settings?.trig_presence) { subscribe(trig_presence, "presence", deviceEvtHandler) }

    // Motion Sensors
    if(valTrigEvt("motion") && settings?.trig_motion) { subscribe(trig_motion, "motion", deviceEvtHandler) }

    // Water Sensors
    if(valTrigEvt("water") && settings?.trig_water) { subscribe(settings?.trig_water, "water", deviceEvtHandler) }

    // Humidity Sensors
    if(valTrigEvt("humidity") && settings?.trig_humidity) { subscribe(settings?.trig_humidity, "humidity", deviceEvtHandler) }

    // Temperature Sensors
    if(valTrigEvt("temperature") && settings?.trig_temperature) { subscribe(settings?.trig_temperature, "temperature", deviceEvtHandler) }

    // Illuminance Sensors
    if(valTrigEvt("illuminance") && settings?.trig_illuminance) { subscribe(settings?.trig_illuminance, "illuminance", deviceEvtHandler) }

    // Power Meters
    if(valTrigEvt("power") && settings?.trig_power) { subscribe(trig_power, "power", deviceEvtHandler) }

    // Locks
    if(valTrigEvt("lock") && settings?.trig_lock) { subscribe(settings?.trig_lock, "lock", deviceEvtHandler) }

    // Window Shades
    if(valTrigEvt("shade") && settings?.trig_shade) { subscribe(settings?.trig_shade, "windowShade", deviceEvtHandler) }

    // Valves
    if(valTrigEvt("valve") && settings?.trig_valve) { subscribe(settings?.trig_valve, "valve", deviceEvtHandler) }

    // Smoke/CO2
    if(valTrigEvt("fire")) {
        if(settings?.trig_carbonMonoxide)   { subscribe(settings?.trig_carbonMonoxide, "carbonMonoxide", deviceEvtHandler) }
        if(settings?.trig_smoke)            { subscribe(settings?.trig_smoke, "smoke", deviceEvtHandler) }
    }

    // Garage Door Openers
    if(valTrigEvt("door") && settings?.trig_door) { subscribe(settings?.trig_door, "garageDoorControl", deviceEvtHandler) }

    //Keypads
    if(valTrigEvt("keypad") && settings?.trig_Keypads) { subscribe(settings?.trig_Keypads, "codeEntered", deviceEvtHandler) }

    //Contact Sensors
    if (valTrigEvt("contact")) {
        if(settings?.trig_contact) { subscribe(settings?.trig_contact, "contact", deviceEvtHandler) }
    }

    // Outlets, Switches
    if (valTrigEvt("switch")) {
        if(settings?.trig_switch) { subscribe(trig_switch, "switch", deviceEvtHandler) }
    }

    // Dimmers/Level
    if (valTrigEvt("level")) {
        if(settings?.trig_level)    { subscribe(settings?.trig_level, "level", deviceEvtHandler) }
    }
}


// EVENT HANDLER FUNCTIONS
def sunriseTimeHandler(evt) {
    def evtDelay = now() - evt?.date?.getTime()
    log.trace "${evt?.name} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${evtDelay}ms"
    String custText = null
    executeAction(evt, false, custText, "sunriseTimeHandler")
}

def sunsetTimeHandler(evt) {
    def evtDelay = now() - evt?.date?.getTime()
    log.trace "${evt?.name} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${evtDelay}ms"
    String custText = null
    executeAction(evt, false, custText, "sunsetTimeHandler")
}

def alarmEvtHandler(evt) {
    def evtDelay = now() - evt?.date?.getTime()
    String custText = null
    log.trace "${evt?.name} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${evtDelay}ms"
    Boolean useAlerts = (settings?.trig_alarm == "Alerts")
    switch(evt?.name) {
        case "hsmStatus":
        case "alarmSystemStatus":
            def inc = (isST() && useAlerts) ? getShmIncidents() : null
            custText = "The ${getAlarmSystemName()} is now set to ${evt?.value}"
            executeAction(evt, false, custText, "alarmEvtHandler")
            break
        case "hsmAlert":
            custText = "A ${getAlarmSystemName()} ${evt?.displayName} alert with ${evt?.value} has occurred."
            executeAction(evt, false, custText, "alarmEvtHandler")
            break
    }
}

def deviceEvtHandler(evt) {
    def evtDelay = now() - evt?.date?.getTime()
    String custText = null
    Boolean evtOk = false
    List d = settings?."trig_${evt?.name}"
    String dc = settings?."trig_${evt?.name}_cmd"
    Boolean dco = (settings?."trig_${evt?.name}_once" == true)
    Boolean dca = (settings?."trig_${evt?.name}_all" == true)
    Integer dcw = settings?."trig_${evt?.name}_wait" ?: null
    log.trace "Device Event | ${evt?.name?.toUpperCase()} | Name: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${evtDelay}ms"

    switch(evt?.name) {
        case "switch":
        case "lock":
        case "door":
        case "smoke":
        case "carbonMonoxide":
        case "windowShade":
        case "presence":
        case "contact":
        case "motion":
        case "water":
        case "valve":
            if(d?.size() && dc) {
                if(dc == "any") {
                    evtOk = true
                    custText = "${evt?.displayName}${!evt?.displayName?.toLowerCase()?.contains(evt?.name) ? " ${evt?.name}" : ""} is ${evt?.value}"
                } else {
                    if(dca && (allDevEqCapVal(d, dc, evt?.value))) {
                        evtOk = true
                        if(d?.size() > 1) {
                            custText = "All ${d?.size()}${!evt?.displayName?.toLowerCase()?.contains(evt?.name) ? " ${evt?.name}" : ""} devices are ${evt?.value}"
                        } else {
                            custText = "${evt?.displayName}${!evt?.displayName?.toLowerCase()?.contains(evt?.name) ? " ${evt?.name}" : ""} is ${evt?.value}"
                        }
                    } else {
                        if(evt?.value == dc) {
                            evtOk=true
                            custText = "${evt?.displayName}${!evt?.displayName?.toLowerCase()?.contains(evt?.name) ? " ${evt?.name}" : ""} is ${evt?.value}"
                        }
                    }
                }
            }
            break

        case "humidity":
        case "temperature":
        case "power":
        case "illuminance":
        case "level":
            Double dcl = settings?."trig_${evt?.name}_low"
            Double dch = settings?."trig_${evt?.name}_high"
            Double dce = settings?."trig_${evt?.name}_equal"
            // log.debug "deviceEvtHandler | cmd: ${dc} | low: ${dcl} | high: ${dch} | equal: ${dce} | all: ${dca}"
            if(d?.size() && dc && evt?.value?.isNumber()) {
                switch(dc) {
                    case "equals":
                        if(!dca && dce && dce?.toDouble() == evt?.value?.toDouble()) {
                            evtOk=true
                            custText = "${evt?.displayName}${!evt?.displayName?.toLowerCase()?.contains(evt?.name) ? " ${evt?.name}" : ""} is now ${evtValueCleanup(evt?.value)} ${getAttrPostfix(evt?.name)}"
                        } else if(dca && dce && allDevCapValsEqual(d, evt?.name, dce)) {
                            evtOk=true
                            custText = "All ${d?.size()} ${evt?.name} devices now ${evtValueCleanup(evt?.value)} ${getAttrPostfix(evt?.name)}"
                        }
                        break
                    case "between":
                        if(!dca && dcl && dch && (evt?.value?.toDouble() in (dcl..dch))) {
                            evtOk=true
                            custText = "${evt?.displayName}${!evt?.displayName?.toLowerCase()?.contains(evt?.name) ? " ${evt?.name}" : ""} of ${evtValueCleanup(evt?.value)} ${getAttrPostfix(evt?.name)} is now ${dc} the ${evtValueCleanup(dcl as String)} ${getAttrPostfix(evt?.name)} and ${evtValueCleanup(dch as String)} ${getAttrPostfix(evt?.name)} threshold you set."
                        } else if(dca && dcl && dch && allDevCapValsBetween(d, evt?.name, dcl, dch)) {
                            evtOk=true
                            custText = "All ${d?.size()} ${evt?.name} devices are now ${evtValueCleanup(evt?.value)} ${getAttrPostfix(evt?.name)} are now ${dc} the ${evtValueCleanup(dcl as String)} ${getAttrPostfix(evt?.name)} and ${evtValueCleanup(dch as String)} ${getAttrPostfix(evt?.name)} threshold you set."
                        }
                        break
                    case "above":
                        if(!dca && dch && (evt?.value?.toDouble() > dch)) {
                            evtOk=true
                            custText = "${evt?.displayName}${!evt?.displayName?.toLowerCase()?.contains(evt?.name) ? " ${evt?.name}" : ""} of ${evtValueCleanup(evt?.value)} ${getAttrPostfix(evt?.name)} is now ${dc} the ${evtValueCleanup(dch)} ${getAttrPostfix(evt?.name)} threshold you set."
                        } else if(dca && dch && allDevCapValsAbove(d, evt?.name, dch)) {
                            evtOk=true
                            custText = "All ${d?.size()} ${evt?.name} devices are now ${evtValueCleanup(evt?.value)} ${getAttrPostfix(evt?.name)} are now ${dc} the ${evtValueCleanup(dch)} ${getAttrPostfix(evt?.name)} threshold you set."
                        }
                        break
                    case "below":
                        if(dcl && (evt?.value?.toDouble() < dcl)) {
                            evtOk=true
                            custText = "${evt?.displayName}${!evt?.displayName?.toLowerCase()?.contains(evt?.name) ? " ${evt?.name}" : ""} of ${evtValueCleanup(evt?.value)} ${getAttrPostfix(evt?.name)} is now ${dc} the ${evtValueCleanup(dcl)} ${getAttrPostfix(evt?.name)} threshold you set."
                        } else if(dca && dcl && allDevCapValsBelow(d, evt?.name, dcl)) {
                            evtOk=true
                            custText = "All ${d?.size()} ${evt?.name} devices are now ${evtValueCleanup(evt?.value)} ${getAttrPostfix(evt?.name)} are now ${dc} the ${evtValueCleanup(dcl)} ${getAttrPostfix(evt?.name)} threshold you set."
                        }
                        break
                }
            }
            break
    }
    if(evtOk ? ((dco || dcw) ? evtWaitRestrictionOk(evt, dco, dcw) : evtOk) : false) {
        executeAction(evt, false, custText, "deviceEvtHandler(${evt?.name})")
    }
}

String evtValueCleanup(val) {
    return val?.toString()?.replaceAll(".0", "")
}

Boolean evtWaitRestrictionOk(evt, Boolean once, Integer wait) {
    Boolean ok = true
    Map evtHistMap = atomicState?.valEvtHistory ?: [:]
    def evtDt = parseIsoDate(evt?.isoDate)
    // log.debug "prevDt: ${evtHistMap["${evt?.deviceId}_${evt?.name}"]?.isoDate ? parseIsoDate(evtHistMap["${evt?.deviceId}_${evt?.name}"]?.isoDate) : null} | evtDt: ${evtDt}"
    if(evtHistMap?.containsKey("${evt?.deviceId}_${evt?.name}")) {
        def prevDt = parseIsoDate(evtHistMap["${evt?.deviceId}_${evt?.name}"]?.isoDate)
        if(prevDt && evtDt) {
            def dur = (int) ((long)(evtDt?.getTime() - prevDt?.getTime())/1000)
            def waitOk = ( (wait && dur) && (wait < dur));
            def dayOk = !once || (once && !isDateToday(prevDt))
            log.info "Last ${evt?.name?.toString()?.capitalize()} Event for Device Occurred: (${dur} sec ago) | Desired Wait: (${wait} sec) - Status: (${waitOk ? "OK" : "Block"}) | OnceDaily: (${once}) - Status: (${dayOk ? "OK" : "Block"})"
            ok = (waitOk && dayOk)
        }
    }
    if(ok) { evtHistMap["${evt?.deviceId}_${evt?.name}"] = [isoDate: evt?.isoDate, value: evt?.value, name: evt?.name, displayName: evt?.displayName] }
    // log.debug "evtWaitRestrictionOk: $ok"
    atomicState?.valEvtHistory = evtHistMap
    return ok
}

String getAttrPostfix(attr) {
    switch(attr) {
        case "humidity":
        case "level":
            return " percent"
        case "temperature":
            return " degrees"
        case "illuminance":
            return " lux"
        case "power":
            return " watts"
        default:
            return ""
    }
}

def routineEvtHandler(evt) {
    def evtDelay = now() - evt?.date?.getTime()
    String custText = "The ${evt?.displayName} routine was just executed!."
    log.trace "${evt?.name} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${evtDelay}ms"
    executeAction(evt, false, custText, "routineEvtHandler")
}

def sceneEvtHandler(evt) {
    def evtDelay = now() - evt?.date?.getTime()
    String custText = "The ${evt?.displayName} scene was just activated!."
    log.trace "${evt?.name} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${evtDelay}ms"
    executeAction(evt, false, custText, "sceneEvtHandler")
}

def modeEvtHandler(evt) {
    def evtDelay = now() - evt?.date?.getTime()
    String custText = "The location mode is now set to ${evt?.value}"
    log.trace "${evt?.name} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${evtDelay}ms"
    executeAction(evt, false, "The location mode is now set to ${evt?.value}", "modeEvtHandler")
}

def locationEvtHandler(evt) {
    def evtDelay = now() - evt?.date?.getTime()
    String custText = null
    log.trace "${evt?.name} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${evtDelay}ms"
    executeAction(evt, false, custText, "locationEvtHandler")
}

/***********************************************************************************************************
   CONDITIONS HANDLER
************************************************************************************************************/
Boolean timeCondOk() {
    def strtTime = null
    def stopTime = null
    def now = new Date()
    def sun = getSunriseAndSunset() // current based on geofence, previously was: def sun = getSunriseAndSunset(zipCode: zipCode)
    if(settings?.cond_time_start_type && settings?.cond_time_stop_type) {
        if(settings?.cond_time_start_type == "sunset") { strtTime = sun?.sunset }
        else if(settings?.cond_time_start_type == "sunrise") { strtTime = sun?.sunrise }
        else if(settings?.cond_time_start_type == "A specific time" && settings?.cond_time_start) { strtTime = settings?.cond_time_start }

        if(settings?.cond_time_stop_type == "sunset") { stopTime = sun?.sunset }
        else if(settings?.cond_time_stop_type == "sunrise") { stopTime = sun?.sunrise }
        else if(settings?.cond_time_stop_type == "A specific time" && settings?.cond_time_stop) { stopTime = settings?.cond_time_stop }
    } else { return true }
    if(strtTime && stopTime) {
        // log.debug "timeCondOk | Start: ${strtTime} | Stop: ${stopTime}"
        if(!isST()) {
            strtTime = toDateTime(strtTime)
            stopTime = toDateTime(stopTime)
        }
        return timeOfDayIsBetween(strtTime, stopTime, new Date(), location?.timeZone) ? false : true
    } else { return true }
}

Boolean dateCondOk() {
    Boolean dOk = settings?.cond_days ? (isDayOfWeek(settings?.cond_days)) : true
    Boolean mOk = settings?.cond_months ? (isMonthOfYear(settings?.cond_months)) : true
    return (dOk && mOk)
}

Boolean locationCondOk() {
    Boolean mOk = settings?.cond_mode ? (isInMode(settings?.cond_mode)) : true
    Boolean aOk = settings?.cond_alarm ? (isInAlarmMode(settings?.cond_alarm)) : true
    // log.debug "locationCondOk | modeOk: $mOk | alarmOk: $aOk"
    return (mOk && aOk)
}

Boolean checkDeviceCondOk(type) {
    def devs = settings?."cond_${type}" ?: null
    def stateVal = settings?."cond_${type}_cmd" ?: null
    if( !(type && devs && stateVal) ) { return true }
    return settings?."cond_${type}_all" ? allDevEqCapVal(devs, type, stateVal) : anyDevEqCapVal(devs, type, stateVal)
}

Boolean allDevEqCapVal(List devs, String cap, val) {
    if(devs) { return (devs?.findAll { it?."current${cap?.capitalize()}" == val }?.size() == devs?.size()) }
    return false
}

Boolean allDevCapValsAbove(List devs, String cap, val) {
    return (devs && cap && val) ? (devs?.findAll { it?."current${cap?.capitalize()}"?.toDouble() > val?.toDouble() }?.size() == devs?.size()) : false
}
Boolean allDevCapValsBelow(List devs, String cap, val) {
    return (devs && cap && val) ? (devs?.findAll { it?."current${cap?.capitalize()}"?.toDouble() < val?.toDouble() }?.size() == devs?.size()) : false
}
Boolean allDevCapValsBetween(List devs, String cap, low, high) {
    return (devs && cap && low && high) ? (devs?.findAll { ( (it?."current${cap?.capitalize()}"?.toDouble() > low?.toDouble()) && (it?."current${cap?.capitalize()}"?.toDouble() < high?.toDouble()) ) }?.size() == devs?.size()) : false
}
Boolean allDevCapValsEqual(List devs, String cap, val) {
    return (devs && cap && val) ? (devs?.findAll { it?."current${cap?.capitalize()}"?.toDouble() == val?.toDouble() }?.size() == devs?.size()) : false
}

Boolean anyDevEqCapVal(List devs, String cap, val) {
    if(devs) { return (devs?.findAll { it?."current${cap?.capitalize()}" == val }?.size() > 0) }
    return false
}

Boolean deviceCondOk() {
    Boolean swDevOk = checkDeviceCondOk("switch")
    Boolean motDevOk = checkDeviceCondOk("motion")
    Boolean presDevOk = checkDeviceCondOk("presence")
    Boolean conDevOk = checkDeviceCondOk("contact")
    Boolean lockDevOk = checkDeviceCondOk("lock")
    Boolean garDevOk = checkDeviceCondOk("door")
    // log.debug "checkDeviceCondOk | switchOk: $swDevOk | motionOk: $motDevOk | presenceOk: $presDevOk | contactOk: $conDevOk | lockOk: $lockDevOk | garageOk: $garDevOk"
    return (swDevOk && motDevOk && presDevOk && conDevOk && lockDevOk && garDevOk)
}

def conditionsOk() {
    def timeOk = timeCondOk()
    def dateOk = dateCondOk()
    def locOk = locationCondOk()
    def devOk = deviceCondOk()
    log.debug "Action Conditions Check | Time: ($timeOk) | Date: ($dateOk) | Location: ($locOk) | Devices: ($devOk)"
    return (timeOk && locOk && devOk)
}

Boolean devCondConfigured(type) {
    return (settings?."cond_${type}" && settings?."cond_${type}_cmd")
}

Boolean timeCondConfigured() {
    Boolean startTime = (settings?.cond_time_start_type in ["Sunrise", "Sunset"] || (settings?.cond_time_start_type && settings?.cond_time_start))
    Boolean stopTime = (settings?.cond_time_stop_type in ["Sunrise", "Sunset"] || (settings?.cond_time_stop_type && settings?.cond_time_stop))
    return (startTime && stopTime)
}

Boolean dateCondConfigured() {
    Boolean days = (settings?.cond_days)
    Boolean months = (settings?.cond_months)
    return (days || months)
}

Boolean locationCondConfigured() {
    Boolean mode = (settings?.cond_mode)
    Boolean alarm = (settings?.cond_alarm)
    return (mode || alarm)
}

Boolean deviceCondConfigured() {
    Boolean swDev = devCondConfigured("switch")
    Boolean motDev = devCondConfigured("motion")
    Boolean presDev = devCondConfigured("presence")
    Boolean conDev = devCondConfigured("contact")
    Boolean lockDev = devCondConfigured("lock")
    Boolean garDev = devCondConfigured("door")
    return (swDev || motDev || presDev || conDev || lockDev || garDev)
}

Boolean conditionsConfigured() {
    return (timeCondConfigured() || dateCondConfigured() || locationCondConfigured() || deviceCondConfigured())
}


/***********************************************************************************************************
    ACTION EXECUTION
************************************************************************************************************/

private executeActTest() {
    settingUpdate("actTestRun", "false", "bool")
    executeAction([name: "contact", displayName: "Front Door", value: "open", date: new Date()], true, null, "executeActTest")
}

String convEvtType(type) {
    Map typeConv = [
        "routineExecuted": "Routine",
        "alarmSystemStatus": "Alarm system",
        "hsmStatus": "Alarm system"
    ]
    return (type && typeConv?.containsKey(type)) ? typeConv[type] : type
}

String decodeVariables(evt, str) {
    if(evt && str) {
        str = (str?.contains("%type%") && evt?.name) ? str?.replaceAll("%type%", convEvtType(evt?.name)) : str
        str = (str?.contains("%name%") && evt?.displayName) ? str?.replaceAll("%name%", evt?.displayName) : str
        str = (str?.contains("%value%") && evt?.value) ? str?.replaceAll("%value%", evt?.value) : str
        str = (str?.contains("%date%") && evt?.date) ? str?.replaceAll("%date%", convToDate(evt?.date)) : str
        str = (str?.contains("%time%") && evt?.date) ? str?.replaceAll("%time%", convToTime(evt?.date)) : str
        str = (str?.contains("%datetime%") && evt?.date) ? str?.replaceAll("%datetime%", convToDateTime(evt?.date)) : str
    }
    return str
}

/***********************************************************************************************************
   HELPER UTILITES
************************************************************************************************************/

void settingUpdate(name, value, type=null) {
    if(name && type) { app?.updateSetting("$name", [type: "$type", value: value]) }
    else if (name && type == null) { app?.updateSetting(name.toString(), value) }
}

void settingRemove(String name) {
    logger("trace", "settingRemove($name)...")
    if(name && settings?.containsKey(name as String)) { isST() ? app?.deleteSetting(name as String) : app?.removeSetting(name as String) }
}

Map notifValEnum(allowCust = true) {
    Map items = [
        300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes",
        1800:"30 Minutes", 2700:"45 Minutes", 3600:"1 Hour", 7200:"2 Hours", 14400:"4 Hours", 21600:"6 Hours", 43200:"12 Hours", 86400:"24 Hours"
    ]
    if(allowCust) { items[100000] = "Custom" }
    return items
}

def fanTimeSecEnum() {
    def vals = [
        60:"1 Minute", 120:"2 Minutes", 180:"3 Minutes", 240:"4 Minutes", 300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes"
    ]
    return vals
}

def longTimeSecEnum() {
    def vals = [
        0:"Off", 60:"1 Minute", 120:"2 Minutes", 180:"3 Minutes", 240:"4 Minutes", 300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes",
        1800:"30 Minutes", 2700:"45 Minutes", 3600:"1 Hour", 7200:"2 Hours", 14400:"4 Hours", 21600:"6 Hours", 43200:"12 Hours", 86400:"24 Hours", 10:"10 Seconds(Testing)"
    ]
    return vals
}

def shortTimeEnum() {
    def vals = [
        1:"1 Second", 2:"2 Seconds", 3:"3 Seconds", 4:"4 Seconds", 5:"5 Seconds", 6:"6 Seconds", 7:"7 Seconds",
        8:"8 Seconds", 9:"9 Seconds", 10:"10 Seconds", 15:"15 Seconds", 30:"30 Seconds", 60:"60 Seconds"
    ]
    return vals
}
List weekDaysEnum() {
    return ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
}

List monthEnum() {
    return ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
}

Map getAlarmTrigOpts() {
    if(isST()) { return ["away":"Armed Away","stay":"Armed Home","off":"Disarmed"] }
    return ["armAway":"Armed Away","armHome":"Armed Home","disarm":"Disarmed", "alerts":"Alerts"]
}

def getShmIncidents() {
    def incidentThreshold = now() - 604800000
    return location.activeIncidents.collect{[date: it?.date?.time, title: it?.getTitle(), message: it?.getMessage(), args: it?.getMessageArgs(), sourceType: it?.getSourceType()]}.findAll{ it?.date >= incidentThreshold } ?: null
}

String getAlarmSystemStatus() {
    if(isST()) {
        def cur = location.currentState("alarmSystemStatus")?.value
        def inc = getShmIncidents()
        if(inc != null && inc?.size()) { cur = 'alarm_active' }
        return cur ?: "disarmed"
    } else { return location?.hsmStatus ?: "disarmed" }
}

Boolean pushStatus() { return (settings?.smsNumbers?.toString()?.length()>=10 || settings?.usePush || settings?.pushoverEnabled) ? ((settings?.usePush || (settings?.pushoverEnabled && settings?.pushoverDevices)) ? "Push Enabled" : "Enabled") : null }
Integer getLastMsgSec() { return !state?.lastMsgDt ? 100000 : GetTimeDiffSeconds(state?.lastMsgDt, "getLastMsgSec").toInteger() }
Integer getLastUpdMsgSec() { return !state?.lastUpdMsgDt ? 100000 : GetTimeDiffSeconds(state?.lastUpdMsgDt, "getLastUpdMsgSec").toInteger() }
Integer getLastMisPollMsgSec() { return !state?.lastMisPollMsgDt ? 100000 : GetTimeDiffSeconds(state?.lastMisPollMsgDt, "getLastMisPollMsgSec").toInteger() }
Integer getLastVerUpdSec() { return !state?.lastVerUpdDt ? 100000 : GetTimeDiffSeconds(state?.lastVerUpdDt, "getLastVerUpdSec").toInteger() }
Integer getLastDevicePollSec() { return !state?.lastDevDataUpd ? 840 : GetTimeDiffSeconds(state?.lastDevDataUpd, "getLastDevicePollSec").toInteger() }
Integer getLastCookieChkSec() { return !state?.lastCookieChkDt ? 3600 : GetTimeDiffSeconds(state?.lastCookieChkDt, "getLastCookieChkSec").toInteger() }
Integer getLastChildInitRefreshSec() { return !state?.lastChildInitRefreshDt ? 3600 : GetTimeDiffSeconds(state?.lastChildInitRefreshDt, "getLastChildInitRefreshSec").toInteger() }
Boolean getOk2Notify() {
    Boolean smsOk = (settings?.smsNumbers?.toString()?.length()>=10)
    Boolean pushOk = settings?.usePush
    Boolean pushOver = (settings?.pushoverEnabled && settings?.pushoverDevices)
    Boolean daysOk = quietDaysOk(settings?.quietDays)
    Boolean timeOk = quietTimeOk()
    Boolean modesOk = quietModesOk(settings?.quietModes)
    logger("debug", "getOk2Notify() | smsOk: $smsOk | pushOk: $pushOk | pushOver: $pushOver || daysOk: $daysOk | timeOk: $timeOk | modesOk: $modesOk")
    if(!(smsOk || pushOk || pushOver)) { return false }
    if(!(daysOk && modesOk && timeOk)) { return false }
    return true
}
Boolean quietModesOk(List modes) { return (modes && location?.mode?.toString() in modes) ? false : true }
Boolean quietTimeOk() {
    def strtTime = null
    def stopTime = null
    def now = new Date()
    def sun = getSunriseAndSunset() // current based on geofence, previously was: def sun = getSunriseAndSunset(zipCode: zipCode)
    if(settings?.qStartTime && settings?.qStopTime) {
        if(settings?.qStartInput == "sunset") { strtTime = sun?.sunset }
        else if(settings?.qStartInput == "sunrise") { strtTime = sun?.sunrise }
        else if(settings?.qStartInput == "A specific time" && settings?.qStartTime) { strtTime = settings?.qStartTime }

        if(settings?.qStopInput == "sunset") { stopTime = sun?.sunset }
        else if(settings?.qStopInput == "sunrise") { stopTime = sun?.sunrise }
        else if(settings?.qStopInput == "A specific time" && settings?.qStopTime) { stopTime = settings?.qStopTime }
    } else { return true }
    if(strtTime && stopTime) {
        return timeOfDayIsBetween(strtTime, stopTime, new Date(), location.timeZone) ? false : true
    } else { return true }
}

Boolean quietDaysOk(days) {
    if(days) {
        def dayFmt = new SimpleDateFormat("EEEE")
        if(location?.timeZone) { dayFmt?.setTimeZone(location?.timeZone) }
        return days?.contains(dayFmt?.format(new Date())) ? false : true
    }
    return true
}

// Sends the notifications based on app settings
public sendMsg(String msgTitle, String msg, Boolean showEvt=true, Map pushoverMap=null, sms=null, push=null) {
    logger("trace", "sendMsg() | msgTitle: ${msgTitle}, msg: ${msg}, showEvt: ${showEvt}")
    String sentstr = "Push"
    Boolean sent = false
    try {
        String newMsg = "${msgTitle}: ${msg}"
        String flatMsg = newMsg.toString().replaceAll("\n", " ")
        if(!getOk2Notify()) {
            log.info "sendMsg: Message Skipped During Quiet Time ($flatMsg)"
            if(showEvt) { sendNotificationEvent(newMsg) }
        } else {
            if(push || settings?.usePush) {
                sentstr = "Push Message"
                if(showEvt) {
                    sendPush(newMsg)	// sends push and notification feed
                } else {
                    sendPushMessage(newMsg)	// sends push
                }
                sent = true
            }
            if(settings?.pushoverEnabled && settings?.pushoverDevices) {
                sentstr = "Pushover Message"
                Map msgObj = [:]
                msgObj = pushoverMap ?: [title: msgTitle, message: msg, priority: (settings?.pushoverPriority?:0)]
                if(settings?.pushoverSound) { msgObj?.sound = settings?.pushoverSound }
                buildPushMessage(settings?.pushoverDevices, msgObj, true)
                sent = true
            }
            String smsPhones = sms ? sms.toString() : (settings?.smsNumbers?.toString() ?: null)
            if(smsPhones) {
                List phones = smsPhones?.toString()?.split("\\,")
                for (phone in phones) {
                    String t0 = newMsg.take(140)
                    if(showEvt) {
                        sendSms(phone?.trim(), t0)	// send SMS and notification feed
                    } else {
                        sendSmsMessage(phone?.trim(), t0)	// send SMS
                    }

                }
                sentstr = "Text Message to Phone [${phones}]"
                sent = true
            }
            if(sent) {
                state?.lastMsg = flatMsg
                state?.lastMsgDt = getDtNow()
                logger("debug", "sendMsg: Sent ${sentstr} (${flatMsg})")
            }
        }
    } catch (ex) {
        incrementCntByKey("appErrorCnt")
        log.error "sendMsg $sentstr Exception:", ex
    }
    return sent
}

//PushOver-Manager Input Generation Functions
private getPushoverSounds(){return (Map) state?.pushoverManager?.sounds?:[:]}
private getPushoverDevices(){List opts=[];Map pmd=state?.pushoverManager?:[:];pmd?.apps?.each{k,v->if(v&&v?.devices&&v?.appId){Map dm=[:];v?.devices?.sort{}?.each{i->dm["${i}_${v?.appId}"]=i};addInputGrp(opts,v?.appName,dm);}};return opts;}
private inputOptGrp(List groups,String title){def group=[values:[],order:groups?.size()];group?.title=title?:"";groups<<group;return groups;}
private addInputValues(List groups,String key,String value){def lg=groups[-1];lg["values"]<<[key:key,value:value,order:lg["values"]?.size()];return groups;}
private listToMap(List original){original.inject([:]){r,v->r[v]=v;return r;}}
private addInputGrp(List groups,String title,values){if(values instanceof List){values=listToMap(values)};values.inject(inputOptGrp(groups,title)){r,k,v->return addInputValues(r,k,v)};return groups;}
private addInputGrp(values){addInputGrp([],null,values)}
//PushOver-Manager Location Event Subscription Events, Polling, and Handlers
public pushover_init(){subscribe(location,"pushoverManager",pushover_handler);pushover_poll()}
public pushover_cleanup(){state?.remove("pushoverManager");unsubscribe("pushoverManager");}
public pushover_poll(){sendLocationEvent(name:"pushoverManagerCmd",value:"poll",data:[empty:true],isStateChange:true,descriptionText:"Sending Poll Event to Pushover-Manager")}
public pushover_msg(List devs,Map data){if(devs&&data){sendLocationEvent(name:"pushoverManagerMsg",value:"sendMsg",data:data,isStateChange:true,descriptionText:"Sending Message to Pushover Devices: ${devs}");}}
public pushover_handler(evt){Map pmd=state?.pushoverManager?:[:];switch(evt?.value){case"refresh":def ed = evt?.jsonData;String id = ed?.appId;Map pA = pmd?.apps?.size() ? pmd?.apps : [:];if(id){pA[id]=pA?."${id}"instanceof Map?pA[id]:[:];pA[id]?.devices=ed?.devices?:[];pA[id]?.appName=ed?.appName;pA[id]?.appId=id;pmd?.apps = pA;};pmd?.sounds=ed?.sounds;break;case "reset":pmd=[:];break;};state?.pushoverManager=pmd;}
//Builds Map Message object to send to Pushover Manager
private buildPushMessage(List devices,Map msgData,timeStamp=false){if(!devices||!msgData){return};Map data=[:];data?.appId=app?.getId();data.devices=devices;data?.msgData=msgData;if(timeStamp){data?.msgData?.timeStamp=new Date().getTime()};pushover_msg(devices,data);}
Integer versionStr2Int(str) { return str ? str.toString()?.replaceAll("\\.", "")?.toInteger() : null }
Boolean checkMinVersion() { return (versionStr2Int(appVersion()) < parent?.minVersions()["actionApp"]) }
/******************************************
|   Restriction validators
*******************************************/

String getCurrentMode() {
    return location?.mode
}

List getLocationModes(Boolean sorted=false) {
    List modes = location?.modes*.name
    log.debug "modes: ${modes}"
    return (sorted) ? modes?.sort() : modes
}

List getLocationRoutines() {
    return (isST()) ? location.helloHome?.getPhrases()*.label?.sort() : []
}

List getClosedContacts(sensors) {
    return sensors?.findAll { it?.currentContact == "closed" } ?: null
}

List getOpenContacts(sensors) {
    return sensors?.findAll { it?.currentContact == "open" } ?: null
}

List getDryWaterSensors(sensors) {
    return sensors?.findAll { it?.currentWater == "dry" } ?: null
}

List getWetWaterSensors(sensors) {
    return sensors?.findAll { it?.currentWater == "wet" } ?: null
}

List getPresentSensors(sensors) {
    return sensors?.findAll { it?.currentPresence == "present" } ?: null
}

List getAwaySensors(sensors) {
    return sensors?.findAll { it?.currentPresence != "present" } ?: null
}

Boolean isContactOpen(sensors) {
    if(sensors) { sensors?.each { if(sensors?.currentSwitch == "open") { return true } } }
    return false
}

Boolean isSwitchOn(devs) {
    if(devs) { devs?.each { if(it?.currentSwitch == "on") { return true } } }
    return false
}

Boolean isSensorPresent(sensors) {
    if(sensors) { sensors?.each { if(it?.currentPresence == "present") { return true } } }
    return false
}

Boolean isSomebodyHome(sensors) {
    if(sensors) { return (sensors?.findAll { it?.currentPresence == "present" }?.size() > 0) }
    return false
}

Boolean isIlluminanceBelow(sensors, val) {
    if(sensors) { return (sensors?.findAll { it?.currentIlluminance?.integer() < val }?.size() > 0) }
    return false
}

Boolean isIlluminanceAbove(sensors, val) {
    if(sensors) { return (sensors?.findAll { it?.currentIlluminance?.integer() > val }?.size() > 0) }
    return false
}

Boolean isWaterWet(sensors) {
    if(sensors) { return (sensors?.findAll { it?.currentWater == "wet" }?.size() > 0) }
    return false
}

Boolean isInMode(modes) {
    return (modes) ? (getCurrentMode() in modes) : false
}

Boolean isInAlarmMode(modes) {
    return (modes) ? (getAlarmSystemStatus() in modes) : false
}

Boolean areAllDevsSame(List devs, String attr, val) {
    if(devs && attr && val) { return (devs?.findAll { it?.currentValue(attr) == val as String }?.size() == devs?.size()) }
    return false
}

String getAlarmSystemName(abbr=false) {
    return isST() ? (abbr ? "SHM" : "Smart Home Monitor") : (abbr ? "HSM" : "Hubitat Safety Monitor")
}
/******************************************
|    Time and Date Conversion Functions
*******************************************/
def formatDt(dt, tzChg=true) {
    def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
    if(tzChg) { if(location.timeZone) { tf.setTimeZone(location?.timeZone) } }
    return tf?.format(dt)
}

def dateTimeFmt(dt, fmt) {
    def tf = new SimpleDateFormat(fmt)
    if(location?.timeZone) { tf.setTimeZone(location?.timeZone) }
    return tf?.format(dt)
}

def convToTime(dt) {
    def newDt = dateTimeFmt(dt, "h:mm a")
    if(newDt?.toString()?.contains(":00 ")) { newDt?.toString()?.replaceAll(":00 ", " ") }
    return newDt
}

def convToDate(dt) {
    def newDt = dateTimeFmt(dt, "EEE, MMM d")
    return newDt
}

def convToDateTime(dt) {
    def t = dateTimeFmt(dt, "h:mm a")
    def d = dateTimeFmt(dt, "EEE, MMM d")
    return "$d, $t"
}

Date parseIsoDate(dt) { return Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", dt?.toString()) }
Boolean isDateToday(Date dt) { return (dt && dt?.clearTime().compareTo(new Date()?.clearTime()) >= 0) }
String strCapitalize(str) { return str ? str?.toString().capitalize() : null }
String isPluralString(obj) { return (obj?.size() > 1) ? "(s)" : "" }

def parseDt(pFormat, dt, tzFmt=true) {
    def result
    def newDt = Date.parse("$pFormat", dt)
    result = formatDt(newDt, tzFmt)
    //log.debug "parseDt Result: $result"
    return result
}

def getDtNow() {
    def now = new Date()
    return formatDt(now)
}

def epochToTime(tm) {
    def tf = new SimpleDateFormat("h:mm a")
    if(location?.timeZone) { tf?.setTimeZone(location?.timeZone) }
    return tf.format(tm)
}

def time2Str(time) {
    if(time) {
        def t = timeToday(time as String, location?.timeZone)
        def f = new java.text.SimpleDateFormat("h:mm a")
        if(location?.timeZone) { tf?.setTimeZone(location?.timeZone) }
        return f?.format(t)
    }
}

def fmtTime(t) {
    if(!t) return null
    def dt = new Date().parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t)
    def tf = new java.text.SimpleDateFormat("h:mm a")
    if(location?.timeZone) { tf?.setTimeZone(location?.timeZone) }
    return tf?.format(dt)
}

def GetTimeDiffSeconds(lastDate, sender=null) {
    try {
        if(lastDate?.contains("dtNow")) { return 10000 }
        def now = new Date()
        def lastDt = Date.parse("E MMM dd HH:mm:ss z yyyy", lastDate)
        def start = Date.parse("E MMM dd HH:mm:ss z yyyy", formatDt(lastDt)).getTime()
        def stop = Date.parse("E MMM dd HH:mm:ss z yyyy", formatDt(now)).getTime()
        def diff = (int) (long) (stop - start) / 1000
        return diff
    }
    catch (ex) {
        log.error "GetTimeDiffSeconds Exception: (${sender ? "$sender | " : ""}lastDate: $lastDate):", ex
        return 10000
    }
}

def getWeekDay() {
    def df = new java.text.SimpleDateFormat("EEEE")
    df.setTimeZone(location?.timeZone)
    return df.format(new Date())
}

def getWeekMonth() {
    def df = new java.text.SimpleDateFormat("W")
    df.setTimeZone(location?.timeZone)
    return df.format(new Date())
}

def getDay() {
    def df = new java.text.SimpleDateFormat("D")
    df.setTimeZone(location?.timeZone)
    return df.format(new Date())
}

def getYear() {
    def df = new java.text.SimpleDateFormat("yyyy")
    df.setTimeZone(location?.timeZone)
    return df.format(new Date())
}

def getMonth() {
    def df = new java.text.SimpleDateFormat("MMMMM")
    df.setTimeZone(location?.timeZone)
    return df.format(new Date())
}

def getWeekYear() {
    def df = new java.text.SimpleDateFormat("w")
    df.setTimeZone(location?.timeZone)
    return df.format(new Date())
}

Map getDateMap() {
    return [d: getWeekDay(), dm: getDay(), wm: getWeekMonth(), wy: getWeekYear(), m: getMonth(), y: getYear() ]
}

Boolean isDayOfWeek(opts) {
    def df = new java.text.SimpleDateFormat("EEEE")
    df.setTimeZone(location?.timeZone)
    def day = df.format(new Date())
    return ( opts?.contains(day) )
}

Boolean isTimeOfDay(startTime, stopTime) {
    if(!startTime && !stopTime) { return true }
    if(!isST()) { startTime = toDateTime(startTime); stopTime = toDateTime(stopTime); }
    return timeOfDayIsBetween(startTime, stopTime, new Date(), location.timeZone)
}

/******************************************
|   App Input Description Functions
*******************************************/

String unitStr(type) {
    switch(type) {
        case "temp":
            return "\u00b0${getTemperatureScale() ?: "F"}"
        case "humidity":
            return "%"
        default:
            return ""
    }
}

String getTriggersDesc() {
    Boolean confd = triggersConfigured()
    List setItem = settings?.triggerEvents
    String sPre = "trig_"
    if(confd && setItem?.size()) {
        String str = "Triggers:\n"
        setItem?.each { evt->
            switch(evt as String) {
                case "scheduled":
                    str += " • ${evt?.capitalize()}${settings?."${sPre}${evt}_recurrence" ? " (${settings?."${sPre}${evt}_recurrence"})" : ""}\n"
                    str += settings?."${sPre}${evt}_time"     ? "    - Time: (${fmtTime(settings?."${sPre}${evt}_time")})\n"      : ""
                    str += settings?."${sPre}${evt}_sunState" ? "    - SunState: (${settings?."${sPre}${evt}_sunState"})\n"       : ""
                    str += settings?."${sPre}${evt}_days"     ? "    - (${settings?."${sPre}${evt}_days"?.size()}) Days\n"      : ""
                    str += settings?."${sPre}${evt}_weeks"    ? "    - (${settings?."${sPre}${evt}_weeks"?.size()}) Weeks\n"    : ""
                    str += settings?."${sPre}${evt}_months"   ? "    - (${settings?."${sPre}${evt}_months"?.size()}) Months\n"  : ""
                    break
                case "alarm":
                    str += " • ${evt?.capitalize()} (${getAlarmSystemName(true)})${settings?."${sPre}${evt}" ? " (${settings?."${sPre}${evt}"?.size()} Selected)" : ""}\n"
                    str += settings?."${sPre}${evt}_once" ? "    - Once a Day: (${settings?."${sPre}${evt}_once"})\n" : ""
                    break
                case "routineExecuted":
                case "mode":
                case "scene":
                    str += " • ${evt == "routineExecuted" ? "Routines" : evt?.capitalize()}${settings?."${sPre}${evt}" ? " (${settings?."${sPre}${evt}"?.size()} Selected)" : ""}\n"
                    str += settings?."${sPre}${evt}_once" ? "    - Once a Day: (${settings?."${sPre}${evt}_once"})\n" : ""
                    break
                default:
                    str += " • ${evt?.capitalize()}${settings?."${sPre}${evt}" ? " (${settings?."${sPre}${evt}"?.size()} Selected)" : ""}\n"
                    def subStr = ""
                    if(settings?."${sPre}${evt}_cmd" in ["above", "below", "equal", "between"]) {
                        if (settings?."${sPre}${evt}_cmd" == "between") {
                            str += settings?."${sPre}${evt}_cmd"  ? "    - ${settings?."${sPre}${evt}_cmd"}: (${settings?."${sPre}${evt}_low"} - ${settings?."${sPre}${evt}_high"})\n" : ""
                        } else {
                            str += (settings?."${sPre}${evt}_cmd" == "above" && settings?."${sPre}${evt}_high")     ? "    - Above: (${settings?."${sPre}${evt}_high"})\n" : ""
                            str += (settings?."${sPre}${evt}_cmd" == "below" && settings?."${sPre}${evt}_low")      ? "    - Below: (${settings?."${sPre}${evt}_low"})\n" : ""
                            str += (settings?."${sPre}${evt}_cmd" == "equal" && settings?."${sPre}${evt}_equal")    ? "    - Equals: (${settings?."${sPre}${evt}_equal"})\n" : ""
                        }
                    } else {
                        str += settings?."${sPre}${evt}_cmd"  ? "    - Value: (${settings?."${sPre}${evt}_cmd"})\n" : ""
                    }
                    str += settings?."${sPre}${evt}_all"  ? "    - Require All: (${settings?."${sPre}${evt}_all"})\n" : ""
                    str += settings?."${sPre}${evt}_once" ? "    - Once a Day: (${settings?."${sPre}${evt}_once"})\n" : ""
                    str += settings?."${sPre}${evt}_wait" ? "    - Wait: (${settings?."${sPre}${evt}_wait"})\n" : ""
                    break
            }
        }
        str += "\ntap to modify..."
        return str
    } else {
        return "tap to configure..."
    }
}

String getConditionsDesc() {
    Boolean confd = conditionsConfigured()
    def time = null
    String sPre = "cond_"
    if(confd) {
        String str = "Conditions: (${conditionsOk() ? "OK" : "Block"})\n"
        if(timeCondConfigured()) {
            str += " • Time:\n"
            str += "    - (${getTimeCondDesc()})\n"
        }
        if(dateCondConfigured()) {
            str += " • Date:\n"
            str += settings?.cond_days      ? "    - Days: (${(isDayOfWeek(settings?.cond_days)) ? "OK" : "Block"})\n" : ""
            str += settings?.cond_months    ? "    - Months: (${(isMonthOfYear(settings?.cond_months)) ? "OK" : "Block"})\n"  : ""
        }
        if(settings?.cond_alarm || settings?.cond_mode) {
            str += " • Location: (${locationCondOk() ? "OK" : "Block"})\n"
            str += settings?.cond_alarm ? "    - Alarm Modes: (${(isInAlarmMode(settings?.cond_alarm)) ? "OK" : "Block"})\n" : ""
            str += settings?.cond_alarm ? "    - Current Alarm: (${getAlarmSystemStatus()})\n" : ""
            str += settings?.cond_mode ? "    - Location Modes: (${(isInMode(settings?.cond_mode)) ? "OK" : "Block"})\n" : ""
            str += settings?.cond_mode ? "    - Current Mode: (${location?.mode})\n" : ""
        }
        if(deviceCondConfigured()) {
            // str += " • Devices: (${deviceCondOk() ? "OK" : "Block"})\n"
            ["switch", "motion", "presence", "contact", "lock", "door"]?.each { evt->
                if(devCondConfigured(evt)) {
                    str += settings?."${sPre}${evt}"     ? " • ${evt?.capitalize()} (${settings?."${sPre}${evt}"?.size()}) (${checkDeviceCondOk(evt) ? "OK" : "Block"})\n" : ""
                    str += settings?."${sPre}${evt}_cmd" ? "    - Value: (${settings?."${sPre}${evt}_cmd"})\n" : ""
                    str += settings?."${sPre}${evt}_all" ? "    - Require All: (${settings?."${sPre}${evt}_all"})\n" : ""
                }
            }
        }
        str += "\ntap to modify..."
        return str
    } else {
        return "tap to configure..."
    }
}

String getActionDesc() {
    Boolean confd = actionsConfigured()
    def time = null
    String sPre = "act_"
    if(confd) {
        String str = "Actions:${settings?.act_EchoDevices ? " (${settings?.act_EchoDevices?.size()} Device${settings?.act_EchoDevices?.size() > 1 ? "s" : ""})" : ""}\n"
        str += " • ${settings?.actionType?.capitalize()}\n"
        str += settings?.act_set_volume ? " • Set Volume: (${settings?.act_set_volume})\n" : ""
        str += settings?.act_restore_volume ? " • Restore Volume: (${settings?.act_restore_volume})\n" : ""
        str += settings?.act_delay ? " • Delay: (${settings?.act_delay})\n" : ""
        str += "\ntap to modify..."
        return str
    } else {
        return "tap to configure..."
    }
    return actionsConfigured() ? "Actions:\n • ${settings?.actionType}\n\ntap to modify..." : "tap to configure..."
}

String getTimeCondDesc(addPre=true) {
    def sun = getSunriseAndSunset()
    def sunsetTime = epochToTime(sun?.sunset?.time)
    def sunriseTime = epochToTime(sun?.sunrise?.time)
    def startType = settings?.cond_time_start_type
    def startTime = settings?.cond_time_start ? fmtTime(settings?.cond_time_start) : null
    log.debug "startTime: ${startTime}"
    def stopType = settings?.cond_time_stop_type
    def stopTime = settings?.cond_time_stop ? fmtTime(settings?.cond_time_stop) : null
    log.debug "stopTime: ${stopTime}"
    String startLbl = (
        (startType in ["Sunset", "Sunrise"]) ?
        ((startType == "Sunset") ? sunsetTime : sunriseTime) :
        startTime
    )
    def stopLbl = (
        (stopType in ["Sunrise", "Sunset"]) ?
        ((stopType == "Sunset") ? sunsetTime : sunriseTime) :
        stopTime
    )
    return ((startLbl && startLbl != "") && (stopLbl && stopLbl != "")) ? "${addPre ? "Time Condition:\n" : ""}(${startLbl} - ${stopLbl})" : "tap to configure..."
}

String getInputToStringDesc(inpt, addSpace = null) {
    Integer cnt = 0
    String str = ""
    if(inpt) {
        inpt.sort().each { item ->
            cnt = cnt+1
            str += item ? (((cnt < 1) || (inpt?.size() > 1)) ? "\n      ${item}" : "${addSpace ? "      " : ""}${item}") : ""
        }
    }
    //log.debug "str: $str"
    return (str != "") ? "${str}" : null
}

String randomString(Integer len) {
    def pool = ["a".."z",0..9].flatten()
    Random rand = new Random(new Date().getTime())
    def randChars = (0..len).collect { pool[rand.nextInt(pool.size())] }
    log.debug "randomString: ${randChars?.join()}"
    return randChars.join()
}

private getPlatform() {
    def p = "SmartThings"
    if(state?.hubPlatform == null) {
        try { [dummy: "dummyVal"]?.encodeAsJson(); } catch (e) { p = "Hubitat" }
        // p = (location?.hubs[0]?.id?.toString()?.length() > 5) ? "SmartThings" : "Hubitat"
        state?.hubPlatform = p
        log.debug "hubPlatform: (${state?.hubPlatform})"
    }
    return state?.hubPlatform
}

String getAppImg(String imgName, frc=false) { return (frc || isST()) ? "https://raw.githubusercontent.com/tonesto7/echo-speaks/${isBeta() ? "beta" : "master"}/resources/icons/${imgName}.png" : "" }
String getPublicImg(String imgName) { return isST() ? "https://raw.githubusercontent.com/tonesto7/SmartThings-tonesto7-public/master/resources/icons/${imgName}.png" : "" }
String sTS(String t, String i = null) { return isST() ? t : """<h3>${i ? """<img src="${i}" width="42"> """ : ""} ${t?.replaceAll("\\n", " ")}</h3>""" }
String inTS(String t, String i = null) { return isST() ? t : """${i ? """<img src="${i}" width="42"> """ : ""} <u>${t?.replaceAll("\\n", " ")}</u>""" }
String pTS(String t, String i = null) { return isST() ? t : """<b>${i ? """<img src="${i}" width="42"> """ : ""} ${t?.replaceAll("\\n", " ")}</b>""" }
String bulletItem(String inStr, String strVal) { return "${inStr == "" ? "" : "\n"} \u2022 ${strVal}" }
String dashItem(String inStr, String strVal, newLine=false) { return "${(inStr == "" && !newLine) ? "" : "\n"} - ${strVal}" }

Integer stateSize() {
    def j = new groovy.json.JsonOutput().toJson(state)
    return j?.toString().length()
}
Integer stateSizePerc() { return (int) ((stateSize() / 100000)*100).toDouble().round(0) }
String debugStatus() { return !settings?.appDebug ? "Off" : "On" }
String deviceDebugStatus() { return !settings?.childDebug ? "Off" : "On" }
Boolean isAppDebug() { return (settings?.appDebug == true) }
Boolean isChildDebug() { return (settings?.childDebug == true) }

String getAppDebugDesc() {
    def str = ""
    str += isAppDebug() ? "App Debug: (${debugStatus()})" : ""
    str += isChildDebug() && str != "" ? "\n" : ""
    str += isChildDebug() ? "Device Debug: (${deviceDebugStatus()})" : ""
    return (str != "") ? "${str}" : null
}

private logger(type, msg, traceOnly=false) {
    if (traceOnly && !settings?.appTrace) { return }
    if(type && msg && settings?.appDebug) {
        log."${type}" "${msg}"
    }
}

String convMusicProvider(String prov) {
    switch (prov) {
        case "Amazon Music":
            return "AMAZON_MUSIC"
        case "Apple Music":
            return "APPLE_MUSIC"
        case "TuneIn":
            return "TUNEIN"
        case "Pandora":
            return "PANDORA"
        case "Sirius Xm":
            return "SIRIUSXM"
        case "Spotify":
            return "SPOTIFY"
        case "Tidal":
            return "TIDAL"
        case "iHeartRadio":
            return "I_HEART_RADIO"
    }
}

String getObjType(obj) {
    if(obj instanceof String) {return "String"}
    else if(obj instanceof GString) {return "GString"}
    else if(obj instanceof Map) {return "Map"}
    else if(obj instanceof List) {return "List"}
    else if(obj instanceof ArrayList) {return "ArrayList"}
    else if(obj instanceof Integer) {return "Integer"}
    else if(obj instanceof BigInteger) {return "BigInteger"}
    else if(obj instanceof Long) {return "Long"}
    else if(obj instanceof Boolean) {return "Boolean"}
    else if(obj instanceof BigDecimal) {return "BigDecimal"}
    else if(obj instanceof Float) {return "Float"}
    else if(obj instanceof Byte) {return "Byte"}
    else if(obj instanceof Date) {return "Date"}
    else { return "unknown"}
}
/************************************************
            SEQUENCE TEST LOGIC
*************************************************/

Map seqItemsAvail() {
    return [
        other: [
            "weather":null, "traffic":null, "flashbriefing":null, "goodmorning":null, "goodnight":null, "cleanup":null,
            "singasong":null, "tellstory":null, "funfact":null, "joke":null, "playsearch":null, "calendartoday":null,
            "calendartomorrow":null, "calendarnext":null, "stop":null, "stopalldevices":null,
            "wait": "value (seconds)", "volume": "value (0-100)", "speak": "message", "announcement": "message",
            "announcementall": "message", "pushnotification": "message", "email": null
        ],
        // dnd: [
        //     "dnd_duration": "2H30M", "dnd_time": "00:30", "dnd_all_duration": "2H30M", "dnd_all_time": "00:30",
        //     "dnd_duration":"2H30M", "dnd_time":"00:30"
        // ],
        speech: [
            "cannedtts_random": ["goodbye", "confirmations", "goodmorning", "compliments", "birthday", "goodnight", "iamhome"]
        ],
        music: [
            "amazonmusic": "search term", "applemusic": "search term", "iheartradio": "search term", "pandora": "search term",
            "spotify": "search term", "tunein": "search term", "cloudplayer": "search term"
        ]
    ]
}

def searchTuneInResultsPage() {
    return dynamicPage(name: "searchTuneInResultsPage", uninstall: false, install: false) {
        def results = executeTuneInSearch()
        Boolean onST = isST()
        section(sTS("Search Results: (Query: ${settings?.tuneinSearchQuery})")) {
            if(results?.browseList && results?.browseList?.size()) {
                results?.browseList?.eachWithIndex { item, i->
                    if(i < 25) {
                        if(item?.browseList != null && item?.browseList?.size()) {
                            item?.browseList?.eachWithIndex { item2, i2->
                                String str = ""
                                str += "ContentType: (${item2?.contentType})"
                                str += "\nId: (${item2?.id})"
                                str += "\nDescription: ${item2?.description}"
                                if(onST) {
                                    paragraph title: pTS(item2?.name?.take(75), (onST ? null : item2?.image)), str, required: true, state: (!item2?.name?.contains("Not Supported") ? "complete" : null), image: item2?.image ?: ""
                                } else { href "searchTuneInResultsPage", title: pTS(item2?.name?.take(75), (onST ? null : item2?.image)), description: str, required: true, state: (!item2?.name?.contains("Not Supported") ? "complete" : null), image: onST && item2?.image ? item2?.image : null }
                            }
                        } else {
                            String str = ""
                            str += "ContentType: (${item?.contentType})"
                            str += "\nId: (${item?.id})"
                            str += "\nDescription: ${item?.description}"
                            if(onST) {
                                paragraph title: pTS(item?.name?.take(75), (onST ? null : item?.image)), str, required: true, state: (!item?.name?.contains("Not Supported") ? "complete" : null), image: item?.image ?: ""
                            } else { href "searchTuneInResultsPage", title: pTS(item?.name?.take(75), (onST ? null : item?.image)), description: str, required: true, state: (!item?.name?.contains("Not Supported") ? "complete" : null), image: onST && item?.image ? item?.image : null }
                        }
                    }
                }
            } else { paragraph "No Results found..." }
        }
    }
}
