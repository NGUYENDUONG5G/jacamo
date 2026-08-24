{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }
{ include("water_boiler/belief.asl") }

+start_boil   <- !boil_water.

+!boil_water : start_boil
   <- .and_branches([ensure_sufficient_water_volume, increase_water_temperature]);
      .print("🫖 Nước nóng đạt chuẩn 92°C, sẵn sàng pha cà phê.").

+!boil_water
   <- .print("🫖 Đang ở trạng thái chờ (Standby) - Nạp belief 'start_boil' từ Web để khởi động quy trình.").

+!ensure_sufficient_water_volume
   <- .print("🫖 Kiểm tra dung tích nước buồng đun...");
      !check_water_step.

+!check_water_step : water_volume(W) & W < 100
   <- .print("🫖     Mực nước buồng đun thấp (", W, "ml). Bơm hút nước từ khoang chứa bên ngoài...");
      draw_water_from_tank(500);
      -supply_tank_empty;
      .wait( water_volume(NewW) & NewW >= 100 ).

+!check_water_step : water_volume(W) & W >= 100
   <- .print("🫖 Mực nước buồng đun hiện tại đã đạt yêu cầu: ", W, "ml.").

+!increase_water_temperature
   <- .print("🫖 Kiểm tra nhiệt độ nước...");
      !heat_step.

+!heat_step : current_temp(CT) & CT < 92
   <- .print("🫖     Nhiệt độ hiện tại: ", CT, "°C. Bắt đầu kích hoạt thanh nhiệt đun tới 92°C...");
      heat_water_to(92);
      .wait( current_temp(T) & T >= 92 ).

+!heat_step : current_temp(CT) & CT >= 92
   <- .print("🫖 Nước đã đủ nhiệt độ yêu cầu: ", CT, "°C.").

+!cooldown_boiler
   <- .print("🫖 Cảnh báo nhiệt độ bình đun quá cao! Ngắt điện trở & kích hoạt làm nguội...");
      cool_down;
      -boiler_overheat_signal;
      .print("🫖 Đã hạ nhiệt và ổn định áp suất/nhiệt độ về mức an toàn 90°C-92°C.").

+!notify_maintenance
   <- .print("🫖 Đã gửi thông báo tình trạng bình đun sang hệ thống giám sát kỹ thuật.").
