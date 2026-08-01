package com.aitips.mail;

import com.aitips.config.AppConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Handles sending formatted HTML emails using SMTP and Jakarta Mail (Angus).
 */
public class EmailSender {
    private static final Logger logger = LoggerFactory.getLogger(EmailSender.class);

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;
    private final String toAddress;
    private final boolean auth;
    private final boolean startTls;

    public EmailSender(AppConfig config) {
        this.host = config.getRequired("smtp.host");
        this.port = config.getIntOrDefault("smtp.port", 587);
        this.username = config.getRequired("smtp.username");
        this.password = config.getRequired("smtp.password");
        this.fromAddress = config.getRequired("smtp.from");
        this.toAddress = config.getRequired("smtp.to");
        this.auth = config.getBooleanOrDefault("smtp.auth", true);
        this.startTls = config.getBooleanOrDefault("smtp.starttls", true);
    }

    /**
     * Sends the generated tip via email.
     */
    public void sendTipEmail(String title, String summary, String detailedHtml) throws MessagingException {
        logger.info("Preparing to send email to {} via SMTP server {}:{}", toAddress, host, port);
        
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", String.valueOf(auth));
        
        if (port == 465) {
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
            logger.debug("Configured SMTP for SSL/TLS on port 465");
        } else {
            props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
            logger.debug("Configured SMTP for STARTTLS on port {}", port);
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        // Debug output for mail session (redirected to logging)
        session.setDebug(false);

        String styledHtml = buildHtmlBody(title, summary, detailedHtml);

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
        message.setSubject("Java Interview Tip: " + title);
        message.setContent(styledHtml, "text/html; charset=utf-8");

        Transport.send(message);
        logger.info("Daily Java Interview Tip email sent successfully to {}", toAddress);
    }

    private String buildHtmlBody(String title, String summary, String detailedHtml) {
        String processedHtml = styleCodeBlocks(detailedHtml);
        
        String template = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Daily Java Interview Tip</title>
                <style>
                    body {
                        margin: 0; padding: 0; background-color: #f8fafc;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                    }
                    p { margin: 0 0 16px 0; }
                    ul, ol { margin: 0 0 16px 0; padding-left: 24px; }
                    li { margin-bottom: 8px; }
                    strong { color: #0f172a; font-weight: 600; }
                    em { color: #334155; }
                </style>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; -webkit-font-smoothing: antialiased;">
                <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background-color: #f8fafc; padding: 24px 0;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="600" style="max-width: 600px; width: 100%; background-color: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05); border-collapse: separate; overflow: hidden;">
                                <!-- Premium Header -->
                                <tr>
                                    <td style="background: linear-gradient(135deg, #4f46e5 0%, #312e81 100%); padding: 32px 24px; text-align: left;">
                                        <span style="color: #c7d2fe; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.15em; display: block; margin-bottom: 8px;">Daily Java Interview Prep</span>
                                        <h1 style="color: #ffffff; font-size: 22px; font-weight: 800; margin: 0; line-height: 1.3; letter-spacing: -0.02em;">{title}</h1>
                                    </td>
                                </tr>
                                <!-- Quick TL;DR Card -->
                                <tr>
                                    <td style="padding: 24px 24px 0 24px;">
                                        <div style="background-color: #f5f3ff; border-left: 4px solid #8b5cf6; padding: 16px; border-radius: 0 8px 8px 0;">
                                            <p style="margin: 0; color: #4c1d95; font-size: 13.5px; font-weight: 600; line-height: 1.5;">
                                                <strong>TL;DR:</strong> {summary}
                                            </p>
                                        </div>
                                    </td>
                                </tr>
                                <!-- Main Body Content -->
                                <tr>
                                    <td style="padding: 24px; color: #334155; font-size: 14.5px; line-height: 1.6; text-align: left;">
                                        {content}
                                    </td>
                                </tr>
                                <!-- Professional Footer -->
                                <tr>
                                    <td style="background-color: #f8fafc; padding: 24px; text-align: center; border-top: 1px solid #f1f5f9; color: #64748b; font-size: 12px; line-height: 1.5;">
                                        <p style="margin: 0 0 6px 0; font-weight: 700; color: #475569;">Daily Java Interview Tip Automation Service</p>
                                        <p style="margin: 0 0 12px 0;">Stay sharp, understand deep JVM internals, and write robust code.</p>
                                        <p style="margin: 0; font-size: 10.5px; color: #94a3b8;">This is an automated background task. To configure parameters, edit the local <code>config.properties</code> configuration file.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """;
        
        return template
                .replace("{title}", title)
                .replace("{summary}", summary)
                .replace("{content}", processedHtml);
    }

    /**
     * Styles pre/code tags inside HTML with standard modern dark code theme.
     */
    private String styleCodeBlocks(String html) {
        if (html == null) return "";
        
        // Match <pre><code class="..."> or <pre><code> structures and apply robust inline styles suitable for email clients
        String styledPre = "<pre style=\"background-color: #0f172a; color: #f8fafc; padding: 18px; border-radius: 8px; overflow-x: auto; font-family: 'Fira Code', Consolas, 'Courier New', monospace; font-size: 13px; line-height: 1.5; border: 1px solid #1e293b; margin: 16px 0; tab-size: 4;\">";
        return html
                .replaceAll("(?i)<pre>\\s*<code[^>]*>", styledPre)
                .replaceAll("(?i)<pre><code[^>]*>", styledPre)
                .replaceAll("(?i)</code>\\s*</pre>", "</pre>")
                .replaceAll("(?i)</code></pre>", "</pre>");
    }
}
