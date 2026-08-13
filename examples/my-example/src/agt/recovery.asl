


-!safe_setup
   <- .my_name(Me);
      .a; 
      .print(Me, " khởi tạo thất bại. Đang thử lại toàn bộ quá trình thiết lập...");
      +setup_failed; 
      .wait(200);
      !safe_setup.


-!wait_and_adopt_role(OrgWId)
   <- .my_name(Me);
      .a; 
      .print(Me, " đang đợi nhóm my_team được tạo...");
      +setup_failed;
      .wait(200);
      !wait_and_adopt_role(OrgWId).


+!report_status(Role)
   :  setup_failed 
   <- .my_name(Me);
      .print("--> [KHÔI PHỤC THÀNH CÔNG] ", Me, " đã khôi phục lỗi kết nối và gia nhập tổ chức với vai trò ", Role, "!");
      -setup_failed.

+!report_status(Role)
   <- .my_name(Me);
      .print("--> [THÀNH CÔNG TRƠN TRU] ", Me, " đã khởi tạo và gia nhập tổ chức với vai trò ", Role, " ngay từ đầu (không gặp lỗi)!").
