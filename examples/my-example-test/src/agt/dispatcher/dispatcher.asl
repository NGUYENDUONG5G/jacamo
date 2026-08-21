{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

/* Initial Goals */
!start_dispatcher.

/* Plans */
+!start_dispatcher
   <- .print("🏢 [Dispatcher] Khởi động trung tâm điều phối kho vận...");
      joinWorkspace("/main/w1", WId);
      joinWorkspace("/main/o1", OrgWId);
      lookupArtifact("my_team", GrId)[wid(OrgWId)];
      adoptRole(dispatcher)[artifact_id(GrId)];
      focus(GrId);
      .print("🏢 [Dispatcher] Đã nhận vai trò [dispatcher] trong nhóm [my_team].").

+!notify_fleet_manager
   <- .print("📢 [Dispatcher Alert] Cảnh báo sự cố điều phối: Đã gửi thông báo khẩn cấp tới Quản lý Đội xe (Fleet Manager)!").

+!reassign_urgent_deliveries
   <- .print("🔄 [Dispatcher Action] Đang phân bổ lại các đơn hàng gấp cho xe dự phòng...").
