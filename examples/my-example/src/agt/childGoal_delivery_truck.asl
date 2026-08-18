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
