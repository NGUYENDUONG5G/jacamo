package recovery;

import jason.asSemantics.Agent;
import cartago.ArtifactId;
import cartago.Op;
import cartago.CartagoEnvironment;
import cartago.Workspace;
import cartago.ICartagoContext;
import cartago.AgentIdCredential;
import cartago.ICartagoCallback;
import cartago.CartagoEvent;
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
            CartagoEnvironment cenv = CartagoEnvironment.getInstance();
            if (cenv == null || cenv.getRootWSP() == null) {
                agent.getTS().getLogger().warning("[EnvironmentAdaptation] Cartago Environment not initialized.");
                return;
            }

            Workspace rootWsp = cenv.getRootWSP().getWorkspace();
            Workspace wsp = rootWsp;
            if (workspaceName != null && !workspaceName.isEmpty() && !workspaceName.equals("default")) {
                var childOpt = rootWsp.getChildWSP(workspaceName);
                if (childOpt.isPresent()) {
                    wsp = childOpt.get().getWorkspace();
                }
            }

            ArtifactId aid = wsp.getArtifact(artifactName);
            if (aid == null) {
                for (var child : rootWsp.getChildWSPs()) {
                    if (child.getWorkspace().getArtifact(artifactName) != null) {
                        wsp = child.getWorkspace();
                        aid = wsp.getArtifact(artifactName);
                        break;
                    }
                }
            }

            if (aid != null) {
                String agName = agent.getTS().getUserAgArch() != null ? agent.getTS().getUserAgArch().getAgName() : "recovery_agent";
                ICartagoContext ctx = wsp.joinWorkspace(new AgentIdCredential(agName), new ICartagoCallback() {
                    public void notifyCartagoEvent(CartagoEvent arg0) {}
                });
                ctx.doAction(1, aid.getName(), new Op(operationName), null, -1);
            } else {
                agent.getTS().getLogger().warning("[EnvironmentAdaptation] Artifact '" + artifactName + "' not found.");
            }
        } catch (Exception e) {
            agent.getTS().getLogger().log(Level.SEVERE, "Failed to execute environment adaptation", e);
        }
    }
}
