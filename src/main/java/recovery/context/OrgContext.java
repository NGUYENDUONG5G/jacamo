package recovery.context;

import cartago.ArtifactId;
import cartago.ArtifactInfo;
import cartago.ArtifactObsProperty;
import cartago.CartagoEnvironment;
import cartago.Workspace;
import jacamo.project.JaCaMoOrgParameters;

import java.util.logging.Logger;

public class OrgContext {
    private static final Logger LOGGER = Logger.getLogger(OrgContext.class.getName());

    private final String orgName;
    private final String groupName;
    private final String schemeName;
    private final JaCaMoOrgParameters orgParams;

    public OrgContext(String orgName, String groupName, String schemeName, JaCaMoOrgParameters orgParams) {
        this.orgName = orgName;
        this.groupName = groupName;
        this.schemeName = schemeName;
        this.orgParams = orgParams;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public JaCaMoOrgParameters getOrgParams() {
        return orgParams;
    }

    public Workspace getWorkspace() {
        CartagoEnvironment cenv = CartagoEnvironment.getInstance();
        if (cenv == null || cenv.getRootWSP() == null) {
            return null;
        }

        Workspace root = cenv.getRootWSP().getWorkspace();
        if (orgName != null && !orgName.isEmpty()) {
            var childOpt = root.getChildWSP(orgName);
            if (childOpt.isPresent()) {
                return childOpt.get().getWorkspace();
            }
        }
        return root;
    }

    public ArtifactId getArtifact(String artifactName) {
        Workspace wsp = getWorkspace();
        if (wsp == null) return null;
        try {
            return wsp.getArtifact(artifactName);
        } catch (Exception e) {
            return null;
        }
    }

    public ArtifactObsProperty getObsProperty(String artifactName, String propertyName) {
        CartagoEnvironment cenv = CartagoEnvironment.getInstance();
        if (cenv == null) return null;

        String[] candidates = { "/main/" + orgName, orgName, "/main" };
        for (String cWsp : candidates) {
            if (cWsp == null) continue;
            try {
                var controller = cenv.getController(cWsp);
                if (controller != null) {
                    ArtifactInfo aInfo = controller.getArtifactInfo(artifactName);
                    if (aInfo != null && aInfo.getObsProperties() != null) {
                        for (ArtifactObsProperty op : aInfo.getObsProperties()) {
                            if (op.getName().equals(propertyName)) {
                                return op;
                            }
                        }
                    }
                }
            } catch (Exception e) {

            }
        }
        return null;
    }
}
