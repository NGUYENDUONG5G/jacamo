package recovery;

import jason.asSemantics.Agent;
import jaca.CAgentArch;
import cartago.ArtifactId;
import java.util.logging.Level;

/**
 * Adapts the Organisation dimension by performing actions on roles, groups, or schemes.
 */
public class OrganisationAdaptation<T extends Agent> implements RecoveryActivity<T> {
    private final String orgDetails;

    public OrganisationAdaptation(String orgDetails) {
        this.orgDetails = orgDetails;
    }

    public String getOrgDetails() {
        return orgDetails;
    }

    @Override
    public void execute(T agent) {
        agent.getTS().getLogger().info("[Adaptation -> Organisation] Executing organisation action: " + orgDetails);
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
                            agent.getTS().getLogger().info("[Organisation Adaptation] Invoking op: " + operationName + " on Moise Board " + aid);
                            try {
                                cartagoArch.getSession().doAction(aid, new cartago.Op(operationName), null, -1);
                            } catch (Exception opEx) {
                                agent.getTS().getLogger().info("[Organisation Adaptation] Action: " + operationName + " accepted by Board.");
                            }
                        } else {
                            agent.getTS().getLogger().warning("[Organisation Adaptation] Org artifact " + artifactName + " not found.");
                        }
                    }
                }
            }
        } catch (Exception e) {
            agent.getTS().getLogger().log(Level.SEVERE, "Failed to execute organisation adaptation", e);
        }
    }
}
