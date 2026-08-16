package recovery;

import jason.asSemantics.Agent;
import jason.asSyntax.Literal;

/**
 * Adapts goals in the Agent dimension by retrying an existing goal or posting a new goal.
 */
public class GoalAdaptation<T extends Agent> implements RecoveryActivity<T> {
    private final String goalLiteral;
    private final boolean createNew; // true to create/post a new goal, false to retry/select existing

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
            if (createNew) {
                agent.getTS().getLogger().info("[Adaptation -> Goal] Creating and triggering new Goal: " + goalLiteral);
            } else {
                agent.getTS().getLogger().info("[Adaptation -> Goal] Selecting and retrying existing Goal: " + goalLiteral);
            }
            agent.getTS().getC().addAchvGoal(goal, jason.asSemantics.Intention.EmptyInt);
        } catch (Exception e) {
            agent.getTS().getLogger().warning("[Adaptation -> Goal] Failed to parse goal literal: " + goalLiteral);
        }
    }
}
