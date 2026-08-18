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
