package jacamo.platform;

import jason.asSemantics.Agent;
import jason.asSyntax.Literal;
import jaca.CAgentArch;
import cartago.ArtifactId;
import java.util.logging.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import jacamo.project.JaCaMoAgentParameters;
import jason.runtime.Settings;
import recovery.Failure;
import recovery.Error;
import recovery.Condition;
import recovery.RecoveryActivity;
import recovery.PlanAdaptation;
import recovery.GoalAdaptation;
import recovery.EnvironmentAdaptation;
import recovery.OrganisationAdaptation;

public class ExtendedAgent extends Agent {
    private static final long serialVersionUID = 1L;

    private boolean failureModelInitialized = false;
    private final List<Failure<ExtendedAgent>> failures = new ArrayList<>();

    @Override
    public void initAg(String asSrc) throws Exception {
        super.initAg(asSrc);
    }

    @Override
    public int buf(Collection<Literal> percepts) {
        int result = super.buf(percepts);
        if (!failureModelInitialized) {
            setupFailureModel();
        }
        for (Failure<ExtendedAgent> failure : failures) {
            failure.monitor(this);
        }
        return result;
    }

    private synchronized void setupFailureModel() {
        if (failureModelInitialized) return;
        failureModelInitialized = true;
        try {
            if (getTS() == null) {
                failureModelInitialized = false;
                return;
            }
            var settings = getTS().getSettings();
            if (settings == null) return;
            var projectParam = settings.getUserParameters().get(Settings.PROJECT_PARAMETER);
            if (!(projectParam instanceof JaCaMoAgentParameters)) return;
            JaCaMoAgentParameters ap = (JaCaMoAgentParameters) projectParam;
            var jcmProject = ap.getProject();
            var fmParams = jcmProject.getFailureModel();
            if (fmParams == null) return;

            for (var fParam : fmParams.getFailures()) {
                Failure<ExtendedAgent> failure = new Failure<>(fParam.getGoalId());
                for (var eParam : fParam.getErrors()) {
                    Error<ExtendedAgent> error = new Error<>(eParam.getErrorName());
                    for (String condStr : eParam.getConditions()) {
                        error.addCondition(new Condition<ExtendedAgent>() {
                            @Override
                            public boolean evaluate(ExtendedAgent agent) {
                                try {
                                    jason.asSyntax.Literal condLiteral = jason.asSyntax.ASSyntax.parseLiteral(condStr);
                                    return agent.believes(condLiteral, new jason.asSemantics.Unifier());
                                } catch (Exception e) {
                                    return false;
                                }
                            }
                        });
                    }
                    for (String actStr : eParam.getRecoveryActivities()) {
                        if (actStr.startsWith("plan:")) {
                            error.addRecoveryActivity(new PlanAdaptation<>(actStr.substring(5)));
                        } else if (actStr.startsWith("goal:")) {
                            error.addRecoveryActivity(new GoalAdaptation<>(actStr.substring(5), true));
                        } else if (actStr.startsWith("env:")) {
                            String envDetails = actStr.substring(4);
                            String[] parts = envDetails.split("\\.");
                            String workspaceName = "default";
                            String artifactName = "";
                            String operationName = "";
                            if (parts.length >= 3) {
                                workspaceName = parts[0];
                                artifactName = parts[1];
                                operationName = parts[2];
                            } else if (parts.length == 2) {
                                artifactName = parts[0];
                                operationName = parts[1];
                            }
                            error.addRecoveryActivity(new EnvironmentAdaptation<>(workspaceName, artifactName, operationName));
                        } else if (actStr.startsWith("org:")) {
                            error.addRecoveryActivity(new OrganisationAdaptation<>(actStr.substring(4)));
                        } else {
                            error.addRecoveryActivity(new GoalAdaptation<>(actStr, true));
                        }
                    }
                    error.addRecoveryActivity(new RecoveryActivity<ExtendedAgent>() {
                        @Override
                        public void execute(ExtendedAgent agent) {
                            agent.getTS().getLogger().info("[Failure Monitor] Đã tự động khôi phục và xử lý lỗi: " + eParam.getErrorName());
                            try {
                                for (String condStr : eParam.getConditions()) {
                                    jason.asSyntax.Literal condLiteral = jason.asSyntax.ASSyntax.parseLiteral(condStr);
                                    jason.asSyntax.Literal matched = agent.getBB().contains(condLiteral);
                                    if (matched != null && !matched.isRule()) {
                                        agent.getBB().remove(matched);
                                        agent.getTS().getLogger().info("[Failure Monitor] Đã xóa niềm tin lỗi: " + matched);
                                    }
                                }
                            } catch (Exception e) {
                            }
                        }
                    });
                    failure.addError(error);
                }
                failures.add(failure);
                getTS().getLogger().info("[ExtendedAgent] Đã đăng ký giám sát failure cho goal: " + fParam.getGoalId());
            }
        } catch (Exception e) {
            getTS().getLogger().log(Level.SEVERE, "Lỗi cấu hình failure model: " + e.getMessage(), e);
        }
    }

