package recovery.builder;

import jacamo.project.JaCaMoFailureModelParameters;
import jacamo.project.JaCaMoFailureModelParameters.ErrorParameters;
import jacamo.project.JaCaMoFailureModelParameters.FailureParameters;
import recovery.Error;
import recovery.Failure;
import recovery.context.OrgContext;
import recovery.factory.OrgConditionFactory;
import recovery.factory.OrgRecoveryActivityFactory;

import java.util.ArrayList;
import java.util.List;

public class OrgFailureModelBuilder {

    public static List<Failure<OrgContext>> buildFailures(JaCaMoFailureModelParameters fmParams, List<String> allowedFailures) {
        List<Failure<OrgContext>> failures = new ArrayList<>();
        if (fmParams == null || fmParams.getFailures() == null) {
            return failures;
        }

        for (FailureParameters fParam : fmParams.getFailures()) {
            if (allowedFailures != null && !allowedFailures.isEmpty() && !allowedFailures.contains(fParam.getGoalId())) {
                continue;
            }

            Failure<OrgContext> failure = new Failure<>(fParam.getGoalId());

            if (fParam.getErrors() != null) {
                for (ErrorParameters eParam : fParam.getErrors()) {
                    Error<OrgContext> error = new Error<>(eParam.getErrorName());

                    if (eParam.getConditions() != null) {
                        for (String condStr : eParam.getConditions()) {
                            error.addCondition(OrgConditionFactory.createCondition(condStr));
                        }
                    }

                    if (eParam.getRecoveryActivities() != null) {
                        for (String actStr : eParam.getRecoveryActivities()) {
                            error.addRecoveryActivity(OrgRecoveryActivityFactory.createActivity(actStr));
                        }
                    }

                    failure.addError(error);
                }
            }

            failures.add(failure);
        }

        return failures;
    }
}
