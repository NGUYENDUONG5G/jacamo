{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }
{ include("delivery_truck/belief.asl") }

pickup_method(robot_arm).
avoidance_mode(dynamic_avoid).
auth_method(otp).
battery_level(100).
next_action_mode(next_order).

+start_delivery : true
   <- +has_order;
      !deliver_success.

+has_order : not delivering
   <- !deliver_success.

+!deliver_success : has_order
   <- +delivering;
      .print("🚀 [G0] Bắt đầu quy trình giao hàng tự hành...");
      .and_branches([pickup_package, transit_to_delivery, handover_package, complete_cycle]);
      -delivering;
      .print("🎉 [G0] Hoàn tất toàn bộ quy trình giao hàng thành công!").

+!pickup_package : not package_loaded
   <- set_stage("G1_PICKUP", "Đang thực hiện [G1: Nhận & Lấy hàng] tại kho...");
      .print("📦 [G1] Bắt đầu [Nhận & Lấy hàng]...");
      .and_branches([move_to_pickup, load_and_verify_package]);
      set_stage("G1_PICKUP_DONE", "Hoàn thành lấy hàng tại kho.");
      .print("✅ [G1] Hoàn thành lấy hàng thành công.").

+!pickup_package : true
   <- set_stage("G1_PICKUP", "Đang thực hiện [G1: Nhận & Lấy hàng] tại kho...");
      .print("📦 [G1] Bắt đầu [Nhận & Lấy hàng]...");
      .and_branches([move_to_pickup, load_and_verify_package]);
      set_stage("G1_PICKUP_DONE", "Hoàn thành lấy hàng tại kho.");
      .print("✅ [G1] Hoàn thành lấy hàng thành công.").

+!move_to_pickup : not at_dock
   <- .wait(800);
      arrive("warehouse_dock_A");
      +at_dock.

+!move_to_pickup : true
   <- arrive("warehouse_dock_A");
      +at_dock.

+!load_and_verify_package : at_dock & not package_loaded
   <- .or_branches([load_robot_arm, load_manual_scan]);
      load_package;
      -at_dock.

+!load_and_verify_package : true
   <- .or_branches([load_robot_arm, load_manual_scan]);
      load_package.

+!load_robot_arm : pickup_method(robot_arm) & not package_loaded
   <- .print("  ↳ [G1.2] Bốc kiện hàng tự động qua Robot Arm...");
      .wait(800);
      +package_loaded.

+!load_manual_scan : pickup_method(manual_scan) & not package_loaded
   <- .print("  ↳ [G1.2] Quét Barcode và bốc hàng thủ công...");
      .wait(800);
      +package_loaded.

+!load_robot_arm : true
   <- .print("  ↳ [G1.2] Bốc kiện hàng tự động qua Robot Arm...");
      .wait(800);
      +package_loaded.

+!load_manual_scan : true
   <- .print("  ↳ [G1.2] Quét Barcode và bốc hàng thủ công...");
      .wait(800);
      +package_loaded.

+!transit_to_delivery : package_loaded
   <- set_stage("G2_TRANSIT", "Đang di chuyển và điều hướng tới địa chỉ khách hàng...");
      .print("🚚 [G2] Bắt đầu [Di chuyển đến điểm giao]...");
      .and_branches([plan_global_path, local_navigation]);
      +at_destination;
      set_stage("G2_TRANSIT_DONE", "Đã đến địa chỉ giao hàng an toàn.");
      .print("✅ [G2] Đã đến điểm giao an toàn.").

+!transit_to_delivery : true
   <- set_stage("G2_TRANSIT", "Đang di chuyển và điều hướng tới địa chỉ khách hàng...");
      .print("🚚 [G2] Bắt đầu [Di chuyển đến điểm giao]...");
      .and_branches([plan_global_path, local_navigation]);
      +at_destination;
      set_stage("G2_TRANSIT_DONE", "Đã đến địa chỉ giao hàng an toàn.");
      .print("✅ [G2] Đã đến điểm giao an toàn.").

+!plan_global_path : package_loaded
   <- reroute;
      .wait(800).

+!plan_global_path : true
   <- reroute;
      .wait(800).

+!local_navigation : package_loaded
   <- .or_branches([navigate_dynamic_avoid, navigate_yield]).

+!navigate_dynamic_avoid : avoidance_mode(dynamic_avoid)
   <- .print("  ↳ [G2.2] Phát hiện vật cản động: Lái vòng tránh an toàn...");
      .wait(1000);
      arrive("customer_address_123").

+!navigate_yield : avoidance_mode(yield)
   <- .print("  ↳ [G2.2] Phát hiện vật cản: Dừng nhường đường (Yield & Wait)...");
      .wait(1000);
      arrive("customer_address_123").

+!navigate_dynamic_avoid : true
   <- .print("  ↳ [G2.2] Phát hiện vật cản động: Lái vòng tránh an toàn...");
      .wait(1000);
      arrive("customer_address_123").

+!navigate_yield : true
   <- .print("  ↳ [G2.2] Phát hiện vật cản: Dừng nhường đường (Yield & Wait)...");
      .wait(1000);
      arrive("customer_address_123").

+!local_navigation : true
   <- arrive("customer_address_123").

+!handover_package : at_destination & package_loaded
   <- set_stage("G3_HANDOVER", "Đang xác thực và bàn giao kiện hàng cho khách...");
      .print("🤝 [G3] Bắt đầu [Bàn giao kiện hàng]...");
      .and_branches([authenticate_receiver, unlock_and_release]);
      -package_loaded;
      -at_destination;
      +package_delivered;
      set_stage("G3_HANDOVER_DONE", "Khách hàng đã nhận kiện hàng thành công.");
      .print("✅ [G3] Bàn giao kiện hàng thành công.").

