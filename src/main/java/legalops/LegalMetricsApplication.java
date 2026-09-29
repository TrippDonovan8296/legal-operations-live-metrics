package legalops;

import java.time.Instant;
import java.util.List;

public final class LegalMetricsApplication {
    private LegalMetricsApplication() {}

    public static void main(String[] args) {
        InfraiConfig config = InfraiConfig.fromEnvironment();
        LegalMetricsService service = new LegalMetricsService(new InfraiClient(config));
        Instant now = Instant.now();
        List<MatterActivity> matters = List.of(
                new MatterActivity("MAT-1042", now.minusSeconds(7200), now.minusSeconds(1800), now.plusSeconds(86400), now),
                new MatterActivity("MAT-1043", now.minusSeconds(3600), null, now.minusSeconds(60), now));

        service.prepareDashboard();
        LegalOperationsSnapshot result = service.recordActivity(matters, "matter-batch-2026-09-27");
        Json.Raw token = service.dashboardToken("compliance-wallboard");
        System.out.printf("published intake=%d delivered=%d follow_up=%d channel=%s%n",
                result.intakeCount(), result.signedDeliveryCount(), result.deadlineFollowUpCount(),
                LegalMetricsService.CHANNEL);
        System.out.println("dashboard_token=" + token.json());
    }
}
