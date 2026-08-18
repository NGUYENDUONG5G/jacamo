// Rules defining error conditions for failure recovery
gps(broken)        :- not using_paper_map & not setup_failed.
traffic(congested) :- using_paper_map & not setup_failed.
fuel(empty)        :- setup_failed & not refueled.
