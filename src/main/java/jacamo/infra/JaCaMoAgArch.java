package jacamo.infra;

import java.util.logging.Level;

import jaca.CAgentArch;
import jacamo.project.JaCaMoAgentParameters;
import jacamo.project.JaCaMoWorkspaceParameters;
import jacamo.project.JaCaMoFailureModelParameters;
import jacamo.project.JaCaMoFailureModelParameters.FailureParameters;
import jacamo.project.JaCaMoFailureModelParameters.ErrorParameters;
import jason.architecture.AgArch;
import jason.asSemantics.Intention;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Atom;
import jason.asSyntax.ListTerm;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.Literal;
import jason.runtime.Settings;
import recovery.setup.AgentFailureModelManager;

/**
 * This class provides an agent architecture when using JaCaMo
 * infrastructure to run the MAS inside Jason.
 *
 * The communication and agent management comes from Local Infra and
 * Perceive and Act are delegated to Cartago.
 *
 */
public class JaCaMoAgArch extends AgArch {
    private static final long serialVersionUID = 1L;
    
    public static Atom jcmAtom = new Atom("jcm");
    private AgentFailureModelManager failureManager;

    @Override
    public void init() throws Exception {
        JaCaMoAgentParameters ap = null;
        try {
            ap = (JaCaMoAgentParameters)getTS().getSettings().getUserParameters().get(Settings.PROJECT_PARAMETER);
        } catch (Exception e) {
            getTS().getLogger().warning("error getting parameters to init JaCaMoAgArch! "+e);
            return;
        }

        if (ap == null)
            return;

        getTS().getLogger().fine("Using parameters from project "+ap.getProject().getSocName()+" for agent "+ap.getAgName());

        ListTerm lart = new ListTermImpl();  // list used to produce a goal to join/focus on artifacts
        ListTerm tail = lart;

        // get my WSPs from AgentParameters
        for (String wId: ap.getWorkspaces()) {
            try {
                JaCaMoWorkspaceParameters w = ap.getProject().getWorkspace(wId);
                if (w == null) {
                    getTS().getLogger().warning("**** Workspace "+wId+" is not defined! The agent will not join it.");
                    continue;
                }
                Literal art = ASSyntax.createLiteral("art_env",
                        ASSyntax.createAtom(w.getName()), // workspace
                        ASSyntax.createString(""), // art
                        Literal.DefaultNS);           // namespace

                if (!lart.contains(art))
                    tail = tail.append(art);

            } catch (Exception e) {
                getTS().getLogger().log(Level.SEVERE,"error joining workspace "+wId,e);
            }
        }

        // focus on artifacts
        for (String[] f: ap.getFocus()) {
            Literal art = ASSyntax.createLiteral("art_env",
                    ASSyntax.createAtom(f[1]),  // workspace
                    ASSyntax.createAtom(f[0]), // art
                    ASSyntax.parseTerm(f[2])); // namespace
            if (!lart.contains(art))
                tail = tail.append(art);
        }

        // focus on group artifacts and adopt roles
        ListTerm lroles = new ListTermImpl();
        tail = lroles;
        for (String[] r: ap.getRoles()) {
            if (r[0] == null) {
                getTS().getLogger().warning("No organisation for group "+r[1]+"! Ignoring role "+r[2]);
                continue;
            }
            Literal role = ASSyntax.createLiteral("role",
                    ASSyntax.createAtom(r[0]),   // org
                    ASSyntax.createAtom(r[1]),   // art
                    ASSyntax.createAtom(r[2]));  // role
            if (!lroles.contains(role)) {
                tail = tail.append(role);

                // add auto focus for this group
                Literal art = ASSyntax.createLiteral("art_env",
                        ASSyntax.createAtom(r[0]),    // workspace
                        ASSyntax.createAtom(r[1]),    // art
                        Literal.DefaultNS);           // namespace
                if (!lart.contains(art))
                    lart.append(art);

                // add auto focus on org board
                art = ASSyntax.createLiteral("art_env",
                        ASSyntax.createAtom(r[0]),    // workspace
                        ASSyntax.createAtom(r[0]),    // art
                        Literal.DefaultNS);           // namespace
                if (!lart.contains(art))
                    lart.append(art);
            }
        }

        if (! lart.isEmpty()) {
            if (getTS().getLogger().isLoggable(Level.FINE)) getTS().getLogger().fine("producing goal to focus on "+lart);
            getTS().getC().addAchvGoal( ASSyntax.createLiteral(jcmAtom, "focus_env_art", lart, ASSyntax.createNumber(5)), null);
        }

        if (! lroles.isEmpty()) {
            if (getTS().getLogger().isLoggable(Level.FINE)) getTS().getLogger().fine("producing goal for initial roles "+lroles);
            getTS().getC().addAchvGoal( ASSyntax.createLiteral(jcmAtom, "initial_roles", lroles, ASSyntax.createNumber(5)), null);
        }

        if (ap.getProject() != null && ap.getProject().getFailureModel() != null) {
            failureManager = new AgentFailureModelManager(getTS().getAg());
            failureManager.setup();
        }

        // Auto Goal Trigger Cleanup Listener via dynamic proxy
        try {
            Class<?> glClass = Class.forName("jason.asSemantics.GoalListener");
            Object listenerProxy = java.lang.reflect.Proxy.newProxyInstance(
                glClass.getClassLoader(),
                new Class<?>[]{ glClass },
                (proxy, method, mArgs) -> {
                    if ("goalFinished".equals(method.getName()) && mArgs != null) {
                        jason.asSyntax.Trigger trigger = null;
                        for (Object arg : mArgs) {
                            if (arg instanceof jason.asSyntax.Trigger) {
                                trigger = (jason.asSyntax.Trigger) arg;
                            }
                        }
                        if (trigger != null && trigger.getLiteral() != null) {
                            String gName = trigger.getLiteral().getFunctor();
                            clearTriggersForGoal(gName);
                        }
                    }
                    return null;
                }
            );
            java.lang.reflect.Method mAdd = getTS().getClass().getMethod("addGoalListener", glClass);
            mAdd.invoke(getTS(), listenerProxy);
        } catch (Exception ignored) {}
    }

