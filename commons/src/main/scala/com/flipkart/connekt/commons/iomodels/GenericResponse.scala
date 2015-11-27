package com.flipkart.connekt.commons.iomodels

/**
 *
 *
 * @author durga.s
 * @version 11/21/15
 */
case class GenericResponse(status: Int, request: Any, response: Response)

case class Response(message: String, data: Any)
