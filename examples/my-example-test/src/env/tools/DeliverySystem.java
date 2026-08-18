package tools;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.ObsProperty;

public class DeliverySystem extends Artifact {

    @OPERATION public void init(String initialLoc){
        defineObsProperty("location", initialLoc);
        defineObsProperty("status", "ready");
        defineObsProperty("cargo_locked", true);
        System.out.println("[Environment] DeliverySystem initialized at: " + initialLoc);
    }

    @OPERATION public void reroute() {
        System.out.println("[Environment] Recalculating global route avoidance matrix...");
    }

    @OPERATION public void emergency_brake() {
        System.out.println("[Environment] EMERGENCY BRAKE ENGAGED! Flashing hazard lights, sounding buzzer, broadcasting GPS coordinates to rescue team.");
    }

    @OPERATION public void lock_cargo() {
        ObsProperty locked = getObsProperty("cargo_locked");
        if (locked != null) locked.updateValue(true);
        System.out.println("[Environment] Cargo hatch securely LOCKED.");
    }

    @OPERATION public void unlock_cargo() {
        ObsProperty locked = getObsProperty("cargo_locked");
        if (locked != null) locked.updateValue(false);
        System.out.println("[Environment] Cargo hatch UNLOCKED for customer.");
    }

    @OPERATION public void arrive(String dest) {
        ObsProperty locProp = getObsProperty("location");
        ObsProperty statusProp = getObsProperty("status");
        if (locProp != null) locProp.updateValue(dest);
        if (statusProp != null) statusProp.updateValue("arrived");
        System.out.println("[Environment] Vehicle arrived at: " + dest);
    }
}
