package com.flipkart.connekt.commons.iomodels
import com.flipkart.connekt.commons.services.TStencilService

case class WATextRequestData(body: String) extends ChannelRequestData {
  override def validate(appName: String)(implicit stencilService: TStencilService): Unit = {

  }
}
