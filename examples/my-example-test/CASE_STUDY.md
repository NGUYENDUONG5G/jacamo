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

## 3. Cách chạy Case Study & Mô phỏng trên Web

### Bước 1: Khởi động hệ thống JaCaMo MAS
Di chuyển vào thư mục `examples/my-example-test` và chạy:
```bash
./gradlew run
# hoặc: .\gradlew.bat run
```

Hệ thống sẽ tự động khởi động:
- CArtAgO Environment & MoISE Organisation.
- 2 Agents: `dispatcher` và `delivery_truck` (ở trạng thái sẵn sàng).
- **Web Simulation Studio / Goal Model Inspector** tại: **`http://localhost:3274`** (hoặc cổng hiển thị trên console).

---

### Bước 2: Điều khiển mô phỏng tương tác trên Web

Mở trình duyệt truy cập: **`http://localhost:3274`** (hoặc tab **Simulation Studio**).

1. **Chạy toàn bộ quy trình giao hàng (Happy Path)**:
   - Trong mục **Inject Belief** của agent `delivery_truck`, nhập `start_delivery` và nhấn **Inject**.
   - Robot sẽ thực hiện tuần tự qua 4 giai đoạn [G1] $\rightarrow$ [G2] $\rightarrow$ [G3] $\rightarrow$ [G4] với các bước delay trực quan và cập nhật trạng thái thời gian thực lên Observable Properties.

2. **Chạy từng bước (Step-by-Step Interactive Mode)**:
   - **Bước 1 (Lấy hàng)**: Inject belief `run_step(pickup)`
   - **Bước 2 (Di chuyển)**: Inject belief `run_step(transit)`
   - **Bước 3 (Bàn giao)**: Inject belief `run_step(handover)`
   - **Bước 4 (Kết thúc)**: Inject belief `run_step(complete)`
   - **Đặt lại ban đầu**: Inject belief `run_step(reset)`

3. **Mô phỏng kích hoạt sự cố & Thích ứng (Failure Injection & Adaptation)**:
   - Tại bảng **Failure Models / Errors** trên Web, bấm nút **⚡ Inject** tại bất kỳ lỗi nào (hoặc inject belief tương ứng):
     * `path_blocked`: Tuyến đường bị chặn $\rightarrow$ Kích hoạt `reroute_new_path`.
     * `gps_lost` hoặc `battery_critical`: Mất GPS / Cạn pin $\rightarrow$ Phanh khẩn cấp `emergency_rescue`.
     * `wait_timeout` hoặc `auth_exceeded`: Khách không nhận $\rightarrow$ Khóa hàng chở về kho `return_to_warehouse`.
     * `arm_disconnected`: Lỗi cánh tay robot $\rightarrow$ Hủy đơn và log WMS `cancel_and_log_wms`.
     * `docks_occupied`: Hết trạm sạc $\rightarrow$ Vào chế độ ngủ sâu `standby_sleep_mode`.
   - Xem dòng thời gian sự kiện (Timeline) và trạng thái Artifacts/Beliefs cập nhật tức thì.

