package tools;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.ObsProperty;

public class WaterBoilerArtifact extends Artifact {

    @OPERATION
    public void init(int initialWaterMl, int initialTemp) {
        defineObsProperty("water_volume", initialWaterMl);
        defineObsProperty("current_temp", initialTemp);
        defineObsProperty("target_temp", initialTemp);
        defineObsProperty("heating_status", "IDLE");
        defineObsProperty("last_event", "Water Boiler initialized and standing by.");
        System.out.println("[WaterBoilerEnv] Initialized: Volume=" + initialWaterMl + "ml, Temp=" + initialTemp + "C");
    }

    @OPERATION
    public void heat_water_to(int target) {
        ObsProperty tt = getObsProperty("target_temp");
        if (tt != null) tt.updateValue(target);
        ObsProperty hs = getObsProperty("heating_status");
        if (hs != null) hs.updateValue("HEATING");
        ObsProperty le = getObsProperty("last_event");
        if (le != null) le.updateValue("Water heater turned ON, waiting for target " + target + "°C.");
        System.out.println("[WaterBoilerEnv] Heating turned ON -> Waiting for temperature to reach " + target + "°C.");
    }

    @OPERATION
    public void refill_water(int ml) {
        ObsProperty le = getObsProperty("last_event");
        if (le != null) le.updateValue("Water refill requested (" + ml + "ml), waiting for water_volume update.");
        System.out.println("[WaterBoilerEnv] Refill requested (" + ml + "ml) -> Waiting for water_volume update.");
    }
}

