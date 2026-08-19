package tools;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.ObsProperty;


public class BatteryArtifact extends Artifact {

    @OPERATION
    public void init(int initialCharge) {
        defineObsProperty("charge", initialCharge);
        defineObsProperty("battery_status", "normal"); // "normal", "low", "critical", "charging", "full"
        System.out.println("[BatteryArtifact] Initialized with charge: " + initialCharge + "%");
    }

    @OPERATION
    public void consume(int amount) {
        ObsProperty chargeProp = getObsProperty("charge");
        ObsProperty statusProp = getObsProperty("battery_status");
        if (chargeProp != null) {
            int current = chargeProp.intValue();
            int updated = Math.max(0, current - amount);
            chargeProp.updateValue(updated);
            
            if (updated <= 10) {
                statusProp.updateValue("low");
                signal("low_battery_warning", updated);
                System.out.println("[BatteryArtifact] WARNING: Battery critically low (" + updated + "% <= 10%)!");
            } else {
                statusProp.updateValue("normal");
            }
            System.out.println("[BatteryArtifact] Consumed " + amount + "%, current charge: " + updated + "%");
        }
    }

    @OPERATION
    public void recharge(int amount) {
        ObsProperty chargeProp = getObsProperty("charge");
        ObsProperty statusProp = getObsProperty("battery_status");
        if (chargeProp != null) {
            int current = chargeProp.intValue();
            int updated = Math.min(100, current + amount);
            chargeProp.updateValue(updated);
            if (updated >= 100) {
                statusProp.updateValue("full");
                signal("battery_full");
                System.out.println("[BatteryArtifact] Battery is fully charged (100%)!");
            } else {
                statusProp.updateValue("charging");
                System.out.println("[BatteryArtifact] Recharging... current charge: " + updated + "%");
            }
        }
    }

    @OPERATION
    public void full_charge() {
        ObsProperty chargeProp = getObsProperty("charge");
        ObsProperty statusProp = getObsProperty("battery_status");
        if (chargeProp != null) {
            chargeProp.updateValue(100);
            if (statusProp != null) {
                statusProp.updateValue("full");
            }
            signal("battery_full");
            System.out.println("[BatteryArtifact] Battery instantly recharged to 100%.");
        }
    }

    @OPERATION
    public void discharge_to(int level) {
        ObsProperty chargeProp = getObsProperty("charge");
        ObsProperty statusProp = getObsProperty("battery_status");
        if (chargeProp != null) {
            chargeProp.updateValue(level);
            if (level <= 10) {
                statusProp.updateValue("low");
                signal("low_battery_warning", level);
            }
            System.out.println("[BatteryArtifact] Battery level manually set to: " + level + "%");
        }
    }
}
