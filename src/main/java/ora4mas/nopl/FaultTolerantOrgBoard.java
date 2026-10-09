package ora4mas.nopl;


public class FaultTolerantOrgBoard extends OrgBoard {

    @Override
    public void init(String osFile) throws npl.parser.ParseException, moise.common.MoiseException, cartago.OperationException {
        super.init(osFile);
    }

    @Override
    protected String getSchemeBoardClass() {
        return "ora4mas.nopl.FaultTolerantSchemeBoard";
    }
}
