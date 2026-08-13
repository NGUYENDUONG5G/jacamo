package jason.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;

public class recovery_location extends DefaultInternalAction {
    private static final long serialVersionUID = 1L;

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length > 0) {
            String loc = args[0].toString().replace("\"", "");
            ts.getLogger().info("Truck quan sát thấy vị trí hiện tại: " + loc);
        }
        return true;
    }
}
