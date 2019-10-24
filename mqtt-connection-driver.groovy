// Copyright (C) Kirk Rader 2019

// See LICENSE.txt for licensing terms of this software.

// Hubitat device driver that wraps the internal MQTT client interface

// Motivation:

// - Make it possible to send and receive MQTT messages as triggers
//   and actions in Hubitat's Rule Machine

// - Teach myself a bit about Hubitat driver development :-)

// Note that this driver and its MQTT Handler companion driver expose only the
// most basic MQTT features. They do not support complex MQTT-based
// functionality such as the homie protocol for automatic device discovery.
// There is a much richer MQTT Client app under development by another member of
// the community which would be a far better choice for anyone who wants such
// features. (See <https://community.hubitat.com/t/mqtt-client/3808/34>)

// This driver's ambitions are far more modest. It is simply to provide support
// for basic MQTT messaging in Rule Machine, coming as close as possible to its
// direct support for HTTP messaging as triggers and actions, within the highly
// constrained limits of what it is possible to do within Rule Machine at all.

// Features:

// - Inherits support for both secure (tcp://) and encrypted (ssl://)
//   connections with or without password protection from the underlying MQTT
//   client interface
//
// - Publish messages to MQTT topics using "run custom action" in Rule Machine
//   to invoke this driver's `publish(topic, message, qos, retain)` method
//
//   - Note that due to deficiencies in Rule Machine you must select the
//     "Actuator" capability when starting the process of choosing a particular
//     device whose method is to be invoked by "run custom action" in order to
//     see you MQTT Connection device offered as an option among the devices
//     whose custom action you wish to invoke.
//
// - Send child device events on receipt of messages on subscribed topics to
//   support "custom attribute" triggers and actions in Rule Machine
//
//    - When using a "custom attribute" action, you can access the actual
//      message payload the standard `%value%` variable

// Installation:

// Copy this file and mqtt-handler-driver.groovy as "New Drivers" in the "Driver
// Code" area of the Hubitat UI.

// Usage:

// 1. Create a virtual "MQTT Connection" device
//
// 2. Set the broker address and, optionally, user name and password
//
//    a. Use tcp:// prefix for a standard, unencrypted connection
//
//    b. Use ssl:// prefix for an encrypted (TLS) connection
//
// 3. Click the "Save Preferences" button
//
// 4. Use the "Publish," "Subscribe" and "Unsubscribe" buttons to send and
//    receive MQTT messages
//
// 5. Use "Run custom action" in RM to invoke "publish"
//
// 6. Use "Add Handler" to add MQTT Handler child devices for specific topics
//
// 7. Use "Custom Attribute" triggers and actions to access received messages in
//    RM

