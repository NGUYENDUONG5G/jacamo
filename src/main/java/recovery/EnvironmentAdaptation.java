package recovery;

import jason.asSemantics.Agent;
import jaca.CAgentArch;
import cartago.ArtifactId;
import java.util.logging.Level;

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
                    cartagoArch.getSession().doAction(aid, new cartago.Op(operationName), null, -1);
                }
            }
        } catch (Exception e) {
            agent.getTS().getLogger().log(Level.SEVERE, "Failed to execute environment adaptation", e);
        }
    }
}
