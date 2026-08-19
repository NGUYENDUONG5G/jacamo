{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }
{ include("delivery_truck/belief.asl") }

// ============================================================================
// 1. Initial Beliefs & State Configurations
// ============================================================================
battery_level(85).            // Mức pin (%)
package_weight(15).           // Trọng lượng gói hàng (kg)
pickup_method(robot_arm).     // robot_arm | manual_scan
avoidance_mode(yield).        // dynamic_avoid | yield
auth_method(qr).              // otp | qr | face_id
next_action_mode(next_order). // next_order | charging_dock | idle

// Root Goal Trigger
!deliver_success.

// ============================================================================
// 2. Goal & Plan Decomposition Tree (AND / OR Branches)
// ============================================================================

// --- [G0] Root Goal: Phân rã AND (Bắt buộc thực hiện tuần tự TẤT CẢ các bước) ---
+!deliver_success : true
   <- .print("=================================================================");
      .print("🚀 [G0] BẮT ĐẦU QUY TRÌNH GIAO HÀNG TỰ HÀNH...");
      .print("=================================================================");
      .and_branches([setup_workspace, pickup_package, transit_to_delivery, handover_package, complete_cycle]);
      .print("=================================================================");
      .print("🎉 [G0] HOÀN TẤT TOÀN BỘ QUY TRÌNH GIAO HÀNG THÀNH CÔNG!");
      .print("=================================================================").

// --- Setup Workspace & Tổ chức MoISE ---
+!setup_workspace
   <- joinWorkspace("/main/w1", WId);
      lookupArtifact("navigation", ArtId)[wid(WId)];
      +nav_art(ArtId);
      joinWorkspace("/main/o1", OrgWId);
      lookupArtifact("my_team", GrId)[wid(OrgWId)];
      adoptRole(delivery_robot)[artifact_id(GrId)];
      focus(GrId);
      .print("✅ [Setup] Đã kết nối Workspace và Tổ chức MoISE thành công.").

// --- [G1] Nhận & Lấy hàng: Phân rã AND ---
+!pickup_package : true
   <- .print("📦 [G1] Bắt đầu [Nhận & Lấy hàng]...");
      .and_branches([move_to_pickup, load_and_verify_package]);
      .print("✅ [G1] Hoàn thành lấy hàng thành công.").

+!move_to_pickup
   <- ?nav_art(ArtId);
      .print("  ↳ [G1.1] Di chuyển đến điểm lấy hàng (Kho A)...");
      .wait(300);
      arrive("warehouse_dock_A")[artifact_id(ArtId)];
      .print("  ↳ [G1.1] Đã đến điểm lấy hàng.").

// --- [G1.2] Bốc xếp hàng: Phân rã OR (Chọn 1 trong các phương thức bốc hàng) ---
+!load_and_verify_package : true
   <- .or_branches([load_robot_arm, load_manual_scan]);
      .print("  ↳ [G1.2] Kiện hàng đã được nạp thành công.").

+!load_robot_arm : pickup_method(robot_arm)
   <- .print("  ↳ [G1.2 - Plan 1.2a] Bốc kiện hàng tự động qua Robot Arm...");
      .wait(300);
      +package_loaded;
      .print("  ↳ [G1.2] Đã bốc hàng và kiểm tra hợp lệ.").

+!load_manual_scan : pickup_method(manual_scan)
   <- .print("  ↳ [G1.2 - Plan 1.2b] Nhân viên kho quét Barcode và bốc hàng thủ công...");
      .wait(300);
      +package_loaded;
      .print("  ↳ [G1.2] Nhân viên xác nhận kiện hàng hợp lệ.").

+!load_and_verify_package : true
   <- .print("  ↳ [G1.2 - Fallback] Tự động nạp kiện hàng theo cấu hình mặc định...");
      +package_loaded.

// --- [G2] Di chuyển đến điểm giao: Phân rã AND ---
+!transit_to_delivery : true
   <- .print("🚚 [G2] Bắt đầu [Di chuyển đến điểm giao]...");
      .and_branches([plan_global_path, local_navigation]);
      .print("✅ [G2] Đã đến điểm giao an toàn.").

+!plan_global_path
   <- ?nav_art(ArtId);
      .print("  ↳ [G2.1] Lập lộ trình toàn cục (Global Path)...");
      reroute[artifact_id(ArtId)];
      .wait(300).

// --- [G2.2] Điều hướng cục bộ: Phân rã OR (Chọn 1 trong các chế độ tránh vật cản) ---
+!local_navigation : true
   <- .or_branches([navigate_dynamic_avoid, navigate_yield]);
      .print("  ↳ [G2.2] Hoàn tất điều hướng cục bộ.").

+!navigate_dynamic_avoid : avoidance_mode(dynamic_avoid)
   <- .print("  ↳ [G2.2 - Plan 2.2a] Phát hiện vật cản động: Lái vòng tránh an toàn...");
      .wait(400);
      ?nav_art(ArtId);
      arrive("customer_address_123")[artifact_id(ArtId)].

+!navigate_yield : avoidance_mode(yield)
   <- .print("  ↳ [G2.2 - Plan 2.2b] Phát hiện vật cản: Dừng nhường đường (Yield & Wait)...");
      .wait(400);
      ?nav_art(ArtId);
      arrive("customer_address_123")[artifact_id(ArtId)].

+!local_navigation : true
   <- ?nav_art(ArtId);
      arrive("customer_address_123")[artifact_id(ArtId)].

// --- [G3] Bàn giao kiện hàng: Phân rã AND ---
+!handover_package : true
   <- .print("🤝 [G3] Bắt đầu [Bàn giao kiện hàng]...");
      .and_branches([authenticate_receiver, unlock_and_release]);
      .print("✅ [G3] Bàn giao kiện hàng thành công.").

