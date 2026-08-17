package gdg.sharinglog.service.push;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gdg.sharinglog.domain.push.PushSubscription;
import org.junit.jupiter.api.Test;

class WebPushSenderTest {

    @Test
    void doesNothingWhenVapidKeysAreNotConfigured() {
        WebPushSender sender = new WebPushSender("", "", "mailto:noreply@example.com");
        PushSubscription subscription = mock(PushSubscription.class);
        when(subscription.getEndpoint()).thenReturn("https://push.example.com/abc");

        assertDoesNotThrow(() -> sender.send(subscription, "{}"));
    }
}