metadata {

  definition (
    name: "MQTT Connection",
    namespace: "parasaurolophus",
    author: "Kirk Rader",
    importUrl: "https://raw.githubusercontent.com/parasaurolophus/hubitat-mqtt-connection/master/mqtt-connection-driver.groovy") {

    // Enable the "initialize" life-cycle method.
    capability "Initialize"

    // Include a capability that is integrated with Rule Machine
    capability "Actuator"

    // Send a MQTT message.
    command "publish", [
      [name: "topic", type: "STRING"],
      [name: "payload", type: "STRING"],
      [name: "qos", type: "INTEGER"],
      [name: "retain", type: "STRING"]
    ]

    // Subscribe to a MQTT topic.
    command "subscribe", [
      [name: "topic", type: "STRING"],
      [name: "qos", type:"INTEGER"]
    ]

    // Unsubscribe from a MQTT topic filter.
    command "unsubscribe", [
      [name: "topic", type: "STRING"]
    ]

    // Add a child MQTT Handler device.
    command "addHandler", [
      [name: "name", type: "STRING"],
      [name: "topic", type: "STRING"],
      [name: "qos", type: "INTEGER"]
    ]

    // Remove the specified child MQTT Handler device.
    command "deleteHandler", [
      [name: "device network id", type: "STRING"]
    ]

    // Change the topic handled by a given child device.
    command "replaceTopic", [
      [name: "topic", type: "STRING"],
      [name: "qos", type: "INTEGER"],
      [name: "device network id", type: "STRING"]
    ]

    // State of the connection to the MQTT broker ("connected" or
    // "disconnected").
    attribute "connection", "STRING"

    // The most recently received MQTT message.
    attribute "mqtt", "JSON_OBJECT"

  }

  preferences {

    input(
      name: "broker",
      type: "text",
      title: "Broker URL",
      description: "use tcp:// or ssl:// prefix",
      required: true,
      displayDuringSetup: true
    )

    input(
      name: "clientId",
      type: "text",
      title: "Client Id",
      description: "MQTT client id",
      required: true,
      displayDuringSetup: true,
      defaultValue: UUID.randomUUID().toString()
    )

    input(
      name: "username",
      type: "text",
      title: "Username",
      description: "(optional)",
      required: false,
      displayDuringSetup: true
    )

    input(
      name: "password",
      type: "password",
      title: "Password",
      description: "(optional)",
      required: false,
      displayDuringSetup: true
    )

    input(
      name: "lwtTopic",
      type: "text",
      title: "LWT Topic",
      description: "LWT message topic when disconnecting from broker",
      required: true,
      displayDuringSetup: true,
      defaultValue: "hubitat/lwt"
    )

    input(
      name: "lwtMessage",
      type: "text",
      title: "LWT Message",
      description: "LWT message body when disconnecting from broker",
      required: true,
      displayDuringSetup: true,
      defaultValue: "lwt"
    )

  }
}

////////////////////////////////////////////////////////////////////////////////
// Driver life-cycle methods

// Defer full initialization until after parameters are set manually.
def installed() {

  state.handlers = [:];
  state.connected = false
  sendEvent(name: "connection", value: "disconnected", isStateChange: true)

  if (settings.broker != null) {
    initialize()
  }
}

// (Re)initialize after parameters are changed.
def updated() {

  initialize()

}

////////////////////////////////////////////////////////////////////////////////
// Initialize capability life-cycle method

// Disconnect from the MQTT broker, if necessary, then connect.
def initialize() {

  synchronized (state.handlers) {

    disconnect()
    connect()

  }
}

////////////////////////////////////////////////////////////////////////////////
// Custom commands

// Publish the given payload on the given MQTT topic.
def publish(String topic, String payload, int qos = 2,
            boolean retained = false ) {

  synchronized (state.handlers) {

    try {

      interfaces.mqtt.publish(topic, payload, qos, retained)

    } catch (e) {

      log.error "error publishing ${payload} on topic ${topic}: ${e}"

    }
  }
}

// RM-compatible overload for publish(String, String, int, boolean).
def publish(String topic, String payload, int qos, String retained) {

  synchronized (state.handlers) {

    publish(topic, payload, qos, retained.toBoolean())

  }
}

// Subscribe to the specified topic at the specified quality of service setting.
def subscribe(String topic, int qos = 2) {

  synchronized (state.handlers) {

    try {

      interfaces.mqtt.subscribe(topic, qos)

    } catch (e) {

      log.error "error subscribing to topic ${topic} (qos=${qos})"

    }
  }
}

// Unsubscribe from the specified topic.
def unsubscribe(String topic) {

  synchronized (state.handlers) {

    try {

      interfaces.mqtt.unsubscribe(topic)

    } catch (e) {

      log.error "error unsubscribing from topic ${topic}"

    }
  }
}

// Add a MQTT Handler child device subscribed to the specified topic.
def addHandler(String name, String topic, int qos) {

  synchronized (state.handlers) {

    def id = UUID.randomUUID().toString()

    addChildDevice("parasaurolophus", "MQTT Handler", id,
                   [isComponent: true, name: name])
    subscribeHandler(topic, qos, id)
    return id

  }
}

