package recovery;

public interface RecoveryActivity<T> {
    void execute(T context);
}
