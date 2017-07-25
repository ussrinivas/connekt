package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.dao.TRequestDao
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.ConnektRequest
import com.flipkart.connekt.commons.utils.StringUtils.generateUUID

import scala.util.{Failure, Success, Try}

/**
  * Created by saurabh.mimani on 24/07/17.
  */
class PullMessageService(requestDao: TRequestDao) extends TService {
  private val messageDao: TRequestDao = requestDao

  def saveRequest(request: ConnektRequest): Try[String] = {
    try {
      val reqWithId = request.copy(id = generateUUID)
      messageDao.saveRequest(reqWithId.id, reqWithId, true)
      Success(reqWithId.id)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.SERVICE).error(s"Failed to save in app request ${e.getMessage}", e)
        Failure(e)
    }
  }
}
