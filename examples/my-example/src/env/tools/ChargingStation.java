package tools;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.ObsProperty;


public class ChargingStation extends Artifact {

    @OPERATION
    public void init(String stationLocation) {
        defineObsProperty("station_location", stationLocation);
        defineObsProperty("dock_status", "vacant"); // "vacant", "occupied", "charging"
        defineObsProperty("rescue_beacon", "idle");
        System.out.println("[ChargingStation] Station initialized at location: " + stationLocation);
    }

    @OPERATION
    public void dock(String robotName) {
        ObsProperty dockProp = getObsProperty("dock_status");
        if (dockProp != null) {
            dockProp.updateValue("occupied");
            System.out.println("[ChargingStation] Robot '" + robotName + "' docked successfully at station.");
            signal("robot_docked", robotName);
        }
    }

    @OPERATION
    public void undock(String robotName) {
        ObsProperty dockProp = getObsProperty("dock_status");
        if (dockProp != null) {
            dockProp.updateValue("vacant");
            System.out.println("[ChargingStation] Robot '" + robotName + "' undocked from station.");
            signal("robot_undocked", robotName);
        }
    }

    @OPERATION
    public void call_support() {
        ObsProperty beaconProp = getObsProperty("rescue_beacon");
        if (beaconProp != null) {
            beaconProp.updateValue("active");
        }
        System.out.println("[ChargingStation] [RECOVERY] EMERGENCY SUPPORT CALLED: Dispatching technician/tether to unreachable iCleaner robot!");
        signal("rescue_support_dispatched");
    }

    @OPERATION
    public void boost_beacon() {
        System.out.println("[ChargingStation] [RECOVERY] Broadcasting high-power directional radio beacon for robot positioning.");
    }
}
