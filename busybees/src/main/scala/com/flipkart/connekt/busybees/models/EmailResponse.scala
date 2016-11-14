package com.flipkart.connekt.busybees.models

import org.apache.commons.lang3.StringUtils


//TODO: Add more fields as required.
case class EmailResponse(messageId:String, responseCode:Int, message:String = StringUtils.EMPTY)
