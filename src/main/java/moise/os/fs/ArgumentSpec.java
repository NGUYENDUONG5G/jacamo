package moise.os.fs;

import java.io.Serializable;

/**
 * Specification of an argument/parameter for an organizational error.
 */
public class ArgumentSpec implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private int arity;

    public ArgumentSpec() {
    }

    public ArgumentSpec(String id, int arity) {
        this.id = id;
        this.arity = arity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getArity() {
        return arity;
    }

    public void setArity(int arity) {
        this.arity = arity;
    }

    @Override
    public String toString() {
        return "ArgumentSpec{id='" + id + "', arity=" + arity + "}";
    }
}
