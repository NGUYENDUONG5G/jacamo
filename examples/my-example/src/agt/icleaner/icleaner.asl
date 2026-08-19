{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }
{ include("icleaner/belief.asl") }

!init_robot.

+!init_robot : true
   <- .my_name(Me);
      .print("=== iCleaner Initializing ===");
      .and_branches([safe_setup, run_cycle]).

+!safe_setup : true
   <- joinWorkspace("/main/w1", WId);
      lookupArtifact("battery", BatId)[wid(WId)];
      focus(BatId);
      lookupArtifact("charging_station", StationId)[wid(WId)];
      focus(StationId);
      lookupArtifact("dustbin", DustId)[wid(WId)];
      focus(DustId);
      lookupArtifact("room", RoomId)[wid(WId)];
      focus(RoomId);
      +setup_done;
      joinWorkspace("/main/o1", OrgWId);
      !wait_and_adopt_role(OrgWId).

+!wait_and_adopt_role(OrgWId) : true
   <- lookupArtifact("cleaner_team", GrId)[wid(OrgWId)];
      adoptRole(cleaner)[artifact_id(GrId)];
      focus(GrId);
      .my_name(Me);
      .print("--> [SUCCESS] ", Me, " joined 'cleaning_org' as 'cleaner'.").

-!safe_setup
   <- .wait(300);
      !safe_setup.

-!wait_and_adopt_role(OrgWId)
   <- .wait(300);
      !wait_and_adopt_role(OrgWId).

+!run_cycle : true
   <- .print("\n>>> STAGE 1: MAINTAIN GOALS (BATTERY > 10%) <<<");
      .and_branches([observe_environment, battery_loaded, clean_room]);
      .wait(1000);
      .print("\n>>> STAGE 2: LOW BATTERY (<= 10%) & DYNAMIC INHIBITION («inhibits») <<<");
      discharge_to(8);
      .and_branches([battery_loaded, clean_room]);
      .wait(1000);
      .print("\n>>> STAGE 3: FAILURE MODEL - CHARGING STATION TOO DISTANT <<<");
      discharge_to(5);
      +station_too_distant;
      .wait(1500);
      .print("\n>>> COMPLETED iCleaner DEMONSTRATION SUCCESSFULLY <<<").

+!observe_environment : true
   <- .print("[Goal: Observe Environment] Scanning obstacles and room layout...");
      scan_obstacles;
      .and_branches([locate_next_target, move_to_target]).

+!locate_next_target : true
   <- .or_branches([locate_dock_target, locate_clean_target]).

+!locate_dock_target : battery_critical
   <- .print("[Subgoal: LocateNextTarget] Low battery! Setting target to dock_station_A.");
      -+target_location("dock_station_A").

+!locate_clean_target : not battery_critical
   <- .print("[Subgoal: LocateNextTarget] Normal battery. Setting target to zone_1.");
      -+target_location("zone_1").

+!move_to_target : true
   <- .or_branches([avoid_and_move, direct_move]).

+!avoid_and_move : obstacle_detected(Obs) & Obs \== "none"
   <- .print("[Subgoal: MoveToTarget] Obstacle detected: ", Obs, ". Bypassing...");
      bypass_obstacle(Obs);
      ?target_location(Target);
      move_to(Target);
      .print("[Subgoal: MoveToTarget] Reached target: ", Target).

+!direct_move : true
   <- ?target_location(Target);
      move_to(Target);
      .print("[Subgoal: MoveToTarget] Reached target: ", Target).

+!battery_loaded : true
   <- .or_branches([battery_normal_plan, battery_recharge_plan]).

+!battery_normal_plan : charge(C) & C > 10
   <- .print("[Goal: Battery Loaded] [SUSPENDED] Charge: ", C, "% (> 10%). Normal operation.").

+!battery_recharge_plan : charge(C) & C <= 10
   <- .print(">>> [Goal: Battery Loaded] [ACTIVE] Low battery: ", C, "% (<= 10%)!");
      .print(">>> [Dynamic Inhibition] «inhibits» triggered: Clean room is SUSPENDED!");
      +cleaning_suspended;
      .and_branches([navigate_to_dock, execute_charge, undock_robot]);
      -cleaning_suspended;
      .print(">>> [Dynamic Inhibition] TargetCondition satisfied (100%). RESUMING Clean room.").

+!navigate_to_dock : true
   <- .print("[Battery Recovery] Moving to dock_station_A...");
      move_to("dock_station_A").

+!execute_charge : true
   <- .my_name(Me);
      .print("[Battery Recovery] Docking and charging battery to 100%...");
      dock(Me);
      full_charge;
      .wait(300).

+!undock_robot : true
   <- .my_name(Me);
      undock(Me);
      .print("[Battery Recovery] Undocked. Ready for missions.").

+!clean_room : true
   <- .or_branches([clean_room_suspended, clean_room_active]).

+!clean_room_suspended : cleaning_suspended | should_inhibit_cleaning
   <- .print("[Goal: Clean Room] [SUSPENDED] Cleaning is inhibited by Battery Loaded!").

+!clean_room_active : not cleaning_suspended & not should_inhibit_cleaning
   <- .print("[Goal: Clean Room] [ACTIVE] Starting vacuum, mop and waste dumping...");
      .and_branches([vacuum_floor, mop_floor, dump_dust]);
      .print("[Goal: Clean Room] Room cleaned and dustbin emptied successfully.").

+!vacuum_floor : true
   <- vacuum("zone_1").

+!mop_floor : true
   <- mop("zone_1").

+!dump_dust : true
   <- empty_robot_dust.

+!emergency_move : true
   <- .print("[RECOVERY ACTIVITY: emergency_move] Switched to ultra low power movement!").

+!send_rescue_signal : true
   <- .print("[RECOVERY ACTIVITY: send_rescue_signal] Broadcasting emergency SOS signal!");
      broadcast_sos.

-!observe_environment
   <- scan_obstacles;
      !observe_environment.

-!clean_room
   <- .wait(300);
      !clean_room.

-!battery_loaded
   <- call_support;
      !send_rescue_signal.

-!G[error(ErrId), error_msg(Msg), code_src(SrcFile), code_line(SrcLine)]
   <- .my_name(Me);
      .print("[FAIL-SAFE] Agent ", Me, " failed goal '!", G, "' at line ", SrcLine, " in ", SrcFile, ". Error: ", ErrId, " - ", Msg).
