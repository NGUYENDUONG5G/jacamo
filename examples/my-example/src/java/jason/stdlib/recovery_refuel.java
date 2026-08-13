package jason.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;
import jason.asSyntax.NumberTerm;

public class recovery_refuel extends DefaultInternalAction {
    private static final long serialVersionUID = 1L;

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length > 0 && args[0].isNumeric()) {
            int step = (int) ((NumberTerm) args[0]).solve();
            if (step == 1) {
                ts.getLogger().info("Truck (delivery_truck): Đang thực hiện mục tiêu cứu trợ refuel...");
            } else if (step == 2) {
                ts.getLogger().info("Truck (delivery_truck): Đã tiếp nhiên liệu thành công!");
            }
        }
        return true;
    }
}
