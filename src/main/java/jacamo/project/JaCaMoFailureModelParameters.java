package jacamo.project;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class JaCaMoFailureModelParameters implements Serializable {
    private static final long serialVersionUID = 1L;

    protected JaCaMoProject project;
    protected String name;
    protected List<FailureParameters> failures = new ArrayList<>();

    public JaCaMoFailureModelParameters(JaCaMoProject project) {
        this.project = project;
    }

    public void setName(String name) { this.name = name; }
    public String getName() { return name; }

    public void addFailure(FailureParameters f) { failures.add(f); }
    public List<FailureParameters> getFailures() { return failures; }

    public static class FailureParameters implements Serializable {
        private static final long serialVersionUID = 1L;
        protected String goalId;
        protected List<ErrorParameters> errors = new ArrayList<>();

        public FailureParameters(String goalId) {
            this.goalId = goalId;
        }
        public String getGoalId() { return goalId; }
        public void addError(ErrorParameters e) { errors.add(e); }
        public List<ErrorParameters> getErrors() { return errors; }
    }

    public static class ErrorParameters implements Serializable {
        private static final long serialVersionUID = 1L;
        protected String errorName;
        protected List<String> conditions = new ArrayList<>();
        protected List<String> recoveryActivities = new ArrayList<>();

        public ErrorParameters(String errorName) {
            this.errorName = errorName;
        }
        public String getErrorName() { return errorName; }
        public void addCondition(String c) { conditions.add(c); }
        public List<String> getConditions() { return conditions; }
        public void addRecoveryActivity(String r) { recoveryActivities.add(r); }
        public List<String> getRecoveryActivities() { return recoveryActivities; }
    }
}
