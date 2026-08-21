package recovery.builder;

import jason.asSemantics.Agent;
import jacamo.project.JaCaMoFailureModelParameters;
import jacamo.project.JaCaMoFailureModelParameters.FailureParameters;
import jacamo.project.JaCaMoFailureModelParameters.ErrorParameters;
import recovery.Error;
import recovery.Failure;
import recovery.factory.ConditionFactory;
import recovery.factory.RecoveryActivityFactory;
import java.util.ArrayList;
import java.util.List;

public class FailureModelBuilder {

    public static <T extends Agent> List<Failure<T>> buildFailures(JaCaMoFailureModelParameters fmParams) {
        List<Failure<T>> failures = new ArrayList<>();
        if (fmParams == null || fmParams.getFailures() == null) {
            return failures;
        }

        for (FailureParameters fParam : fmParams.getFailures()) {
            Failure<T> failure = new Failure<>(fParam.getGoalId());

            if (fParam.getErrors() != null) {
                for (ErrorParameters eParam : fParam.getErrors()) {
                    Error<T> error = new Error<>(eParam.getErrorName());

                    if (eParam.getConditions() != null) {
                        for (String condStr : eParam.getConditions()) {
                            error.addCondition(ConditionFactory.createCondition(condStr));
                        }
                    }

                    if (eParam.getRecoveryActivities() != null) {
                        for (String actStr : eParam.getRecoveryActivities()) {
                            error.addRecoveryActivity(RecoveryActivityFactory.createActivity(actStr));
                        }
                    }

                    error.addRecoveryActivity(RecoveryActivityFactory.createCleanupActivity(
                            eParam.getErrorName(),
                            eParam.getConditions()
                    ));

                    failure.addError(error);
                }
            }

            failures.add(failure);
        }

        return failures;
    }
}
