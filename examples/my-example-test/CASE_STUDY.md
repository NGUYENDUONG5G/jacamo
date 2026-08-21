# Case Study: Hệ Thống Giao Hàng Tự Hành Thông Minh (Autonomous Delivery MAS)

Tài liệu này mô tả chi tiết Case Study kiểm thử mô hình **Failure Recovery & Adaptation** đa tầng (Agent, Environment, Organisation) trong dự án `examples/my-example-test`.

---

## 1. Bối cảnh & Mục tiêu (Context & Objectives)

Hệ thống mô phỏng một dịch vụ giao hàng tự hành bao gồm:
1. **Trung tâm điều phối (`dispatcher`)**: Đảm nhận vai trò điều phối viên trong tổ chức MoISE, phân bổ đơn và xử lý sự cố cấp độ toàn đội xe.
2. **Xe tải giao hàng tự hành (`delivery_truck`)**: Đảm nhận vai trò tác tử thực thi nhiệm vụ giao hàng tuần tự qua 4 giai đoạn mục tiêu:
   * **[G1] Nhận & Bốc hàng (`pickup_package`)**
   * **[G2] Di chuyển & Điều hướng (`transit_to_delivery`)**
   * **[G3] Bàn giao kiện hàng (`handover_package`)**
   * **[G4] Hoàn tất chu trình & Sạc pin (`complete_cycle`)**
3. **Môi trường CArtAgO (`tools.DeliverySystem`)**: Quản lý trạng thái định vị, lộ trình, khóa thùng xe, và phanh khẩn cấp.
4. **Tổ chức MoISE (`my_org.xml`)**: Nhóm `my_team` và Đồ hình kế hoạch `s1: delivery_scheme`.

---

## 2. Bản đồ Giám sát Sự cố & Thích ứng (Failure & Recovery Matrix)

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 TỔNG QUAN CASE STUDY                                   │
├──────────────────────┬────────────────────────┬────────────────────────────────────────┤
│ Tầng giám sát        │ Tình huống lỗi (Error) │ Chiến lược Thích ứng (Adaptation)      │
├──────────────────────┼────────────────────────┼────────────────────────────────────────┤
│ 1. Tổ chức (Group)   │ Thiếu điều phối viên   │ • "org:my_team.adoptRole(dispatcher)"   │
│                      │ Quá tải đội xe         │ • "goal:reassign_urgent_deliveries"    │
├──────────────────────┼────────────────────────┼────────────────────────────────────────┤
│ 2. Kế hoạch (Scheme) │ Tắc nghẽn tiến độ giao │ • "org:s1.substitute_scheme(...)"      │
│                      │                        │ • "env:w1.navigation.reroute"          │
├──────────────────────┼────────────────────────┼────────────────────────────────────────┤
│ 3. Tác tử: Bốc hàng  │ Cánh tay robot lỗi     │ • "goal:cancel_and_log_wms"            │
│                      │ Mã barcode không hợp lệ│                                        │
│                      │ Kiện hàng quá tải trọng│                                        │
├──────────────────────┼────────────────────────┼────────────────────────────────────────┤
│ 4. Tác tử: Điều hướng│ Tuyến đường bị chặn    │ • "goal:reroute_new_path"              │
│                      │                        │ • "env:w1.navigation.reroute"          │
│                      │ Mất GPS / Pin cạn kiệt │ • "goal:emergency_rescue"              │
│                      │                        │ • "env:w1.navigation.emergency_brake"  │
├──────────────────────┼────────────────────────┼────────────────────────────────────────┤
│ 5. Tác tử: Bàn giao  │ Khách hàng quá hạn chờ │ • "goal:return_to_warehouse"           │
│                      │ Xác thực thất bại 3 lần│ • "env:w1.navigation.lock_cargo"       │
│                      │ Khóa thùng xe bị kẹt   │                                        │
├──────────────────────┼────────────────────────┼────────────────────────────────────────┤
│ 6. Tác tử: Trạm sạc  │ Trạm sạc bị chiếm dụng │ • "goal:standby_sleep_mode" (Ngủ sâu)  │
│                      │ Chân tiếp xúc lệch     │                                        │
└──────────────────────┴────────────────────────┴────────────────────────────────────────┘
```

---

## 3. Cách chạy Case Study

Từ thư mục gốc `e:\jacamo`:

```bash
# 1. Chạy biên dịch toàn bộ dự án
.\gradlew.bat compileJava

# 2. Chạy case study my-example-test qua Gradle application runner
.\gradlew.bat :examples:my-example-test:run
```

Hoặc chạy file cấu hình `.jcm`:
```bash
java -jar build/libs/jacamo-1.3.1-SNAPSHOT.jar examples/my-example-test/my-example.jcm
```
