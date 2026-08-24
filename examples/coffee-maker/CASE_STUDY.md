# Case Study: Hệ Thống Pha Cà Phê Thông Minh Đa Tác Tử (Coffee Maker MAS)

Tài liệu này mô tả chi tiết mô hình **Failure Recovery & Adaptation** đa tầng (Agent BDI, Environment CArtAgO, Organisation MoISE) trong dự án `examples/coffee-maker`.

---

## 1. Bối Cảnh & Mục Tiêu (Context & Objectives)

Hệ thống mô phỏng một trạm pha chế cà phê thông minh tự động bao gồm:
1. **Tác tử Máy xay & Chuẩn bị Cà phê (`coffee_maker`)**: Đảm nhận vai trò `coffee_maker_role` trong tổ chức MoISE, thực thi mục tiêu **`grind_beans`** (định lượng hạt, kích hoạt cối xay, tự động nạp hạt hoặc thông kẹt cối xay).
2. **Tác tử Máy đun nước nóng thông minh (`water_boiler`)**: Đảm nhận vai trò `water_boiler_role` trong tổ chức MoISE, thực thi mục tiêu **`boil_water`** (kiểm soát dung tích nước buồng đun bằng cách bơm hút `draw_water_from_tank` từ khoang cấp nước bên ngoài, đun sôi đạt chuẩn 92°C, tự động làm nguội khi quá nhiệt).
3. **Môi trường CArtAgO (`w1`)**:
   - `tools.CoffeeMachineArtifact`: Quản lý lượng hạt cà phê `beans_level`, trạng thái cối xay `grinder_state`, và các thao tác xay/nạp/reset.
   - `tools.WaterBoilerArtifact`: Quản lý dung tích nước buồng đun `water_volume`, khoang cấp nước bên ngoài `supply_reservoir_volume`, nhiệt độ `current_temp`, trạng thái đun `heating_status`, và các thao tác bơm hút/đun/làm nguội.
4. **Tổ chức MoISE (`src/org/coffee-org.xml`)**:
   - Nhóm `my_coffee_team` (`coffee_team`)
   - Đồ hình kế hoạch `s1: coffee_scheme` với mục tiêu song song `prepare_ingredients` (`boil_water` & `grind_beans`).

---

