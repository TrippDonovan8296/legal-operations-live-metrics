package legalops;

import java.time.Instant;
import java.util.List;

public final class LegalOperationsSnapshotTest {
    public static void main(String[] args) {
        Instant now = Instant.parse("2026-09-27T09:00:00Z");
        List<MatterActivity> matters = List.of(
                new MatterActivity("MAT-001", now.minusSeconds(4000), now.minusSeconds(300), now.plusSeconds(8000), now),
                new MatterActivity("MAT-002", now.minusSeconds(3000), null, now.minusSeconds(1), now),
                new MatterActivity("MAT-003", now.minusSeconds(2000), null, now.plusSeconds(9000), now));

        LegalOperationsSnapshot snapshot = LegalOperationsSnapshot.from(matters);
        require(snapshot.intakeCount() == 3, "all three matters entered intake");
        require(snapshot.signedDeliveryCount() == 1, "one signed document was delivered");
        require(snapshot.deadlineFollowUpCount() == 1, "only the overdue unsigned matter needs follow-up");
        System.out.println("PASS overdue unsigned matter is selected for deadline follow-up");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
