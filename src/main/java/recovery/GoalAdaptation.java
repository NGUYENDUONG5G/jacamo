package recovery;

import jason.asSemantics.Agent;
import jason.asSyntax.Literal;

public class GoalAdaptation<T extends Agent> implements RecoveryActivity<T> {
    private final String goalLiteral;
    private final boolean createNew;

    public GoalAdaptation(String goalLiteral, boolean createNew) {
        this.goalLiteral = goalLiteral;
        this.createNew = createNew;
    }

    public String getGoalLiteral() {
        return goalLiteral;
    }

    public boolean isCreateNew() {
        return createNew;
    }

    @Override
    public void execute(T agent) {
        try {
            Literal goal = Literal.parseLiteral(goalLiteral);
            agent.getTS().getC().addAchvGoal(goal, null);
        } catch (Exception e) {
            agent.getTS().getLogger().warning("Failed to execute goal adaptation: " + goalLiteral);
        }
    }
}
