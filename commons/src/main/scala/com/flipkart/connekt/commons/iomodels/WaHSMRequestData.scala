package com.flipkart.connekt.commons.iomodels
import com.flipkart.connekt.commons.services.TStencilService

case class WaHSMRequestData(namespace: String, element_name: String, fallback_lg: String = "en", fallback_lc: String = "US", localizable_params: Array[WaHSMDefaults]) extends  ChannelRequestData{

  override def validate(appName: String)(implicit stencilService: TStencilService): Unit = {

  }
}
