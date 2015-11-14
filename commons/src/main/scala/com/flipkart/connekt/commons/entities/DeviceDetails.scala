package com.flipkart.connekt.commons.entities

import com.fasterxml.jackson.annotation.JsonProperty

/**
 *
 *
 * @author durga.s
 * @version 11/20/15
 */
case class DeviceDetails(@JsonProperty(required = true) deviceId: String, userId: String, c2dmId: String, osName: String, osVersion: String,
                         @JsonProperty(required = true) appName: String, appVersion: String, brand: String, model: String, state: String, altPush: Boolean)
