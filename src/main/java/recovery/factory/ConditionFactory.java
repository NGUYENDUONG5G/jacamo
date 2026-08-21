package recovery.factory;

import jason.asSemantics.Agent;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import jason.asSemantics.Unifier;
import recovery.Condition;

public class ConditionFactory {

    public static <T extends Agent> Condition<T> createCondition(String condStr) {
        if (condStr == null || condStr.trim().isEmpty()) {
            return agent -> false;
        }

        final String cleanCond = condStr.trim();

        return new Condition<T>() {
            @Override
            public boolean evaluate(T agent) {
                try {
                    Literal condLiteral = ASSyntax.parseLiteral(cleanCond);
                    return agent.believes(condLiteral, new Unifier());
                } catch (Exception e) {
                    return false;
                }
            }
        };
    }
}
