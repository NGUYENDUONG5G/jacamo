beans_depleted      :- beans_empty_detected & not beans_refilled.
waste_bin_full      :- waste_overflow_detected & not waste_cleaned.
pump_pressure_low   :- pressure_drop_detected & not pump_recalibrated.
cup_missing         :- no_cup_detected & not cup_supplied.
boiler_unavailable  :- boiler_not_responding & not boiler_reconnected.
