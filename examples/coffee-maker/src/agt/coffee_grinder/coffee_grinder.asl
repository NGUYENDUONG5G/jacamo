{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }
{ include("coffee_grinder/belief.asl") }

+start_grind  <- !grind_beans.

+!grind_beans : start_grind
   <- .print("☕ [CoffeeGrinder] Nhận lệnh từ Web -> Bắt đầu quy trình chuẩn bị và xay hạt cà phê...");
      .and_branches([ensure_required_bean_amount, grind_coffee_beans]);
      .print("☕ ✅ [CoffeeGrinder] Đã hoàn tất xay hạt cà phê tiêu chuẩn.").

+!grind_beans
   <- .print("☕ ⏳ [CoffeeGrinder] Đang ở trạng thái chờ (Standby) - Nạp belief 'start_grind' từ Web để khởi động quy trình.").

+!ensure_required_bean_amount
   <- .print("☕ [EnsureRequiredBeanAmount] Kiểm tra định lượng hạt...");
      !set_bean_amount.

+!set_bean_amount : beans_level(B) & B < 18
   <- .print("☕     [SetBeanAmount] Lượng hạt hiện tại không đủ (", B, "g < 18g). Tự động nạp bổ sung hạt...");
      refill_beans(100);
      -beans_empty_detected;
      .wait( beans_level(NewB) & NewB >= 18 );
      .print("☕     [SetBeanAmount] Đã nhận được lượng hạt đạt yêu cầu: ", NewB, "g.").

+!set_bean_amount : beans_level(B) & B >= 18
   <- .print("☕     [SetBeanAmount] Lượng hạt hiện tại đạt yêu cầu: ", B, "g.").

+!grind_coffee_beans
   <- .print("☕ [GrindCoffeeBeans] Bắt đầu tiến trình xay...");
      !grind.

+!grind : beans_level(B) & B >= 18
   <- .print("☕     [Grind] Định lượng 18g hạt & kích hoạt máy xay...");
      grind_beans(18);
      +ground_coffee_ready;
      .wait(800);
      .print("☕     [Grind] Đã xay & nén bột cà phê mịn sẵn sàng.").

+!grind : beans_level(B) & B < 18
   <- .print("☕ ⚠️  [Grind] Lượng hạt không đủ để xay (", B, "g). Đang chờ thích ứng nạp hạt...");
      !set_bean_amount.

+!unclog_grinder
   <- .print("☕ 🛡️ [Adaptation] Phát hiện cối xay bị kẹt! Đang đảo chiều động cơ và reset cối xay...");
      reset_grinder;
      -grinder_motor_stuck;
      .wait(500);
      .print("☕ 🛡️ [Adaptation] Cối xay đã được thông và hoạt động bình thường.").

+!notify_maintenance
   <- .print("☕ 📢 [OrgAdaptation] Gửi cảnh báo đến hệ thống bảo trì thiết bị pha chế.").

+!reset_simulation
   <- .print("☕ 🔄 [OrgAdaptation] Đang thiết lập lại đồ hình pha chế và môi trường...");
      cancel_operation;
      -start_grind;
      -ground_coffee_ready;
      .print("☕ 🔄 [OrgAdaptation] Đã hoàn tất reset chu trình pha chế.").

+!abort_and_clean_up
   <- .print("☕ 🛑 [OrgAdaptation] Tiến trình quá hạn! Hủy thao tác xay và trả máy về trạng thái an toàn...");
      cancel_operation.
