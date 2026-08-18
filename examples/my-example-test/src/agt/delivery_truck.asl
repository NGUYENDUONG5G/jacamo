{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

{ include("childGoal_delivery_truck.asl") }
{ include("recovery_delivery_truck.asl") }

!start.

+!start
   <- .a;
      !safe_setup.

+!plan_route
   <- .recovery_setup("route");
      goalAchieved(plan_route).

+!deliver_on_time
   :  setup_failed & refueled
   <- .my_name(Me);
      ?counter_id(ArtId);
      .recovery_fuel(2);
      arrive("customer_address")[artifact_id(ArtId)];
      goalAchieved(deliver_on_time).

+!deliver_on_time
   :  setup_failed
   <- .my_name(Me);
      .recovery_traffic(2);
      .recovery_fuel(1);
      +fuel(empty).

+!deliver_on_time
   :  using_paper_map
   <- .my_name(Me);
      .recovery_traffic(1);
      +setup_failed;
      +traffic(congested).

+!deliver_on_time
   <- .my_name(Me);
      .print("Dispatcher (delivery_truck): Starting GPS system...");
      +gps(broken).
