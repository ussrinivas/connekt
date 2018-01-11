/*
 *         -╥⌐⌐⌐⌐            -⌐⌐⌐⌐-
 *      ≡╢░░░░⌐\░░░φ     ╓╝░░░░⌐░░░░╪╕
 *     ╣╬░░`    `░░░╢┘ φ▒╣╬╝╜     ░░╢╣Q
 *    ║╣╬░⌐        ` ╤▒▒▒Å`        ║╢╬╣
 *    ╚╣╬░⌐        ╔▒▒▒▒`«╕        ╢╢╣▒
 *     ╫╬░░╖    .░ ╙╨╨  ╣╣╬░φ    ╓φ░╢╢Å
 *      ╙╢░░░░⌐"░░░╜     ╙Å░░░░⌐░░░░╝`
 *        ``˚¬ ⌐              ˚˚⌐´
 *
 *      Copyright © 2016 Flipkart.com
 */
package com.flipkart.connekt.commons.iomodels

object MessageStatus {

  object GCMResponseStatus extends Enumeration {
    type GCMResponseStatus = Value

    val InvalidJsonError = Value("gcm_invalid_json_error")
    val AuthError = Value("gcm_auth_error")
    val Received_HTTP = Value("gcm_received_http")
    val Received_XMPP = Value("gcm_received_xmpp")
    val Delivered = Value("gcm_delivered")
    val Error = Value("gcm_error")
    val InternalError = Value("gcm_internal_error")
    val InvalidDevice = Value("gcm_invalid_device")
  }

  object WNSResponseStatus extends Enumeration {
    type WNSResponseStatus = Value

    val Received = Value("wns_received")
    val InvalidHeader = Value("wns_invalid_header")
    val InvalidMethod = Value("wns_invalid_method")
    val InvalidChannelUri = Value("wns_invalid_channel_uri")
    val InvalidDevice = Value("wns_invalid_device")
    val AuthError = Value("wns_auth_error")
    val ThrottleLimitExceeded = Value("wns_throttle_limit_exceeded")
    val ChannelExpired = Value("wns_channel_expired")
    val EntityTooLarge = Value("wns_entity_too_large")
    val PreConditionFailed = Value("wns_precondition_failed")
    val InternalError = Value("wns_internal_error")
  }

  object APNSResponseStatus extends Enumeration {
    type APNSResponseStatus = Value

    val Received = Value("apns_received")
    val TokenExpired = Value("apns_rejected_token_expired")
    val Rejected = Value("apns_rejected")
    val UnknownFailure = Value("apns_unknown_failure")
  }

  object OpenWebResponseStatus extends Enumeration {
    type OpenWebResponseStatus = Value

    val Received = Value("openweb_received")
    val InvalidPayload = Value("openweb_invalid_payload")
    val Error = Value("openweb_error")
    val InternalError = Value("openweb_internal_error")
    val AuthError = Value("openweb_auth_error")
    val InvalidToken = Value("openweb_invalid_token")
  }

  object EmailResponseStatus extends Enumeration {
    type EmailResponseStatus = Value

    val Received = Value("email_received")
    val Error = Value("email_error")
    val InternalError = Value("email_server_error")
    val AuthError = Value("email_auth_error")
  }

  object SmsResponseStatus extends Enumeration {
    type SmsResponseStatus = Value

    val Received = Value("sms_received")
    val Error = Value("sms_error")
    val Delivered = Value("sms_delivered")
    val InternalError = Value("sms_server_error")
    val AuthError = Value("sms_auth_error")
  }

  object WAResponseStatus extends Enumeration {
    type WAResponseStatus = Value

    val Received = Value("wa_received")
    val Error = Value("wa_error")
    val InternalError = Value("wa_server_error")
    val Delivered = Value("wa_delivered")
    val WANotExist = Value("wa_not_exists")

    val ContactError = Value("wa_contact_error")
    val ContactReceived = Value("wa_contact_received")
    val ContactSystemError = Value("wa_contact_system_error")

    val MediaUploadError = Value("wa_media_upload_error")
    val MediaUploadSystemError = Value("wa_media_upload_system_error")
    val MediaUploaded = Value("wa_media_uploaded")

    val SendSystemError = Value("wa_send_system_error")

    val StencilNotFound = Value("wa_stencil_not_found")
    val AuthError = Value("wa_auth_error")
  }


  object InternalStatus extends Enumeration {
    type InternalStatus = Value

    val RenderFailure = Value("connekt_render_failure")
    val TrackingFailure = Value("connekt_tracking_failure")
    val MissingDeviceInfo = Value("connekt_missing_device")
    val InvalidToken = Value("connekt_invalid_token")
    val GcmResponseParseError = Value("connekt_gcm_response_parse_error")
    val WnsResponseHandleError = Value("connekt_wns_response_handle_error")
    val OpenWebResponseHandleError = Value("connekt_openweb_response_handle_error")
    val TTLExpired = Value("connekt_ttl_expired")
    val InvalidRequest = Value("connekt_invalid_request")
    val ExcludedRequest = Value("connekt_excluded_request")
    val StageError = Value("connekt_stage_error")
    val EncryptionError = Value("connekt_encryption_error")
    val ProviderSendError = Value("connekt_provider_send_error")
    val Received = Value("connekt_received")
    val Rejected = Value("connekt_rejected")

  }
}
