{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }
{ include("coffee_maker/belief.asl") }

+!grind_beans
   <- .print("☕ [CoffeeMaker] Bắt đầu quy trình chuẩn bị và xay hạt cà phê...");
      .and_branches([ensure_required_bean_amount, grind_coffee_beans]);
      .print("☕ ✅ [CoffeeMaker] Đã hoàn tất xay hạt cà phê tiêu chuẩn.").


+!ensure_required_bean_amount
   <- .print("☕ [EnsureRequiredBeanAmount] Kiểm tra định lượng hạt...");
      !set_bean_amount.


+!set_bean_amount : beans_level(B) & B < 18
   <- .print("☕     [SetBeanAmount] Lượng hạt hiện tại không đủ (", B, "g < 18g). Đang chờ nạp hạt >= 18g (nạp từ Web)...");
      refill_beans(100);
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


