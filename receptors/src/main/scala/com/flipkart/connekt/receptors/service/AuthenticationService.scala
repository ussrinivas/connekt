package com.flipkart.connekt.receptors.service

/**
 *
 *
 * @author durga.s
 * @version 11/22/15
 */
object AuthenticationService {
  def isUserAuthenticated(company: String, client: String, apiKey: String): Boolean = client.equals("connekt-genesis") //TODO
}
