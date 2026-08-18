# JaCaMo Goal Studio (Trình Thiết Kế & Trực Quan Hóa Goal Model)

Ứng dụng web trực quan, hiện đại dùng để **Import**, **Thiết kế trực tiếp**, **Mô phỏng thực thi (Simulation)** và **Xuất mã nguồn (Export)** cho Goal Model từ **2 file AgentSpeak (`.asl`)**:
1. **File Agent ASL** (ví dụ `delivery_truck.asl`): Chứa mục tiêu gốc (`!goal`), các kế hoạch thực thi (`+!goal : context <- body`), và các Recovery Plans.
2. **File Belief ASL** (ví dụ `belief.asl`): Chứa các luật suy diễn niềm tin dạng Horn-Clause (`head :- condition1 & not condition2.`).

---

## 🌟 Các Tính Năng Nổi Bật

### 1. Sơ đồ tương tác Goal Canvas (Tropos4AS & AgentSpeak Hierarchy)
- **Cây phân rã Mục tiêu (Goal Decomposition)**:
  - Phân rã **AND-Decomposition** (mục tiêu con tuần tự).
  - Phân rã **OR-Decomposition** (nhánh kế hoạch có điều kiện ngữ cảnh `: context`).
- **Liên kết Xử lý sự cố (Failure & Recovery Links)**:
  - Kết nối trực tiếp giữa **Goal** ➔ **Điều kiện lỗi (Monitored Belief)** ➔ **Recovery Plan** thích ứng.
- **Kéo thả, Phóng to/Thu nhỏ & Tự động sắp xếp (Auto Layout)**:
  - Tự động căn chỉnh cấu trúc cây với thuật toán phân tầng (Hierarchical DAG layout).
  - Bản đồ thu nhỏ (Minimap) và thanh công cụ điều hướng trực quan.

### 2. Import 2 File .ASL Linh Hoạt
- Kéo thả trực tiếp file `delivery_truck.asl` và `belief.asl` hoặc dán mã nguồn trực tiếp.
- Tự động phân tích cú pháp AST của AgentSpeak và xây dựng mô hình đồ thị Goal Model ngay lập tức.

### 3. Đồng bộ 2 chiều (Bi-directional Live Sync)
- **Chỉnh sửa trên sơ đồ**: Thêm Goal, Plan, Recovery Plan, Belief Rule hoặc sửa thuộc tính trong Property Inspector ➔ Mã nguồn ASL của 2 file tự động cập nhật theo thời gian thực.
- **Chỉnh sửa trong Trình soạn thảo (Dual Code Editor)**: Gõ trực tiếp mã ASL và bấm **"Cập nhật sơ đồ"** để đồng bộ lại đồ thị.

### 4. Bảng Ánh Xạ Xử Lý Lỗi (Failure & Recovery Matrix) & Xuất .JCM
- Tổng hợp toàn bộ các ca lỗi trong hệ thống theo chuẩn **Tropos4AS Failure Model**.
- Tự động sinh khối cấu hình `failure { error { conditions: ... recovery-activities: ... } }` tương thích với JaCaMo runtime `.jcm`.

### 5. Bộ Mô Phỏng Vòng Lặp Lý Trí (Reasoning Cycle Simulator)
- Bật/tắt các niềm tin lỗi (`road_blocked_detected`, `robot_arm_conn_failed`, `battery_below_10`,...).
- Đổi các biến ngữ cảnh (`pickup_method`, `avoidance_mode`, `battery_level`).
- Bấm **"Bước tiếp theo (Step)"** hoặc **"Chạy tự động (Auto)"** để quan sát Agent chọn nhánh Plan hoặc tự động kích hoạt Recovery Plan khi có sự cố.

---

## 🚀 Hướng Dẫn Sử Dụng

### Cách 1: Mở trực tiếp trên trình duyệt
Chỉ cần nhấp đúp (hoặc mở bằng trình duyệt Chrome/Edge/Firefox) file:
```
e:\jacamo\tools\goal-modeler\index.html
```

### Cách 2: Chạy qua Local HTTP Server (Tùy chọn)
Mở terminal tại thư mục `tools/goal-modeler`:
```powershell
cd e:\jacamo\tools\goal-modeler
# Dùng Python:
python -m http.server 3000
# Hoặc dùng npx serve:
npx -y serve .
```
Truy cập: `http://localhost:3000`

---

## 📁 Cấu Trúc Mã Nguồn

```
tools/goal-modeler/
├── index.html       # Giao diện chính, Canvas SVG, Split Panes, Modals
├── styles.css       # Theme tối Futuristic, Glassmorphism, animations
├── parser.js        # Bộ phân tích và sinh mã AgentSpeak & Belief Rules
├── model.js         # Quản lý đồ thị Goal Model, Auto-layout, Validation
├── canvas.js        # Bộ dựng sơ đồ tương tác SVG, Pan/Zoom, Minimap
├── simulator.js     # Mô phỏng thực thi Goal & kích hoạt Failure Recovery
├── samples.js       # Các bộ mẫu có sẵn (Delivery Truck, AMR Picker, Drone)
├── app.js           # Controller điều phối tổng thể và quản lý sự kiện UI
└── README.md        # Tài liệu hướng dẫn
```
