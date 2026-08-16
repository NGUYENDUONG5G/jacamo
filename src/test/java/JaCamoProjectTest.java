import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringReader;

import org.junit.Test;

import jacamo.project.JaCaMoProject;
import jacamo.project.parser.JaCaMoProjectParser;
import jacamo.project.parser.ParseException;

/** JUnit test case for syntax package */
public class JaCamoProjectTest {

    JaCaMoProjectParser parser;

    @Test
    public void testToString() {
        boolean ok = true;
        try {
            parser = new JaCaMoProjectParser(new FileReader("src/test/java/project/p3.jcm") );
            JaCaMoProject project = parser.parse(".");
            assertEquals("product(\"banana\",\"this is a condition\",15000),rft(gui)",
                    project.getAg("b").getAsSetts(false, false).getUserParameter("beliefs"));
            //System.out.println(project);
            parser = new JaCaMoProjectParser(new StringReader(project.toString()));
            parser.parse(".");
        } catch (Exception e) {
            System.err.println("Error:"+e);
            e.printStackTrace();
            ok = false;
        }
        assertTrue(ok);
    }

    @Test
    public void testParse1() throws FileNotFoundException, ParseException {
        parser = new JaCaMoProjectParser(new FileReader("src/test/java/project/p1.jcm") );
        JaCaMoProject project = parser.parse(".");
        System.out.println(project);
    }

    @Test
    public void testParse2() throws FileNotFoundException, ParseException {
        parser = new JaCaMoProjectParser(new FileReader("src/test/java/project/p2.jcm") );
        JaCaMoProject project = parser.parse("src/test/java/project");
        System.out.println(project);
    }

    @Test
    public void testParseInst() throws FileNotFoundException, ParseException {
        parser = new JaCaMoProjectParser(new FileReader("src/test/java/project/p4.jcm") );
        JaCaMoProject project = parser.parse("src/test/java/project");
        System.out.println(project);
    }

    @Test
    public void testParseFailureModel() {
        String jcmSource = "mas test_failure {\n" +
                           "    failure goal_x {\n" +
                           "        error err_y {\n" +
                           "            conditions: battery(low)\n" +
                           "            recovery-activities: \"plan:substitute_plan\"\n" +
                           "        }\n" +
                           "    }\n" +
                           "}\n";
        try {
            parser = new JaCaMoProjectParser(new StringReader(jcmSource));
            JaCaMoProject project = parser.parse(".");
            assertTrue(project.getFailureModel() != null);
            assertEquals(1, project.getFailureModel().getFailures().size());
            
            var failure = project.getFailureModel().getFailures().get(0);
            assertEquals("goal_x", failure.getGoalId());
            assertEquals(1, failure.getErrors().size());
            
            var error = failure.getErrors().get(0);
            assertEquals("err_y", error.getErrorName());
            assertEquals(1, error.getConditions().size());
            assertEquals("battery(low)", error.getConditions().get(0));
            assertEquals(1, error.getRecoveryActivities().size());
            assertEquals("plan:substitute_plan", error.getRecoveryActivities().get(0));
            
            System.out.println("Parse test passed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            org.junit.Assert.fail("Parsing failed: " + e.getMessage());
        }
    }

    @Test
    public void testRecoveryActivitiesExecution() {
        recovery.PlanAdaptation<jason.asSemantics.Agent> planAct = new recovery.PlanAdaptation<>("plan_x");
        assertEquals("plan_x", planAct.getSubstitutePlanTrigger());

        recovery.GoalAdaptation<jason.asSemantics.Agent> goalAct = new recovery.GoalAdaptation<>("goal_y", true);
        assertEquals("goal_y", goalAct.getGoalLiteral());
        assertTrue(goalAct.isCreateNew());

        recovery.EnvironmentAdaptation<jason.asSemantics.Agent> envAct = new recovery.EnvironmentAdaptation<>("wsp_z", "art_a", "op_b");
        assertEquals("wsp_z", envAct.getWorkspaceName());
        assertEquals("art_a", envAct.getArtifactName());
        assertEquals("op_b", envAct.getOperationName());

        recovery.OrganisationAdaptation<jason.asSemantics.Agent> orgAct = new recovery.OrganisationAdaptation<>("group_team.action_c");
        assertEquals("group_team.action_c", orgAct.getOrgDetails());
    }

}

