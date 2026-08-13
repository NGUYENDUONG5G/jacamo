{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

!start.

{ include("recovery.asl") }

+!start
   <- .a;
      !safe_setup.

+!safe_setup
   :  counter_id(ArtId)
   <- .recovery_setup("exist");
      joinWorkspace("/main/o1", OrgWId);
      !wait_and_adopt_role(OrgWId).

+!safe_setup
   <- .recovery_setup("init");
      createWorkspace("w1");
      joinWorkspace("/main/w1", WId);
      makeArtifact("navigation", "tools.DeliverySystem", ["warehouse"], ArtId);
      focus(ArtId);
      +counter_id(ArtId);
      joinWorkspace("/main/o1", OrgWId);
      !wait_and_adopt_role(OrgWId).

+!wait_and_adopt_role(OrgWId)
   <- lookupArtifact("my_team", GrId)[wid(OrgWId)];
      adoptRole(dispatcher)[artifact_id(GrId)];
      adoptRole(truck)[artifact_id(GrId)];
      focus(GrId);
      !report_status("dispatcher and truck").

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
   <- .my_name(Me);
      .recovery_traffic(1);
      +setup_failed;
      +traffic(congested).

+!refuel
   <- .recovery_refuel(1);
      .wait(500);
      +refueled;
      .recovery_refuel(2);
      !deliver_on_time.

+location(Loc)
   <- .recovery_location(Loc).
