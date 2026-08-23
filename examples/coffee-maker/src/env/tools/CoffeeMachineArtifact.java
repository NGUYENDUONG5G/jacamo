package tools;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.ObsProperty;

public class CoffeeMachineArtifact extends Artifact {

    @OPERATION
    public void init(int initialBeans) {
        defineObsProperty("beans_level", initialBeans);
        defineObsProperty("coffee_status", "STANDBY");
        defineObsProperty("last_event", "Coffee Machine initialized and ready.");
        System.out.println("[CoffeeMachineEnv] Initialized: Beans=" + initialBeans + "g");
    }

   
    @OPERATION
    public void grind_beans(int amount) {
        ObsProperty bl = getObsProperty("beans_level");
        if (bl != null) {
            int current = bl.intValue();
            int next = Math.max(0, current - amount);
            bl.updateValue(next);
        }
        ObsProperty cs = getObsProperty("coffee_status");
        if (cs != null) cs.updateValue("GRINDING");
        ObsProperty le = getObsProperty("last_event");
        if (le != null) le.updateValue("Ground " + amount + "g of fresh coffee beans.");
        System.out.println("[CoffeeMachineEnv] Grinding " + amount + "g beans. Remaining: " + (bl != null ? bl.intValue() : 0) + "g");
    }

    @OPERATION
    public void refill_beans(int amount) {
        ObsProperty le = getObsProperty("last_event");
        if (le != null) le.updateValue("Beans refill requested (" + amount + "g), waiting for beans_level update.");
        System.out.println("[CoffeeMachineEnv] Refill requested (" + amount + "g) -> Waiting for beans_level update.");
    }
}

