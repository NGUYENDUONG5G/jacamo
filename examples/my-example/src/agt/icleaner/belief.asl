battery_critical :- charge(C) & C <= 10.
battery_healthy  :- charge(C) & C > 10.
battery_full     :- charge(100).

should_inhibit_cleaning :- battery_critical.

station_unreachable :- station_too_distant & battery_critical.
