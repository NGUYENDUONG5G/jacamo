========================================================================
CASE STUDY: Robot Hút bụi Thông minh (iCleaner)
========================================================================

Bối cảnh: Robot iCleaner vừa thực hiện nhiệm vụ lau dọn phòng (hút bụi, mopping)
vừa phải tự động kiểm soát năng lượng pin, đổ rác và tránh chướng ngại vật trong phòng.

1. Các Thực thể Môi trường (Artifacts):
   - Battery (Viên pin - Internal): Trạng thái lưu trữ mức pin % charge.
   - Charging Station (Trạm sạc - External): Vị trí sạc pin và hỗ trợ cứu hộ.
   - Dustbin (Thùng rác ngoài - External): Vị trí xả rác cho robot.
   - Obstacles (Chướng ngại vật - External) & Room (Căn phòng - External): Cản trở hành trình.

2. Mô hình Mục tiêu Mở rộng (Extended Goal Model):
   - Mục tiêu duy trì 1: Clean room (Lau dọn phòng)
   - Mục tiêu duy trì 2: Battery loaded (Duy trì pin)
     * MaintainCondition: charge > 10% (Trạng thái Suspended)
     * Khi charge <= 10%: Trạng thái Active
     * TargetCondition: Pin sạc đầy 100%
     * Mối quan hệ ức chế động: Battery loaded «inhibits» Clean room
   - Mục tiêu duy trì 3: Observe Environment (Quan sát môi trường)
     * Phân rã: LocateNextTarget và MoveToTarget

3. Mô hình Lỗi (Failure Model):
   - Goal: Battery loaded -> Failure: battery discharged
   - Error: charging_station_too_distant
   - Recovery Activities: emergency_move, send_rescue_signal, charging_station.call_support
