water_tank_empty    :- tank_level_zero & not tank_refilled.
overheat_alert      :- temp_exceeded_100 & not cooldown_completed.
sensor_error        :- sensor_malfunction & not sensor_reset.
