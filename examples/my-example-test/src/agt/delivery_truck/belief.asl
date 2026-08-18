arm_disconnected   :- robot_arm_conn_failed & not package_loaded.
barcode_invalid    :- barcode_scan_failed & not package_loaded.
package_overweight :- weight_exceeded & not package_loaded.

path_blocked       :- road_blocked_detected & not reroute_done.
gps_lost           :- sensor_offline & not emergency_handled.
battery_critical   :- battery_below_10 & not emergency_handled.

wait_timeout       :- customer_no_show & not return_initiated.
auth_exceeded      :- auth_attempts_over_3 & not return_initiated.
hatch_jammed       :- hatch_mechanism_stuck & not return_initiated.

docks_occupied     :- all_docks_busy & not sleep_mode_active.
dock_misaligned    :- dock_contact_error & not sleep_mode_active.
