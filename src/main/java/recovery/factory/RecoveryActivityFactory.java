package recovery.factory;

import jason.asSemantics.Agent;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import recovery.*;
import java.util.List;
import java.util.logging.Logger;

public class RecoveryActivityFactory {
    private static final Logger LOGGER = Logger.getLogger(RecoveryActivityFactory.class.getName());

    @SuppressWarnings("unchecked")
    public static <T> RecoveryActivity<T> createActivity(String actStr) {
        if (actStr == null || actStr.trim().isEmpty()) {
            return context -> {};
        }

        String trimmed = actStr.trim();

        if (trimmed.startsWith("plan:")) {
            return (RecoveryActivity<T>) new PlanAdaptation<>(trimmed.substring(5).trim());
        } else if (trimmed.startsWith("goal:")) {
            return (RecoveryActivity<T>) new GoalAdaptation<>(trimmed.substring(5).trim(), true);
        } else if (trimmed.startsWith("env:")) {
            String envDetails = trimmed.substring(4).trim();
            String[] parts = envDetails.split("\\.");
            String workspaceName = "default";
            String artifactName = "";
            String operationName = "";

            if (parts.length >= 3) {
                workspaceName = parts[0];
                artifactName = parts[1];
                operationName = parts[2];
            } else if (parts.length == 2) {
                artifactName = parts[0];
                operationName = parts[1];
            } else if (parts.length == 1) {
                artifactName = parts[0];
            }
            return (RecoveryActivity<T>) new EnvironmentAdaptation<>(workspaceName, artifactName, operationName);
        } else if (trimmed.startsWith("org:")) {
            return (RecoveryActivity<T>) new OrganisationAdaptation<>(trimmed.substring(4).trim());
        } else {
            return (RecoveryActivity<T>) new GoalAdaptation<>(trimmed, true);
        }
    }

    public static <T> RecoveryActivity<T> createCleanupActivity(String errorName, List<String> conditions) {
        return new RecoveryActivity<T>() {
            @Override
            public void execute(T context) {
                if (context instanceof Agent) {
                    Agent agent = (Agent) context;
                    agent.getTS().getLogger().info("[Failure Monitor] Đã tự động khôi phục và xử lý lỗi: " + errorName);
                    if (conditions == null) return;

                    try {
                        for (String condStr : conditions) {
                            Literal condLiteral = ASSyntax.parseLiteral(condStr);
                            Literal matched = agent.getBB().contains(condLiteral);
                            if (matched != null && !matched.isRule()) {
                                agent.getBB().remove(matched);
                                agent.getTS().getLogger().info("[Failure Monitor] Đã xóa niềm tin lỗi: " + matched);
                            }
                        }
                    } catch (Exception e) {

                    }
                } else {
                    LOGGER.info("[Failure Monitor] [Organisation] Đã tự động khôi phục và xử lý lỗi: " + errorName);
                }
            }
        };
    }
}