    private void clearTriggersForGoal(String goalName) {
        if (goalName == null || goalName.isEmpty()) return;
        java.util.Set<String> toClear = new java.util.HashSet<>();

        try {
            // 1. Dynamically extract from Project Failure Model for this goal
            var apObj = getTS().getSettings().getUserParameters().get(Settings.PROJECT_PARAMETER);
            if (apObj instanceof JaCaMoAgentParameters) {
                JaCaMoAgentParameters ap = (JaCaMoAgentParameters) apObj;
                if (ap.getProject() != null && ap.getProject().getFailureModel() != null) {
                    for (JaCaMoFailureModelParameters.FailureParameters f : ap.getProject().getFailureModel().getFailures()) {
                        if (f.getGoalId().equalsIgnoreCase(goalName)) {
                            for (JaCaMoFailureModelParameters.ErrorParameters err : f.getErrors()) {
                                for (String cond : err.getConditions()) {
                                    if (cond != null && !cond.trim().isEmpty()) {
                                        toClear.add(cond.trim());
                                        resolveRuleBodyLiterals(cond.trim(), toClear);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Dynamically extract activation preconditions from candidate plans for +!goalName
            try {
                jason.asSyntax.Trigger te = new jason.asSyntax.Trigger(
                    jason.asSyntax.Trigger.TEOperator.add,
                    jason.asSyntax.Trigger.TEType.achieve,
                    ASSyntax.parseLiteral(goalName)
                );
                java.util.List<jason.asSyntax.Plan> candidatePlans = getTS().getAg().getPL().getCandidatePlans(te);
                if (candidatePlans != null) {
                    for (jason.asSyntax.Plan p : candidatePlans) {
                        if (p.getContext() != null) {
                            extractLiteralsFromLogicalFormula(p.getContext(), toClear);
                        }
                    }
                }
            } catch (Exception ignored) {}

        } catch (Exception ignored) {}

        if (toClear.isEmpty()) return;

        // 3. Abolish beliefs in agent
        for (String name : toClear) {
            try {
                Literal lit = ASSyntax.parseLiteral(name);
                getTS().getAg().abolish(lit, new jason.asSemantics.Unifier());
            } catch (Exception ignored) {}
        }

        // 4. Remove Observable Properties on all CArtAgO Artifacts
        try {
            cartago.CartagoEnvironment cenv = cartago.CartagoEnvironment.getInstance();
            if (cenv != null && cenv.getRootWSP() != null) {
                java.util.List<cartago.Workspace> wsps = new java.util.ArrayList<>();
                wsps.add(cenv.getRootWSP().getWorkspace());
                for (var child : cenv.getRootWSP().getWorkspace().getChildWSPs()) {
                    wsps.add(child.getWorkspace());
                }
                for (cartago.Workspace wsp : wsps) {
                    java.util.List<cartago.Artifact> arts = getAllArtifacts(wsp);
                    for (cartago.Artifact art : arts) {
                        for (String name : toClear) {
                            try {
                                java.lang.reflect.Method mHas = cartago.Artifact.class.getDeclaredMethod("hasObsProperty", String.class);
                                mHas.setAccessible(true);
                                boolean hasProp = (Boolean) mHas.invoke(art, name);
                                if (hasProp) {
                                    java.lang.reflect.Method mRem = cartago.Artifact.class.getDeclaredMethod("removeObsProperty", String.class);
                                    mRem.setAccessible(true);
                                    mRem.invoke(art, name);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void resolveRuleBodyLiterals(String condHead, java.util.Set<String> toClear) {
        try {
            for (jason.asSyntax.Literal b : getTS().getAg().getBB()) {
                if (b.isRule()) {
                    jason.asSyntax.Rule r = (jason.asSyntax.Rule) b;
                    if (r.getHead().getFunctor().equalsIgnoreCase(condHead)) {
                        extractLiteralsFromLogicalFormula(r.getBody(), toClear);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void extractLiteralsFromLogicalFormula(jason.asSyntax.LogicalFormula formula, java.util.Set<String> toClear) {
        if (formula == null) return;
        if (formula instanceof jason.asSyntax.Literal) {
            jason.asSyntax.Literal lit = (jason.asSyntax.Literal) formula;
            String functor = lit.getFunctor();
            if (functor != null && !functor.isEmpty() && !functor.startsWith(".") && !functor.equalsIgnoreCase("true") && !functor.equalsIgnoreCase("false")) {
                toClear.add(functor);
            }
        } else if (formula instanceof jason.asSyntax.LogExpr) {
            jason.asSyntax.LogExpr expr = (jason.asSyntax.LogExpr) formula;
            if (expr.getLHS() instanceof jason.asSyntax.LogicalFormula) {
                extractLiteralsFromLogicalFormula((jason.asSyntax.LogicalFormula) expr.getLHS(), toClear);
            }
            if (expr.getRHS() instanceof jason.asSyntax.LogicalFormula) {
                extractLiteralsFromLogicalFormula((jason.asSyntax.LogicalFormula) expr.getRHS(), toClear);
            }
        } else if (formula instanceof jason.asSyntax.RelExpr) {
            jason.asSyntax.RelExpr expr = (jason.asSyntax.RelExpr) formula;
            if (expr.getLHS() instanceof jason.asSyntax.LogicalFormula) {
                extractLiteralsFromLogicalFormula((jason.asSyntax.LogicalFormula) expr.getLHS(), toClear);
            }
            if (expr.getRHS() instanceof jason.asSyntax.LogicalFormula) {
                extractLiteralsFromLogicalFormula((jason.asSyntax.LogicalFormula) expr.getRHS(), toClear);
            }
        }
    }

    private java.util.List<cartago.Artifact> getAllArtifacts(cartago.Workspace w) {
        java.util.List<cartago.Artifact> list = new java.util.ArrayList<>();
        try {
            for (java.lang.reflect.Field f : w.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object fVal = f.get(w);
                if (fVal instanceof java.util.Map) {
                    java.util.Map<?, ?> map = (java.util.Map<?, ?>) fVal;
                    for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                        cartago.Artifact candidate = extractArtifact(entry.getValue());
                        if (candidate != null && !list.contains(candidate)) {
                            list.add(candidate);
                        }
                    }
                } else if (fVal != null && fVal.getClass().getName().contains("ArtifactRegistry")) {
                    for (java.lang.reflect.Field rf : fVal.getClass().getDeclaredFields()) {
                        rf.setAccessible(true);
                        Object rVal = rf.get(fVal);
                        if (rVal instanceof java.util.Map) {
                            java.util.Map<?, ?> map = (java.util.Map<?, ?>) rVal;
                            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                                cartago.Artifact candidate = extractArtifact(entry.getValue());
                                if (candidate != null && !list.contains(candidate)) {
                                    list.add(candidate);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private cartago.Artifact extractArtifact(Object obj) {
        if (obj == null) return null;
        if (obj instanceof cartago.Artifact) return (cartago.Artifact) obj;
        try {
            for (java.lang.reflect.Method m : obj.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && cartago.Artifact.class.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    Object res = m.invoke(obj);
                    if (res instanceof cartago.Artifact) return (cartago.Artifact) res;
                }
            }
            for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
                if (cartago.Artifact.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object res = f.get(obj);
                    if (res instanceof cartago.Artifact) return (cartago.Artifact) res;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void reasoningCycleStarting() {
        super.reasoningCycleStarting();
        if (failureManager != null) {
            failureManager.monitor();
        }
    }

    public AgentFailureModelManager getFailureManager() {
        return failureManager;
    }

    protected CAgentArch getCartagoArch() {
        AgArch arch = getTS().getAgArch().getFirstAgArch();
        while (arch != null) {
            if (arch instanceof CAgentArch) {
                return (CAgentArch)arch;
            }
            arch = arch.getNextAgArch();
        }
        return null;
    }
}
