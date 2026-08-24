package recovery.factory;

import jason.asSemantics.Agent;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import jason.asSyntax.LogicalFormula;
import jason.asSyntax.LogExpr;
import jason.asSyntax.Rule;
import recovery.*;

import java.util.ArrayList;
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

                            // 1. Xóa trực tiếp nếu condition là một fact niềm tin
                            Literal matched = agent.getBB().contains(condLiteral);
                            if (matched != null && !matched.isRule()) {
                                agent.getBB().remove(matched);
                                agent.getTS().getLogger().info("[Failure Monitor] Đã xóa niềm tin lỗi: " + matched);
                            }

                            // 2. Nếu condition là một Rule (ví dụ: supply_water_insufficient :- supply_tank_empty.)
                            // Tìm các niềm tin sự kiện cơ sở trong thân rule (body) để xóa triệt để
                            List<Literal> factsToRemove = new ArrayList<>();
                            for (Literal b : agent.getBB()) {
                                if (b.isRule() && b.getFunctor().equals(condLiteral.getFunctor())) {
                                    Rule rule = (Rule) b;
                                    LogicalFormula body = rule.getBody();
                                    collectPositiveLiterals(body, factsToRemove);
                                }
                            }

                            for (Literal fact : factsToRemove) {
                                Literal factMatched = agent.getBB().contains(fact);
                                if (factMatched != null && !factMatched.isRule()) {
                                    agent.getBB().remove(factMatched);
                                    agent.getTS().getLogger().info("[Failure Monitor] Đã xóa niềm tin sự kiện cơ sở: " + factMatched);
                                } else {
                                    agent.delBel(fact);
                                }
                            }
                        }
                    } catch (Exception e) {
                        agent.getTS().getLogger().warning("[Failure Monitor] Lỗi khi dọn dẹp niềm tin lỗi: " + e.getMessage());
                    }
                } else {
                    LOGGER.info("[Failure Monitor] [Organisation] Đã tự động khôi phục và xử lý lỗi: " + errorName);
                }
            }
        };
    }

    private static void collectPositiveLiterals(LogicalFormula formula, List<Literal> result) {
        if (formula == null) return;
        if (formula instanceof LogExpr) {
            LogExpr expr = (LogExpr) formula;
            if (expr.getOp() != LogExpr.LogicalOp.not) {
                if (expr.getLHS() instanceof LogicalFormula) {
                    collectPositiveLiterals((LogicalFormula) expr.getLHS(), result);
                }
                if (expr.getRHS() instanceof LogicalFormula) {
                    collectPositiveLiterals((LogicalFormula) expr.getRHS(), result);
                }
            }
        } else if (formula instanceof Literal) {
            Literal lit = (Literal) formula;
            if (!lit.isRule() && !lit.negated()) {
                result.add(lit);
            }
        }
    }
}
