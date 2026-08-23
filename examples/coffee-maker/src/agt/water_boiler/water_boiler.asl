{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }
{ include("water_boiler/belief.asl") }


+!boil_water
   <- .print("🫖 [WaterBoiler] Bắt đầu quy trình chuẩn bị nước nóng...");
      .and_branches([ensure_sufficient_water_volume, increase_water_temperature]);
      .print("🫖 ✅ [WaterBoiler] Nước nóng đạt chuẩn, sẵn sàng pha cà phê.").


+!ensure_sufficient_water_volume
   <- .print("🫖 [WaterBoiler] Kiểm tra dung tích nước...");
      .or_branches([refill_water_step, skip_refill_step]).

+!refill_water_step : water_volume(W) & W < 100
   <- .print("🫖     Mực nước thấp (", W, "ml). Đang chờ nạp nước >= 100ml (nạp từ Web)...");
      refill_water(500);
      .wait( water_volume(NewW) & NewW >= 100 );
      .print("🫖     Đã nhận được mực nước đạt yêu cầu: ", NewW, "ml.").

+!skip_refill_step : water_volume(W) & W >= 100
   <- .print("🫖     Mực nước hiện tại đã đạt yêu cầu: ", W, "ml.").

+!skip_refill_step.

+!increase_water_temperature
   <- .print("🫖 [WaterBoiler] Kiểm tra nhiệt độ nước...");
      .or_branches([heat_water_to_target_temperature, temperature_already_hot]).

+!heat_water_to_target_temperature : current_temp(CT) & CT < 92
   <- .print("🫖     Nhiệt độ hiện tại: ", CT, "°C. Bắt đầu đun, đang chờ nhiệt độ đạt >= 92°C (nạp từ Web)...");
      heat_water_to(92);
      .wait( current_temp(T) & T >= 92 );
      .print("🫖     Đã nhận được nhiệt độ đạt chuẩn: ", T, "°C.").

+!temperature_already_hot : current_temp(CT) & CT >= 92
   <- .print("🫖     Nước đã đủ nhiệt độ yêu cầu: ", CT, "°C.").

+!temperature_already_hot.



