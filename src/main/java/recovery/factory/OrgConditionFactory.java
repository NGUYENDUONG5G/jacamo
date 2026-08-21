package recovery.factory;

import cartago.ArtifactObsProperty;
import recovery.Condition;
import recovery.context.OrgContext;

import java.util.logging.Logger;

public class OrgConditionFactory {
    private static final Logger LOGGER = Logger.getLogger(OrgConditionFactory.class.getName());

    public static Condition<OrgContext> createCondition(String condStr) {
        if (condStr == null || condStr.trim().isEmpty()) {
            return ctx -> false;
        }

        final String cleanCond = condStr.trim();

        if (cleanCond.startsWith("cardinality(") || cleanCond.startsWith("role_cardinality(")) {
            return ctx -> evaluateCardinality(ctx, cleanCond);
        }

        return ctx -> {
            try {

                String targetArt = ctx.getGroupName() != null ? ctx.getGroupName() : ctx.getOrgName();
                if (targetArt == null) return false;

                ArtifactObsProperty prop = ctx.getObsProperty(targetArt, cleanCond);
                if (prop != null) {
                    Object val = prop.getValue();
                    if (val instanceof Boolean) return (Boolean) val;
                    return true;
                }
                return false;
            } catch (Exception e) {
                return false;
            }
        };
    }

    private static boolean evaluateCardinality(OrgContext ctx, String condStr) {
        try {
            int openIdx = condStr.indexOf('(');
            int closeIdx = condStr.indexOf(')');
            if (openIdx == -1 || closeIdx == -1) return false;

            String role = condStr.substring(openIdx + 1, closeIdx).trim();
            int minVal = 1;

            if (condStr.contains("<")) {
                String valStr = condStr.substring(condStr.indexOf("<") + 1).trim();
                minVal = Integer.parseInt(valStr);
            }

            String targetArt = ctx.getGroupName() != null ? ctx.getGroupName() : ctx.getOrgName();
            if (targetArt == null) return false;

            ArtifactObsProperty prop = ctx.getObsProperty(targetArt, "players");
            if (prop != null) {

                int count = 0;
                for (int i = 0; i < prop.getValues().length; i++) {
                    Object v = prop.getValue(i);
                    if (v != null && v.toString().contains(role)) {
                        count++;
                    }
                }
                return count < minVal;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
