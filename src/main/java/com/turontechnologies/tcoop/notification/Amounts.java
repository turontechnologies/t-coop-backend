package com.turontechnologies.tcoop.notification;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** How money reads in a notification: "₦12,500" or "₦12,500.5". */
public final class Amounts {

  private Amounts() {}

  public static String naira(BigDecimal amount) {
    NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
    format.setMaximumFractionDigits(2);
    return "₦" + format.format(amount == null ? BigDecimal.ZERO : amount);
  }
}
