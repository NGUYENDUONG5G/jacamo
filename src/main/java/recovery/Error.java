package recovery;

import java.util.ArrayList;
import java.util.List;

public class Error<T> {
    private final String errorName;
    private final List<Condition<T>> conditions = new ArrayList<>();
    private final List<RecoveryActivity<T>> recoveryActivities = new ArrayList<>();

    public Error(String errorName) {
        this.errorName = errorName;
    }

    public void addCondition(Condition<T> cond) {
        conditions.add(cond);
    }

    public void addRecoveryActivity(RecoveryActivity<T> action) {
        recoveryActivities.add(action);
    }

    public boolean isTriggered(T context) {
        if (conditions.isEmpty()) return false;
        for (Condition<T> cond : conditions) {
            if (!cond.evaluate(context)) {
                return false;
            }
        }
        return true;
    }

    public void performRecovery(T context) {
        if (context instanceof jason.asSemantics.Agent) {
            ((jason.asSemantics.Agent) context).getTS().getLogger().info("[Error Handler] Triggering recovery for: " + errorName);
        } else {
            System.out.println("[Error Handler] Triggering recovery for: " + errorName);
        }
        for (RecoveryActivity<T> activity : recoveryActivities) {
            activity.execute(context);
        }
    }
}
