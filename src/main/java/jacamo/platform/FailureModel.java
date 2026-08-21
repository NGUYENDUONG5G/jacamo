package jacamo.platform;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import recovery.setup.OrgFailureModelManager;

public class FailureModel extends DefaultPlatformImpl {

    private final Logger logger = Logger.getLogger(FailureModel.class.getName());
    private OrgFailureModelManager orgFailureManager;

    @Override
    public void init(String[] args) throws Exception {
        logger.info("Initializing FailureModel platform...");
        if (project != null) {
            orgFailureManager = new OrgFailureModelManager(project);
            orgFailureManager.setup();
        }
    }

    @Override
    public void start() {
        logger.info("Starting FailureModel platform...");
        if (orgFailureManager != null) {
            orgFailureManager.start(1000, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        logger.info("Stopping FailureModel platform...");
        if (orgFailureManager != null) {
            orgFailureManager.stop();
        }
    }

    public OrgFailureModelManager getOrgFailureManager() {
        return orgFailureManager;
    }
}
