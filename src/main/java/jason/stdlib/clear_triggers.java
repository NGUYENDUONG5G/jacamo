package jason.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.ListTerm;
import jason.asSyntax.Literal;
import jason.asSyntax.Term;
import jason.asSyntax.ASSyntax;
import cartago.CartagoEnvironment;
import cartago.Workspace;

import java.util.ArrayList;
import java.util.List;

public class clear_triggers extends DefaultInternalAction {
    private static final long serialVersionUID = 1L;

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        List<String> namesToClear = new ArrayList<>();

        if (args.length == 0) {
            String[] defaultTriggers = {
                "has_order", "start_delivery", "delivering",
                "battery_below_10", "sensor_offline", "robot_arm_conn_failed",
                "barcode_scan_failed", "weight_exceeded", "road_blocked_detected",
                "customer_no_show", "auth_attempts_over_3", "hatch_mechanism_stuck",
                "all_docks_busy", "dock_contact_error", "emergency_handled",
                "reroute_done", "return_initiated", "sleep_mode_active"
            };
            for (String t : defaultTriggers) namesToClear.add(t);
        } else if (args[0].isList()) {
            ListTerm list = (ListTerm) args[0];
            for (Term t : list) {
                String name = extractName(t);
                if (name != null && !name.isEmpty()) {
                    namesToClear.add(name);
                }
            }
        } else {
            for (Term t : args) {
                String name = extractName(t);
                if (name != null && !name.isEmpty()) {
                    namesToClear.add(name);
                }
            }
        }

        // 1. Abolish from Jason Belief Base
        for (String name : namesToClear) {
            try {
                Literal lit = ASSyntax.parseLiteral(name);
                ts.getAg().abolish(lit, un);
            } catch (Exception ignored) {}
        }

        // 2. Remove Observable Property from all CArtAgO Artifacts
        try {
            CartagoEnvironment cenv = CartagoEnvironment.getInstance();
            if (cenv != null && cenv.getRootWSP() != null) {
                List<Workspace> wsps = new ArrayList<>();
                wsps.add(cenv.getRootWSP().getWorkspace());
                for (var child : cenv.getRootWSP().getWorkspace().getChildWSPs()) {
                    wsps.add(child.getWorkspace());
                }
                for (Workspace wsp : wsps) {
                    List<cartago.Artifact> arts = getAllArtifacts(wsp);
                    for (cartago.Artifact art : arts) {
                        for (String name : namesToClear) {
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

        return true;
    }

    private String extractName(Term t) {
        if (t == null) return null;
        if (t.isString()) return t.toString().replace("\"", "").trim();
        if (t instanceof Literal) return ((Literal) t).getFunctor();
        return t.toString().replaceAll("^[\"']|[\"']$", "").trim();
    }

    private List<cartago.Artifact> getAllArtifacts(Workspace w) {
        List<cartago.Artifact> list = new ArrayList<>();
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
}
