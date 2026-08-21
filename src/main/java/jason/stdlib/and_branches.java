package jason.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.IntendedMeans;
import jason.asSemantics.Intention;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.ListTerm;
import jason.asSyntax.Literal;
import jason.asSyntax.PlanBody;
import jason.asSyntax.PlanBodyImpl;
import jason.asSyntax.Term;

import java.util.ArrayList;
import java.util.List;

public class and_branches extends DefaultInternalAction {
    private static final long serialVersionUID = 1L;

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length == 0 || !args[0].isList()) {
            return false;
        }

        ListTerm subGoalsList = (ListTerm) args[0];
        if (subGoalsList.isEmpty()) {
            return true;
        }

        Intention intention = ts.getC().getSelectedIntention();
        if (intention == null || intention.size() == 0) {
            return true;
        }

        IntendedMeans im = intention.peek();
        PlanBody currentStep = im.getCurrentStep();
        if (currentStep == null) {
            return true;
        }

        PlanBody remainingSteps = currentStep.getBodyNext();

        List<PlanBody> newSteps = new ArrayList<>();
        for (Term t : subGoalsList) {
            PlanBody pb;
            if (t instanceof PlanBody) {
                pb = (PlanBody) ((PlanBody) t).clone();
            } else if (t instanceof Literal) {
                Literal lit = (Literal) t;
                String functor = lit.getFunctor();
                if (functor.startsWith(".")) {
                    pb = new PlanBodyImpl(PlanBody.BodyType.internalAction, (Literal) lit.clone());
                } else {
                    pb = new PlanBodyImpl(PlanBody.BodyType.achieve, (Literal) lit.clone());
                }
            } else {
                String s = t.toString().trim();
                if (s.startsWith(".")) {
                    pb = new PlanBodyImpl(PlanBody.BodyType.internalAction, Literal.parseLiteral(s));
                } else {
                    if (s.startsWith("!")) s = s.substring(1).trim();
                    pb = new PlanBodyImpl(PlanBody.BodyType.achieve, Literal.parseLiteral(s));
                }
            }
            newSteps.add(pb);
        }

        if (!newSteps.isEmpty()) {
            for (int i = 0; i < newSteps.size() - 1; i++) {
                newSteps.get(i).setBodyNext(newSteps.get(i + 1));
            }
            im.insertAsNextStep(newSteps.get(0));
        }

        return true;
    }
}
