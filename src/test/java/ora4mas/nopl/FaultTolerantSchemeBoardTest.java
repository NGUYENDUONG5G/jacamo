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

    @Test
    public void testArgumentResolutionFromAgentBeliefBase() throws Exception {
        FaultTolerantSchemeBoard board = new FaultTolerantSchemeBoard();

        // Tao 1 Agent gia lap voi Belief Base chua cac belief delivery_address, prescription_id, delivery_attempt
        jason.asSemantics.Agent carrier = new jason.asSemantics.Agent();
        carrier.initAg();
        carrier.getBB().add(jason.asSyntax.ASSyntax.parseLiteral("delivery_address(\"Số 1 Đại Cồ Việt, Hà Nội\")"));
        carrier.getBB().add(jason.asSyntax.ASSyntax.parseLiteral("prescription_id(p101)"));
        carrier.getBB().add(jason.asSyntax.ASSyntax.parseLiteral("delivery_attempt(3)"));
        carrier.getBB().add(jason.asSyntax.ASSyntax.parseLiteral("location(105, 21)"));

        board.registerAgent("carrier", carrier);

        // Test 1: <argument id="delivery_address" arity="1" />
        ArgumentSpec argAddress = new ArgumentSpec("delivery_address", 1);
        Object resolvedAddress = board.resolveArgumentValue(argAddress, null);
        assertEquals("Số 1 Đại Cồ Việt, Hà Nội", resolvedAddress);

        // Test 2: <argument id="prescription_id" arity="1" />
        ArgumentSpec argPrescription = new ArgumentSpec("prescription_id", 1);
        Object resolvedPrescription = board.resolveArgumentValue(argPrescription, null);
        assertEquals("p101", resolvedPrescription);

        // Test 3: <argument id="delivery_attempt" arity="1" />
        ArgumentSpec argAttempt = new ArgumentSpec("delivery_attempt", 1);
        Object resolvedAttempt = board.resolveArgumentValue(argAttempt, null);
        assertEquals(3L, resolvedAttempt);

        // Test 4: <argument id="location" arity="2" />
        ArgumentSpec argLocation = new ArgumentSpec("location", 2);
        Object resolvedLocation = board.resolveArgumentValue(argLocation, null);
        assertTrue(resolvedLocation instanceof List);
        List<?> locList = (List<?>) resolvedLocation;
        assertEquals(2, locList.size());
        assertEquals(105L, locList.get(0));
        assertEquals(21L, locList.get(1));
    }

    @Test
    public void testErrorTriggerWithAgentBeliefResolution() throws Exception {
        File file = new File("sample_org.xml");
        Map<String, List<Failure>> failuresByScheme = FaultTolerantXMLReader.parseFailuresFromFile(file);

        FaultTolerantSchemeBoard board = new FaultTolerantSchemeBoard();
        for (Failure f : failuresByScheme.get("therapy_sch")) {
            board.addFailureSpec(f);
        }

        // Dang ky agent benh nhan co dia chi giao hang
        jason.asSemantics.Agent patient = new jason.asSemantics.Agent();
        patient.initAg();
        patient.getBB().add(jason.asSyntax.ASSyntax.parseLiteral("delivery_address(\"221B Baker Street\")"));
        patient.getBB().add(jason.asSyntax.ASSyntax.parseLiteral("delivery_attempt(1)"));
        patient.getBB().add(jason.asSyntax.ASSyntax.parseLiteral("failure_cause(\"recipient_absent\")"));
        patient.getBB().add(jason.asSyntax.ASSyntax.parseLiteral("prescription_id(rx999)"));
        board.registerAgent("alice_patient", patient);

        // Kich hoat loi no_delivery:
        // condition: not delivered(PrescriptionId) & ctime(Now) & Now > Deadline
        // Them ctime va Deadline de thoa man Now > Deadline
        board.updateOrgBelief("ctime(100)");
        board.updateOrgBelief("delivered(rx999)"); // Ban dau da delivered thi NOT TRIGGER
        assertFalse(board.isGoalSuspended("follow_therapy"));

        // Khi co loi missing_prescription:
        // condition: achieved(therapy_sch, consult, Doctor) & not available(prescription)
        // Agent doctor co doctor_id va issue_type
        jason.asSemantics.Agent doctor = new jason.asSemantics.Agent();
        doctor.initAg();
        doctor.getBB().add(jason.asSyntax.ASSyntax.parseLiteral("doctor_id(dr_bob)"));
        doctor.getBB().add(jason.asSyntax.ASSyntax.parseLiteral("consultation_id(c001)"));
        doctor.getBB().add(jason.asSyntax.ASSyntax.parseLiteral("issue_type(\"pharmacy_out_of_stock\")"));
        board.registerAgent("dr_bob", doctor);

        board.updateOrgBelief("achieved(therapy_sch, consult, dr_bob)");
        // Se kich hoat missing_prescription va treo goal follow_therapy
        assertTrue("Goal follow_therapy should be suspended", board.isGoalSuspended("follow_therapy"));
    }

    @Test
    public void testConditionSpecClass() {
        moise.os.fs.ConditionSpec cond = new moise.os.fs.ConditionSpec("symptoms_cleared(P, D) & D < 5");
        assertEquals("symptoms_cleared(P, D) & D < 5", cond.getExpression());
        assertEquals("symptoms_cleared(P, D) & D < 5", cond.getValue());
        assertTrue(cond.isDefined());
        assertFalse(cond.isEmpty());
        assertTrue(cond.contains("symptoms_cleared"));

        cond.setExpression("  test_formula  ");
        assertEquals("test_formula", cond.trim());

        moise.os.fs.ConditionSpec emptyCond = new moise.os.fs.ConditionSpec();
        assertTrue(emptyCond.isEmpty());
        assertFalse(emptyCond.isDefined());

        ErrorSpec err = new ErrorSpec("err_test");
        err.setCondition(cond);
        assertSame(cond, err.getCondition());
        assertEquals("test_formula", err.getCondition().trim());

        err.setCondition("q(Y)");
        assertEquals("q(Y)", err.getCondition().getExpression());
    }

    @Test
    public void testTriggerUnification() throws Exception {
        jason.asSyntax.Trigger t1 = jason.asSyntax.ASSyntax.parseTrigger("+!follow_therapy");
        jason.asSyntax.Trigger t2 = jason.asSyntax.ASSyntax.parseTrigger("+!follow_therapy[scheme(Sch)]");
        jason.asSemantics.Unifier u = new jason.asSemantics.Unifier();
        boolean res = u.unifies(t1, t2);
        System.out.println("t1 unifies t2: " + res);
        assertTrue(res);
    }

    @Test
    public void testAgentGoalSuspensionAndResume() throws Exception {
        jason.asSemantics.Agent ag = new jason.asSemantics.Agent();
        ag.initAg();
        jason.asSemantics.TransitionSystem ts = new jason.asSemantics.TransitionSystem(ag, new jason.asSemantics.Circumstance(), null, new jason.infra.local.LocalAgArch());
        ag.setTS(ts);

        // Tao mot intention voi trigger +!follow_therapy[scheme(main_sch)]
        jason.asSemantics.Intention intention = new jason.asSemantics.Intention();
        jason.asSyntax.Plan plan = jason.asSyntax.ASSyntax.parsePlan("+!follow_therapy[scheme(Sch)] <- .wait(1000).");
        jason.asSemantics.IntendedMeans im = new jason.asSemantics.IntendedMeans(new jason.asSemantics.Option(plan, new jason.asSemantics.Unifier()), jason.asSyntax.ASSyntax.parseTrigger("+!follow_therapy[scheme(main_sch)]"));
        intention.push(im);

        ts.getC().addRunningIntention(intention);
        assertEquals(1, ts.getC().getNbRunningIntentions());
        assertTrue(ts.getC().getPendingIntentions().isEmpty());

        // Suspend
        jason.asSyntax.Literal gLit = jason.asSyntax.ASSyntax.parseLiteral("follow_therapy");
        new jason.stdlib.suspend().execute(ts, new jason.asSemantics.Unifier(), new jason.asSyntax.Term[] { gLit });

        assertEquals(0, ts.getC().getNbRunningIntentions());
        assertFalse(ts.getC().getPendingIntentions().isEmpty());

        // Resume
        new jason.stdlib.resume().execute(ts, new jason.asSemantics.Unifier(), new jason.asSyntax.Term[] { gLit });
        assertEquals(1, ts.getC().getNbRunningIntentions());
        assertTrue(ts.getC().getPendingIntentions().isEmpty());
    }

    @Test
    public void testProactiveAndActiveBoardGoalSuspension() throws Exception {
        FaultTolerantSchemeBoard board = new FaultTolerantSchemeBoard();

        // 1. Setup mock agent with an active intention for follow_therapy
        jason.asSemantics.Agent patient = new jason.asSemantics.Agent();
        patient.initAg();
        jason.asSemantics.TransitionSystem ts = new jason.asSemantics.TransitionSystem(patient, new jason.asSemantics.Circumstance(), null, new jason.infra.local.LocalAgArch());
        patient.setTS(ts);

        jason.asSemantics.Intention intention = new jason.asSemantics.Intention();
        jason.asSyntax.Plan plan = jason.asSyntax.ASSyntax.parsePlan("+!follow_therapy[scheme(main_sch)] <- .wait(1000).");
        jason.asSemantics.IntendedMeans im = new jason.asSemantics.IntendedMeans(
            new jason.asSemantics.Option(plan, new jason.asSemantics.Unifier()),
            jason.asSyntax.ASSyntax.parseTrigger("+!follow_therapy[scheme(main_sch)]")
        );
        intention.push(im);
        ts.getC().addRunningIntention(intention);

        board.registerAgent("alice_patient", patient);

        assertEquals(1, ts.getC().getNbRunningIntentions());
        assertFalse(board.isGoalSuspended("follow_therapy"));
        assertFalse(board.isJasonGoalSuspended("alice_patient", "follow_therapy"));

        // 2. Active suspension via board operation
        board.suspendGoal("alice_patient", "follow_therapy");

        assertTrue("Organizational goal should be suspended", board.isGoalSuspended("follow_therapy"));
        assertTrue("Jason goal for alice_patient should be suspended", board.isJasonGoalSuspended("alice_patient", "follow_therapy"));
        assertEquals("Running intentions should be empty", 0, ts.getC().getNbRunningIntentions());
        assertTrue("Board should be in recovery for this goal", board.isInRecovery("follow_therapy"));

        // 3. Active resume via board operation
        board.resumeGoal("alice_patient", "follow_therapy");

        assertFalse("Organizational goal should be resumed", board.isGoalSuspended("follow_therapy"));
        assertFalse("Jason goal should be resumed", board.isJasonGoalSuspended("alice_patient", "follow_therapy"));
        assertEquals("Running intentions should be restored", 1, ts.getC().getNbRunningIntentions());
        assertFalse("Recovery should no longer be active", board.isInRecovery("follow_therapy"));
    }

    @Test
    public void testAutomaticProactiveSuspensionOnErrorDetection() throws Exception {
        File file = new File("sample_org.xml");
        Map<String, List<Failure>> failuresByScheme = FaultTolerantXMLReader.parseFailuresFromFile(file);

        FaultTolerantSchemeBoard board = new FaultTolerantSchemeBoard();
        for (Failure f : failuresByScheme.get("therapy_sch")) {
            board.addFailureSpec(f);
        }

        // Setup agent with running intention
        jason.asSemantics.Agent patient = new jason.asSemantics.Agent();
        patient.initAg();
        jason.asSemantics.TransitionSystem ts = new jason.asSemantics.TransitionSystem(patient, new jason.asSemantics.Circumstance(), null, new jason.infra.local.LocalAgArch());
        patient.setTS(ts);

        jason.asSemantics.Intention intention = new jason.asSemantics.Intention();
        jason.asSyntax.Plan plan = jason.asSyntax.ASSyntax.parsePlan("+!follow_therapy[scheme(therapy_sch)] <- .wait(1000).");
        jason.asSemantics.IntendedMeans im = new jason.asSemantics.IntendedMeans(
            new jason.asSemantics.Option(plan, new jason.asSemantics.Unifier()),
            jason.asSyntax.ASSyntax.parseTrigger("+!follow_therapy[scheme(therapy_sch)]")
        );
        intention.push(im);
        ts.getC().addRunningIntention(intention);

        board.registerAgent("alice_patient", patient);

        assertEquals(1, ts.getC().getNbRunningIntentions());
        assertFalse(board.isGoalSuspended("follow_therapy"));

        // Kich hoat loi lost_symptoms bang updateOrgBelief:
        // Day 3 < 5 -> condition TRUE -> triggers handleError
        board.updateOrgBelief("symptoms_cleared(alice, 3)");

        assertTrue("Goal follow_therapy should be suspended on board", board.isGoalSuspended("follow_therapy"));
        assertTrue("Jason goal for alice should be proactively suspended", board.isJasonGoalSuspended("alice_patient", "follow_therapy"));
        assertEquals("Running intentions should be 0", 0, ts.getC().getNbRunningIntentions());
        assertTrue("Should be in recovery state", board.isInRecovery("follow_therapy"));

        // Khi recovery xong, goi resumeGoal
        board.resumeGoal("follow_therapy");

        assertFalse("Goal should be resumed on board", board.isGoalSuspended("follow_therapy"));
        assertFalse("Jason goal should be resumed", board.isJasonGoalSuspended("alice_patient", "follow_therapy"));
        assertEquals("Running intentions should be restored to 1", 1, ts.getC().getNbRunningIntentions());
    }

    @Test
    public void testAutomaticResumeOnRecoveryCompletion() throws Exception {
        FaultTolerantSchemeBoard.clearPendingRecoveries();

        File file = new File("sample_org.xml");
        Map<String, List<Failure>> failuresByScheme = FaultTolerantXMLReader.parseFailuresFromFile(file);

        FaultTolerantSchemeBoard mainBoard = new FaultTolerantSchemeBoard();
        mainBoard.setSchemeType("therapy_sch");
        for (Failure f : failuresByScheme.get("therapy_sch")) {
            mainBoard.addFailureSpec(f);
        }

        // Setup Alice intention
        jason.asSemantics.Agent patient = new jason.asSemantics.Agent();
        patient.initAg();
        jason.asSemantics.TransitionSystem ts = new jason.asSemantics.TransitionSystem(patient, new jason.asSemantics.Circumstance(), null, new jason.infra.local.LocalAgArch());
        patient.setTS(ts);

        jason.asSemantics.Intention intention = new jason.asSemantics.Intention();
        jason.asSyntax.Plan plan = jason.asSyntax.ASSyntax.parsePlan("+!follow_therapy[scheme(therapy_sch)] <- .wait(1000).");
        jason.asSemantics.IntendedMeans im = new jason.asSemantics.IntendedMeans(
            new jason.asSemantics.Option(plan, new jason.asSemantics.Unifier()),
            jason.asSyntax.ASSyntax.parseTrigger("+!follow_therapy[scheme(therapy_sch)]")
        );
        intention.push(im);
        ts.getC().addRunningIntention(intention);

        mainBoard.registerAgent("alice_patient", patient);

        // Kich hoat loi
        mainBoard.updateOrgBelief("symptoms_cleared(alice, 3)");

        assertTrue("Goal follow_therapy should be suspended", mainBoard.isGoalSuspended("follow_therapy"));
        assertTrue("Alice intention should be suspended", mainBoard.isJasonGoalSuspended("alice_patient", "follow_therapy"));
        assertEquals(0, ts.getC().getNbRunningIntentions());
        assertTrue("reconsult_scheme should be pending recovery", FaultTolerantSchemeBoard.hasPendingRecovery("reconsult_scheme"));

        // Tao recovery scheme board dai dien cho reconsult_scheme
        FaultTolerantSchemeBoard recoveryBoard = new FaultTolerantSchemeBoard();
        recoveryBoard.setSchemeType("reconsult_scheme");

        // Doctor hoan tat cac muc tieu recovery tren recoveryBoard
        recoveryBoard.goalAchieved("review_symptoms");
        // Goal chua xong hoan toan neu chi review_symptoms, hoac neu adjust_therapy la goal cuoi
        recoveryBoard.goalAchieved("adjust_therapy");

        // KIEM TRA: Main board phai TU DONG RESUME ma KHONG CAN goi resumeGoal thu cong!
        assertFalse("Goal follow_therapy should be AUTOMATICALLY resumed on main board", mainBoard.isGoalSuspended("follow_therapy"));
        assertFalse("Alice jason goal should be AUTOMATICALLY resumed", mainBoard.isJasonGoalSuspended("alice_patient", "follow_therapy"));
        assertEquals("Running intentions should be restored to 1", 1, ts.getC().getNbRunningIntentions());
        assertFalse("reconsult_scheme pending recovery should be cleared", FaultTolerantSchemeBoard.hasPendingRecovery("reconsult_scheme"));
    }
}



