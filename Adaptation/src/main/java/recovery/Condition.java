package recovery;

public interface Condition<T> {
    boolean evaluate(T context);
}
