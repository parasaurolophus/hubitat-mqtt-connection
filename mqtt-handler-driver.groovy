// Copyright (C) Kirk Rader 2019

// See LICENSE.txt for licensing terms of this software.

// Child device type used by MQTT Connection.

// Installation:

// Copy this file and mqtt-connection-driver.groovy as "New Drivers" in the user
// driver area of the Hubitat UI.

// Usage:

// This device type is not designed to be used on its own. Devices of this type
// are created as "component" devices of a MQTT Connection device using the
// latter's Add Handler custom command. Once created, you can refer to the
// "message" custom attribute of MQTT Handler devices in RM triggers and
// actions.

metadata {

  definition (name: "MQTT Handler",
              namespace: "parasaurolophus",
              author: "Kirk Rader",
              importUrl: "https://raw.githubusercontent.com/parasaurolophus/hubitat-mqtt-connection/master/mqtt-handler-driver.groovy") {

    // Declare a capability that is compatible with RM
    capability "Initialize"

    // Payload of incoming message.
    attribute "payload", "STRING"

  }
}

// Standard driver life-cycle callback.
def installed() {

  initialize()

}

// Standard driver life-cycle callback.
def updated() {

  initialize()

}

// Standard Initialize life-cycle command.
def initialize() {

  // nothing to do here

}
