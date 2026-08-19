package tools;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.ObsProperty;


public class RoomEnvironment extends Artifact {

    @OPERATION
    public void init(String roomName) {
        defineObsProperty("room_name", roomName);
        defineObsProperty("robot_position", "zone_1");
        defineObsProperty("zone_1_dirt", 40);
        defineObsProperty("zone_2_dirt", 60);
        defineObsProperty("obstacle_detected", "none");
        defineObsProperty("room_clean_status", "in_progress");
        System.out.println("[RoomEnvironment] Room '" + roomName + "' initialized with zones and obstacle layout.");
    }

    @OPERATION
    public void vacuum(String zone) {
        ObsProperty dirtProp = getObsProperty(zone + "_dirt");
        if (dirtProp != null) {
            dirtProp.updateValue(0);
            System.out.println("[RoomEnvironment] Vacuuming completed at " + zone + ". Dirt removed.");
        }
    }

    @OPERATION
    public void mop(String zone) {
        System.out.println("[RoomEnvironment] Mopping completed at " + zone + ". Floor sanitized.");
    }

    @OPERATION
    public void scan_obstacles() {
        System.out.println("[RoomEnvironment] Scanning room obstacles: Chair legs, carpet edges, and coffee table detected.");
    }

    @OPERATION
    public void move_to(String destination) {
        ObsProperty posProp = getObsProperty("robot_position");
        if (posProp != null) {
            posProp.updateValue(destination);
            System.out.println("[RoomEnvironment] Robot navigated to: " + destination);
            signal("arrived", destination);
        }
    }

    @OPERATION
    public void bypass_obstacle(String obstacleName) {
        System.out.println("[RoomEnvironment] Successfully executed evasive maneuver to bypass obstacle: " + obstacleName);
    }

    @OPERATION
    public void broadcast_sos() {
        System.out.println("[RoomEnvironment] [RECOVERY] Robot broadcasting optical and sound SOS emergency signal in the room!");
    }
}
