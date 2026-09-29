package legalops;

import java.util.List;

public final class LegalMetricsService {
    public static final String CHANNEL = "legal-operations-live";
    private final InfraiClient infrai;

    public LegalMetricsService(InfraiClient infrai) {
        this.infrai = infrai;
    }

    public void prepareDashboard() {
        infrai.createChannel(CHANNEL);
    }

    public LegalOperationsSnapshot recordActivity(List<MatterActivity> matters, String operationId) {
        LegalOperationsSnapshot snapshot = LegalOperationsSnapshot.from(matters);
        Json.Raw metrics = infrai.queryMetrics();
        infrai.publish(CHANNEL, "legal.metrics.updated", snapshot.asJsonWithMetrics(metrics), operationId);
        return snapshot;
    }

    public Json.Raw dashboardToken(String clientId) {
        return infrai.issueDashboardToken(clientId, CHANNEL);
    }
}
