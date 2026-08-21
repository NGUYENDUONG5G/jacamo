package recovery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OrCondition<T> implements Condition<T> {
    private final List<Condition<T>> conditions = new ArrayList<>();

    @SafeVarargs
    public OrCondition(Condition<T>... conditions) {
        this.conditions.addAll(Arrays.asList(conditions));
    }

    public OrCondition(List<Condition<T>> conditions) {
        this.conditions.addAll(conditions);
    }

    public OrCondition<T> add(Condition<T> condition) {
        this.conditions.add(condition);
        return this;
    }

    @Override
    public boolean evaluate(T context) {
        if (conditions.isEmpty()) {
            return false;
        }
        for (Condition<T> condition : conditions) {
            if (condition.evaluate(context)) {
                return true;
            }
        }
        return false;
    }
}
