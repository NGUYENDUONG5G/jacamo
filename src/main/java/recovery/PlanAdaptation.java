package recovery;

import jason.asSemantics.Agent;
import jason.asSyntax.Literal;

/**
 * Adapts plans in the Agent dimension by triggering/selecting a substitute plan.
 */
public class PlanAdaptation<T extends Agent> implements RecoveryActivity<T> {
    private final String substitutePlanTrigger;

    public PlanAdaptation(String substitutePlanTrigger) {
        this.substitutePlanTrigger = substitutePlanTrigger;
    }

    public String getSubstitutePlanTrigger() {
        return substitutePlanTrigger;
    }

    @Override
    public void execute(T agent) {
        agent.getTS().getLogger().info("[Adaptation -> Plan] Selecting/Triggering plan: " + substitutePlanTrigger);
        try {
            Literal planLiteral = Literal.parseLiteral(substitutePlanTrigger);
            agent.getTS().getC().addAchvGoal(planLiteral, jason.asSemantics.Intention.EmptyInt);
        } catch (Exception e) {
            agent.getTS().getLogger().warning("[Adaptation -> Plan] Failed to parse substitute plan trigger: " + substitutePlanTrigger);
        }
    }
}
