package tools;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.ObsProperty;

public class DeliverySystem extends Artifact {

    @OPERATION public void init(String initialLoc){
        defineObsProperty("location", initialLoc);
        defineObsProperty("status", "STANDBY");
        defineObsProperty("stage", "G0_IDLE");
        defineObsProperty("battery_level", 85);
        defineObsProperty("cargo_locked", true);
        defineObsProperty("package_loaded", false);
        defineObsProperty("last_event", "System initialized. Waiting for web simulation trigger.");
        System.out.println("[Environment] DeliverySystem initialized at: " + initialLoc);
    }

    @OPERATION public void set_stage(String stage, String statusMsg) {
        ObsProperty st = getObsProperty("stage");
        if (st != null) st.updateValue(stage);
        ObsProperty msg = getObsProperty("last_event");
        if (msg != null) msg.updateValue(statusMsg);
        ObsProperty stat = getObsProperty("status");
        if (stat != null) stat.updateValue("IN_PROGRESS");
    }

    @OPERATION public void update_battery(int lvl) {
        ObsProperty b = getObsProperty("battery_level");
        if (b != null) b.updateValue(lvl);
    }

    @OPERATION public void reroute() {
        ObsProperty msg = getObsProperty("last_event");
        if (msg != null) msg.updateValue("Recalculating route to avoid obstacles...");
        System.out.println("[Environment] Recalculating global route avoidance matrix...");
    }

    @OPERATION public void emergency_brake() {
        ObsProperty stat = getObsProperty("status");
        if (stat != null) stat.updateValue("EMERGENCY_STOP");
        ObsProperty msg = getObsProperty("last_event");
        if (msg != null) msg.updateValue("EMERGENCY BRAKE ENGAGED! Rescue signal broadcasting.");
        System.out.println("[Environment] EMERGENCY BRAKE ENGAGED! Flashing hazard lights, sounding buzzer, broadcasting GPS coordinates to rescue team.");
    }

    @OPERATION public void lock_cargo() {
        ObsProperty locked = getObsProperty("cargo_locked");
        if (locked != null) locked.updateValue(true);
        ObsProperty msg = getObsProperty("last_event");
        if (msg != null) msg.updateValue("Cargo hatch securely LOCKED.");
        System.out.println("[Environment] Cargo hatch securely LOCKED.");
    }

    @OPERATION public void unlock_cargo() {
        ObsProperty locked = getObsProperty("cargo_locked");
        if (locked != null) locked.updateValue(false);
        ObsProperty msg = getObsProperty("last_event");
        if (msg != null) msg.updateValue("Cargo hatch UNLOCKED for customer.");
        System.out.println("[Environment] Cargo hatch UNLOCKED for customer.");
    }

    @OPERATION public void load_package() {
        ObsProperty p = getObsProperty("package_loaded");
        if (p != null) p.updateValue(true);
        ObsProperty msg = getObsProperty("last_event");
        if (msg != null) msg.updateValue("Package loaded onto vehicle.");
        System.out.println("[Environment] Package loaded onto vehicle.");
    }

    @OPERATION public void arrive(String dest) {
        ObsProperty locProp = getObsProperty("location");
        ObsProperty statusProp = getObsProperty("status");
        ObsProperty msg = getObsProperty("last_event");
        if (locProp != null) locProp.updateValue(dest);
        if (statusProp != null) statusProp.updateValue("ARRIVED");
        if (msg != null) msg.updateValue("Arrived at destination: " + dest);
        System.out.println("[Environment] Vehicle arrived at: " + dest);
    }

    @OPERATION public void deliver_package() {
        ObsProperty p = getObsProperty("package_loaded");
        if (p != null) p.updateValue(false);
        ObsProperty msg = getObsProperty("last_event");
        if (msg != null) msg.updateValue("Package delivered and received by customer.");
        System.out.println("[Environment] Package delivered to customer (unloaded).");
    }

    @OPERATION public void clear_trigger(String propName) {
        if (hasObsProperty(propName)) {
            removeObsProperty(propName);
            System.out.println("[Environment] Cleared trigger property: " + propName);
        }
    }

    @OPERATION public void clear_all_triggers() {
        String[] triggers = {
            "has_order", "start_delivery", "battery_below_10", "sensor_offline",
            "robot_arm_conn_failed", "barcode_scan_failed", "weight_exceeded",
            "road_blocked_detected", "customer_no_show", "auth_attempts_over_3",
            "hatch_mechanism_stuck", "all_docks_busy", "dock_contact_error"
        };
        for (String t : triggers) {
            if (hasObsProperty(t)) {
                removeObsProperty(t);
            }
        }
        System.out.println("[Environment] All simulation trigger properties cleared.");
    }

    @OPERATION public void finish_cycle() {
        ObsProperty st = getObsProperty("stage");
        if (st != null) st.updateValue("G4_COMPLETE_DONE");
        ObsProperty stat = getObsProperty("status");
        if (stat != null) stat.updateValue("STANDBY");
        ObsProperty p = getObsProperty("package_loaded");
        if (p != null) p.updateValue(false);
        ObsProperty msg = getObsProperty("last_event");
        if (msg != null) msg.updateValue("Hoàn tất chu trình giao hàng an toàn. Xe ở trạng thái STANDBY.");
        clear_all_triggers();
        System.out.println("[Environment] Delivery cycle completed. Status: STANDBY.");
    }

    @OPERATION public void reset_env() {
        ObsProperty locProp = getObsProperty("location");
        if (locProp != null) locProp.updateValue("warehouse_dock_A");
        ObsProperty statusProp = getObsProperty("status");
        if (statusProp != null) statusProp.updateValue("STANDBY");
        ObsProperty st = getObsProperty("stage");
        if (st != null) st.updateValue("G0_IDLE");
        ObsProperty p = getObsProperty("package_loaded");
        if (p != null) p.updateValue(false);
        ObsProperty locked = getObsProperty("cargo_locked");
        if (locked != null) locked.updateValue(true);
        if (hasObsProperty("has_order")) {
            removeObsProperty("has_order");
        }
        ObsProperty msg = getObsProperty("last_event");
        if (msg != null) msg.updateValue("Simulation environment reset to initial state.");
        System.out.println("[Environment] Simulation environment reset.");
    }
}