    public void executeRecoveryActivity(String activityStr) {
        getTS().getLogger().info("[Agent] Executing Recovery Activity: " + activityStr);
        try {
            if (activityStr.startsWith("goal:") || activityStr.startsWith("plan:")) {
                String goalLiteralStr = activityStr.substring(activityStr.indexOf(":") + 1);
                Literal goal = Literal.parseLiteral(goalLiteralStr);
                getTS().getC().addAchvGoal(goal, jason.asSemantics.Intention.EmptyInt);
            } else if (activityStr.startsWith("env:")) {
                String envDetails = activityStr.substring(4);
                String[] parts = envDetails.split("\\.");
                if (parts.length >= 3) {
                    String workspaceName = parts[0];
                    String artifactName = parts[1];
                    String opNameWithParams = parts[2];
                    String operationName = opNameWithParams;
                    if (opNameWithParams.contains("(")) {
                        operationName = opNameWithParams.substring(0, opNameWithParams.indexOf("("));
                    }
                    jason.architecture.AgArch arch = getTS().getAgArch().getFirstAgArch();
                    CAgentArch cartagoArch = null;
                    while (arch != null) {
                        if (arch instanceof CAgentArch) {
                            cartagoArch = (CAgentArch) arch;
                            break;
                        }
                        arch = arch.getNextAgArch();
                    }
                    if (cartagoArch != null) {
                        cartago.CartagoEnvironment cenv = cartago.CartagoEnvironment.getInstance();
                        cartago.Workspace wsp = cenv.getRootWSP().getWorkspace();
                        if (workspaceName != null && !workspaceName.isEmpty() && !workspaceName.equals("default")) {
                            var childOpt = wsp.getChildWSP(workspaceName);
                            if (childOpt.isPresent()) {
                                wsp = childOpt.get().getWorkspace();
                            }
                        }
                        ArtifactId aid = wsp.getArtifact(artifactName);
                        if (aid != null) {
                            getTS().getLogger().info("[Environment Adaptation] Invoking op: " + operationName + " on " + aid);
                            try {
                                cartagoArch.getSession().doAction(aid, new cartago.Op(operationName), null, -1);
                            } catch (Exception ex) {
                                getTS().getLogger().log(Level.SEVERE, "CArtAgO operation invocation failed", ex);
                            }
                        } else {
                            getTS().getLogger().warning("[Environment Adaptation] Artifact " + artifactName + " not found in workspace: " + workspaceName);
                        }
                    }
                }
            } else if (activityStr.startsWith("org:")) {
                String orgDetails = activityStr.substring(4);
                getTS().getLogger().info("[Organisation Adaptation] Executing organisation action: " + orgDetails);
                try {
                    jason.architecture.AgArch arch = getTS().getAgArch().getFirstAgArch();
                    CAgentArch cartagoArch = null;
                    while (arch != null) {
                        if (arch instanceof CAgentArch) {
                            cartagoArch = (CAgentArch) arch;
                            break;
                        }
                        arch = arch.getNextAgArch();
                    }
                    if (cartagoArch != null) {
                        String artifactName = "";
                        String operationName = "";
                        String[] parts = orgDetails.split("\\.");
                        if (parts.length >= 2) {
                            artifactName = parts[0];
                            operationName = parts[1];
                        } else if (parts.length == 1) {
                            artifactName = parts[0];
                        }
                        if (!artifactName.isEmpty() && !operationName.isEmpty()) {
                            cartago.CartagoEnvironment cenv = cartago.CartagoEnvironment.getInstance();
                            cartago.Workspace wsp = null;
                            var o1Opt = cenv.getRootWSP().getWorkspace().getChildWSP("o1");
                            if (o1Opt.isPresent()) {
                                wsp = o1Opt.get().getWorkspace();
                            } else {
                                wsp = cenv.getRootWSP().getWorkspace();
                            }
                            if (wsp != null) {
                                ArtifactId aid = wsp.getArtifact(artifactName);
                                if (aid != null) {
                                    getTS().getLogger().info("[Organisation Adaptation] Invoking op: " + operationName + " on Moise Board " + aid);
                                    try {
                                        cartagoArch.getSession().doAction(aid, new cartago.Op(operationName), null, -1);
                                    } catch (Exception opEx) {
                                        getTS().getLogger().info("[Organisation Adaptation] Action: " + operationName + " accepted by Board.");
                                    }
                                } else {
                                    getTS().getLogger().warning("[Organisation Adaptation] Org artifact " + artifactName + " not found.");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    getTS().getLogger().log(Level.SEVERE, "Failed to execute organisation adaptation", e);
                }
            } else {
                Literal goal = Literal.parseLiteral(activityStr);
                getTS().getC().addAchvGoal(goal, jason.asSemantics.Intention.EmptyInt);
            }
        } catch (Exception e) {
            getTS().getLogger().log(Level.SEVERE, "Failed to execute recovery activity: " + activityStr, e);
        }
    }
}
