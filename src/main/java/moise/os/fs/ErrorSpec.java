package moise.os.fs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class ErrorSpec implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private ConditionSpec condition;
    private List<ArgumentSpec> arguments = new ArrayList<>();
    private RecoveryAct recoveryAct;

    public ErrorSpec() {
    }

    public ErrorSpec(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ConditionSpec getCondition() {
        return condition;
    }

    public void setCondition(ConditionSpec condition) {
        this.condition = condition;
    }

    public void setCondition(String condition) {
        this.condition = (condition != null) ? new ConditionSpec(condition) : null;
    }

    public String getConditionString() {
        return condition != null ? condition.getExpression() : null;
    }

    public List<ArgumentSpec> getArguments() {
        return Collections.unmodifiableList(arguments);
    }

    public void setArguments(List<ArgumentSpec> arguments) {
        this.arguments = (arguments != null) ? new ArrayList<>(arguments) : new ArrayList<>();
    }

    public void addArgument(ArgumentSpec arg) {
        if (arg != null) {
            this.arguments.add(arg);
        }
    }

    public ArgumentSpec getArgument(String argId) {
        for (ArgumentSpec a : arguments) {
            if (a.getId() != null && a.getId().equals(argId)) {
                return a;
            }
        }
        return null;
    }

    public RecoveryAct getRecoveryAct() {
        return recoveryAct;
    }

    public void setRecoveryAct(RecoveryAct recoveryAct) {
        this.recoveryAct = recoveryAct;
    }

    @Override
    public String toString() {
        return "ErrorSpec{" +
                "id='" + id + '\'' +
                ", condition='" + condition + '\'' +
                ", arguments=" + arguments +
                ", recoveryAct=" + recoveryAct +
                '}';
    }
}
