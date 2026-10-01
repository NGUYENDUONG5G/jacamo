package ora4mas.nopl;

/**
 * Fault-tolerant OrgBoard that creates FaultTolerantSchemeBoard for schemes.
 */
public class FaultTolerantOrgBoard extends OrgBoard {

    @Override
    protected String getSchemeBoardClass() {
        return "ora4mas.nopl.FaultTolerantSchemeBoard";
    }
}
