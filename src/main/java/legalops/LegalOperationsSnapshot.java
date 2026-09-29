package legalops;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LegalOperationsSnapshot(long intakeCount, long signedDeliveryCount, long deadlineFollowUpCount) {
    public static LegalOperationsSnapshot from(List<MatterActivity> matters) {
        long intake = matters.stream().filter(m -> m.intakeAt() != null).count();
        long delivered = matters.stream().filter(m -> m.signedDeliveryAt() != null).count();
        long followUp = matters.stream()
                .filter(m -> m.deadline() != null && !m.deadline().isAfter(m.now()))
                .filter(m -> m.signedDeliveryAt() == null)
                .count();
        return new LegalOperationsSnapshot(intake, delivered, followUp);
    }

    Json.Raw asJsonWithMetrics(Json.Raw reportedMetrics) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("matter_intake", intakeCount);
        values.put("signed_document_delivery", signedDeliveryCount);
        values.put("deadline_follow_up", deadlineFollowUpCount);
        values.put("reported_metrics", reportedMetrics);
        return new Json.Raw(Json.object(values));
    }
}
