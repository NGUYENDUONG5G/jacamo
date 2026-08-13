package recovery;

import java.util.ArrayList;
import java.util.List;

public class Failure<T> {
    private final String goalId;
    private final List<Error<T>> errors = new ArrayList<>();

    public Failure(String goalId) {
        this.goalId = goalId;
    }

    public void addError(Error<T> error) {
        errors.add(error);
    }

    public void monitor(T context) {
        for (Error<T> error : errors) {
            if (error.isTriggered(context)) {
                if (context instanceof jason.asSemantics.Agent) {
                    ((jason.asSemantics.Agent) context).getTS().getLogger().info("[Failure Monitor] Goal '" + goalId + "' failed due to error trigger.");
                } else {
                    System.out.println("[Failure Monitor] Goal '" + goalId + "' failed due to error trigger.");
                }
                error.performRecovery(context);
            }
        }
    }
}
