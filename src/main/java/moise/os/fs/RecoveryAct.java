package moise.os.fs;

import java.io.Serializable;


public class RecoveryAct implements Serializable {
    private static final long serialVersionUID = 1L;

    private String scheme;

    public RecoveryAct() {
    }

    public RecoveryAct(String scheme) {
        this.scheme = scheme;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public String toString() {
        return "RecoveryAct{scheme='" + scheme + "'}";
    }
}
