package moise.os.fs;

import java.io.Serializable;
import java.util.Objects;


public class ConditionSpec implements Serializable, CharSequence {
    private static final long serialVersionUID = 1L;

    private String expression;

    public ConditionSpec() {
    }

    public ConditionSpec(String expression) {
        this.expression = expression;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    /**
     * Alias for getExpression.
     */
    public String getValue() {
        return expression;
    }

    /**
     * Alias for setExpression.
     */
    public void setValue(String value) {
        this.expression = value;
    }

    public boolean isEmpty() {
        return expression == null || expression.trim().isEmpty();
    }

    public boolean isDefined() {
        return !isEmpty();
    }

    public String trim() {
        return expression != null ? expression.trim() : "";
    }

    public boolean contains(CharSequence s) {
        return expression != null && expression.contains(s);
    }

    @Override
    public int length() {
        return expression != null ? expression.length() : 0;
    }

    @Override
    public char charAt(int index) {
        if (expression == null) {
            throw new IndexOutOfBoundsException("Condition expression is null");
        }
        return expression.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        if (expression == null) {
            throw new IndexOutOfBoundsException("Condition expression is null");
        }
        return expression.subSequence(start, end);
    }

    @Override
    public String toString() {
        return expression != null ? expression : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConditionSpec that = (ConditionSpec) o;
        return Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression);
    }
}
