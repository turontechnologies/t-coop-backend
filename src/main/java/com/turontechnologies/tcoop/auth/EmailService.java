package com.turontechnologies.tcoop.auth;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends real transactional email via Gmail SMTP — the OTP for the forgot-password flow, and the
 * welcome email a co-op's first admin gets when a super admin onboards their co-op. Both share
 * one branded HTML shell (see {@link #htmlShell}) mirroring the frontend's design preview exactly
 * (t-coop-app's src/components/features/auth/otp-email-preview.tsx) so what a developer sees in
 * that preview component is what actually lands in an inbox.
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
    String firstName = firstNameOf(recipientName);

    StringBuilder digits = new StringBuilder();
    for (char digit : otp.toCharArray()) {
      digits.append(
          "<td style=\"width:40px;height:40px;text-align:center;vertical-align:middle;"
              + "border:1px solid #a7f3d0;background:#ecfdf5;border-radius:8px;"
              + "font-size:20px;font-weight:700;color:#065f46;font-family:monospace;\">"
              + digit
              + "</td><td style=\"width:8px;\"></td>");
    }

    String body =
        "<p style=\"margin:0 0 4px;color:#047857;font-size:12px;font-weight:700;"
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
            + "This code expires in 10 minutes. If you didn't request this, you can safely "
            + "ignore this email.</p>";

    send(toEmail, "Your one-time password for T-Cooperative", body);
  }

  /** Throws if the email genuinely couldn't be sent — the caller decides how to respond. */
  public void sendAdminWelcomeEmail(
      String toEmail,
      String recipientName,
      String cooperativeName,
      String coopId,
      String defaultPassword) {
    String firstName = firstNameOf(recipientName);

    String body =
        "<p style=\"margin:0 0 4px;color:#047857;font-size:12px;font-weight:700;"
            + "letter-spacing:0.08em;text-transform:uppercase;\">Welcome aboard</p>"
            + "<h1 style=\"margin:0 0 16px;color:#0f172a;font-size:19px;font-weight:700;\">"
            + "Your admin account is ready</h1>"
            + "<p style=\"margin:0 0 20px;color:#475569;font-size:14px;line-height:1.6;\">"
            + "Hi " + escapeHtml(firstName) + ", " + escapeHtml(cooperativeName)
            + " has been onboarded to T-Cooperative. Your co-operative's ID is also your "
            + "login ID — sign in with the credentials below, then change your password "
            + "from Settings.</p>"
            + "<table role=\"presentation\" width=\"100%\" style=\"margin:0 0 20px;"
            + "border-collapse:collapse;\">"
            + "<tr><td style=\"padding:12px 16px;background:#ecfdf5;border:1px solid #a7f3d0;"
            + "border-radius:8px 8px 0 0;border-bottom:none;\">"
            + "<p style=\"margin:0;color:#64748b;font-size:11px;text-transform:uppercase;"
            + "letter-spacing:0.06em;\">Co-op ID (your login ID)</p>"
            + "<p style=\"margin:2px 0 0;color:#065f46;font-size:16px;font-weight:700;"
            + "font-family:monospace;\">" + escapeHtml(coopId) + "</p>"
            + "</td></tr>"
            + "<tr><td style=\"padding:12px 16px;background:#ecfdf5;border:1px solid #a7f3d0;"
            + "border-radius:0 0 8px 8px;\">"
            + "<p style=\"margin:0;color:#64748b;font-size:11px;text-transform:uppercase;"
            + "letter-spacing:0.06em;\">Default password</p>"
            + "<p style=\"margin:2px 0 0;color:#065f46;font-size:16px;font-weight:700;"
            + "font-family:monospace;\">" + escapeHtml(defaultPassword) + "</p>"
            + "</td></tr>"
            + "</table>"
            + "<p style=\"margin:0 0 20px;text-align:center;color:#64748b;font-size:12px;\">"
            + "Keep this email private — anyone with these credentials can sign in as your "
            + "co-operative's administrator.</p>";

    send(
        toEmail,
        "Welcome to T-Cooperative — your admin account for " + cooperativeName,
        body);
  }

  public void sendMemberWelcomeEmail(
      String toEmail,
      String recipientName,
      String cooperativeName,
      String membershipId,
      String defaultPassword) {
    String firstName = firstNameOf(recipientName);

    String body =
        "<p style=\"margin:0 0 4px;color:#047857;font-size:12px;font-weight:700;"
            + "letter-spacing:0.08em;text-transform:uppercase;\">Welcome aboard</p>"
            + "<h1 style=\"margin:0 0 16px;color:#0f172a;font-size:19px;font-weight:700;\">"
            + "Your member account is ready</h1>"
            + "<p style=\"margin:0 0 20px;color:#475569;font-size:14px;line-height:1.6;\">"
            + "Hi " + escapeHtml(firstName) + ", you've been added to " + escapeHtml(cooperativeName)
            + " on T-Cooperative. Sign in with the credentials below, then change your "
            + "password from Settings.</p>"
            + "<table role=\"presentation\" width=\"100%\" style=\"margin:0 0 20px;"
            + "border-collapse:collapse;\">"
            + "<tr><td style=\"padding:12px 16px;background:#ecfdf5;border:1px solid #a7f3d0;"
            + "border-radius:8px 8px 0 0;border-bottom:none;\">"
            + "<p style=\"margin:0;color:#64748b;font-size:11px;text-transform:uppercase;"
            + "letter-spacing:0.06em;\">Membership ID (your login ID)</p>"
            + "<p style=\"margin:2px 0 0;color:#065f46;font-size:16px;font-weight:700;"
            + "font-family:monospace;\">" + escapeHtml(membershipId) + "</p>"
            + "</td></tr>"
            + "<tr><td style=\"padding:12px 16px;background:#ecfdf5;border:1px solid #a7f3d0;"
            + "border-radius:0 0 8px 8px;\">"
            + "<p style=\"margin:0;color:#64748b;font-size:11px;text-transform:uppercase;"
            + "letter-spacing:0.06em;\">Default password</p>"
            + "<p style=\"margin:2px 0 0;color:#065f46;font-size:16px;font-weight:700;"
            + "font-family:monospace;\">" + escapeHtml(defaultPassword) + "</p>"
            + "</td></tr>"
            + "</table>"
            + "<p style=\"margin:0 0 20px;text-align:center;color:#64748b;font-size:12px;\">"
            + "Keep this email private — anyone with these credentials can sign in as you.</p>";

    send(
        toEmail,
        "Welcome to T-Cooperative — your member account for " + cooperativeName,
        body);
  }

  /** Platform staff invite (Settings -> User Management) — unlike the member/admin welcome
   * emails, there's no password to show: the invitee sets their own by following the link,
   * which is what actually activates the account (login is blocked until they do). */
  public void sendPlatformStaffInviteEmail(
      String toEmail, String roleName, String inviteLink) {
    String body =
        "<p style=\"margin:0 0 4px;color:#047857;font-size:12px;font-weight:700;"
            + "letter-spacing:0.08em;text-transform:uppercase;\">You're invited</p>"
            + "<h1 style=\"margin:0 0 16px;color:#0f172a;font-size:19px;font-weight:700;\">"
            + "Join T-Cooperative as " + escapeHtml(roleName) + "</h1>"
            + "<p style=\"margin:0 0 24px;color:#475569;font-size:14px;line-height:1.6;\">"
            + "You've been invited to help operate T-Cooperative with the <strong>"
            + escapeHtml(roleName) + "</strong> role. Click below to accept and set your "
            + "password — this link expires in 7 days.</p>"
            + "<table role=\"presentation\" width=\"100%\" style=\"margin:0 0 20px;\">"
            + "<tr><td align=\"center\">"
            + "<a href=\"" + inviteLink + "\" style=\"display:inline-block;padding:12px 28px;"
            + "background:#047857;color:#ffffff;font-size:14px;font-weight:700;"
            + "text-decoration:none;border-radius:8px;\">Accept Invite</a>"
            + "</td></tr>"
            + "</table>"
            + "<p style=\"margin:0 0 20px;text-align:center;color:#64748b;font-size:12px;\">"
            + "If you weren't expecting this, you can safely ignore this email.</p>";

    send(toEmail, "You're invited to T-Cooperative", body);
  }

  /** Guarantor accept-workflow — the guarantor has no T-Coop account of their own, so this link
   * is the entire interaction: it lands on a public page where they can accept or decline. */
  public void sendGuarantorRequestEmail(
      String toEmail,
      String guarantorName,
      String memberName,
      String cooperativeName,
      String inviteLink) {
    String firstName = firstNameOf(guarantorName);

    String body =
        "<p style=\"margin:0 0 4px;color:#047857;font-size:12px;font-weight:700;"
            + "letter-spacing:0.08em;text-transform:uppercase;\">You're requested as a guarantor</p>"
            + "<h1 style=\"margin:0 0 16px;color:#0f172a;font-size:19px;font-weight:700;\">"
            + escapeHtml(memberName) + " named you as a guarantor</h1>"
            + "<p style=\"margin:0 0 24px;color:#475569;font-size:14px;line-height:1.6;\">"
            + "Hi " + escapeHtml(firstName) + ", <strong>" + escapeHtml(memberName)
            + "</strong> named you as a guarantor while joining <strong>" + escapeHtml(cooperativeName)
            + "</strong> on T-Cooperative. Please confirm whether you accept.</p>"
            + "<table role=\"presentation\" width=\"100%\" style=\"margin:0 0 20px;\">"
            + "<tr><td align=\"center\">"
            + "<a href=\"" + inviteLink + "\" style=\"display:inline-block;padding:12px 28px;"
            + "background:#047857;color:#ffffff;font-size:14px;font-weight:700;"
            + "text-decoration:none;border-radius:8px;\">Review Request</a>"
            + "</td></tr>"
            + "</table>"
            + "<p style=\"margin:0 0 20px;text-align:center;color:#64748b;font-size:12px;\">"
            + "This link expires in 14 days. If you weren't expecting this, you can safely "
            + "ignore this email.</p>";

    send(toEmail, memberName + " named you as a guarantor on T-Cooperative", body);
  }

  /** Throws if the email genuinely couldn't be sent — the caller decides how to respond. */
  public void sendSubscriptionReceiptEmail(
      String toEmail,
      String recipientName,
      String cooperativeName,
      BigDecimal amountPaid,
      String type,
      String cycle,
      LocalDate nextRenewalDate) {
    String firstName = firstNameOf(recipientName);
    String verb = "New Subscription".equals(type) ? "activated" : "renewed";
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy");

    String body =
        "<p style=\"margin:0 0 4px;color:#047857;font-size:12px;font-weight:700;"
            + "letter-spacing:0.08em;text-transform:uppercase;\">Payment received</p>"
            + "<h1 style=\"margin:0 0 16px;color:#0f172a;font-size:19px;font-weight:700;\">"
            + "Your subscription has been " + verb + "</h1>"
            + "<p style=\"margin:0 0 20px;color:#475569;font-size:14px;line-height:1.6;\">"
            + "Hi " + escapeHtml(firstName) + ", we've recorded " + escapeHtml(cooperativeName)
            + "'s subscription payment. Here's a summary for your records:</p>"
            + "<table role=\"presentation\" width=\"100%\" style=\"margin:0 0 20px;"
            + "border-collapse:collapse;\">"
            + receiptRow("Amount paid", formatNaira(amountPaid), true)
            + receiptRow("Subscription type", type, false)
            + receiptRow("Billing cycle", cycle, false)
            + receiptRow("Next renewal date", nextRenewalDate.format(dateFormat), false)
            + "</table>"
            + "<p style=\"margin:0 0 20px;text-align:center;color:#64748b;font-size:12px;\">"
            + "Your co-operative's account is active. We'll remind you again as your next "
            + "renewal date approaches.</p>";

    send(toEmail, "Payment received — " + cooperativeName + "'s subscription is active", body);
  }

  /** Notice Board — sent when a notice's medium includes "Email". Non-fatal by design at the
   * call site (NoticeController): a delivery failure shouldn't block the notice itself or its
   * in-app notification, so the caller logs and moves on rather than propagating this. */
  public void sendNoticeEmail(
      String toEmail, String recipientName, String noticeType, String title, String message) {
    String firstName = firstNameOf(recipientName);

    String body =
        "<p style=\"margin:0 0 4px;color:#047857;font-size:12px;font-weight:700;"
            + "letter-spacing:0.08em;text-transform:uppercase;\">" + escapeHtml(noticeType) + "</p>"
            + "<h1 style=\"margin:0 0 16px;color:#0f172a;font-size:19px;font-weight:700;\">"
            + escapeHtml(title) + "</h1>"
            + "<p style=\"margin:0 0 8px;color:#475569;font-size:14px;line-height:1.6;\">"
            + "Hi " + escapeHtml(firstName) + ",</p>"
            + "<p style=\"margin:0 0 20px;color:#475569;font-size:14px;line-height:1.6;"
            + "white-space:pre-line;\">" + escapeHtml(message) + "</p>"
            + "<p style=\"margin:0 0 20px;text-align:center;color:#64748b;font-size:12px;\">"
            + "Log in to T-Cooperative's Notice Board to reply or see the full announcement.</p>";

    send(toEmail, title, body);
  }

  private String receiptRow(String label, String value, boolean firstRow) {
    String radius = firstRow ? "border-radius:8px 8px 0 0;border-bottom:none;" : "";
    return "<tr><td style=\"padding:12px 16px;background:#ecfdf5;border:1px solid #a7f3d0;"
        + radius
        + "\">"
        + "<p style=\"margin:0;color:#64748b;font-size:11px;text-transform:uppercase;"
        + "letter-spacing:0.06em;\">"
        + escapeHtml(label)
        + "</p>"
        + "<p style=\"margin:2px 0 0;color:#065f46;font-size:16px;font-weight:700;\">"
        + escapeHtml(value)
        + "</p>"
        + "</td></tr>";
  }

  private String formatNaira(BigDecimal amount) {
    NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
    format.setMinimumFractionDigits(2);
    format.setMaximumFractionDigits(2);
    return "₦" + format.format(amount);
  }

  private void send(String toEmail, String subject, String bodyHtml) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
      helper.setFrom(fromAddress, fromName);
      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(htmlShell(bodyHtml), true);
      mailSender.send(message);
    } catch (MailException | java.io.UnsupportedEncodingException | jakarta.mail.MessagingException e) {
      log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
      throw new EmailDeliveryException("Couldn't send the email. Please try again.", e);
    }
  }

  private String htmlShell(String bodyContent) {
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
        + bodyContent
        + "<hr style=\"border:none;border-top:1px solid #f1f5f9;margin:0 0 16px;\" />"
        + "<p style=\"margin:0;text-align:center;color:#64748b;font-size:12px;\">"
        + "Need help? <a href=\"mailto:support@turon.tech\" "
        + "style=\"color:#047857;font-weight:600;text-decoration:none;\">Contact our support team</a>"
        + "</p>"
        + "</td></tr>"
        + "</table>"
        + "</body></html>";
  }

  private String firstNameOf(String fullName) {
    return fullName == null || fullName.isBlank()
        ? "there"
        : fullName.trim().split("\\s+")[0];
  }

  private String escapeHtml(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
