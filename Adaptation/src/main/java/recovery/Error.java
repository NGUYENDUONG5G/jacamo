package recovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class Error<T> {
    private static final Logger LOGGER = Logger.getLogger(Error.class.getName());

    private final String errorName;
    private final List<Condition<T>> conditions = new ArrayList<>();
    private final List<RecoveryActivity<T>> recoveryActivities = new ArrayList<>();
    private boolean wasTriggered = false;
    private long lastRecoveryTime = 0;

    public Error(String errorName) {
        this.errorName = errorName;
    }

    public String getErrorName() {
        return errorName;
    }

    public void addCondition(Condition<T> cond) {
        conditions.add(cond);
    }

    public void addRecoveryActivity(RecoveryActivity<T> action) {
        recoveryActivities.add(action);
    }

    public List<Condition<T>> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    public List<RecoveryActivity<T>> getRecoveryActivities() {
        return Collections.unmodifiableList(recoveryActivities);
    }

    public boolean isTriggered(T context) {
        if (conditions.isEmpty()) {
            wasTriggered = false;
            return false;
        }
        boolean active = true;
        for (Condition<T> cond : conditions) {
            if (!cond.evaluate(context)) {
                active = false;
                break;
            }
        }
        if (!active) {
            wasTriggered = false;
            return false;
        }
        long now = System.currentTimeMillis();
        if (!wasTriggered || (now - lastRecoveryTime > 3000)) {
            wasTriggered = true;
            lastRecoveryTime = now;
            return true;
        }
        return false;
    }

    public void performRecovery(T context) {
        LOGGER.info("[Error Handler] Triggering recovery for: " + errorName);
        for (RecoveryActivity<T> activity : recoveryActivities) {
            activity.execute(context);
        }
    }
}
