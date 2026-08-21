package recovery;

import jason.asSemantics.Agent;
import jason.asSyntax.Literal;

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
        try {
            Literal planLiteral = Literal.parseLiteral(substitutePlanTrigger);
            agent.getTS().getC().addAchvGoal(planLiteral, null);
        } catch (Exception e) {
            agent.getTS().getLogger().warning("Failed to execute plan adaptation: " + substitutePlanTrigger);
        }
    }
}