// --- [G3.1] Xác thực khách hàng: Phân rã OR (Chọn 1 trong các phương thức OTP / QR / FaceID) ---
+!authenticate_receiver : true
   <- .or_branches([auth_via_otp, auth_via_qr, auth_via_face_id]);
      .print("  ↳ [G3.1] Xác thực người nhận thành công.").

+!auth_via_otp : auth_method(otp)
   <- .print("  ↳ [G3.1 - Plan 3.1a] Xác thực khách hàng qua mã OTP/SMS...");
      .wait(300);
      .print("  ↳ [G3.1] OTP chính xác!").

+!auth_via_qr : auth_method(qr)
   <- .print("  ↳ [G3.1 - Plan 3.1b] Quét mã QR trên ứng dụng của khách hàng...");
      .wait(300);
      .print("  ↳ [G3.1] QR hợp lệ!").

+!auth_via_face_id : auth_method(face_id)
   <- .print("  ↳ [G3.1 - Plan 3.1c] Nhận diện khuôn mặt người nhận (FaceID)...");
      .wait(300);
      .print("  ↳ [G3.1] FaceID xác thực thành công!").

+!authenticate_receiver : true
   <- .print("  ↳ [G3.1 - Default] Xác thực mặc định hoàn tất.").

+!unlock_and_release
   <- ?nav_art(ArtId);
      .print("  ↳ [G3.2] Mở khóa thùng chứa và nhả kiện hàng...");
      unlock_cargo[artifact_id(ArtId)];
      .wait(400);
      lock_cargo[artifact_id(ArtId)];
      .print("  ↳ [G3.2] Khách hàng đã lấy hàng, thùng xe đã khóa an toàn.").

// --- [G4] Hoàn tất chu trình: Phân rã OR (Tiếp tục đơn mới | Về trạm sạc | Nghỉ ngơi) ---
+!complete_cycle : true
   <- .or_branches([finish_and_next_order, charge_at_dock, park_and_idle]);
      .print("✅ [G4] Hoàn tất chu trình giao hàng.").

+!finish_and_next_order : battery_level(B) & B > 30 & next_action_mode(next_order)
   <- .print("🔄 [G4 - Plan 4.1] Pin còn tốt (", B, "% > 30%): Sẵn sàng nhận đơn hàng kế tiếp!").

+!charge_at_dock : battery_level(B) & B <= 30
   <- .print("⚡ [G4 - Plan 4.2] Pin yếu (", B, "% <= 30%): Di chuyển về trạm sạc tự động...");
      ?nav_art(ArtId);
      arrive("charging_dock_1")[artifact_id(ArtId)];
      .wait(300);
      .print("⚡ [G4 - Plan 4.2] Đã kết nối nguồn sạc thành công.").

+!park_and_idle : next_action_mode(idle)
   <- .print("🅿️ [G4 - Plan 4.3] Chuyển trạng thái nghỉ (Idle) tại bãi đỗ.").

+!complete_cycle : true
   <- .print("🅿️ [G4 - Default] Trở về trạng thái nghỉ an toàn.").

// ============================================================================
// 3. Failure & Recovery Plans
// ============================================================================

+!cancel_and_log_wms
   <- .print("⚠️ [RECOVERY PLAN 1] Lấy hàng thất bại: Đang hủy đơn trên hệ thống, gửi log về WMS và tìm đơn khác...");
      .wait(500);
      .print("⚠️ [RECOVERY PLAN 1] Đơn hàng đã hủy thành công, chuyển sang nhận đơn kế tiếp.").

+!reroute_new_path
   <- ?nav_art(ArtId);
      .print("⚠️ [RECOVERY PLAN 2.1] Đường bị chặn (Deadlock): Kích hoạt tính lại lộ trình tránh điểm tắc nghẽn...");
      reroute[artifact_id(ArtId)];
      +reroute_done;
      .print("⚠️ [RECOVERY PLAN 2.1] Đã tìm được đường mới! Tiếp tục hành trình...").

+!emergency_rescue
   <- ?nav_art(ArtId);
      .print("🚨 [RECOVERY PLAN 2.2] KHẨN CẤP (Mất cảm biến/Hết pin): Phanh dừng xe, bật đèn cảnh báo, gửi tọa độ cứu hộ...");
      emergency_brake[artifact_id(ArtId)];
      +emergency_handled;
      .print("🚨 [RECOVERY PLAN 2.2] Đã gửi tín hiệu cứu hộ thành công.").

+!return_to_warehouse
   <- ?nav_art(ArtId);
      .print("⚠️ [RECOVERY PLAN 3] Giao hàng không thành công: Khóa thùng bảo mật, đánh dấu đơn hoàn và chở hàng về kho...");
      lock_cargo[artifact_id(ArtId)];
      +return_initiated;
      arrive("warehouse_dock_A")[artifact_id(ArtId)];
      .print("⚠️ [RECOVERY PLAN 3] Đã nhập kho lại kiện hàng.").

+!standby_sleep_mode
   <- ?nav_art(ArtId);
      .print("⚠️ [RECOVERY PLAN 4] Không thể sạc tại dock: Di chuyển sang bãi đỗ phụ, bật chế độ ngủ sâu (Sleep Mode) và gửi cảnh báo kỹ thuật viên...");
      arrive("auxiliary_parking")[artifact_id(ArtId)];
      +sleep_mode_active;
      .print("⚠️ [RECOVERY PLAN 4] Robot đã vào chế độ ngủ sâu an toàn.").
