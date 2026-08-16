package jason.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;
import jason.asSyntax.StringTerm;

public class recovery_setup extends DefaultInternalAction {
    private static final long serialVersionUID = 1L;

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length > 0 && args[0].isString()) {
            String val = ((StringTerm) args[0]).getString();
            if ("exist".equals(val)) {
                ts.getLogger().info("Môi trường giao hàng đã tồn tại. Chỉ tham gia tổ chức...");
            } else if ("init".equals(val)) {
                ts.getLogger().info("DeliveryTruck đang chuẩn bị thiết lập môi trường giao hàng...");
            } else if ("route".equals(val)) {
                ts.getLogger().info("Dispatcher (delivery_truck): Đang lập lộ trình giao hàng tối ưu...");
            }
        }
        return true;
    }
}