+!handover_package : true
   <- set_stage("G3_HANDOVER", "Đang xác thực và bàn giao kiện hàng cho khách...");
      .print("🤝 [G3] Bắt đầu [Bàn giao kiện hàng]...");
      .and_branches([authenticate_receiver, unlock_and_release]);
      -package_loaded;
      -at_destination;
      +package_delivered;
      set_stage("G3_HANDOVER_DONE", "Khách hàng đã nhận kiện hàng thành công.");
      .print("✅ [G3] Bàn giao kiện hàng thành công.").

+!authenticate_receiver : package_loaded
   <- .or_branches([auth_via_otp, auth_via_qr, auth_via_face_id]);
      +auth_verified.

+!auth_via_otp : auth_method(otp) & not auth_verified
   <- .print("  ↳ [G3.1] Xác thực khách hàng qua mã OTP/SMS...");
      .wait(800).

+!auth_via_qr : auth_method(qr) & not auth_verified
   <- .print("  ↳ [G3.1] Quét mã QR trên ứng dụng của khách hàng...");
      .wait(800).

+!auth_via_face_id : auth_method(face_id) & not auth_verified
   <- .print("  ↳ [G3.1] Nhận diện khuôn mặt người nhận (FaceID)...");
      .wait(800).

+!auth_via_otp : true
   <- .print("  ↳ [G3.1] Xác thực khách hàng qua mã OTP/SMS...");
      .wait(800).

+!authenticate_receiver : true
   <- true.

+!unlock_and_release : auth_verified
   <- unlock_cargo;
      deliver_package;
      .wait(800);
      lock_cargo;
      -auth_verified.

+!unlock_and_release : true
   <- unlock_cargo;
      deliver_package;
      .wait(800);
      lock_cargo.

+!complete_cycle : package_delivered
   <- set_stage("G4_COMPLETE", "Đang xử lý kết thúc chu trình giao hàng...");
      .or_branches([finish_and_next_order, charge_at_dock, park_and_idle]);
      -package_delivered;
      finish_cycle;
      .print("✅ [G4] Hoàn tất chu trình giao hàng.").

+!finish_and_next_order : battery_level(B) & B > 30 & next_action_mode(next_order)
   <- .print("🔄 [G4] Pin tốt (", B, "%): Sẵn sàng nhận đơn hàng kế tiếp!");
      .wait(600).

+!charge_at_dock : battery_level(B) & B <= 30
   <- .print("⚡ [G4] Pin yếu (", B, "%): Di chuyển về trạm sạc tự động...");
      arrive("charging_dock_1");
      .wait(800).

+!park_and_idle : next_action_mode(idle)
   <- .print("🅿️ [G4] Chuyển trạng thái nghỉ (Idle) tại bãi đỗ.");
      .wait(600).

+!finish_and_next_order : true
   <- .print("🔄 [G4] Sẵn sàng nhận đơn hàng kế tiếp!");
      .wait(600).

+!complete_cycle : true
   <- finish_cycle;
      .print("🅿️ [G4] Trở về trạng thái nghỉ an toàn.").

+!reset_simulation
   <- reset_env;
      -delivering;
      -has_order;
      -package_loaded;
      -at_dock;
      -at_destination;
      -auth_verified;
      -package_delivered;
      -reroute_done;
      -emergency_handled;
      -return_initiated;
      -sleep_mode_active;
      .print("🔄 [delivery_truck] Đã đặt lại trạng thái ban đầu.").

+!cancel_and_log_wms
   <- set_stage("G1_RECOVERY", "Xảy ra sự cố bốc hàng -> Hủy đơn & gửi log WMS");
      .print("⚠️ [RECOVERY] Lấy hàng thất bại: Đang hủy đơn và gửi log về WMS...");
      .wait(800).

+!reroute_new_path
   <- set_stage("G2_RECOVERY_REROUTE", "Tắc đường/Chặn lộ trình -> Tính toán lại đường vòng tránh");
      .print("⚠️ [RECOVERY] Đường bị chặn: Kích hoạt tính lại lộ trình tránh tắc nghẽn...");
      reroute;
      +reroute_done;
      .wait(800).

+!emergency_rescue
   <- set_stage("G2_EMERGENCY", "SỰ CỐ KHẨN CẤP (Mất cảm biến / Cạn pin) -> Phanh khẩn cấp & Cứu hộ");
      .print("🚨 [RECOVERY] Khẩn cấp (Mất cảm biến/Hết pin): Phanh dừng xe & phát tín hiệu cứu hộ...");
      emergency_brake;
      +emergency_handled;
      .wait(800).

+!return_to_warehouse
   <- set_stage("G3_RECOVERY_RETURN", "Bàn giao thất bại (Timeout/Auth failed/Kẹt khóa) -> Khóa thùng & Chở về kho");
      .print("⚠️ [RECOVERY] Giao hàng không thành công: Khóa thùng và chuyển hàng về kho...");
      lock_cargo;
      +return_initiated;
      .wait(800);
      arrive("warehouse_dock_A").

+!standby_sleep_mode
   <- set_stage("G4_RECOVERY_SLEEP", "Trạm sạc bận/Lệch chân sạc -> Chuyển bãi đỗ phụ & Ngủ sâu");
      .print("⚠️ [RECOVERY] Trạm sạc bận: Di chuyển sang bãi đỗ phụ và bật Sleep Mode...");
      arrive("auxiliary_parking");
      +sleep_mode_active;
      .wait(800).


