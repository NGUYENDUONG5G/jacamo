package jacamo.platform;

import java.util.logging.Logger;
import jacamo.project.JaCaMoFailureModelParameters;

public class FailureModel extends DefaultPlatformImpl {
    
    Logger logger = Logger.getLogger(FailureModel.class.getName());

    @Override
    public void init(String[] args) throws Exception {
        logger.info("Initializing FailureModel platform...");
    }

    @Override
    public void start() {
        logger.info("Starting FailureModel platform...");
    }

    @Override
    public void stop() {
        logger.info("Stopping FailureModel platform...");
    }
}
