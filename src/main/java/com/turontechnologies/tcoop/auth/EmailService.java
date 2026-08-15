package com.turontechnologies.tcoop.auth;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends the OTP email for the forgot-password flow via Gmail SMTP. The HTML template mirrors the
 * frontend's design preview exactly (t-coop-app's src/components/features/auth/otp-email-preview.tsx)
 * so what a developer sees in that preview component is what actually lands in an inbox.
 */
@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);
  private static final String LOGO_URL =
      "https://res.cloudinary.com/djstai84f/image/upload/v1784102518/Logo_1_kspxky.png";

  private final JavaMailSender mailSender;
  private final String fromAddress;
  private final String fromName;

  public EmailService(
      JavaMailSender mailSender,
      @Value("${app.mail.from-address}") String fromAddress,
      @Value("${app.mail.from-name}") String fromName) {
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
    this.fromName = fromName;
  }

  /** Throws if the email genuinely couldn't be sent — the caller decides how to respond. */
  public void sendOtpEmail(String toEmail, String recipientName, String otp) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
      helper.setFrom(fromAddress, fromName);
      helper.setTo(toEmail);
      helper.setSubject("Your one-time password for T-Cooperative");
      helper.setText(buildOtpEmailHtml(recipientName, otp), true);
      mailSender.send(message);
    } catch (MailException | java.io.UnsupportedEncodingException | jakarta.mail.MessagingException e) {
      log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
      throw new EmailDeliveryException("Couldn't send the OTP email. Please try again.", e);
    }
  }

  private String buildOtpEmailHtml(String recipientName, String otp) {
    String firstName = recipientName == null || recipientName.isBlank()
        ? "there"
        : recipientName.trim().split("\\s+")[0];

    StringBuilder digits = new StringBuilder();
    for (char digit : otp.toCharArray()) {
      digits.append(
          "<td style=\"width:40px;height:40px;text-align:center;vertical-align:middle;"
              + "border:1px solid #a7f3d0;background:#ecfdf5;border-radius:8px;"
              + "font-size:20px;font-weight:700;color:#065f46;font-family:monospace;\">"
              + digit
              + "</td><td style=\"width:8px;\"></td>");
    }

    return "<!DOCTYPE html>"
        + "<html><body style=\"margin:0;padding:24px;background:#f1f5f9;"
        + "font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;\">"
        + "<table role=\"presentation\" width=\"100%\" style=\"max-width:480px;margin:0 auto;"
        + "background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e2e8f0;\">"
        + "<tr><td style=\"background:linear-gradient(160deg,#00654A 0%,#00543D 45%,#003224 100%);"
        + "padding:32px 24px;text-align:center;\">"
        // The source asset is a wide wordmark lockup (179x42, authored in
        // white for dark surfaces) — sized here at its real aspect ratio,
        // not forced into a square, which is what made it look shrunken.
        + "<img src=\"" + LOGO_URL + "\" alt=\"T-Cooperative\" width=\"160\" height=\"38\" "
        + "style=\"display:block;margin:0 auto;\" />"
        + "</td></tr>"
        + "<tr><td style=\"padding:28px 32px;\">"
        + "<p style=\"margin:0 0 4px;color:#047857;font-size:12px;font-weight:700;"
        + "letter-spacing:0.08em;text-transform:uppercase;\">Secure access</p>"
        + "<h1 style=\"margin:0 0 16px;color:#0f172a;font-size:19px;font-weight:700;\">"
        + "Your one-time password</h1>"
        + "<p style=\"margin:0 0 20px;color:#475569;font-size:14px;line-height:1.6;\">"
        + "Hi " + escapeHtml(firstName) + ", we received a request to verify your identity. "
        + "Use the code below to continue — it's only valid for a few minutes.</p>"
        + "<table role=\"presentation\" align=\"center\" style=\"margin:0 auto 20px;\"><tr>"
        + digits
        + "</tr></table>"
        + "<p style=\"margin:0 0 20px;text-align:center;color:#64748b;font-size:12px;\">"
        + "This code expires in 10 minutes. If you didn't request this, you can safely ignore "
        + "this email.</p>"
        + "<hr style=\"border:none;border-top:1px solid #f1f5f9;margin:0 0 16px;\" />"
        + "<p style=\"margin:0;text-align:center;color:#64748b;font-size:12px;\">"
        + "Need help? <a href=\"mailto:support@turon.tech\" "
        + "style=\"color:#047857;font-weight:600;text-decoration:none;\">Contact our support team</a>"
        + "</p>"
        + "</td></tr>"
        + "</table>"
        + "</body></html>";
  }

  private String escapeHtml(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
