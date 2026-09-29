package legalops;

import java.time.Instant;

public record MatterActivity(
        String matterId,
        Instant intakeAt,
        Instant signedDeliveryAt,
        Instant deadline,
        Instant now) {
}
