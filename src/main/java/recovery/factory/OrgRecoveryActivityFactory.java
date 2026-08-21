package recovery.factory;

import cartago.AgentIdCredential;
import cartago.ArtifactId;
import cartago.CartagoEvent;
import cartago.CartagoException;
import cartago.ICartagoCallback;
import cartago.ICartagoContext;
import cartago.Op;
import cartago.Workspace;
import recovery.RecoveryActivity;
import recovery.context.OrgContext;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrgRecoveryActivityFactory {
    private static final Logger LOGGER = Logger.getLogger(OrgRecoveryActivityFactory.class.getName());

    public static RecoveryActivity<OrgContext> createActivity(String actStr) {
        if (actStr == null || actStr.trim().isEmpty()) {
            return ctx -> {};
        }

        final String rawAction = actStr.startsWith("org:") ? actStr.substring(4).trim() : actStr.trim();

        return ctx -> executeOrgAction(ctx, rawAction);
    }

    private static void executeOrgAction(OrgContext ctx, String actionDetails) {
        try {
            String target = actionDetails;
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
            String artifactName = ctx.getGroupName() != null ? ctx.getGroupName() : ctx.getOrgName();
            String operationName = target;

            if (parts.length >= 2) {
                artifactName = parts[0];
                operationName = parts[1];
            } else if (parts.length == 1) {
                operationName = parts[0];
            }

            Workspace wsp = ctx.getWorkspace();
            if (wsp == null) {
                LOGGER.warning("[OrgRecovery] Workspace not found for organisation: " + ctx.getOrgName());
                return;
            }

            ArtifactId aid = wsp.getArtifact(artifactName);
            if (aid == null) {
                LOGGER.warning("[OrgRecovery] Artifact '" + artifactName + "' not found in workspace.");
                return;
            }

            ICartagoContext cartagoCtx = wsp.joinWorkspace(new AgentIdCredential("OrgFailureMonitorSystem"), new ICartagoCallback() {
                public void notifyCartagoEvent(CartagoEvent a) {}
            });

            Op op = opArgs.isEmpty() ? new Op(operationName) : new Op(operationName, opArgs.toArray());
            LOGGER.info("[OrgRecovery] Executing recovery action '" + operationName + "' on artifact '" + artifactName + "' with args " + opArgs);
            cartagoCtx.doAction(1, aid.getName(), op, null, -1);

        } catch (CartagoException e) {
            LOGGER.log(Level.SEVERE, "[OrgRecovery] Error executing organisation recovery: " + actionDetails, e);
        }
    }
}
