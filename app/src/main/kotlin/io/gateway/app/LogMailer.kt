package io.gateway.app

import io.gateway.domain.notification.Mailer
import org.slf4j.LoggerFactory

/**
 * Development mailer: logs the message instead of sending. Swap for an SMTP/API
 * implementation in production by rebinding [Mailer] in the DI module.
 */
class LogMailer : Mailer {
    private val log = LoggerFactory.getLogger("io.gateway.mail")

    override suspend fun send(to: String, subject: String, body: String) {
        log.info("[mail] to={} subject=\"{}\" body=\"{}\"", to, subject, body)
    }
}
