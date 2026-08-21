package recovery.setup;

import jacamo.project.JaCaMoFailureModelParameters;
import jacamo.project.JaCaMoGroupParameters;
import jacamo.project.JaCaMoOrgParameters;
import jacamo.project.JaCaMoProject;
import jacamo.project.JaCaMoSchemeParameters;
import recovery.Failure;
import recovery.builder.OrgFailureModelBuilder;
import recovery.context.OrgContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrgFailureModelManager {
    private static final Logger LOGGER = Logger.getLogger(OrgFailureModelManager.class.getName());

    private final JaCaMoProject project;
    private final Map<OrgContext, List<Failure<OrgContext>>> orgFailuresMap = new HashMap<>();
    private ScheduledExecutorService scheduler;
    private boolean initialized = false;
    private boolean isRunning = false;

    public OrgFailureModelManager(JaCaMoProject project) {
        this.project = project;
    }

    public synchronized void setup() {
        if (initialized) return;
        initialized = true;

        if (project == null) return;
        JaCaMoFailureModelParameters fmParams = project.getFailureModel();
        if (fmParams == null) return;

        try {
            for (JaCaMoOrgParameters org : project.getOrgs()) {

                OrgContext orgCtx = new OrgContext(org.getName(), null, null, org);
                List<Failure<OrgContext>> orgFailures = OrgFailureModelBuilder.buildFailures(fmParams, org.getFailures());
                if (!orgFailures.isEmpty()) {
                    orgFailuresMap.put(orgCtx, orgFailures);
                    LOGGER.info("[OrgFailureManager] Registered " + orgFailures.size() + " failure(s) for Organisation: " + org.getName());
                }

                for (JaCaMoGroupParameters grp : org.getGroups()) {
                    OrgContext grpCtx = new OrgContext(org.getName(), grp.getName(), null, org);
                    List<Failure<OrgContext>> grpFailures = OrgFailureModelBuilder.buildFailures(fmParams, grp.getFailures());
                    if (!grpFailures.isEmpty()) {
                        orgFailuresMap.put(grpCtx, grpFailures);
                        LOGGER.info("[OrgFailureManager] Registered " + grpFailures.size() + " failure(s) for Group: " + grp.getName());
                    }
                }

                for (JaCaMoSchemeParameters sch : org.getSchemes()) {
                    OrgContext schCtx = new OrgContext(org.getName(), null, sch.getName(), org);
                    List<Failure<OrgContext>> schFailures = OrgFailureModelBuilder.buildFailures(fmParams, sch.getFailures());
                    if (!schFailures.isEmpty()) {
                        orgFailuresMap.put(schCtx, schFailures);
                        LOGGER.info("[OrgFailureManager] Registered " + schFailures.size() + " failure(s) for Scheme: " + sch.getName());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[OrgFailureManager] Error setting up organisation failure models: " + e.getMessage(), e);
        }
    }

    public void monitor() {
        if (!initialized) {
            setup();
        }

        for (Map.Entry<OrgContext, List<Failure<OrgContext>>> entry : orgFailuresMap.entrySet()) {
            OrgContext ctx = entry.getKey();
            for (Failure<OrgContext> failure : entry.getValue()) {
                try {
                    failure.monitor(ctx);
                } catch (Exception e) {
                    LOGGER.warning("[OrgFailureManager] Exception monitoring failure '" + failure.getGoalId() + "': " + e.getMessage());
                }
            }
        }
    }

    public synchronized void start(long interval, TimeUnit unit) {
        if (isRunning) return;
        setup();

        if (orgFailuresMap.isEmpty()) {
            LOGGER.info("[OrgFailureManager] No organisation failures to monitor.");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OrgFailureModelManager-Daemon");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::monitor, 1000, interval, unit);
        isRunning = true;
        LOGGER.info("[OrgFailureManager] Started background organisation failure monitor with interval " + interval + " " + unit);
    }

    public synchronized void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            isRunning = false;
            LOGGER.info("[OrgFailureManager] Stopped background organisation failure monitor.");
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public Map<OrgContext, List<Failure<OrgContext>>> getOrgFailuresMap() {
        return orgFailuresMap;
    }
}
