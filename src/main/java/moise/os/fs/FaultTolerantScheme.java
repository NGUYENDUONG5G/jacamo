package moise.os.fs;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;


public class FaultTolerantScheme extends Scheme {
    private static final long serialVersionUID = 1L;

    private Map<String, Failure> failures = new LinkedHashMap<>();

    public FaultTolerantScheme(String id, FS fs) {
        super(id, fs);
    }

    public void addFailure(Failure f) {
        if (f != null && f.getGoalId() != null) {
            failures.put(f.getGoalId(), f);
        }
    }

    public Failure getFailureForGoal(String goalId) {
        return failures.get(goalId);
    }

    public Failure getFailure(String failureId) {
        for (Failure f : failures.values()) {
            if (f.getId() != null && f.getId().equals(failureId)) {
                return f;
            }
        }
        return null;
    }

    public Collection<Failure> getFailures() {
        return Collections.unmodifiableCollection(failures.values());
    }
}
