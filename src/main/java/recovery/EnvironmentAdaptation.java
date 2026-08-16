package recovery;

import jason.asSemantics.Agent;
import jaca.CAgentArch;
import cartago.ArtifactId;
import java.util.logging.Level;

/**
 * Adapts the Environment dimension by invoking operations on artifacts within a workspace.
 */
public class EnvironmentAdaptation<T extends Agent> implements RecoveryActivity<T> {
    private final String workspaceName;
    private final String artifactName;
    private final String operationName;

    public EnvironmentAdaptation(String workspaceName, String artifactName, String operationName) {
        this.workspaceName = workspaceName;
        this.artifactName = artifactName;
        this.operationName = operationName;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public String getArtifactName() {
        return artifactName;
    }

    public String getOperationName() {
        return operationName;
    }

    @Override
    public void execute(T agent) {
        agent.getTS().getLogger().info("[Adaptation -> Environment] Target workspace: " + workspaceName + ", artifact: " + artifactName + ", operation: " + operationName);
        try {
            jason.architecture.AgArch arch = agent.getTS().getAgArch().getFirstAgArch();
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
                    agent.getTS().getLogger().info("[Environment Adaptation] Invoking op: " + operationName + " on " + aid);
                    try {
                        cartagoArch.getSession().doAction(aid, new cartago.Op(operationName), null, -1);
                    } catch (Exception ex) {
                        agent.getTS().getLogger().log(Level.SEVERE, "CArtAgO operation invocation failed", ex);
                    }
                } else {
                    agent.getTS().getLogger().warning("[Environment Adaptation] Artifact " + artifactName + " not found in workspace: " + workspaceName);
                }
            } else {
                agent.getTS().getLogger().warning("[Environment Adaptation] CAgentArch not found for agent: " + agent.getTS().getUserAgArch().getAgName());
            }
        } catch (Exception e) {
            agent.getTS().getLogger().log(Level.SEVERE, "Failed to execute environment adaptation", e);
        }
    }
}
