package ora4mas.nopl;

import cartago.OPERATION;
import cartago.OpFeedbackParam;
import jason.asSemantics.Agent;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import jason.asSyntax.LogicalFormula;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;
import jason.bb.BeliefBase;
import jason.bb.DefaultBeliefBase;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

import moise.os.fs.ArgumentSpec;
import moise.os.fs.ErrorSpec;
import moise.os.fs.Failure;
import moise.xml.FaultTolerantXMLReader;


public class FaultTolerantSchemeBoard extends SchemeBoard {

    private static final Logger logger = Logger.getLogger(FaultTolerantSchemeBoard.class.getName());

    protected Map<String, Failure> failures = new LinkedHashMap<>();
    protected BeliefBase boardBeliefBase = new DefaultBeliefBase();
    protected Set<String> suspendedGoals = new HashSet<>();
    protected Set<String> failedGoals = new HashSet<>();
    protected Agent evalAgent = new Agent();

    {
        evalAgent.initAg();
        evalAgent.setBB(boardBeliefBase);
    }

    public FaultTolerantSchemeBoard() {
        super();
    }

    public void loadFailuresFromOS(String osPath, String schemeType) {
        try {
            File f = new File(osPath);
            if (f.exists()) {
                Map<String, List<Failure>> map = FaultTolerantXMLReader.parseFailuresFromFile(f);
                List<Failure> list = map.get(schemeType);
                if (list != null) {
                    for (Failure fail : list) {
                        failures.put(fail.getGoalId(), fail);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Could not load failure specs from " + osPath + ": " + e.getMessage());
        }
    }

    public void addFailureSpec(Failure failure) {
        if (failure != null && failure.getGoalId() != null) {
            failures.put(failure.getGoalId(), failure);
        }
    }

    public Map<String, Failure> getFailures() {
        return Collections.unmodifiableMap(failures);
    }

    @OPERATION
    public void updateOrgBelief(String literalStr) {
        try {
            Literal lit = ASSyntax.parseLiteral(literalStr);
            boardBeliefBase.add(lit);
            logger.info("Org belief updated: " + lit);

            evaluateFailures();
        } catch (Exception e) {
            logger.warning("Error in updateOrgBelief for '" + literalStr + "': " + e.getMessage());
            failed("Error parsing/adding belief: " + e.getMessage());
        }
    }

    public void evaluateFailures() {
        for (Failure f : failures.values()) {
            String goalId = f.getGoalId();
            
            if (suspendedGoals.contains(goalId) || failedGoals.contains(goalId)) {
                continue;
            }

            for (ErrorSpec error : f.getErrors()) {
                if (error.getCondition() == null || error.getCondition().trim().isEmpty()) {
                    continue;
                }

                try {
                    LogicalFormula formula = ASSyntax.parseFormula(error.getCondition());
                    Iterator<Unifier> unifs = formula.logicalConsequence(evalAgent, new Unifier());
                    if (unifs != null && unifs.hasNext()) {
                        Unifier unif = unifs.next();
                        handleError(f, error, unif);
                        break;
                    }
                } catch (Exception e) {
                    logger.warning("Error evaluating condition for error " + error.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    protected void handleError(Failure failure, ErrorSpec error, Unifier unif) {
        String goalId = failure.getGoalId();
        suspendedGoals.add(goalId);

 
        List<Object> resolvedArgs = new ArrayList<>();
        for (ArgumentSpec argSpec : error.getArguments()) {
            String varName = argSpec.getId();
           
           Term val = unif.get(varName);
            if (val == null && varName.length() > 0) {
                String capitalized = Character.toUpperCase(varName.charAt(0)) + (varName.length() > 1 ? varName.substring(1) : "");
                val = unif.get(capitalized);
            }
            resolvedArgs.add(val != null ? val.toString() : varName);
        }

        logger.severe("FAILURE DETECTED for goal '" + goalId + "' with error '" + error.getId() + "'. Args: " + resolvedArgs);

        safeDefineObsProperty("goalSuspended", goalId, error.getId());
        safeSignal("org_error", goalId, error.getId(), resolvedArgs.toArray());

       
        if (error.getRecoveryAct() != null) {
            String recoveryScheme = error.getRecoveryAct().getScheme();
            logger.info("Triggering recovery act: scheme " + recoveryScheme);
            triggerRecoveryScheme(recoveryScheme, goalId, error.getId(), resolvedArgs);
        }
    }

    protected void triggerRecoveryScheme(String recoverySchemeId, String goalId, String errorId, List<Object> args) {
        safeSignal("recovery_required", recoverySchemeId, goalId, errorId, args.toArray());
        safeDefineObsProperty("recoverySchemeRequired", recoverySchemeId, goalId);
    }

    @OPERATION
    public void resumeGoal(String goalId) {
        if (suspendedGoals.remove(goalId)) {
            logger.info("Goal '" + goalId + "' has been RESUMED from suspension.");
            safeRemoveObsProperty("goalSuspended");
            safeDefineObsProperty("goalResumed", goalId);
            safeSignal("goal_resumed", goalId);
        }
    }

    @OPERATION
    public void failGoal(String goalId) {
        suspendedGoals.remove(goalId);
        failedGoals.add(goalId);
        logger.severe("Goal '" + goalId + "' has been marked as FAILED.");
        safeRemoveObsProperty("goalSuspended");
        safeDefineObsProperty("goalFailed", goalId);
        safeSignal("goal_failed", goalId);
    }

    protected void safeSignal(String signalName, Object... args) {
        try {
            signal(signalName, args);
        } catch (Exception ignored) {
        }
    }

    protected void safeDefineObsProperty(String name, Object... values) {
        try {
            if (hasObsProperty(name)) {
                getObsProperty(name).updateValues(values);
            } else {
                defineObsProperty(name, values);
            }
        } catch (Exception ignored) {
        }
    }

    protected void safeRemoveObsProperty(String name) {
        try {
            if (hasObsProperty(name)) {
                removeObsProperty(name);
            }
        } catch (Exception ignored) {
        }
    }

    public boolean isGoalSuspended(String goalId) {
        return suspendedGoals.contains(goalId);
    }

    public boolean isGoalFailed(String goalId) {
        return failedGoals.contains(goalId);
    }
}
