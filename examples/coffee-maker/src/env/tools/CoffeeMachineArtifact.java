package tools;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.ObsProperty;

public class CoffeeMachineArtifact extends Artifact {

    @OPERATION
    public void init(int initialBeans) {
        defineObsProperty("beans_level", initialBeans);
        defineObsProperty("coffee_status", "STANDBY");
        defineObsProperty("grinder_state", "READY");
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
        ObsProperty gs = getObsProperty("grinder_state");
        if (gs != null) gs.updateValue("OPERATING");
        ObsProperty le = getObsProperty("last_event");
        if (le != null) le.updateValue("Ground " + amount + "g of fresh coffee beans.");
        System.out.println("[CoffeeMachineEnv] Grinding " + amount + "g beans. Remaining: " + (bl != null ? bl.intValue() : 0) + "g");
    }

    @OPERATION
    public void refill_beans(int amount) {
        ObsProperty bl = getObsProperty("beans_level");
        if (bl != null) {
            int current = bl.intValue();
            int next = current + amount;
            bl.updateValue(next);
            System.out.println("[CoffeeMachineEnv] Refilled " + amount + "g beans. New total: " + next + "g");
        }
        ObsProperty le = getObsProperty("last_event");
        if (le != null) le.updateValue("Refilled " + amount + "g of beans.");
    }

    @OPERATION
    public void reset_grinder() {
        ObsProperty gs = getObsProperty("grinder_state");
        if (gs != null) gs.updateValue("READY");
        ObsProperty cs = getObsProperty("coffee_status");
        if (cs != null) cs.updateValue("STANDBY");
        ObsProperty le = getObsProperty("last_event");
        if (le != null) le.updateValue("Grinder unjammed and reset successfully.");
        System.out.println("[CoffeeMachineEnv] Grinder unclogged and reset to READY.");
    }

    @OPERATION
    public void cancel_operation() {
        ObsProperty cs = getObsProperty("coffee_status");
        if (cs != null) cs.updateValue("CANCELLED");
        ObsProperty le = getObsProperty("last_event");
        if (le != null) le.updateValue("Coffee making operation cancelled and reset.");
        System.out.println("[CoffeeMachineEnv] Operation cancelled.");
    }
}
