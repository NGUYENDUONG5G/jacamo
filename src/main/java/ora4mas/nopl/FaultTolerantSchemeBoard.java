package ora4mas.nopl;

import cartago.OPERATION;
import jason.asSemantics.ActionExec;
import jason.asSemantics.Agent;
import jason.asSemantics.Circumstance;
import jason.asSemantics.CircumstanceListener;
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
import jason.stdlib.resume;
import jason.stdlib.suspend;
import moise.common.MoiseException;
import moise.os.fs.ArgumentSpec;
import moise.os.fs.ConditionSpec;
import moise.os.fs.ErrorSpec;
import moise.os.fs.Failure;
import moise.os.fs.Mission;
import moise.os.ns.Norm;
import moise.xml.FaultTolerantXMLReader;
import npl.parser.ParseException;
import ora4mas.nopl.oe.Player;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class FaultTolerantSchemeBoard extends SchemeBoard {

    private static final String SUSPENDED_PREFIX = "suspended-";
    private static final Field ARTIFACT_LOCK_FIELD;

    static {
        Field f = null;
        try {
            f = cartago.Artifact.class.getDeclaredField("lock");
            f.setAccessible(true);
        } catch (Throwable ignored) {
            f = null;
        }
        ARTIFACT_LOCK_FIELD = f;
    }

    public static class PendingRecovery {
        private final FaultTolerantSchemeBoard sourceBoard;
        private final String failedGoalId;
        private final String recoverySchemeType;
        private final String errorId;

        public PendingRecovery(FaultTolerantSchemeBoard sourceBoard, String failedGoalId, String recoverySchemeType, String errorId) {
            this.sourceBoard = sourceBoard;
            this.failedGoalId = failedGoalId;
            this.recoverySchemeType = recoverySchemeType;
            this.errorId = errorId;
        }

        public FaultTolerantSchemeBoard getSourceBoard() {
            return sourceBoard;
        }

        public String getFailedGoalId() {
            return failedGoalId;
        }

        public String getRecoverySchemeType() {
            return recoverySchemeType;
        }

        public String getErrorId() {
            return errorId;
        }
    }

    protected static final CopyOnWriteArrayList<FaultTolerantSchemeBoard> activeBoards = new CopyOnWriteArrayList<>();
    protected static final Map<String, CopyOnWriteArrayList<PendingRecovery>> pendingRecoveries = new ConcurrentHashMap<>();

    private final Logger logger = new Logger("SchemeBoard", null) {
        @Override
        public boolean isLoggable(Level level) {
            return Logger.getLogger(getSchemeName()).isLoggable(level);
        }

        @Override
        public void log(LogRecord record) {
            String name = getSchemeName();
            if (name != null && !name.isEmpty()) {
                record.setLoggerName(name);
                Logger.getLogger(name).log(record);
            } else {
                super.log(record);
            }
        }
    };

    protected Map<String, Failure> failures = new LinkedHashMap<>();
    protected BeliefBase boardBeliefBase = new DefaultBeliefBase();
    protected Set<String> suspendedGoals = new HashSet<>();
    protected Set<String> failedGoals = new HashSet<>();
    protected Set<String> handledErrors = Collections.synchronizedSet(new HashSet<>());
    protected Agent evalAgent = new Agent();

    protected Map<String, Agent> registeredAgents = new LinkedHashMap<>();
    protected Map<String, BeliefBase> registeredBeliefBases = new LinkedHashMap<>();
    protected String lastCallingAgent = null;
    protected Map<String, Set<String>> suspendedAgentGoals = new LinkedHashMap<>();

    protected String osFile = null;
    protected String schemeType = null;
    protected Set<String> achievedGoals = Collections.synchronizedSet(new LinkedHashSet<>());

    protected final Set<String> listenedAgents = Collections.synchronizedSet(new HashSet<>());
    protected ScheduledExecutorService syncExecutor;

    {
        evalAgent.initAg();
        evalAgent.setBB(boardBeliefBase);
        activeBoards.add(this);
    }

    public FaultTolerantSchemeBoard() {
        super();
    }

    public String getSchemeName() {
        try {
            if (getId() != null && getId().getName() != null && !getId().getName().isEmpty()) {
                return getId().getName();
            }
        } catch (Throwable ignored) {}
        if (schemeType != null && !schemeType.isEmpty()) {
            return schemeType;
        }
        if (getSpec() != null && getSpec().getId() != null) {
            return getSpec().getId();
        }
        return "SchemeBoard";
    }

    @Override
    public void destroy() {
        stopBeliefSyncMonitor();
        activeBoards.remove(this);
        super.destroy();
    }

    @Override
    public void init(String osFile, String schType) throws ParseException, MoiseException {
        this.osFile = osFile;
        this.schemeType = schType;
        super.init(osFile, schType);
        loadFailuresFromOS(osFile, schType);
        startBeliefSyncMonitor();
    }

    public synchronized void startBeliefSyncMonitor() {
        if (syncExecutor == null || syncExecutor.isShutdown()) {
            syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "FaultTolerant-BeliefSync-" + getSchemeName());
                t.setDaemon(true);
                return t;
            });
            syncExecutor.scheduleWithFixedDelay(() -> {
                try {
                    syncAllAgentBeliefs();
                    evaluateFailures();
                } catch (Throwable ignored) {}
            }, 300, 300, TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void stopBeliefSyncMonitor() {
        if (syncExecutor != null && !syncExecutor.isShutdown()) {
            syncExecutor.shutdownNow();
            syncExecutor = null;
        }
    }

    private static void addPlayersByRole(Iterable<Player> players, Set<String> roles, Set<String> linked) {
        if (players == null) return;
        for (Player p : players) {
            if (p != null && p.getAg() != null) {
                String role = p.getTarget();
                if (roles.isEmpty() || (role != null && roles.contains(role))) {
                    linked.add(p.getAg());
                }
            }
        }
    }

    public Set<String> getLinkedAgents() {
        Set<String> linked = new HashSet<>(registeredAgents.keySet());

        Set<String> schemeMissionIds = new HashSet<>();
        if (getSpec() != null && getSpec().getMissions() != null) {
            for (Mission m : getSpec().getMissions()) {
                schemeMissionIds.add(m.getId());
            }
        }

        if (getSchState() != null && getSchState().getPlayers() != null) {
            for (Player p : getSchState().getPlayers()) {
                if (p != null && p.getAg() != null
                        && (schemeMissionIds.isEmpty() || schemeMissionIds.contains(p.getTarget()))) {
                    linked.add(p.getAg());
                }
            }
        }

        Set<String> linkedRoles = new HashSet<>();
        try {
            if (getSpec() != null && getSpec().getFS() != null && getSpec().getFS().getOS() != null
                    && getSpec().getFS().getOS().getNS() != null) {
                for (Norm norm : getSpec().getFS().getOS().getNS().getNorms()) {
                    if (norm != null && norm.getMission() != null && norm.getRole() != null
                            && schemeMissionIds.contains(norm.getMission().getId())) {
                        linkedRoles.add(norm.getRole().getId());
                    }
                }
            }
        } catch (Throwable ignored) {}

        String schId = getSchemeName();
        String schType = getSchemeType();
        try {
            Collection<GroupBoard> gbs = GroupBoard.getGroupBoards();
            if (gbs != null) {
                for (GroupBoard gb : gbs) {
                    if (gb == null || gb.getGrpState() == null) continue;
                    Collection<String> respSchemes = gb.getGrpState().getSchemesResponsibleFor();
                    if (respSchemes != null
                            && ((schId != null && respSchemes.contains(schId)) || (schType != null && respSchemes.contains(schType)))) {
                        addPlayersByRole(gb.getGrpState().getPlayers(), linkedRoles, linked);
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            if (getSchState() != null && getSchState().getGroupsResponsibleFor() != null) {
                for (ora4mas.nopl.oe.Group grp : getSchState().getGroupsResponsibleFor()) {
                    if (grp != null) {
                        addPlayersByRole(grp.getPlayers(), linkedRoles, linked);
                    }
                }
            }
        } catch (Throwable ignored) {}

        return linked;
    }

    public synchronized void syncAllAgentBeliefs() {
        for (String agName : getLinkedAgents()) {
            Agent ag = getAgentByName(agName);
            if (ag != null) {
                syncBeliefsFromAgent(agName, ag);
            }
            BeliefBase bb = registeredBeliefBases.get(agName);
            if (bb != null) {
                syncBeliefsFromBB(agName, bb);
            }
        }
    }

    public void syncBeliefsFromAgent(String agName, Agent ag) {
        if (ag == null || agName == null) return;
        attachAgentListener(agName, ag);
        syncBeliefsFromBB(agName, ag.getBB());
    }

    public void syncBeliefsFromBB(String agName, BeliefBase bb) {
        if (bb == null) return;
        try {
            for (Literal lit : bb) {
                if (boardBeliefBase.contains(lit) == null) {
                    boardBeliefBase.add(lit.copy());
                    logger.fine("Synced belief from " + agName + ": " + lit);
                }
            }
        } catch (Exception ignored) {}
    }

    public void attachAgentListener(String agName, Agent ag) {
        if (ag == null || agName == null || listenedAgents.contains(agName)) return;
        try {
            if (ag.getTS() != null && ag.getTS().getC() != null) {
                ag.getTS().getC().addEventListener(new CircumstanceListener() {
                    @Override
                    public void eventAdded(Event ev) {
                        if (ev == null) return;
                        Trigger trig = ev.getTrigger();
                        if (trig != null && !trig.isGoal()) {
                            Literal lit = trig.getLiteral();
                            if (lit != null) {
                                if (trig.isAddition()) {
                                    onAgentBeliefAdded(agName, lit);
                                } else {
                                    onAgentBeliefRemoved(agName, lit);
                                }
                            }
                        }
                    }
                });
                listenedAgents.add(agName);
                logger.fine("Attached automatic belief listener to agent '" + agName + "' on scheme '" + getSchemeName() + "'.");
            }
        } catch (Exception e) {
            logger.warning("Could not attach listener to agent " + agName + ": " + e.getMessage());
        }
    }

    public synchronized void onAgentBeliefAdded(String agName, Literal lit) {
        if (lit == null || agName == null) return;
        try {
            Literal copyLit = lit.copy();
            if (boardBeliefBase.contains(copyLit) == null) {
                boardBeliefBase.add(copyLit);
                if (syncExecutor != null && !syncExecutor.isShutdown()) {
                    syncExecutor.submit(this::evaluateFailures);
                } else {
                    evaluateFailures();
                }
            }
        } catch (Exception e) {
            logger.warning("Error processing auto-synced belief from " + agName + ": " + e.getMessage());
        }
    }

    public synchronized void onAgentBeliefRemoved(String agName, Literal lit) {
        if (lit == null || agName == null) return;
        try {
            if (boardBeliefBase.contains(lit) != null) {
                boardBeliefBase.remove(lit);
                logger.fine("Belief removed from linked agent '" + agName + "': " + lit);
            }
        } catch (Exception e) {
            logger.warning("Error processing belief removal from " + agName + ": " + e.getMessage());
        }
    }

    public String getSchemeType() {
        if (schemeType != null) return schemeType;
        return getSpec() != null ? getSpec().getId() : null;
    }

    public void setSchemeType(String schType) {
        this.schemeType = schType;
    }

    public Set<String> getAchievedGoals() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(achievedGoals));
    }

    public static void clearPendingRecoveries() {
        pendingRecoveries.clear();
    }

    public static boolean hasPendingRecovery(String recoverySchemeId) {
        CopyOnWriteArrayList<PendingRecovery> list = pendingRecoveries.get(recoverySchemeId);
        return list != null && !list.isEmpty();
    }

    public void registerAgent(String agName, Agent ag) {
        if (agName != null && ag != null) {
            registeredAgents.put(agName, ag);
            syncBeliefsFromAgent(agName, ag);
            evaluateFailures();
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
            registerAgent(name, ag);
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
                ClassLoader cl = getClass().getClassLoader();
                InputStream is = cl.getResourceAsStream(osPath);
                if (is == null) {
                    is = cl.getResourceAsStream("org/" + osPath);
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

        String fileName = new File(cleanPath).getName();
        File[] candidates = {
                new File(cleanPath),
                new File("src/org", cleanPath),
                new File("src", cleanPath),
                new File("../src/org", cleanPath),
                new File("src/org", fileName),
                new File(fileName)
        };
        for (File c : candidates) {
            if (c.exists()) return c;
        }
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
        registerAgentBelief(caller != null ? caller : "caller_agent", literalStr);
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

    public synchronized void evaluateFailures() {
        syncAllAgentBeliefs();
        for (Failure f : failures.values()) {
            String goalId = f.getGoalId();
            if (suspendedGoals.contains(goalId) || failedGoals.contains(goalId)) {
                continue;
            }

            for (ErrorSpec error : f.getErrors()) {
                if (handledErrors.contains(goalId + "::" + error.getId())) {
                    continue;
                }
                ConditionSpec cond = error.getCondition();
                if (cond == null || cond.isEmpty()) {
                    continue;
                }

                try {
                    LogicalFormula formula = ASSyntax.parseFormula(cond.getExpression());
                    Iterator<Unifier> unifs = formula.logicalConsequence(evalAgent, new Unifier());
                    if (unifs != null && unifs.hasNext()) {
                        handleError(f, error, unifs.next());
                        break;
                    }
                } catch (Exception e) {
                    logger.warning("Error evaluating condition for error " + error.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    private static Term findTermInUnifier(String id, Unifier unif) {
        if (id == null) return null;

        if (!id.isEmpty()) {
            Term val = unif.get(Character.toUpperCase(id.charAt(0)) + id.substring(1));
            if (val != null) return val;
        }

        for (VarTerm vt : unif) {
            String vName = vt.getFunctor();
            if (vName != null && vName.equalsIgnoreCase(id)) {
                return unif.get(vt);
            }
        }

        String idLower = id.toLowerCase();
        String[] tokens = idLower.split("_");

        for (VarTerm vt : unif) {
            String vName = vt.getFunctor();
            if (vName == null) continue;
            for (String token : tokens) {
                if (!token.isEmpty() && !token.equals("id") && token.equalsIgnoreCase(vName)) {
                    return unif.get(vt);
                }
            }
        }

        for (VarTerm vt : unif) {
            String vName = vt.getFunctor();
            if (vName == null) continue;
            String vNameLower = vName.toLowerCase();
            if (idLower.contains(vNameLower) || vNameLower.contains(idLower)) {
                return unif.get(vt);
            }
        }
        return null;
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
            Term val = findTermInUnifier(id, unif);
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
            Literal lit = findInBeliefBase(registeredBeliefBases.get(caller), pi);
            if (lit != null) return lit;
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

        return findInBeliefBase(boardBeliefBase, pi);
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
        }
        if (arity > 1) {
            List<Object> terms = new ArrayList<>();
            for (Term t : lit.getTerms()) {
                terms.add(extractTermValue(t));
            }
            return terms;
        }
        return lit.getFunctor();
    }

    public static Object extractTermValue(Term t) {
        if (t == null) return null;
        if (t.isString()) {
            return ((StringTerm) t).getString();
        }
        if (t.isNumeric()) {
            try {
                double d = ((NumberTerm) t).solve();
                if (d == (long) d) {
                    return (long) d;
                }
                return d;
            } catch (Exception e) {
                return t.toString();
            }
        }
        return t.toString();
    }

    protected void handleError(Failure failure, ErrorSpec error, Unifier unif) {
        String goalId = failure.getGoalId();
        suspendedGoals.add(goalId);
        handledErrors.add(goalId + "::" + error.getId());

        List<Object> resolvedArgs = new ArrayList<>();
        for (ArgumentSpec argSpec : error.getArguments()) {
            Object resolvedVal = resolveArgumentValue(argSpec, unif);
            resolvedArgs.add(resolvedVal);
            safeSetArgumentValue(goalId, argSpec.getId(), resolvedVal);
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
        pendingRecoveries.computeIfAbsent(recoverySchemeId, k -> new CopyOnWriteArrayList<>())
                .add(new PendingRecovery(this, goalId, recoverySchemeId, errorId));
        logger.info("Registered pending recovery: recovery scheme '" + recoverySchemeId + "' for failed goal '" + goalId + "' (error: " + errorId + ").");

        autoCreateRecoverySchemeArtifact(recoverySchemeId);

        safeSignal("recovery_required", recoverySchemeId, goalId, errorId, args.toArray());
        safeDefineObsProperty("recoverySchemeRequired", recoverySchemeId, goalId);
    }

    private String resolveKnownOsPath() {
        if (osFile != null) return osFile;
        for (FaultTolerantSchemeBoard b : activeBoards) {
            if (b != null && b.osFile != null) {
                return b.osFile;
            }
        }
        try {
            Collection<OrgBoard> orgBoards = OrgBoard.getOrbBoards();
            if (orgBoards != null) {
                for (OrgBoard ob : orgBoards) {
                    if (ob != null && ob.getOSFile() != null) {
                        return ob.getOSFile();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void linkRecoverySchemeToGroupBoards(String recoverySchemeId) {
        try {
            Collection<GroupBoard> groupBoards = GroupBoard.getGroupBoards();
            if (groupBoards == null) return;
            for (GroupBoard gb : groupBoards) {
                try {
                    cartago.Workspace targetWsp = null;
                    try {
                        Field f = cartago.Artifact.class.getDeclaredField("wsp");
                        f.setAccessible(true);
                        targetWsp = (cartago.Workspace) f.get(gb);
                    } catch (Throwable ignored) {}

                    if (targetWsp != null) {
                        cartago.ICartagoContext ctx = targetWsp.joinWorkspace(
                                new cartago.AgentIdCredential("FaultTolerantSystem"),
                                new cartago.ICartagoCallback() {
                                    public void notifyCartagoEvent(cartago.CartagoEvent a) {}
                                }
                        );
                        String gbArtName = gb.getGrpState() != null ? gb.getGrpState().getId() : "t_group";
                        ctx.doAction(1, gbArtName, new cartago.Op("addSchemeWhenFormationOk", new Object[]{recoverySchemeId}), null, -1);
                        logger.info("GroupBoard automatically linked recovery scheme '" + recoverySchemeId + "'.");
                    } else if (gb.getGrpState() != null) {
                        gb.getGrpState().addResponsibleForScheme(recoverySchemeId);
                    }
                } catch (Throwable e) {
                    logger.warning("GroupBoard link notice: " + e.getMessage());
                }
            }
        } catch (Throwable ignored) {}
    }

    protected void autoCreateRecoverySchemeArtifact(String recoverySchemeId) {
        String osPath = resolveKnownOsPath();
        if (osPath == null) {
            logger.warning("Cannot auto-create recovery scheme artifact '" + recoverySchemeId + "': OS specification file is unknown.");
            return;
        }

        boolean created = false;
        try {
            if (lookupArtifact(recoverySchemeId) != null) {
                created = true;
                logger.info("Recovery scheme artifact '" + recoverySchemeId + "' already exists.");
            }
        } catch (Throwable ignored) {}

        if (!created) {
            try {
                makeArtifact(recoverySchemeId, getClass().getName(), new cartago.ArtifactConfig(osPath, recoverySchemeId));
                logger.info("FaultTolerantSchemeBoard automatically created recovery scheme artifact '" + recoverySchemeId + "'.");
                created = true;
            } catch (Throwable e) {
                logger.warning("makeArtifact failed for '" + recoverySchemeId + "': " + e.getMessage());
            }
        }

        if (created) {
            safeDefineObsProperty("recoverySchemeCreated", recoverySchemeId);
            safeSignal("recovery_scheme_created", recoverySchemeId);
            linkRecoverySchemeToGroupBoards(recoverySchemeId);
        }
    }

    @OPERATION
    @Override
    public void goalAchieved(String goal) {
        try {
            super.goalAchieved(goal);
        } catch (Exception e) {
            logger.fine("super.goalAchieved notice: " + e.getMessage());
        }
        achievedGoals.add(goal);
        logger.info("Goal achieved on board '" + getSchemeType() + "': " + goal);
        checkAndResumeOnRecoverySuccess(goal);
    }

    protected void checkAndResumeOnRecoverySuccess(String achievedGoal) {
        String currentSch = getSchemeType();

        if (currentSch != null && pendingRecoveries.containsKey(currentSch) && isSchemeCompleted(achievedGoal)) {
            completeRecoveryForScheme(currentSch);
            return;
        }

        for (Map.Entry<String, CopyOnWriteArrayList<PendingRecovery>> entry : pendingRecoveries.entrySet()) {
            String recSchType = entry.getKey();
            for (PendingRecovery rec : entry.getValue()) {
                if (rec.getSourceBoard() == this && isRecoveryGoalAchievedOnBoard(recSchType, achievedGoal)) {
                    completeRecoveryForScheme(recSchType);
                    return;
                }
            }
        }
    }

    private boolean allLeafGoalsAchieved(Collection<moise.os.fs.Goal> goals) {
        if (goals == null || goals.isEmpty()) return false;
        Set<String> leafGoalIds = new HashSet<>();
        for (moise.os.fs.Goal g : goals) {
            if (!g.hasPlan()) {
                leafGoalIds.add(g.getId());
            }
        }
        return !leafGoalIds.isEmpty() && achievedGoals.containsAll(leafGoalIds);
    }

    private static boolean hasLeafGoals(Collection<moise.os.fs.Goal> goals) {
        if (goals == null) return false;
        for (moise.os.fs.Goal g : goals) {
            if (!g.hasPlan()) return true;
        }
        return false;
    }

    public boolean isSchemeCompleted(String lastGoal) {
        if (getSpec() != null) {
            Collection<moise.os.fs.Goal> goals = getSpec().getGoals();
            if (hasLeafGoals(goals)) {
                return allLeafGoalsAchieved(goals);
            }
        }
        return !achievedGoals.isEmpty();
    }

    protected boolean isRecoveryGoalAchievedOnBoard(String recSchType, String achievedGoal) {
        if (getSpec() != null && getSpec().getFS() != null) {
            moise.os.fs.Scheme recSpec = getSpec().getFS().findScheme(recSchType);
            if (recSpec != null && hasLeafGoals(recSpec.getGoals())) {
                return allLeafGoalsAchieved(recSpec.getGoals());
            }
        }
        return achievedGoal != null && achievedGoal.contains(recSchType);
    }

    public void completeRecoveryForScheme(String recSchType) {
        CopyOnWriteArrayList<PendingRecovery> list = pendingRecoveries.remove(recSchType);
        if (list == null || list.isEmpty()) return;
        for (PendingRecovery rec : list) {
            FaultTolerantSchemeBoard src = rec.getSourceBoard();
            String failedGoal = rec.getFailedGoalId();
            String msg = "Recovery scheme '" + recSchType + "' succeeded! Automatically RESUMING failed goal '" + failedGoal + "' on source board.";
            if (src != null) {
                src.logger.info(msg);
                src.resumeGoal(failedGoal);
                src.safeSignal("recovery_succeeded", recSchType, failedGoal);
                src.safeDefineObsProperty("recoverySucceeded", recSchType, failedGoal);
            } else {
                Logger.getLogger(recSchType).info(msg);
            }
        }
    }

    @OPERATION
    public void recoveryFinished() {
        String st = getSchemeType();
        if (st != null) {
            completeRecoveryForScheme(st);
        }
    }

    @OPERATION
    public void recoveryFinished(String recoverySchemeId) {
        completeRecoveryForScheme(recoverySchemeId);
    }

    @OPERATION
    public void reportRecoverySuccess(String recoverySchemeId) {
        completeRecoveryForScheme(recoverySchemeId);
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
            if (suspendJasonGoalForAgent(ag, goalId)) {
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
            if (callerAg != null && agentHasGoal(callerAg, goalId) && suspendJasonGoalForAgent(callerAg, goalId)) {
                affected.add(lastCallingAgent);
            }
        }

        for (Map.Entry<String, Agent> entry : getAllAvailableAgents().entrySet()) {
            Agent ag = entry.getValue();
            if (agentHasGoal(ag, goalId) && suspendJasonGoalForAgent(ag, goalId)) {
                affected.add(entry.getKey());
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
            markGoalResumed(goalId);
        }
    }

    @OPERATION
    public void resumeGoal(String agName, String goalId) {
        resumeJasonGoal(agName, goalId);
        Set<String> ags = suspendedAgentGoals.get(goalId);
        if (ags == null || ags.isEmpty()) {
            suspendedGoals.remove(goalId);
            markGoalResumed(goalId);
        } else {
            safeSignal("goal_resumed", goalId, agName);
        }
    }

    private void markGoalResumed(String goalId) {
        safeRemoveObsProperty("goalSuspended");
        safeRemoveObsProperty("recoveryState");
        safeDefineObsProperty("goalResumed", goalId);
        safeSignal("goal_resumed", goalId);
    }

    @OPERATION
    public void resumeJasonGoal(String agName, String goalId) {
        Agent ag = getAgentByName(agName);
        if (ag != null) {
            if (resumeJasonGoalForAgent(ag, goalId)) {
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
        for (String agName : getAllAvailableAgents().keySet()) {
            if (isJasonGoalSuspended(agName, goalId)) {
                targetAgents.add(agName);
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

    private static boolean isGoalTrigger(Trigger tr, String goalId) {
        return tr != null && tr.isAchvGoal() && tr.getLiteral() != null && goalId.equals(tr.getLiteral().getFunctor());
    }

    private boolean eventMatchesGoal(Event ev, String goalId) {
        return isGoalTrigger(ev.getTrigger(), goalId)
                || (ev.getIntention() != null && matchesGoalFunctor(ev.getIntention(), goalId));
    }

    public boolean suspendJasonGoalForAgent(Agent ag, String goalId) {
        if (ag == null || ag.getTS() == null || goalId == null) return false;
        TransitionSystem ts = ag.getTS();
        Circumstance c = ts.getC();
        boolean suspended = false;

        try {
            Literal gLit = ASSyntax.parseLiteral(goalId);
            if (Boolean.TRUE.equals(new suspend().execute(ts, new Unifier(), new Term[]{gLit}))) {
                suspended = true;
            }
        } catch (Exception e) {
            logger.fine("jason.stdlib.suspend error: " + e.getMessage());
        }

        Atom reason = ASSyntax.createAtom("org_recovery");
        synchronized (c) {
            try {
                for (Intention i : new ArrayList<>(c.getRunningIntentions())) {
                    if (!i.isSuspended() && matchesGoalFunctor(i, goalId)) {
                        i.setSuspended(true);
                        c.removeRunningIntention(i);
                        c.addPendingIntention(SUSPENDED_PREFIX + i.getId(), reason, i, true);
                        suspended = true;
                    }
                }

                for (ActionExec act : new ArrayList<>(c.getPendingActions().values())) {
                    Intention i = act.getIntention();
                    if (i != null && !i.isSuspended() && matchesGoalFunctor(i, goalId)) {
                        i.setSuspended(true);
                        c.addPendingIntention(SUSPENDED_PREFIX + i.getId(), reason, i, true);
                        suspended = true;
                    }
                }

                Intention sel = c.getSelectedIntention();
                if (sel != null && !sel.isSuspended() && matchesGoalFunctor(sel, goalId)) {
                    sel.setSuspended(true);
                    c.addPendingIntention(SUSPENDED_PREFIX + "self-" + sel.getId(), reason, sel, true);
                    suspended = true;
                }

                for (Event ev : new ArrayList<>(c.getEvents())) {
                    if (eventMatchesGoal(ev, goalId)) {
                        c.removeEvent(ev);
                        c.addPendingEvent(SUSPENDED_PREFIX + ev.getTrigger() + (ev.getIntention() != null ? ev.getIntention().getId() : "0"), reason, ev);
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
            if (Boolean.TRUE.equals(new resume().execute(ts, new Unifier(), new Term[]{gLit}))) {
                resumed = true;
            }
        } catch (Exception e) {
            logger.fine("jason.stdlib.resume error: " + e.getMessage());
        }

        synchronized (c) {
            try {
                for (Map.Entry<String, Intention> entry : new ArrayList<>(c.getPendingIntentions().entrySet())) {
                    String key = entry.getKey();
                    Intention i = entry.getValue();
                    if (key.startsWith(SUSPENDED_PREFIX) && matchesGoalFunctor(i, goalId)) {
                        c.removePendingIntention(key);
                        c.resumeIntention(i, null);
                        i.setSuspended(false);
                        resumed = true;
                    }
                }

                for (Map.Entry<String, Event> entry : new ArrayList<>(c.getPendingEvents().entrySet())) {
                    String key = entry.getKey();
                    Event ev = entry.getValue();
                    if (key.startsWith(SUSPENDED_PREFIX) && eventMatchesGoal(ev, goalId)) {
                        c.removePendingEvent(key);
                        c.addEvent(ev);
                        if (ev.getIntention() != null) {
                            ev.getIntention().setSuspended(false);
                        }
                        resumed = true;
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
                if (isGoalTrigger(im.getTrigger(), goalId)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean intentionHasGoal(Intention i, Trigger g, Unifier u, String goalId) {
        return i != null && (i.hasTrigger(g, u) || matchesGoalFunctor(i, goalId));
    }

    public boolean agentHasGoal(Agent ag, String goalId) {
        if (ag == null || ag.getTS() == null || goalId == null) return false;
        try {
            Circumstance c = ag.getTS().getC();
            Trigger g = new Trigger(Trigger.TEOperator.add, Trigger.TEType.achieve, ASSyntax.parseLiteral(goalId));
            Unifier u = new Unifier();

            synchronized (c) {
                for (Intention i : new ArrayList<>(c.getRunningIntentions())) {
                    if (intentionHasGoal(i, g, u, goalId)) return true;
                }

                for (ActionExec act : new ArrayList<>(c.getPendingActions().values())) {
                    if (intentionHasGoal(act.getIntention(), g, u, goalId)) return true;
                }

                if (intentionHasGoal(c.getSelectedIntention(), g, u, goalId)) return true;

                for (Event ev : new ArrayList<>(c.getEvents())) {
                    if (u.unifies(g, ev.getTrigger()) || intentionHasGoal(ev.getIntention(), g, u, goalId)) return true;
                }

                for (Intention i : new ArrayList<>(c.getPendingIntentions().values())) {
                    if (intentionHasGoal(i, g, u, goalId)) return true;
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
                                list.add(t.isString() ? ((StringTerm) t).getString() : t.toString());
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
                        LocalAgArch arch = entry.getValue();
                        if (arch != null && arch.getTS() != null && arch.getTS().getAg() != null) {
                            all.putIfAbsent(entry.getKey(), arch.getTS().getAg());
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
                if (entry.getKey().startsWith(SUSPENDED_PREFIX) && matchesGoalFunctor(entry.getValue(), goalId)) {
                    return true;
                }
            }
            for (Map.Entry<String, Event> entry : c.getPendingEvents().entrySet()) {
                if (entry.getKey().startsWith(SUSPENDED_PREFIX) && eventMatchesGoal(entry.getValue(), goalId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isJasonGoalSuspended(String goalId) {
        Set<String> ags = suspendedAgentGoals.get(goalId);
        if (ags != null) {
            for (String agName : ags) {
                if (isJasonGoalSuspended(agName, goalId)) return true;
            }
        }
        for (String agName : getAllAvailableAgents().keySet()) {
            if (isJasonGoalSuspended(agName, goalId)) return true;
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

    private ReentrantLock getArtifactLock() {
        if (ARTIFACT_LOCK_FIELD != null) {
            try {
                return (ReentrantLock) ARTIFACT_LOCK_FIELD.get(this);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private void withArtifactLock(Runnable action) {
        ReentrantLock l = getArtifactLock();
        boolean lockedHere = l != null && !l.isHeldByCurrentThread();
        if (lockedHere) {
            l.lock();
        }
        try {
            action.run();
        } catch (Throwable ignored) {
        } finally {
            if (lockedHere && l.isHeldByCurrentThread()) {
                l.unlock();
            }
        }
    }

    protected void safeSignal(String signalName, Object... args) {
        withArtifactLock(() -> signal(signalName, args));
    }

    protected void safeDefineObsProperty(String name, Object... values) {
        withArtifactLock(() -> {
            if (hasObsProperty(name)) {
                getObsProperty(name).updateValues(values);
            } else {
                defineObsProperty(name, values);
            }
        });
    }

    protected void safeRemoveObsProperty(String name) {
        withArtifactLock(() -> {
            if (hasObsProperty(name)) {
                removeObsProperty(name);
            }
        });
    }

    protected void safeSetArgumentValue(String goalId, String argId, Object value) {
        try {
            if (getSchState() != null) {
                getSchState().setGoalArgValue(goalId, argId, value);
            }
        } catch (Exception ignored) {}
        safeDefineObsProperty("goalArgument", getSchemeName(), goalId, argId, value);
    }

    public boolean isGoalSuspended(String goalId) {
        return suspendedGoals.contains(goalId);
    }

    public boolean isGoalFailed(String goalId) {
        return failedGoals.contains(goalId);
    }
}
