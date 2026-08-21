package recovery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AndCondition<T> implements Condition<T> {
    private final List<Condition<T>> conditions = new ArrayList<>();

    @SafeVarargs
    public AndCondition(Condition<T>... conditions) {
        this.conditions.addAll(Arrays.asList(conditions));
    }

    public AndCondition(List<Condition<T>> conditions) {
        this.conditions.addAll(conditions);
    }

    public AndCondition<T> add(Condition<T> condition) {
        this.conditions.add(condition);
        return this;
    }

    @Override
    public boolean evaluate(T context) {
        if (conditions.isEmpty()) {
            return false;
        }
        for (Condition<T> condition : conditions) {
            if (!condition.evaluate(context)) {
                return false;
            }
        }
        return true;
    }
}
