package ora4mas.nopl;

import cartago.OPERATION;
import cartago.OpFeedbackParam;
import jason.asSemantics.Agent;
import jason.asSemantics.Unifier;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import jason.asSyntax.LogicalFormula;
import jason.asSyntax.NumberTerm;
import jason.asSyntax.PredicateIndicator;
import jason.asSyntax.StringTerm;
import jason.asSyntax.Term;
import jason.bb.BeliefBase;
import jason.bb.DefaultBeliefBase;
import jason.infra.local.LocalAgArch;
import jason.infra.local.RunLocalMAS;

import java.io.File;
import java.io.InputStream;
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

    protected Map<String, Agent> registeredAgents = new LinkedHashMap<>();
    protected Map<String, BeliefBase> registeredBeliefBases = new LinkedHashMap<>();
    protected String lastCallingAgent = null;

    {
        evalAgent.initAg();
        evalAgent.setBB(boardBeliefBase);
    }

    public FaultTolerantSchemeBoard() {
        super();
    }

    @Override
    public void init(String osFile, String schType) throws npl.parser.ParseException, moise.common.MoiseException {
        super.init(osFile, schType);
        loadFailuresFromOS(osFile, schType);
    }

    public void registerAgent(String agName, Agent ag) {
        if (agName != null && ag != null) {
            registeredAgents.put(agName, ag);
        }
    }

    public void registerAgent(Agent ag) {
        if (ag != null) {
            String name = null;
            try {
                if (ag.getTS() != null && ag.getTS().getUserAgArch() != null) {
                    name = ag.getTS().getUserAgArch().getAgName();
                }
            } catch (Exception ignored) {}
            if (name == null) {
                name = "agent_" + (registeredAgents.size() + 1);
            }
            registeredAgents.put(name, ag);
        }
    }

    public void unregisterAgent(String agName) {
        if (agName != null) {
            registeredAgents.remove(agName);
        }
    }

    public void registerAgentBeliefBase(String agName, BeliefBase bb) {
        if (agName != null && bb != null) {
            registeredBeliefBases.put(agName, bb);
        }
    }

    public Agent getAgentByName(String name) {
        if (name == null) return null;
        Agent ag = registeredAgents.get(name);
        if (ag != null) return ag;
        try {
            if (RunLocalMAS.getRunner() != null) {
                LocalAgArch arch = RunLocalMAS.getRunner().getAg(name);
                if (arch != null && arch.getTS() != null) {
                    return arch.getTS().getAg();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void loadFailuresFromOS(String osPath, String schemeType) {
        try {
            File f = resolveOSFile(osPath);
            Map<String, List<Failure>> map = null;
            if (f != null && f.exists()) {
                map = FaultTolerantXMLReader.parseFailuresFromFile(f);
            } else {
                InputStream is = getClass().getClassLoader().getResourceAsStream(osPath);
                if (is == null) {
                    is = getClass().getClassLoader().getResourceAsStream("org/" + osPath);
                }
                if (is != null) {
                    map = FaultTolerantXMLReader.parseFailuresFromStream(is);
                }
            }

            if (map != null) {
                List<Failure> list = map.get(schemeType);
                if (list != null) {
                    for (Failure fail : list) {
                        failures.put(fail.getGoalId(), fail);
                        logger.info("Loaded failure spec for goal: " + fail.getGoalId() + " in scheme: " + schemeType);
                    }
                }
            } else {
                logger.warning("Could not find OS file: " + osPath);
            }
        } catch (Exception e) {
            logger.warning("Could not load failure specs from " + osPath + ": " + e.getMessage());
        }
    }

    private File resolveOSFile(String osPath) {
        if (osPath == null) return null;
        String cleanPath = osPath;
        if (cleanPath.startsWith("file:")) {
            cleanPath = cleanPath.substring(5);
        }
        while (cleanPath.startsWith("/") || cleanPath.startsWith("\\")) {
            cleanPath = cleanPath.substring(1);
        }

        File f = new File(cleanPath);
        if (f.exists()) return f;

        f = new File("src/org", cleanPath);
        if (f.exists()) return f;

        f = new File("src", cleanPath);
        if (f.exists()) return f;

        f = new File("../src/org", cleanPath);
        if (f.exists()) return f;

        // Try getting just the filename
        String fileName = new File(cleanPath).getName();
        f = new File("src/org", fileName);
        if (f.exists()) return f;

        f = new File(fileName);
        if (f.exists()) return f;

        return null;
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
    public void registerAgentBelief(String agName, String literalStr) {
        try {
            Literal lit = ASSyntax.parseLiteral(literalStr);
            Agent ag = registeredAgents.get(agName);
            if (ag == null) {
                ag = new Agent();
                ag.initAg();
                registeredAgents.put(agName, ag);
            }
            ag.getBB().add(lit);
            logger.info("Registered belief for agent " + agName + ": " + lit);
            evaluateFailures();
        } catch (Exception e) {
            logger.warning("Error registering belief for agent " + agName + ": " + e.getMessage());
            failed("Error registering agent belief: " + e.getMessage());
        }
    }

    @OPERATION
    public void shareAgentBelief(String literalStr) {
        String caller = null;
        try {
            caller = getOpUserName();
        } catch (Exception ignored) {}
        if (caller == null) {
            caller = "caller_agent";
        }
        registerAgentBelief(caller, literalStr);
    }

    @OPERATION
    public void updateOrgBelief(String literalStr) {
        try {
            try {
                lastCallingAgent = getOpUserName();
            } catch (Exception ignored) {}

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

    /**
     * Resolves the value of an argument specification:
     * 1. Looks into the agent's belief base for a belief matching functor = id and arity = arity (e.g. delivery_address(..)).
     * 2. If arity == 1: extracts the single parameter inside the parentheses.
     *    If arity > 1: extracts the list of parameters inside the parentheses.
     *    If arity == 0: returns the functor.
     * 3. Fallback: checks condition unifier or returns the argument id.
     */
    public Object resolveArgumentValue(ArgumentSpec argSpec, Unifier unif) {
        String id = argSpec.getId();
        int arity = argSpec.getArity();

     
        Literal foundBelief = findBeliefInAgents(id, arity);
        if (foundBelief != null) {
            Object val = extractValueFromLiteral(foundBelief, arity);
            logger.info("Resolved argument '" + id + "' (arity " + arity + ") from agent belief base: " + foundBelief + " -> " + val);
            return val;
        }

        if (unif != null) {
            Term val = unif.get(id);
            if (val == null && id.length() > 0) {
                String capitalized = Character.toUpperCase(id.charAt(0)) + (id.length() > 1 ? id.substring(1) : "");
                val = unif.get(capitalized);
            }
            if (val == null && id.contains("_")) {
                String prefix = id.substring(0, id.indexOf('_'));
                val = unif.get(prefix);
                if (val == null) {
                    val = unif.get(Character.toUpperCase(prefix.charAt(0)) + prefix.substring(1));
                }
            }
            if (val != null) {
                Object termVal = extractTermValue(val);
                logger.info("Resolved argument '" + id + "' from condition unifier: " + val + " -> " + termVal);
                return termVal;
            }
        }

        logger.warning("Could not resolve argument '" + id + "' (arity " + arity + "). Using fallback id.");
        return id;
    }

    public Literal findBeliefInAgents(String id, int arity) {
        PredicateIndicator pi = new PredicateIndicator(id, arity);

        String caller = lastCallingAgent;
        if (caller == null) {
            try {
                caller = getOpUserName();
            } catch (Exception ignored) {}
        }
        if (caller != null) {
            Agent callerAg = getAgentByName(caller);
            if (callerAg != null) {
                Literal lit = findInBeliefBase(callerAg.getBB(), pi);
                if (lit != null) return lit;
            }
            BeliefBase callerBB = registeredBeliefBases.get(caller);
            if (callerBB != null) {
                Literal lit = findInBeliefBase(callerBB, pi);
                if (lit != null) return lit;
            }
        }


        for (Agent ag : registeredAgents.values()) {
            Literal lit = findInBeliefBase(ag.getBB(), pi);
            if (lit != null) return lit;
        }


        for (BeliefBase bb : registeredBeliefBases.values()) {
            Literal lit = findInBeliefBase(bb, pi);
            if (lit != null) return lit;
        }


        try {
            if (RunLocalMAS.getRunner() != null) {
                Map<String, LocalAgArch> masAgs = RunLocalMAS.getRunner().getAgs();
                if (masAgs != null) {
                    for (LocalAgArch arch : masAgs.values()) {
                        if (arch != null && arch.getTS() != null && arch.getTS().getAg() != null) {
                            Literal lit = findInBeliefBase(arch.getTS().getAg().getBB(), pi);
                            if (lit != null) return lit;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}


        Literal lit = findInBeliefBase(boardBeliefBase, pi);
        if (lit != null) return lit;

        return null;
    }

    protected Literal findInBeliefBase(BeliefBase bb, PredicateIndicator pi) {
        if (bb == null) return null;
        try {
            Iterator<Literal> it = bb.getCandidateBeliefs(pi);
            if (it != null && it.hasNext()) {
                return it.next();
            }
        } catch (Exception ignored) {}

        try {
            for (Literal b : bb) {
                if (b.getFunctor().equals(pi.getFunctor()) && b.getArity() == pi.getArity()) {
                    return b;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    protected Object extractValueFromLiteral(Literal lit, int arity) {
        if (lit == null) return null;
        if (arity == 1 && lit.getArity() >= 1) {
            return extractTermValue(lit.getTerm(0));
        } else if (arity > 1) {
            List<Object> terms = new ArrayList<>();
            for (Term t : lit.getTerms()) {
                terms.add(extractTermValue(t));
            }
            return terms;
        } else {
            return lit.getFunctor();
        }
    }

    public static Object extractTermValue(Term t) {
        if (t == null) return null;
        if (t.isString()) {
            return ((StringTerm) t).getString();
        } else if (t.isNumeric()) {
            try {
                double d = ((NumberTerm) t).solve();
                if (d == (long) d) {
                    return (long) d;
                }
                return d;
            } catch (Exception e) {
                return t.toString();
            }
        } else {
            return t.toString();
        }
    }

    protected void handleError(Failure failure, ErrorSpec error, Unifier unif) {
        String goalId = failure.getGoalId();
        suspendedGoals.add(goalId);

        List<Object> resolvedArgs = new ArrayList<>();
        for (ArgumentSpec argSpec : error.getArguments()) {
            Object resolvedVal = resolveArgumentValue(argSpec, unif);
            resolvedArgs.add(resolvedVal);

            try {
                setArgumentValue(goalId, argSpec.getId(), resolvedVal);
            } catch (Exception ignored) {}
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
