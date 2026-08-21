package recovery.setup;

import jason.asSemantics.Agent;
import jason.runtime.Settings;
import jacamo.project.JaCaMoAgentParameters;
import jacamo.project.JaCaMoFailureModelParameters;
import recovery.Failure;
import recovery.builder.FailureModelBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class AgentFailureModelManager {
    private final Agent agent;
    private final List<Failure<Agent>> failures = new ArrayList<>();
    private boolean initialized = false;

    public AgentFailureModelManager(Agent agent) {
        this.agent = agent;
    }

    public synchronized void setup() {
        if (initialized) return;
        initialized = true;

        try {
            if (agent == null || agent.getTS() == null) {
                initialized = false;
                return;
            }

            Settings settings = agent.getTS().getSettings();
            if (settings == null) return;

            Object projectParam = settings.getUserParameters().get(Settings.PROJECT_PARAMETER);
            if (!(projectParam instanceof JaCaMoAgentParameters)) return;

            JaCaMoAgentParameters ap = (JaCaMoAgentParameters) projectParam;
            var jcmProject = ap.getProject();
            if (jcmProject == null) return;

            JaCaMoFailureModelParameters fmParams = jcmProject.getFailureModel();
            if (fmParams == null) return;

            List<Failure<Agent>> builtFailures = FailureModelBuilder.buildFailures(fmParams);
            if (ap.getFailures() != null && !ap.getFailures().isEmpty()) {
                for (Failure<Agent> f : builtFailures) {
                    if (ap.getFailures().contains(f.getGoalId())) {
                        failures.add(f);
                    }
                }
            } else {
                failures.addAll(builtFailures);
            }

            for (Failure<Agent> failure : failures) {
                agent.getTS().getLogger().info("[FailureModelManager] Registered failure monitoring for goal: " + failure.getGoalId() + " on agent: " + agent.getTS().getUserAgArch().getAgName());
            }

        } catch (Exception e) {
            if (agent.getTS() != null && agent.getTS().getLogger() != null) {
                agent.getTS().getLogger().log(Level.SEVERE, "[FailureModelManager] Error setting up failure model: " + e.getMessage(), e);
            }
        }
    }

    public void monitor() {
        if (!initialized) {
            setup();
        }
        for (Failure<Agent> failure : failures) {
            failure.monitor(agent);
        }
    }

    public List<Failure<Agent>> getFailures() {
        return Collections.unmodifiableList(failures);
    }

    public boolean isInitialized() {
        return initialized;
    }
}
