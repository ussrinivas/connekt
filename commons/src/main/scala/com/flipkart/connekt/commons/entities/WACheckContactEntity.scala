package com.flipkart.connekt.commons.entities

case class WACheckContactEntity(destination: String,
                                waUserName: String,
                                waExists: String,
                                lastContacted: Option[Long],
                                appName: String,
                                waLastCheckContactTS: Long = System.currentTimeMillis)
