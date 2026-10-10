# Example: Fault-Tolerant Therapy

Ví dụ này minh họa cơ chế **Phát hiện lỗi (Failure Detection) và Phục hồi tổ chức (Recovery Scheme)** trong Moise và JaCaMo dựa trên kịch bản điều trị y tế.

---

## 1. Kịch bản minh họa (Scenario)

* **Tổ chức**: `medical_org` gồm 3 vai trò:
  * `patient` (Bệnh nhân: Alice)
  * `doctor` (Bác sĩ: Bob)
  * `pharmacist` (Dược sĩ: Carol)
* **Scheme chính (`therapy_sch`)**:
  * Các bước chuẩn bị: `consult` (khám) $\rightarrow$ `prescribe` (kê đơn) $\rightarrow$ `fill_prescription` (cấp thuốc).
  * Mục tiêu song song: `follow_therapy` (uống thuốc theo dõi trong 5 ngày).
* **Ngoại lệ phát sinh (`lost_symptoms`)**:
  * Vào ngày thứ 3 ($Day < 5$), bệnh nhân Alice thấy hết triệu chứng sớm.
  * Alice chia sẻ dữ kiện `symptoms_cleared(alice, 3)` lên `FaultTolerantSchemeBoard`.
  * Board đánh giá điều kiện `symptoms_cleared(Patient, Day) & Day < 5` là `TRUE`.
  * **Tạm dừng**: Goal `follow_therapy` bị ngắt sang trạng thái `suspended`, Alice tạm dừng uống thuốc.
  * **Kích hoạt phục hồi**: Board phát tín hiệu `recovery_required("reconsult_scheme", ...)`.
* **Phục hồi (`reconsult_scheme`)**:
  * Bác sĩ Bob tiếp nhận yêu cầu, tiến hành tái khám (`review_symptoms`) và điều chỉnh liều lượng thuốc (`adjust_therapy`).
  * Khi recovery scheme hoàn tất (`adjust_therapy` hoàn thành), `FaultTolerantSchemeBoard` **tự động kích hoạt `resumeGoal("follow_therapy")`** mà Bác sĩ không cần phải gọi thủ công.
  * Alice nhận được tín hiệu `goalResumed`, tự động được gỡ đình chỉ (unfrozen intention), tiếp tục và hoàn tất liệu trình an toàn.

---

## 2. Cấu trúc thư mục

```
examples/fault-tolerant-therapy/
├── fault-tolerant-therapy.jcm   # Cấu hình hệ thống MAS và tổ chức JaCaMo
├── build.gradle                 # Cấu hình Gradle
├── logging.properties           # Cấu hình log console
├── readme.md                    # Tài liệu giải thích kịch bản
└── src/
    ├── agt/
    │   ├── patient.asl          # Mã Agent Bệnh nhân Alice
    │   ├── doctor.asl           # Mã Agent Bác sĩ Bob
    │   └── pharmacist.asl       # Mã Agent Dược sĩ Carol
    └── org/
        └── therapy-os.xml       # Đặc tả tổ chức Moise có chứa khối <failure> & recovery scheme
```

---

## 3. Cách chạy ví dụ

Từ thư mục gốc dự án:

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"
./gradlew test --tests ora4mas.nopl.FaultTolerantSchemeBoardTest
```

Hoặc chạy trực tiếp ứng dụng MAS:

```powershell
cd examples/fault-tolerant-therapy
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"
.\gradlew.bat run
# Hoặc trên Linux / macOS / Bash:
# ./gradlew run
```
