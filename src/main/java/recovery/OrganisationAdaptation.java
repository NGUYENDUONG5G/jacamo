package recovery;

import jason.asSemantics.Agent;
import jaca.CAgentArch;
import cartago.ArtifactId;
import cartago.CartagoEnvironment;
import cartago.Workspace;
import cartago.Op;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class OrganisationAdaptation<T extends Agent> implements RecoveryActivity<T> {
    private final String orgDetails;

    public OrganisationAdaptation(String orgDetails) {
        this.orgDetails = orgDetails != null ? orgDetails.trim() : "";
    }

    public String getOrgDetails() {
        return orgDetails;
    }

    @Override
    public void execute(T agent) {
        if (orgDetails.isEmpty()) return;

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

            if (cartagoArch == null) {
                agent.getTS().getLogger().warning("[OrganisationAdaptation] CAgentArch not found for agent: " + agent.getTS().getUserAgArch().getAgName());
                return;
            }

            String target = orgDetails;
            List<Object> opArgs = new ArrayList<>();

            if (target.contains("(") && target.endsWith(")")) {
                int openIdx = target.indexOf('(');
                int closeIdx = target.lastIndexOf(')');
                String argsStr = target.substring(openIdx + 1, closeIdx).trim();
                target = target.substring(0, openIdx).trim();

                if (!argsStr.isEmpty()) {
                    String[] rawArgs = argsStr.split(",");
                    for (String arg : rawArgs) {
                        String cleanArg = arg.trim().replaceAll("^[\"']|[\"']$", "");
                        opArgs.add(cleanArg);
                    }
                }
            }

            String[] parts = target.split("\\.");
            String workspaceName = null;
            String artifactName = "";
            String operationName = "";

            if (parts.length >= 3) {
                workspaceName = parts[0];
                artifactName = parts[1];
                operationName = parts[2];
            } else if (parts.length == 2) {
                artifactName = parts[0];
                operationName = parts[1];
            } else if (parts.length == 1) {
                artifactName = parts[0];
            }

            CartagoEnvironment cenv = CartagoEnvironment.getInstance();
            if (cenv == null || cenv.getRootWSP() == null) {
                agent.getTS().getLogger().warning("[OrganisationAdaptation] Cartago Environment not initialized.");
                return;
            }

            Workspace wsp = findWorkspace(cenv, workspaceName, artifactName);
            if (wsp == null) {
                agent.getTS().getLogger().warning("[OrganisationAdaptation] Workspace or Artifact '" + artifactName + "' not found.");
                return;
            }

            ArtifactId aid = wsp.getArtifact(artifactName);
            if (aid == null) {
                agent.getTS().getLogger().warning("[OrganisationAdaptation] Artifact '" + artifactName + "' not found in workspace.");
                return;
            }

            Op op;
            if (opArgs.isEmpty()) {
                op = new Op(operationName);
            } else {
                op = new Op(operationName, opArgs.toArray());
            }

            String agName = agent.getTS().getUserAgArch() != null ? agent.getTS().getUserAgArch().getAgName() : "recovery_agent";
            cartago.ICartagoContext ctx = wsp.joinWorkspace(new cartago.AgentIdCredential(agName), new cartago.ICartagoCallback() {
                public void notifyCartagoEvent(cartago.CartagoEvent arg0) {}
            });
            agent.getTS().getLogger().info("[OrganisationAdaptation] Executing org action '" + operationName + "' on artifact '" + artifactName + "' with args " + opArgs);
            ctx.doAction(1, aid.getName(), op, null, -1);

        } catch (Exception e) {
            agent.getTS().getLogger().log(Level.SEVERE, "[OrganisationAdaptation] Failed to execute organisation adaptation: " + orgDetails, e);
        }
    }

    private Workspace findWorkspace(CartagoEnvironment cenv, String specificWspName, String artifactName) {
        Workspace rootWsp = cenv.getRootWSP().getWorkspace();

        if (specificWspName != null && !specificWspName.isEmpty() && !specificWspName.equals("default")) {
            var childOpt = rootWsp.getChildWSP(specificWspName);
            if (childOpt.isPresent()) {
                return childOpt.get().getWorkspace();
            }
        }

        var o1Opt = rootWsp.getChildWSP("o1");
        if (o1Opt.isPresent()) {
            Workspace o1Wsp = o1Opt.get().getWorkspace();
            if (o1Wsp.getArtifact(artifactName) != null) {
                return o1Wsp;
            }
        }

        if (rootWsp.getArtifact(artifactName) != null) {
            return rootWsp;
        }

        for (var child : rootWsp.getChildWSPs()) {
            if (child.getWorkspace().getArtifact(artifactName) != null) {
                return child.getWorkspace();
            }
        }

        return o1Opt.isPresent() ? o1Opt.get().getWorkspace() : rootWsp;
    }
}
