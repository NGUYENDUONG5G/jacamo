{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

{ include("delivery_truck/belief.asl") }

!deliver_on_time.

+!safe_setup
   <- .recovery_setup("init");
      joinWorkspace("/main/w1", WId);
      lookupArtifact("navigation", ArtId)[wid(WId)];
      +counter_id(ArtId);
      joinWorkspace("/main/o1", OrgWId);
      !wait_and_adopt_role(OrgWId).

+!wait_and_adopt_role(OrgWId)
   <- lookupArtifact("my_team", GrId)[wid(OrgWId)];
      adoptRole(dispatcher)[artifact_id(GrId)];
      adoptRole(truck)[artifact_id(GrId)];
      focus(GrId);
      !report_status("dispatcher and truck").

+!report_status(Role)
   :  setup_failed 
   <- .my_name(Me);
      .print("--> [KHÔI PHỤC THÀNH CÔNG] ", Me, " đã khôi phục lỗi kết nối và gia nhập tổ chức với vai trò ", Role, "!");
      -setup_failed.

+!report_status(Role)
   <- .my_name(Me);
      .print("--> [THÀNH CÔNG TRƠN TRU] ", Me, " đã khởi tạo và gia nhập tổ chức với vai trò ", Role, " ngay từ đầu (không gặp lỗi)!").

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
      .recovery_fuel(1).

+!deliver_on_time
   :  using_paper_map
   <- .my_name(Me);
      .recovery_traffic(1);
      +setup_failed.

+!deliver_on_time
   :  not counter_id(_)
   <- .a;
      !safe_setup;
      .my_name(Me);
      .print("Dispatcher (delivery_truck): Starting GPS system...").

+!deliver_on_time
   :  counter_id(_) & not setup_failed & not using_paper_map
   <- .print("Moise delivery obligation registered. GPS status check in progress...").

+!use_paper_map
   <- .print("GPS broken! Switching to paper map plan...");
      +using_paper_map;
      !deliver_on_time.

+!refuel
   <- .recovery_refuel(1);
      .wait(500);
      +refueled;
      .recovery_refuel(2);
      !deliver_on_time.

+location(Loc)
   <- .recovery_location(Loc).

// Specific goal failure handler for safe_setup
-!safe_setup
   <- .my_name(Me);
      .a; 
      .print(Me, " khởi tạo thất bại. Đang thử lại toàn bộ quá trình thiết lập...");
      +setup_failed; 
      .wait(200);
      !safe_setup.

// Specific goal failure handler for wait_and_adopt_role
-!wait_and_adopt_role(OrgWId)
   <- .my_name(Me);
      .a; 
      .print(Me, " đang đợi nhóm my_team được tạo...");
      +setup_failed;
      .wait(200);
      !wait_and_adopt_role(OrgWId).

// Specific goal failure handler for plan_route
-!plan_route
   <- .my_name(Me);
      .print("[RECOVERY] Lỗi khi thực hiện lập lộ trình giao hàng (plan_route). Đang thử lại...");
      .wait(500);
      !plan_route.

// Specific goal failure handler for deliver_on_time
-!deliver_on_time
   <- .my_name(Me);
      .print("[RECOVERY] Lỗi khi thực hiện giao hàng đúng giờ (deliver_on_time). Đang thử lại...");
      .wait(500);
      !deliver_on_time.

// Specific goal failure handler for refuel
-!refuel
   <- .my_name(Me);
      .print("[RECOVERY] Thất bại khi nạp nhiên liệu. Đang thử lại...");
      .wait(500);
      !refuel.

// Fallback generic goal failure handler to catch any other goal failures of the agent
-!G[error(ErrId), error_msg(Msg), code_src(SrcFile), code_line(SrcLine)]
   <- .my_name(Me);
      .print("[FAIL-SAFE] Agent ", Me, " gặp lỗi khi thực hiện goal '!", G, "' tại dòng ", SrcLine, " trong file '", SrcFile, "'. Mã lỗi: ", ErrId, ". Chi tiết: ", Msg).

-!G
   <- .my_name(Me);
      .print("[FAIL-SAFE] Agent ", Me, " gặp lỗi khi thực hiện goal '!", G, "' (Không có thông tin chi tiết về lỗi).").
