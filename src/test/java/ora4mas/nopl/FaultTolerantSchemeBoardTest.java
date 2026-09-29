package ora4mas.nopl;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.List;
import java.util.Map;

import moise.os.fs.ArgumentSpec;
import moise.os.fs.ErrorSpec;
import moise.os.fs.Failure;
import moise.xml.FaultTolerantXMLReader;

public class FaultTolerantSchemeBoardTest {

    @Test
    public void testParseSampleOrgXML() throws Exception {
        File file = new File("sample_org.xml");
        assertTrue("sample_org.xml should exist", file.exists());

        Map<String, List<Failure>> failuresByScheme = FaultTolerantXMLReader.parseFailuresFromFile(file);
        assertNotNull("Result should not be null", failuresByScheme);
        assertTrue("Should contain therapy_sch", failuresByScheme.containsKey("therapy_sch"));

        List<Failure> failures = failuresByScheme.get("therapy_sch");
        assertEquals("therapy_sch should have 1 failure definition", 1, failures.size());

        Failure failure = failures.get(0);
        assertEquals("Goal should be follow_therapy", "follow_therapy", failure.getGoalId());
        assertEquals("Failure should have 3 errors", 3, failure.getErrors().size());

        // Error 1: lost_symptoms
        ErrorSpec err1 = failure.getError("lost_symptoms");
        assertNotNull("Should have lost_symptoms error", err1);
        assertNotNull("Should have condition", err1.getCondition());
        assertTrue(err1.getCondition().contains("symptoms_cleared(Patient, Day)"));
        assertEquals("Should have 3 arguments", 3, err1.getArguments().size());
        assertEquals("patient_id", err1.getArguments().get(0).getId());
        assertEquals(1, err1.getArguments().get(0).getArity());
        assertNotNull("Should have recovery act", err1.getRecoveryAct());
        assertEquals("reconsult_scheme", err1.getRecoveryAct().getScheme());

        // Error 2: no_delivery
        ErrorSpec err2 = failure.getError("no_delivery");
        assertNotNull("Should have no_delivery error", err2);
        assertEquals("redelivery_scheme", err2.getRecoveryAct().getScheme());

        // Error 3: missing_prescription
        ErrorSpec err3 = failure.getError("missing_prescription");
        assertNotNull("Should have missing_prescription error", err3);
        assertEquals("represcribe_scheme", err3.getRecoveryAct().getScheme());
    }

    @Test
    public void testRuntimeEvaluationAndSuspension() throws Exception {
        File file = new File("sample_org.xml");
        Map<String, List<Failure>> failuresByScheme = FaultTolerantXMLReader.parseFailuresFromFile(file);

        FaultTolerantSchemeBoard board = new FaultTolerantSchemeBoard();
        for (Failure f : failuresByScheme.get("therapy_sch")) {
            board.addFailureSpec(f);
        }

        assertFalse("Goal should not be suspended initially", board.isGoalSuspended("follow_therapy"));

        // Condition in sample_org: symptoms_cleared(Patient, Day) & Day < 5
        // First belief: Day is 7 (Day < 5 is FALSE, should NOT trigger)
        board.updateOrgBelief("symptoms_cleared(bob, 7)");
        assertFalse("Goal should NOT be suspended when Day >= 5", board.isGoalSuspended("follow_therapy"));

        // Second belief: Day is 3 (Day < 5 is TRUE, SHOULD trigger)
        board.updateOrgBelief("symptoms_cleared(alice, 3)");
        assertTrue("Goal should be SUSPENDED when Day < 5", board.isGoalSuspended("follow_therapy"));

        // Test recovery success: resume goal
        board.resumeGoal("follow_therapy");
        assertFalse("Goal should no longer be suspended after resume", board.isGoalSuspended("follow_therapy"));
        assertFalse("Goal should not be failed", board.isGoalFailed("follow_therapy"));

        // Test recovery failed: fail goal
        board.failGoal("follow_therapy");
        assertTrue("Goal should be marked as failed", board.isGoalFailed("follow_therapy"));
    }
}
