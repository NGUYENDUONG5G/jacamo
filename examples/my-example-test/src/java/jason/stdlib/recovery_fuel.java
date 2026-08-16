package jason.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;
import jason.asSyntax.NumberTerm;

public class recovery_fuel extends DefaultInternalAction {
    private static final long serialVersionUID = 1L;

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length > 0 && args[0].isNumeric()) {
            int step = (int) ((NumberTerm) args[0]).solve();
            if (step == 1) {
                ts.getLogger().info("Đột ngột phát hiện kim xăng chỉ về 0! Xe hết xăng! Kích hoạt Failure Model bằng cách thêm niềm tin fuel(empty)...");
            } else if (step == 2) {
                ts.getLogger().info("Truck (delivery_truck): Nhiên liệu đầy, bắt đầu chặng cuối giao hàng đúng giờ deliver_on_time!");
            }
        }
        return true;
    }
}
