package jason.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;

public class a extends DefaultInternalAction {
    private static final long serialVersionUID = 1L;

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        ts.getLogger().info("Đã khôi phục mục tiêu: safe_setup");
        return true;
    }
}
