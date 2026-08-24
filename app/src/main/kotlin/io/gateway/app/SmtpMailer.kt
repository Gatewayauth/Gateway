package io.gateway.app

import io.gateway.app.config.SmtpSettings
import io.gateway.domain.notification.Mailer
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

/** SMTP-backed [Mailer] (Jakarta Mail). Sends plaintext messages off the request thread. */
class SmtpMailer(private val settings: SmtpSettings) : Mailer {

    private val session: Session = buildSession()

    override suspend fun send(to: String, subject: String, body: String) {
        withContext(Dispatchers.IO) {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(settings.from))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject)
                setText(body)
            }
            Transport.send(message)
        }
    }

    private fun buildSession(): Session {
        val props = Properties().apply {
            put("mail.smtp.host", settings.host)
            put("mail.smtp.port", settings.port.toString())
            put("mail.smtp.auth", (settings.username != null).toString())
            put("mail.smtp.starttls.enable", settings.startTls.toString())
        }
        val username = settings.username ?: return Session.getInstance(props)
        return Session.getInstance(
            props,
            object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(username, settings.password.orEmpty())
            },
        )
    }
}
