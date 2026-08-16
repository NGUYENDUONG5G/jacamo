package jason.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;
import jason.asSyntax.NumberTerm;

public class recovery_traffic extends DefaultInternalAction {
    private static final long serialVersionUID = 1L;

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length > 0 && args[0].isNumeric()) {
            int step = (int) ((NumberTerm) args[0]).solve();
            if (step == 1) {
                ts.getLogger().info("Truck (delivery_truck): Bắt đầu thực hiện mục tiêu giao hàng đúng giờ deliver_on_time lần đầu...");
                ts.getLogger().info("Phát hiện kẹt xe nghiêm trọng! Kích hoạt Failure Model bằng cách thêm niềm tin traffic(congested)...");
            } else if (step == 2) {
                ts.getLogger().info("Truck (delivery_truck): Đang thực hiện lại mục tiêu deliver_on_time với tốc độ cao hơn sau khi khôi phục kẹt xe...");
            }
        }
        return true;
    }
}
