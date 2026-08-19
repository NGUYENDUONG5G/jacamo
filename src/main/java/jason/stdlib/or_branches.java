package jason.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.IntendedMeans;
import jason.asSemantics.Intention;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.ListTerm;
import jason.asSyntax.Literal;
import jason.asSyntax.Plan;
import jason.asSyntax.PlanBody;
import jason.asSyntax.PlanBodyImpl;
import jason.asSyntax.Term;
import jason.asSyntax.Trigger;

import java.util.List;

public class or_branches extends DefaultInternalAction {
    private static final long serialVersionUID = 1L;

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length == 0 || !args[0].isList()) {
            return false;
        }

        ListTerm candidateList = (ListTerm) args[0];
        if (candidateList.isEmpty()) {
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

        Term selectedCandidate = null;

        for (Term t : candidateList) {
            Literal goalLit = extractGoalLiteral(t);
            if (goalLit != null) {
                if (goalLit.getFunctor().startsWith(".")) {
                    selectedCandidate = t;
                    break;
                }

                Trigger te = new Trigger(Trigger.TEOperator.add, Trigger.TEType.achieve, goalLit);
                List<Plan> candidatePlans = ts.getAg().getPL().getCandidatePlans(te);
                if (candidatePlans != null && !candidatePlans.isEmpty()) {
                    for (Plan plan : candidatePlans) {
                        if (plan.getContext() == null || 
                            plan.getContext().logicalConsequence(ts.getAg(), un).hasNext()) {
                            selectedCandidate = t;
                            break;
                        }
                    }
                }
            }
            if (selectedCandidate != null) {
                break;
            }
        }

        if (selectedCandidate == null) {
            selectedCandidate = candidateList.get(0);
        }

        PlanBody selectedStep;
        if (selectedCandidate instanceof PlanBody) {
            selectedStep = (PlanBody) ((PlanBody) selectedCandidate).clone();
        } else if (selectedCandidate instanceof Literal) {
            Literal lit = (Literal) selectedCandidate;
            if (lit.getFunctor().startsWith(".")) {
                selectedStep = new PlanBodyImpl(PlanBody.BodyType.internalAction, (Literal) lit.clone());
            } else {
                selectedStep = new PlanBodyImpl(PlanBody.BodyType.achieve, (Literal) lit.clone());
            }
        } else {
            String s = selectedCandidate.toString().trim();
            if (s.startsWith(".")) {
                selectedStep = new PlanBodyImpl(PlanBody.BodyType.internalAction, Literal.parseLiteral(s));
            } else {
                if (s.startsWith("!")) s = s.substring(1).trim();
                selectedStep = new PlanBodyImpl(PlanBody.BodyType.achieve, Literal.parseLiteral(s));
            }
        }

        currentStep.setBodyNext(selectedStep);
        selectedStep.setBodyNext(remainingSteps);

        return true;
    }

    private Literal extractGoalLiteral(Term t) {
        try {
            if (t instanceof PlanBody) {
                Term bodyTerm = ((PlanBody) t).getBodyTerm();
                return bodyTerm instanceof Literal ? (Literal) bodyTerm : Literal.parseLiteral(bodyTerm.toString());
            } else if (t instanceof Literal) {
                return (Literal) t;
            } else {
                String s = t.toString().trim();
                if (s.startsWith("!")) s = s.substring(1).trim();
                return Literal.parseLiteral(s);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
