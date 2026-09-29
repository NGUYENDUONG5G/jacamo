package moise.os.fs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class Failure implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String goalId;
    private List<ErrorSpec> errors = new ArrayList<>();

    public Failure() {
    }

    public Failure(String id, String goalId) {
        this.id = id;
        this.goalId = goalId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGoalId() {
        return goalId;
    }

    public void setGoalId(String goalId) {
        this.goalId = goalId;
    }

    public List<ErrorSpec> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public void setErrors(List<ErrorSpec> errors) {
        this.errors = (errors != null) ? new ArrayList<>(errors) : new ArrayList<>();
    }

    public void addError(ErrorSpec error) {
        if (error != null) {
            this.errors.add(error);
        }
    }

    public ErrorSpec getError(String errorId) {
        for (ErrorSpec e : errors) {
            if (e.getId() != null && e.getId().equals(errorId)) {
                return e;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Failure{" +
                "id='" + id + '\'' +
                ", goalId='" + goalId + '\'' +
                ", errors=" + errors +
                '}';
    }
}
