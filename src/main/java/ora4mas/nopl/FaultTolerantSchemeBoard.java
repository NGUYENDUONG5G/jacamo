package ora4mas.nopl;

import cartago.OPERATION;
import cartago.OpFeedbackParam;
import jason.asSemantics.ActionExec;
import jason.asSemantics.Agent;
import jason.asSemantics.Circumstance;
import jason.asSemantics.Event;
import jason.asSemantics.IntendedMeans;
import jason.asSemantics.Intention;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Atom;
import jason.asSyntax.ListTerm;
import jason.asSyntax.Literal;
import jason.asSyntax.LogicalFormula;
import jason.asSyntax.NumberTerm;
import jason.asSyntax.PredicateIndicator;
import jason.asSyntax.StringTerm;
import jason.asSyntax.Term;
import jason.asSyntax.Trigger;
import jason.asSyntax.VarTerm;
import jason.bb.BeliefBase;
import jason.bb.DefaultBeliefBase;
import jason.infra.local.LocalAgArch;
import jason.infra.local.RunLocalMAS;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

import moise.os.fs.ArgumentSpec;
import moise.os.fs.ConditionSpec;
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


    protected Map<String, Set<String>> suspendedAgentGoals = new LinkedHashMap<>();

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
                ConditionSpec cond = error.getCondition();
                if (cond == null || cond.isEmpty()) {
                    continue;
                }

                try {
                    LogicalFormula formula = ASSyntax.parseFormula(cond.getExpression());
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
            Term val = null;

           
            if (id != null && !id.isEmpty()) {
                String capitalized = Character.toUpperCase(id.charAt(0)) + (id.length() > 1 ? id.substring(1) : "");
                val = unif.get(capitalized);
            }

           
            if (val == null && id != null) {
                String idLower = id.toLowerCase();
                String[] tokens = idLower.split("_");

          
                for (VarTerm vt : unif) {
                    String vName = vt.getFunctor();
                    if (vName != null && vName.equalsIgnoreCase(id)) {
                        val = unif.get(vt);
                        break;
                    }
                }

               
                if (val == null) {
                    for (VarTerm vt : unif) {
                        String vName = vt.getFunctor();
                        if (vName == null) continue;
                        String vNameLower = vName.toLowerCase();

                        for (String token : tokens) {
                            if (!token.isEmpty() && !token.equals("id") && token.equalsIgnoreCase(vNameLower)) {
                                val = unif.get(vt);
                                break;
                            }
                        }
                        if (val != null) break;
                    }
                }

             
                if (val == null) {
                    for (VarTerm vt : unif) {
                        String vName = vt.getFunctor();
                        if (vName == null) continue;
                        String vNameLower = vName.toLowerCase();
                        if (idLower.contains(vNameLower) || vNameLower.contains(idLower)) {
                            val = unif.get(vt);
                            break;
                        }
                    }
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

    
        suspendJasonGoal(goalId);

        safeDefineObsProperty("goalSuspended", goalId, error.getId());
        safeDefineObsProperty("recoveryState", goalId, "active");
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
    public void suspendGoal(String goalId) {
        suspendedGoals.add(goalId);
        suspendJasonGoal(goalId);
        logger.info("Goal '" + goalId + "' has been proactively SUSPENDED (scheme and Jason intentions).");
        safeDefineObsProperty("goalSuspended", goalId, "proactive_suspension");
        safeDefineObsProperty("recoveryState", goalId, "active");
        safeSignal("goal_suspended", goalId);
    }

    @OPERATION
    public void suspendGoal(String agName, String goalId) {
        suspendedGoals.add(goalId);
        suspendJasonGoal(agName, goalId);
        logger.info("Goal '" + goalId + "' for agent '" + agName + "' has been proactively SUSPENDED.");
        safeDefineObsProperty("goalSuspended", goalId, "proactive_suspension");
        safeDefineObsProperty("recoveryState", goalId, "active");
        safeSignal("goal_suspended", goalId, agName);
    }

    @OPERATION
    public void suspendJasonGoal(String agName, String goalId) {
        Agent ag = getAgentByName(agName);
        if (ag != null) {
            boolean done = suspendJasonGoalForAgent(ag, goalId);
            if (done) {
                suspendedAgentGoals.computeIfAbsent(goalId, k -> new HashSet<>()).add(agName);
                logger.info("Actively suspended Jason goal '" + goalId + "' for agent '" + agName + "'.");
                safeSignal("jason_goal_suspended", agName, goalId);
            }
        } else {
            logger.warning("Cannot suspend Jason goal '" + goalId + "': Agent '" + agName + "' not found.");
        }
    }

    @OPERATION
    public void suspendJasonGoal(String goalId) {
        Set<String> affected = new HashSet<>();

        
        for (String agName : getAgentsCommittedToGoal(goalId)) {
            Agent ag = getAgentByName(agName);
            if (ag != null && suspendJasonGoalForAgent(ag, goalId)) {
                affected.add(agName);
            }
        }

        
        if (lastCallingAgent != null) {
            Agent callerAg = getAgentByName(lastCallingAgent);
            if (callerAg != null && agentHasGoal(callerAg, goalId)) {
                if (suspendJasonGoalForAgent(callerAg, goalId)) {
                    affected.add(lastCallingAgent);
                }
            }
        }

       
        for (Map.Entry<String, Agent> entry : getAllAvailableAgents().entrySet()) {
            Agent ag = entry.getValue();
            if (agentHasGoal(ag, goalId)) {
                if (suspendJasonGoalForAgent(ag, goalId)) {
                    affected.add(entry.getKey());
                }
            }
        }

        if (!affected.isEmpty()) {
            suspendedAgentGoals.computeIfAbsent(goalId, k -> new HashSet<>()).addAll(affected);
            logger.info("Actively suspended Jason goal '" + goalId + "' for agents: " + affected);
            for (String agName : affected) {
                safeSignal("jason_goal_suspended", agName, goalId);
            }
        } else {
            logger.info("No active Jason intentions found to suspend for goal '" + goalId + "'.");
        }
    }

    @OPERATION
    public void resumeGoal(String goalId) {
        boolean wasSuspended = suspendedGoals.remove(goalId);
        resumeJasonGoal(goalId);
        if (wasSuspended || !isJasonGoalSuspended(goalId)) {
            logger.info("Goal '" + goalId + "' has been RESUMED from suspension (scheme and Jason intentions).");
            safeRemoveObsProperty("goalSuspended");
            safeRemoveObsProperty("recoveryState");
            safeDefineObsProperty("goalResumed", goalId);
            safeSignal("goal_resumed", goalId);
        }
    }

    @OPERATION
    public void resumeGoal(String agName, String goalId) {
        resumeJasonGoal(agName, goalId);
        Set<String> ags = suspendedAgentGoals.get(goalId);
        if (ags == null || ags.isEmpty()) {
            suspendedGoals.remove(goalId);
            safeRemoveObsProperty("goalSuspended");
            safeRemoveObsProperty("recoveryState");
            safeDefineObsProperty("goalResumed", goalId);
            safeSignal("goal_resumed", goalId);
        } else {
            safeSignal("goal_resumed", goalId, agName);
        }
    }

    @OPERATION
    public void resumeJasonGoal(String agName, String goalId) {
        Agent ag = getAgentByName(agName);
        if (ag != null) {
            boolean done = resumeJasonGoalForAgent(ag, goalId);
            if (done) {
                Set<String> set = suspendedAgentGoals.get(goalId);
                if (set != null) {
                    set.remove(agName);
                    if (set.isEmpty()) suspendedAgentGoals.remove(goalId);
                }
                logger.info("Actively resumed Jason goal '" + goalId + "' for agent '" + agName + "'.");
                safeSignal("jason_goal_resumed", agName, goalId);
            }
        } else {
            logger.warning("Cannot resume Jason goal '" + goalId + "': Agent '" + agName + "' not found.");
        }
    }

    @OPERATION
    public void resumeJasonGoal(String goalId) {
        Set<String> targetAgents = new HashSet<>();
        Set<String> recorded = suspendedAgentGoals.get(goalId);
        if (recorded != null) {
            targetAgents.addAll(recorded);
        }
        targetAgents.addAll(getAgentsCommittedToGoal(goalId));
        for (Map.Entry<String, Agent> entry : getAllAvailableAgents().entrySet()) {
            if (isJasonGoalSuspended(entry.getKey(), goalId)) {
                targetAgents.add(entry.getKey());
            }
        }

        Set<String> resumedAgents = new HashSet<>();
        for (String agName : targetAgents) {
            Agent ag = getAgentByName(agName);
            if (ag != null && resumeJasonGoalForAgent(ag, goalId)) {
                resumedAgents.add(agName);
            }
        }

        suspendedAgentGoals.remove(goalId);
        if (!resumedAgents.isEmpty()) {
            logger.info("Actively resumed Jason goal '" + goalId + "' for agents: " + resumedAgents);
            for (String agName : resumedAgents) {
                safeSignal("jason_goal_resumed", agName, goalId);
            }
        }
    }

    public boolean suspendJasonGoalForAgent(Agent ag, String goalId) {
        if (ag == null || ag.getTS() == null || goalId == null) return false;
        TransitionSystem ts = ag.getTS();
        Circumstance c = ts.getC();
        boolean suspended = false;

        // 1. Jason standard suspend internal action
        try {
            Literal gLit = ASSyntax.parseLiteral(goalId);
            Object res = new jason.stdlib.suspend().execute(ts, new Unifier(), new Term[] { gLit });
            if (Boolean.TRUE.equals(res)) {
                suspended = true;
            }
        } catch (Exception e) {
            logger.fine("jason.stdlib.suspend error: " + e.getMessage());
        }

        
        Atom reason = ASSyntax.createAtom("org_recovery");
        synchronized (c) {
            try {
                
                for (Intention i : new ArrayList<>(c.getRunningIntentions())) {
                    if (matchesGoalFunctor(i, goalId) && !i.isSuspended()) {
                        i.setSuspended(true);
                        c.removeRunningIntention(i);
                        c.addPendingIntention("suspended-" + i.getId(), reason, i, true);
                        suspended = true;
                    }
                }
                
                for (ActionExec act : new ArrayList<>(c.getPendingActions().values())) {
                    Intention i = act.getIntention();
                    if (i != null && matchesGoalFunctor(i, goalId) && !i.isSuspended()) {
                        i.setSuspended(true);
                        c.addPendingIntention("suspended-" + i.getId(), reason, i, true);
                        suspended = true;
                    }
                }
                
                Intention sel = c.getSelectedIntention();
                if (sel != null && matchesGoalFunctor(sel, goalId) && !sel.isSuspended()) {
                    sel.setSuspended(true);
                    c.addPendingIntention("suspended-self-" + sel.getId(), reason, sel, true);
                    suspended = true;
                }
                
                for (Event ev : new ArrayList<>(c.getEvents())) {
                    Trigger tr = ev.getTrigger();
                    boolean match = (tr != null && tr.isAchvGoal() && tr.getLiteral() != null && goalId.equals(tr.getLiteral().getFunctor()));
                    if (!match && ev.getIntention() != null) {
                        match = matchesGoalFunctor(ev.getIntention(), goalId);
                    }
                    if (match) {
                        c.removeEvent(ev);
                        c.addPendingEvent("suspended-" + ev.getTrigger() + (ev.getIntention() != null ? ev.getIntention().getId() : "0"), reason, ev);
                        if (ev.getIntention() != null) {
                            ev.getIntention().setSuspended(true);
                        }
                        suspended = true;
                    }
                }
            } catch (Exception e) {
                logger.warning("Error during intention suspension for goal " + goalId + ": " + e.getMessage());
            }
        }
        return suspended;
    }

    public boolean resumeJasonGoalForAgent(Agent ag, String goalId) {
        if (ag == null || ag.getTS() == null || goalId == null) return false;
        TransitionSystem ts = ag.getTS();
        Circumstance c = ts.getC();
        boolean resumed = false;

        
        try {
            Literal gLit = ASSyntax.parseLiteral(goalId);
            Object res = new jason.stdlib.resume().execute(ts, new Unifier(), new Term[] { gLit });
            if (Boolean.TRUE.equals(res)) {
                resumed = true;
            }
        } catch (Exception e) {
            logger.fine("jason.stdlib.resume error: " + e.getMessage());
        }

        
        synchronized (c) {
            try {
                
                for (Map.Entry<String, Intention> entry : new ArrayList<>(c.getPendingIntentions().entrySet())) {
                    String key = entry.getKey();
                    if (key.startsWith("suspended-")) {
                        Intention i = entry.getValue();
                        if (matchesGoalFunctor(i, goalId)) {
                            c.removePendingIntention(key);
                            c.resumeIntention(i, null);
                            i.setSuspended(false);
                            resumed = true;
                        }
                    }
                }

               
                for (Map.Entry<String, Event> entry : new ArrayList<>(c.getPendingEvents().entrySet())) {
                    String key = entry.getKey();
                    if (key.startsWith("suspended-")) {
                        Event ev = entry.getValue();
                        Trigger tr = ev.getTrigger();
                        boolean match = (tr != null && tr.isAchvGoal() && tr.getLiteral() != null && goalId.equals(tr.getLiteral().getFunctor()));
                        if (!match && ev.getIntention() != null) {
                            match = matchesGoalFunctor(ev.getIntention(), goalId);
                        }
                        if (match) {
                            c.removePendingEvent(key);
                            c.addEvent(ev);
                            if (ev.getIntention() != null) {
                                ev.getIntention().setSuspended(false);
                            }
                            resumed = true;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error during intention resume for goal " + goalId + ": " + e.getMessage());
            }
        }
        return resumed;
    }

    protected boolean matchesGoalFunctor(Intention i, String goalId) {
        if (i == null || goalId == null) return false;
        try {
            for (IntendedMeans im : i) {
                Trigger tr = im.getTrigger();
                if (tr != null && tr.isAchvGoal() && tr.getLiteral() != null && goalId.equals(tr.getLiteral().getFunctor())) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public boolean agentHasGoal(Agent ag, String goalId) {
        if (ag == null || ag.getTS() == null || goalId == null) return false;
        try {
            Circumstance c = ag.getTS().getC();
            Trigger g = new Trigger(Trigger.TEOperator.add, Trigger.TEType.achieve, ASSyntax.parseLiteral(goalId));
            Unifier u = new Unifier();

            synchronized (c) {
                
                for (Intention i : new ArrayList<>(c.getRunningIntentions())) {
                    if (i.hasTrigger(g, u) || matchesGoalFunctor(i, goalId)) return true;
                }
                
                for (ActionExec act : new ArrayList<>(c.getPendingActions().values())) {
                    if (act.getIntention() != null && (act.getIntention().hasTrigger(g, u) || matchesGoalFunctor(act.getIntention(), goalId))) return true;
                }
               
                if (c.getSelectedIntention() != null && (c.getSelectedIntention().hasTrigger(g, u) || matchesGoalFunctor(c.getSelectedIntention(), goalId))) return true;
              
                for (Event ev : new ArrayList<>(c.getEvents())) {
                    if (u.unifies(g, ev.getTrigger()) || (ev.getIntention() != null && (ev.getIntention().hasTrigger(g, u) || matchesGoalFunctor(ev.getIntention(), goalId)))) return true;
                }
                
                for (Intention i : new ArrayList<>(c.getPendingIntentions().values())) {
                    if (i.hasTrigger(g, u) || matchesGoalFunctor(i, goalId)) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public List<String> getAgentsCommittedToGoal(String goalId) {
        List<String> list = new ArrayList<>();
        try {
            if (getSpec() != null && getSchState() != null) {
                moise.os.fs.Goal g = getSpec().getGoal(goalId);
                if (g != null) {
                    ListTerm ags = getSchState().getCommittedAgents(g);
                    if (ags != null) {
                        for (Term t : ags) {
                            if (t.isAtom() || t.isString()) {
                                String agName = t.isString() ? ((StringTerm) t).getString() : t.toString();
                                list.add(agName);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("Could not get committed agents for goal " + goalId + ": " + e.getMessage());
        }
        return list;
    }

    public Map<String, Agent> getAllAvailableAgents() {
        Map<String, Agent> all = new LinkedHashMap<>(registeredAgents);
        try {
            if (RunLocalMAS.getRunner() != null) {
                Map<String, LocalAgArch> masAgs = RunLocalMAS.getRunner().getAgs();
                if (masAgs != null) {
                    for (Map.Entry<String, LocalAgArch> entry : masAgs.entrySet()) {
                        if (entry.getValue() != null && entry.getValue().getTS() != null && entry.getValue().getTS().getAg() != null) {
                            all.putIfAbsent(entry.getKey(), entry.getValue().getTS().getAg());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return all;
    }

    public boolean isJasonGoalSuspended(String agName, String goalId) {
        Agent ag = getAgentByName(agName);
        if (ag == null || ag.getTS() == null || goalId == null) return false;
        Circumstance c = ag.getTS().getC();
        synchronized (c) {
            for (Map.Entry<String, Intention> entry : c.getPendingIntentions().entrySet()) {
                if (entry.getKey().startsWith("suspended-") && matchesGoalFunctor(entry.getValue(), goalId)) {
                    return true;
                }
            }
            for (Map.Entry<String, Event> entry : c.getPendingEvents().entrySet()) {
                if (entry.getKey().startsWith("suspended-")) {
                    Event ev = entry.getValue();
                    Trigger tr = ev.getTrigger();
                    if ((tr != null && tr.isAchvGoal() && tr.getLiteral() != null && goalId.equals(tr.getLiteral().getFunctor()))
                            || (ev.getIntention() != null && matchesGoalFunctor(ev.getIntention(), goalId))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isJasonGoalSuspended(String goalId) {
        Set<String> ags = suspendedAgentGoals.get(goalId);
        if (ags != null && !ags.isEmpty()) {
            for (String agName : ags) {
                if (isJasonGoalSuspended(agName, goalId)) return true;
            }
        }
        for (Map.Entry<String, Agent> entry : getAllAvailableAgents().entrySet()) {
            if (isJasonGoalSuspended(entry.getKey(), goalId)) return true;
        }
        return false;
    }

    public boolean isInRecovery(String goalId) {
        return suspendedGoals.contains(goalId);
    }

    @OPERATION
    public void failGoal(String goalId) {
        suspendedGoals.remove(goalId);
        failedGoals.add(goalId);
        suspendedAgentGoals.remove(goalId);
        logger.severe("Goal '" + goalId + "' has been marked as FAILED.");
        safeRemoveObsProperty("goalSuspended");
        safeRemoveObsProperty("recoveryState");
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
