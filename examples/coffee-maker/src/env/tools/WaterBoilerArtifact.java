package tools;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.ObsProperty;

public class WaterBoilerArtifact extends Artifact {

    @OPERATION
    public void init(int initialWaterMl, int initialTemp) {
        defineObsProperty("water_volume", initialWaterMl);
        defineObsProperty("supply_reservoir_volume", 2000);
        defineObsProperty("current_temp", initialTemp);
        defineObsProperty("target_temp", initialTemp);
        defineObsProperty("heating_status", "IDLE");
        defineObsProperty("last_event", "Water Boiler initialized with external water supply reservoir.");
        System.out.println("[WaterBoilerEnv] Initialized: BoilerVolume=" + initialWaterMl + "ml, SupplyReservoir=2000ml, Temp=" + initialTemp + "C");
    }

    @OPERATION
    public void draw_water_from_tank(int ml) {
        ObsProperty sv = getObsProperty("supply_reservoir_volume");
        ObsProperty wv = getObsProperty("water_volume");
        ObsProperty le = getObsProperty("last_event");
        if (sv != null && wv != null) {
            int supply = sv.intValue();
            if (supply <= 0) {
                if (le != null) le.updateValue("Warning: External water supply reservoir is EMPTY!");
                System.out.println("[WaterBoilerEnv] Error: External water supply reservoir is EMPTY (0ml).");
                return;
            }
            int transfer = Math.min(supply, ml);
            sv.updateValue(supply - transfer);
            int newBoilerVol = wv.intValue() + transfer;
            wv.updateValue(newBoilerVol);
            System.out.println("[WaterBoilerEnv] Pumped " + transfer + "ml from supply reservoir into boiler. BoilerVolume=" + newBoilerVol + "ml, SupplyReservoir=" + (supply - transfer) + "ml");
            if (le != null) le.updateValue("Pumped " + transfer + "ml water from external supply compartment into boiler chamber.");
        }
    }

    @OPERATION
    public void refill_supply_tank(int ml) {
        ObsProperty sv = getObsProperty("supply_reservoir_volume");
        if (sv != null) {
            int current = sv.intValue();
            int next = current + ml;
            sv.updateValue(next);
            System.out.println("[WaterBoilerEnv] External supply reservoir refilled with " + ml + "ml. Total reservoir: " + next + "ml");
        }
        ObsProperty le = getObsProperty("last_event");
        if (le != null) le.updateValue("External water supply reservoir refilled with " + ml + "ml.");
    }

    @OPERATION
    public void heat_water_to(int target) {
        ObsProperty tt = getObsProperty("target_temp");
        if (tt != null) tt.updateValue(target);
        ObsProperty ct = getObsProperty("current_temp");
        if (ct != null) ct.updateValue(target);
        ObsProperty hs = getObsProperty("heating_status");
        if (hs != null) hs.updateValue("READY");
        ObsProperty le = getObsProperty("last_event");
        if (le != null) le.updateValue("Water heated to target " + target + "°C (READY).");
        System.out.println("[WaterBoilerEnv] Water heated to " + target + "°C (READY).");
    }

    @OPERATION
    public void cool_down() {
        ObsProperty ct = getObsProperty("current_temp");
        if (ct != null) ct.updateValue(90);
        ObsProperty hs = getObsProperty("heating_status");
        if (hs != null) hs.updateValue("STABILIZED");
        ObsProperty le = getObsProperty("last_event");
        if (le != null) le.updateValue("Emergency cooling activated: Boiler temperature lowered to safe range (90°C).");
        System.out.println("[WaterBoilerEnv] Cooldown activated -> Temp stabilized at 90°C.");
    }

    @OPERATION
    public void reset_boiler() {
        ObsProperty hs = getObsProperty("heating_status");
        if (hs != null) hs.updateValue("IDLE");
        ObsProperty le = getObsProperty("last_event");
        if (le != null) le.updateValue("Water Boiler system reset to standby.");
        System.out.println("[WaterBoilerEnv] Boiler reset complete.");
    }
}
