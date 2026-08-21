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

    @Test
    public void testCompositeConditions() {
        recovery.Condition<String> cond1 = ctx -> ctx.contains("error");
        recovery.Condition<String> cond2 = ctx -> ctx.length() > 5;

        recovery.AndCondition<String> andCond = new recovery.AndCondition<>(cond1, cond2);
        assertTrue(andCond.evaluate("error_critical"));
        org.junit.Assert.assertFalse(andCond.evaluate("err"));
        org.junit.Assert.assertFalse(andCond.evaluate("warning_long"));

        recovery.OrCondition<String> orCond = new recovery.OrCondition<>(cond1, cond2);
        assertTrue(orCond.evaluate("error"));
        assertTrue(orCond.evaluate("warning_long"));
        org.junit.Assert.assertFalse(orCond.evaluate("ok"));
    }

    @Test
    public void testAgentFailureModelManager() {
        String jcmSource = "mas test_standard_agent {\n" +
                           "    agent bob : bob.asl {}\n" +
                           "    failure g1 {\n" +
                           "        error e1 {\n" +
                           "            conditions: battery(low)\n" +
                           "            recovery-activities: \"plan:recharge\"\n" +
                           "        }\n" +
                           "    }\n" +
                           "}\n";
        try {
            parser = new JaCaMoProjectParser(new StringReader(jcmSource));
            JaCaMoProject project = parser.parse(".");
            
            var fmParams = project.getFailureModel();
            assertTrue(fmParams != null);
            
            var failures = recovery.builder.FailureModelBuilder.buildFailures(fmParams);
            assertEquals(1, failures.size());
            assertEquals("g1", failures.get(0).getGoalId());
            assertEquals(1, failures.get(0).getErrors().size());
            assertEquals("e1", failures.get(0).getErrors().get(0).getErrorName());
        } catch (Exception e) {
            org.junit.Assert.fail("Test failed: " + e.getMessage());
        }
    }

    @Test
    public void testScopedFailuresInJcm() {
        String jcmSource = "mas test_scoped_failures {\n" +
                           "    agent icleaner : icleaner.asl {\n" +
                           "        failure: battery_loaded, move_fail\n" +
                           "    }\n" +
                           "    organisation o1 : my-org.xml {\n" +
                           "        failure: org_fail_1\n" +
                           "        group cleaner_team : team {\n" +
                           "            failure: missing_cleaner_role\n" +
                           "        }\n" +
                           "        scheme s1 : cleaning_scheme {\n" +
                           "            failure: scheme_stalled\n" +
                           "        }\n" +
                           "    }\n" +
                           "    failure battery_loaded {\n" +
                           "        error e1 {\n" +
                           "            conditions: battery(low)\n" +
                           "            recovery-activities: \"plan:recharge\"\n" +
                           "        }\n" +
                           "    }\n" +
                           "}\n";
        try {
            parser = new JaCaMoProjectParser(new StringReader(jcmSource));
            JaCaMoProject project = parser.parse(".");

            var ag = (jacamo.project.JaCaMoAgentParameters) project.getAg("icleaner");
            assertEquals(2, ag.getFailures().size());
            assertTrue(ag.getFailures().contains("battery_loaded"));
            assertTrue(ag.getFailures().contains("move_fail"));

            var org = project.getOrg("o1");
            assertEquals(1, org.getFailures().size());
            assertTrue(org.getFailures().contains("org_fail_1"));

            var grp = org.getGroup("cleaner_team");
            assertEquals(1, grp.getFailures().size());
            assertTrue(grp.getFailures().contains("missing_cleaner_role"));

            var sch = org.getScheme("s1");
            assertEquals(1, sch.getFailures().size());
            assertTrue(sch.getFailures().contains("scheme_stalled"));

            System.out.println("Scoped failures test passed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            org.junit.Assert.fail("Scoped failures parsing failed: " + e.getMessage());
        }
    }

    @Test
    public void testOrgFailureModelManager() {
        String jcmSource = "mas test_org_monitoring {\n" +
                           "    organisation o1 : my-org.xml {\n" +
                           "        failure: org_fail_1\n" +
                           "        group cleaner_team : team {\n" +
                           "            failure: missing_cleaner_role\n" +
                           "        }\n" +
                           "    }\n" +
                           "    failure org_fail_1 {\n" +
                           "        error e_org {\n" +
                           "            conditions: org_broken\n" +
                           "            recovery-activities: \"org:cleaner_team.adoptRole(leader)\"\n" +
                           "        }\n" +
                           "    }\n" +
                           "    failure missing_cleaner_role {\n" +
                           "        error e_grp {\n" +
                           "            conditions: \"cardinality(cleaner) < 1\"\n" +
                           "            recovery-activities: \"org:cleaner_team.adoptRole(cleaner)\"\n" +
                           "        }\n" +
                           "    }\n" +
                           "}\n";
        try {
            parser = new JaCaMoProjectParser(new StringReader(jcmSource));
            JaCaMoProject project = parser.parse(".");

            recovery.setup.OrgFailureModelManager orgMgr = new recovery.setup.OrgFailureModelManager(project);
            orgMgr.setup();

            assertEquals(2, orgMgr.getOrgFailuresMap().size());
            
            // Test that monitor() runs cleanly without exceptions
            orgMgr.monitor();
            System.out.println("OrgFailureModelManager test passed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            org.junit.Assert.fail("OrgFailureModelManager failed: " + e.getMessage());
        }
    }

    @Test
    public void testBranchesExecution() {
        try {
            jason.asSemantics.Agent ag = new jason.asSemantics.Agent();
            ag.initAg();
            
            // Add plans using .and_branches and .or_branches
            String asl = "+!test_and <- .and_branches([g1, g2]).\n" +
                         "+!g1 <- +g1_done.\n" +
                         "+!g2 <- +g2_done.\n" +
                         "+!test_or <- .or_branches([branch_a, branch_b]).\n" +
                         "+!branch_a : cond_a <- +branch_a_done.\n" +
                         "+!branch_b <- +branch_b_done.\n";
            jason.asSyntax.parser.as2j parser = new jason.asSyntax.parser.as2j(new java.io.StringReader(asl));
            parser.agent(ag);
            
            assertTrue(ag.getPL().size() >= 4);
            System.out.println(".and_branches and .or_branches parsed successfully in agent plan library!");
        } catch (Exception e) {
            e.printStackTrace();
            org.junit.Assert.fail("testBranchesExecution failed: " + e.getMessage());
        }
    }
}



