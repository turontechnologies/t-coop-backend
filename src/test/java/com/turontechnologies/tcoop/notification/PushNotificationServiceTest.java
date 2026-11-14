package com.turontechnologies.tcoop.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PushNotificationServiceTest {

  @Test
  void picksTheAndroidChannelByType() {
    assertEquals("messages", PushNotificationService.channelFor("SUPPORT_TICKET_REPLY"));
    assertEquals("alerts", PushNotificationService.channelFor("NOTICE_BOARD"));
    assertEquals("alerts", PushNotificationService.channelFor("SUBSCRIPTION_EXPIRING"));
    assertEquals("activity", PushNotificationService.channelFor("LOAN_DECISION"));
    assertEquals("activity", PushNotificationService.channelFor(null));
  }

  @Test
  void deletesOnlyTokensExpoSaysAreGone() {
    PushTokenRepository repository = mock(PushTokenRepository.class);
    PushNotificationService service = new PushNotificationService(repository, new ObjectMapper());
    PushToken alive = new PushToken("MB-1", "ExponentPushToken[alive]", "ios");
    PushToken gone = new PushToken("MB-1", "ExponentPushToken[gone]", "android");

    service.removeDeadTokens(
        List.of(alive, gone),
        "{\"data\":[{\"status\":\"ok\",\"id\":\"a\"},{\"status\":\"error\",\"message\":\"x\",\"details\":{\"error\":\"DeviceNotRegistered\"}}]}");

    verify(repository).delete(gone);
    verify(repository, never()).delete(alive);
  }
}