## 2. Bản Đồ Giám Sát Sự Cố & Thích Ứng (Failure & Recovery Matrix)

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       MA TRẬN GIÁM SÁT SỰ CỐ & THÍCH ỨNG                                │
├────────────────────────┬─────────────────────────────┬───────────────────┬─────────────────────────────┤
│ Tầng Giám Sát          │ Tình Huống Lỗi (Error)      │ Điều Kiện (Belief)│ Hoạt Động Thích Ứng         │
├────────────────────────┼─────────────────────────────┼───────────────────┼─────────────────────────────┤
│ 🏢 1. TỔ CHỨC (Group)  │ Thiếu máy đun nước          │ cardinality(...)<1│ • "org:my_coffee_team.adopt │
│ [coffee_team_          │ (missing_boiler_agent)      │                   │   Role(water_boiler_role)"  │
│  coordination]         │                             │                   │ • "goal:notify_maintenance" │
│                        │ Thiếu máy pha cà phê        │ cardinality(...)<1│ • "org:my_coffee_team.adopt │
│                        │ (missing_brewer_agent)      │                   │   Role(coffee_maker_role)"  │
│                        │                             │                   │ • "goal:notify_maintenance" │
├────────────────────────┼─────────────────────────────┼───────────────────┼─────────────────────────────┤
│ 🏢 2. ĐỒ HÌNH (Scheme) │ Tắc nghẽn đồ hình           │ scheme_deadlock   │ • "org:s1.substitute_scheme │
│ [coffee_scheme_        │ (scheme_deadlock)           │                   │   (emergency_coffee_scheme)"│
│  execution]            │                             │                   │ • "goal:reset_simulation"   │
│                        │ Quá thời gian pha chế       │ brewing_timeout   │ • "goal:abort_and_clean_up" │
│                        │ (brewing_timeout)           │                   │ • "env:w1.coffee_system.    │
│                        │                             │                   │   cancel_operation"         │
├────────────────────────┼─────────────────────────────┼───────────────────┼─────────────────────────────┤
│ 🤖 3. TÁC TỬ CÀ PHÊ   │ Hết hạt cà phê              │ beans_depleted    │ • "goal:ensure_required_    │
│ [grind_beans]          │ (no_beans)                  │                   │   bean_amount"              │
│                        │                             │                   │ • "env:w1.coffee_system.    │
│                        │                             │                   │   refill_beans(200)"        │
│                        │ Cối xay bị kẹt hạt          │ grinder_overload  │ • "goal:unclog_grinder"     │
│                        │ (grinder_jammed)            │                   │ • "env:w1.coffee_system.    │
│                        │                             │                   │   reset_grinder"            │
├────────────────────────┼─────────────────────────────┼───────────────────┼─────────────────────────────┤
│ 🤖 4. TÁC TỬ BÌNH ĐUN  │ Khoang cấp không đủ nước    │ supply_water_     │ • "goal:ensure_sufficient_  │
│ [boil_water]           │ (insufficient_supply_water) │ insufficient      │   water_volume"             │
│                        │                             │                   │ • "env:w1.water_system.     │
│                        │                             │                   │   refill_supply_tank(1000)" │
│                        │ Bình đun quá nhiệt (>100°C) │ water_overheated  │ • "goal:cooldown_boiler"    │
│                        │ (heater_overheated)         │                   │ • "env:w1.water_system.     │
│                        │                             │                   │   cool_down"                │
└────────────────────────┴─────────────────────────────┴───────────────────┴─────────────────────────────┘
```

---

## 3. Cách Chạy & Thử Nghiệm Tương Tác Trên Web Simulation Studio

### Bước 1: Khởi động hệ thống JaCaMo MAS
```bash
cd examples/coffee-maker
./gradlew run
# hoặc trên Windows: .\gradlew.bat run
```

Khi vừa khởi động, hai tác tử `coffee_maker` và `water_boiler` sẽ ở **chế độ chờ (Standby)**, không tự động chạy nếu chưa nhận lệnh từ người dùng.

---

### Bước 2: Điều khiển chạy chu trình từ Web Studio

Mở trình duyệt truy cập: **`http://localhost:3274`** (hoặc cổng thông báo trên console).

1. **Khởi động chu trình pha chế (Happy Path)**:
   - Trong tab **Belief Base** của `coffee_maker`: Inject belief **`start_coffee`** (hoặc `start_grind`).
   - Trong tab **Belief Base** của `water_boiler`: Inject belief **`start_coffee`** (hoặc `start_boil`).
   - Hai tác tử sẽ bắt đầu quy trình xay hạt và đun nước đồng bộ với các bước quan sát trực quan.

2. **Mô phỏng Thích ứng Lỗi (Failure & Adaptation Studio)**:
   - Chuyển sang tab **Failure & Adaptation**.
   - Bấm nút **⚡ Giả lập Lỗi** hoặc **🛡️ Phục hồi** tại từng lỗi:
     * `beans_empty_detected`: Tự động nạp bổ sung hạt thông qua goal `ensure_required_bean_amount` và xóa belief lỗi `-beans_empty_detected`.
     * `grinder_motor_stuck`: Tự động đảo chiều, reset cối xay sang trạng thái sẵn sàng và xóa belief lỗi `-grinder_motor_stuck`.
     * `supply_tank_empty`: Nạp bổ sung khoang cấp nước bên ngoài (`refill_supply_tank`), kích hoạt goal `ensure_sufficient_water_volume` và xóa belief lỗi `-supply_tank_empty`.
     * `boiler_overheat_signal`: Tự động ngắt điện trở, kích hoạt làm nguội về 90°C-92°C và xóa belief lỗi `-boiler_overheat_signal`.

3. **Quan sát Nhật Ký Phục Hồi (Adaptation Log Console)**:
   - Theo dõi từng sự kiện lỗi được phát hiện và các bước thích ứng được tác tử tự động xử lý trong thời gian thực.
