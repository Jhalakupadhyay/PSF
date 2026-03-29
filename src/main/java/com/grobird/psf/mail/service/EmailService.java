package com.grobird.psf.mail.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.io.FileWriter;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    // #region agent log
    private static final String DEBUG_LOG = "/Users/jhalak/Developer/psf/.cursor/debug-0acc2f.log";
    private void debugLog(String loc, String msg, String data, String hid) {
        try (FileWriter fw = new FileWriter(DEBUG_LOG, true)) {
            fw.write("{\"sessionId\":\"0acc2f\",\"location\":\"" + loc + "\",\"message\":\"" + msg + "\",\"data\":" + data + ",\"hypothesisId\":\"" + hid + "\",\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (Exception ignored) {}
    }
    // #endregion

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.from:${spring.mail.username:noreply@presalesforce.ai}}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        // #region agent log
        String smtpHost = "", smtpPort = "", smtpUser = "";
        if (mailSender instanceof JavaMailSenderImpl impl) {
            smtpHost = impl.getHost() != null ? impl.getHost() : "null";
            smtpPort = String.valueOf(impl.getPort());
            smtpUser = impl.getUsername() != null ? impl.getUsername() : "null";
        }
        debugLog("EmailService:init", "Mail config on startup",
                "{\"fromEmail\":\"" + fromEmail + "\",\"smtpHost\":\"" + smtpHost + "\",\"smtpPort\":\"" + smtpPort + "\",\"smtpUser\":\"" + smtpUser + "\",\"smtpUserEmpty\":" + (smtpUser.isEmpty() || smtpUser.equals("null")) + "}", "H1");
        // #endregion
    }

    /**
     * Send welcome email with login credentials to a new sales user.
     *
     * @param toEmail   recipient email address
     * @param password  the auto-generated password
     * @param firstName the sales user's first name
     * @throws MailException if sending fails
     */
    public void sendWelcomeEmail(String toEmail, String password, String firstName) throws MailException {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Welcome to PSF - Your Login Credentials");
        message.setText(buildWelcomeEmailBody(firstName, toEmail, password));

        // #region agent log
        debugLog("EmailService:sendWelcomeEmail", "About to send welcome email",
                "{\"from\":\"" + fromEmail + "\",\"to\":\"" + toEmail + "\",\"firstName\":\"" + firstName + "\"}", "H2,H4");
        // #endregion
        log.info("Sending welcome email to {}", toEmail);
        try {
            mailSender.send(message);
            // #region agent log
            debugLog("EmailService:sendWelcomeEmail", "send() returned without exception", "{\"to\":\"" + toEmail + "\"}", "H3");
            // #endregion
            log.info("Welcome email sent successfully to {}", toEmail);
        } catch (MailException e) {
            // #region agent log
            debugLog("EmailService:sendWelcomeEmail", "send() threw MailException",
                    "{\"to\":\"" + toEmail + "\",\"error\":\"" + e.getMessage().replace("\"", "'") + "\",\"class\":\"" + e.getClass().getSimpleName() + "\"}", "H1,H3");
            // #endregion
            log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
            throw e;
        }
    }

    /**
     * Send password reset email with new credentials.
     *
     * @param toEmail     recipient email address
     * @param newPassword the new auto-generated password
     * @param firstName   the user's first name
     * @throws MailException if sending fails
     */
    public void sendPasswordResetEmail(String toEmail, String newPassword, String firstName) throws MailException {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("PSF - Your Password Has Been Reset");
        message.setText(buildPasswordResetEmailBody(firstName, toEmail, newPassword));

        // #region agent log
        debugLog("EmailService:sendPasswordResetEmail", "About to send reset email",
                "{\"from\":\"" + fromEmail + "\",\"to\":\"" + toEmail + "\",\"firstName\":\"" + firstName + "\"}", "H2,H4");
        // #endregion
        log.info("Sending password reset email to {}", toEmail);
        try {
            mailSender.send(message);
            // #region agent log
            debugLog("EmailService:sendPasswordResetEmail", "send() returned without exception", "{\"to\":\"" + toEmail + "\"}", "H3");
            // #endregion
            log.info("Password reset email sent successfully to {}", toEmail);
        } catch (MailException e) {
            // #region agent log
            debugLog("EmailService:sendPasswordResetEmail", "send() threw MailException",
                    "{\"to\":\"" + toEmail + "\",\"error\":\"" + e.getMessage().replace("\"", "'") + "\",\"class\":\"" + e.getClass().getSimpleName() + "\"}", "H1,H3");
            // #endregion
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            throw e;
        }
    }

    private String buildWelcomeEmailBody(String firstName, String email, String password) {
        return String.format("""
            Hello %s,

            Welcome to PSF (Pre-Sales Force)!

            Your account has been created. Please use the following credentials to log in:

            Email: %s
            Temporary Password: %s

            For security, please change your password after your first login.

            Best regards,
            The PSF Team
            """, firstName, email, password);
    }

    private String buildPasswordResetEmailBody(String firstName, String email, String newPassword) {
        return String.format("""
            Hello %s,

            Your password has been reset by your administrator.

            Please use the following credentials to log in:

            Email: %s
            New Password: %s

            For security, please change your password after logging in.

            Best regards,
            The PSF Team
            """, firstName, email, newPassword);
    }
}
