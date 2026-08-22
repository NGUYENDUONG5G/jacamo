{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }

!start_dispatcher.

+!start_dispatcher
   <- .print("🏢 [Dispatcher] Khởi động trung tâm điều phối kho vận...").

+!notify_fleet_manager
   <- .print("📢 [Dispatcher Alert] Cảnh báo sự cố điều phối: Đã gửi thông báo khẩn cấp tới Quản lý Đội xe (Fleet Manager)!").

+!reassign_urgent_deliveries
   <- .print("🔄 [Dispatcher Action] Đang phân bổ lại các đơn hàng gấp cho xe dự phòng...").