// Delete the specified MQTT Handler child device and unsubscribe from the
// corresponding topic.
def deleteHandler(String id) {

  synchronized (state.handlers) {

    unsubscribeHandler(id)
    deleteChildDevice(id)

  }
}

// Replace the topic associated with the child device with given id.
def replaceTopic(String topic, int qos, String id) {

  synchronized (state.handlers) {

    unsubscribeHandler(id)
    subscribeHandler(topic, qos, id)

  }
}


////////////////////////////////////////////////////////////////////////////////
// MQTT client call-back functions

// Callback invoked by the MQTT interface on status changes and errors.
void mqttClientStatus(String message) {

  if (message.startsWith("Error:")) {

    log.error "mqttClientStatus: ${message}"
    disconnect()
    runIn (5,"connect")

  } else {

    log.info "mqttClientStatus: ${message}"

  }
}

// Callback invoked by the MQTT interface on receipt of a MQTT message.
//
// Looks up the corresponding MQTT Handler child device in state.handlers
// and forwards the message payload as an event.
def parse(String event) {

  synchronized (state.handlers) {

    def message = interfaces.mqtt.parseMessage(event)
    def timestamp = new Date().getTime()

    sendEvent(name: "mqtt", value: message)

    for (element in state.handlers) {

      if (element.key.equals(message.topic)) {

        def handler = getChildDevice(element.value.id)

        if (handler == null) {

          log.warn "parse: no child found with id ${element.value.id}"

        } else {

          handler.sendEvent(name: "payload", value: message.payload)

        }
      }
    }
  }
}

////////////////////////////////////////////////////////////////////////////////
// Helper functions

// Connect to the MQTT broker.
def connect() {

  synchronized (state.handlers) {

    if (!state.connected) {

      try {

        interfaces.mqtt.connect(settings.broker, settings.clientId,
                                settings.username, settings.password,
                                lastWillTopic: settings.twtTopic,
                                lastWillQos: 0,
                                lastWillMessage: settings.lwtMessage)

        state.connected = true;

        state.handlers.each { topic, subscription ->

          subscribe(topic, subscription.qos)
          log.info "subscribed to ${topic}"

        }

        sendEvent(name: "connection", value: "connected", isStateChange: true)

      } catch (e) {

        log.error "error connecting to MQTT broker: ${e}"

      }
    }
  }
}

// Disconnect from the MQTT broker.
def disconnect() {

  synchronized (state.handlers) {

    if (state.connected) {

      state.handlers.each { topic, subscription ->

        unsubscribe(topic)
        log.info "unsubscribed from ${topic}"

      }

      try {

        interfaces.mqtt.disconnect()

      } catch (e) {

        log.error "error disconnecting: ${e}"

      }

      state.connected = false;
      sendEvent(name: "connection", value: "disconnected", isStateChange: true)

    }
  }
}

// Unsubscribe from the topic currently handled by the child device with the
// given id.
def unsubscribeHandler(String id) {

  synchronized (state.handlers) {

    // note: the version of groovy in hubitat seems to be missing
    // Map.removeAll()

    def topic = null

    for (element in state.handlers) {

      if (element.value.id == id) {

        topic = element.key
        break

      }
    }

    if (topic != null) {

      state.handlers.remove(topic)
      unsubscribe(topic)

    }
  }
}

// Subscribe to the given topic and associate it with the child device with the
// given id.
def subscribeHandler(String topic, int qos, String id) {

  synchronized (state.handlers) {

    def handler = getChildDevice(id)

    if (handler != null) {

      subscribe(topic, qos)
      state.handlers.putAt(topic, [id: id, qos: qos])
      handler.sendEvent(name: "topic", value: topic)

    } else {

      log.warn "subscribeHandler: no child device found with id ${id}"

    }
  }
}
