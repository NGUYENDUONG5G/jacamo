package recovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class Failure<T> {
    private static final Logger LOGGER = Logger.getLogger(Failure.class.getName());

    private final String goalId;
    private final List<Error<T>> errors = new ArrayList<>();

    public Failure(String goalId) {
        this.goalId = goalId;
    }

    public String getGoalId() {
        return goalId;
    }

    public void addError(Error<T> error) {
        errors.add(error);
    }

    public List<Error<T>> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public void monitor(T context) {
        for (Error<T> error : errors) {
            if (error.isTriggered(context)) {
                LOGGER.info("[Failure Monitor] Goal '" + goalId + "' failed due to error trigger: " + error.getErrorName());
                error.performRecovery(context);
            }
        }
    }
}
