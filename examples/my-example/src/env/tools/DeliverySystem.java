package tools;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.ObsProperty;

public class DeliverySystem extends Artifact {

    @OPERATION public void init(String initialLoc){
        defineObsProperty("location", initialLoc);
        defineObsProperty("status", "ready");
        System.out.println("DeliverySystem initialized at: " + initialLoc);
    }

    @OPERATION public void reroute() {
        System.out.println("DEBUG: [Environment] Executing navigation reroute operation to find a detour.");
    }

    @OPERATION public void speedUp() {
        System.out.println("DEBUG: [Environment] Adjusting vehicle speed to offset delivery delays.");
    }

    @OPERATION public void find_gas_station() {
        System.out.println("DEBUG: [Environment] Scanning navigation database for nearby gas stations.");
    }

    @OPERATION public void arrive(String dest) {
        try {
            ObsProperty locProp = getObsProperty("location");
            ObsProperty statusProp = getObsProperty("status");
            if (locProp != null) {
                locProp.updateValue(dest);
            }
            if (statusProp != null) {
                statusProp.updateValue("delivered");
            }
            System.out.println("DeliverySystem: Vehicle arrived at " + dest);
            signal("arrived");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
