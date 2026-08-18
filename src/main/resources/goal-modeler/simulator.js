/**
 * JaCaMo Goal Execution & Failure Recovery Interactive Simulator
 */

class GoalSimulator {
  constructor(model) {
    this.model = model;
    this.beliefs = new Set();
    this.contextVariables = {
      pickup_method: "robot_arm",
      avoidance_mode: "yield",
      auth_method: "qr",
      battery_level: 85,
      next_action_mode: "next_order"
    };

    this.goalStack = [];
    this.executionHistory = [];
    this.currentStepIndex = 0;
    this.isRunningAuto = false;
    this.autoTimer = null;

    this.simBeliefsList = document.getElementById("simBeliefsList");
    this.simParamsList = document.getElementById("simParamsList");
    this.simTimeline = document.getElementById("simTimeline");
    this.simConsole = document.getElementById("simConsole");
    this.simStepCounter = document.getElementById("simStepCounter");
  }

  init() {
    this.reset();
    this.renderBeliefToggles();
    this.renderParamSliders();
  }

  reset() {
    this.stopAutoPlay();
    this.currentStepIndex = 0;
    this.goalStack = [];
    this.executionHistory = [];

    // Push root goal
    const rootGoal = Array.from(this.model.goals.values()).find(g => g.isRoot || g.name === this.model.initialGoal);
    if (rootGoal) {
      this.goalStack.push({
        goal: rootGoal,
        status: "pending",
        plan: null,
        subgoalIndex: 0
      });
    }

    if (this.simStepCounter) this.simStepCounter.innerText = "Bước: 0";
    if (this.simTimeline) this.simTimeline.innerHTML = `<div class="sim-step-item">Sẵn sàng. Mục tiêu gốc: <strong>!${rootGoal ? rootGoal.name : "none"}</strong></div>`;
    this.log("Mô phỏng được thiết lập lại. Sẵn sàng thực thi vòng lặp lý trí.", "info");
  }

  log(msg, type = "info") {
    if (!this.simConsole) return;
    const line = document.createElement("div");
    line.className = `console-line ${type}`;
    const time = new Date().toLocaleTimeString();
    line.textContent = `[${time}] ${msg}`;
    this.simConsole.appendChild(line);
    this.simConsole.scrollTop = this.simConsole.scrollHeight;
  }

  renderBeliefToggles() {
    if (!this.simBeliefsList) return;
    this.simBeliefsList.innerHTML = "";

    // Extract all atomic condition predicates from belief rules
    const conditionSet = new Set([
      "robot_arm_conn_failed",
      "barcode_scan_failed",
      "weight_exceeded",
      "road_blocked_detected",
      "sensor_offline",
      "battery_below_10",
      "customer_no_show",
      "auth_attempts_over_3",
      "hatch_mechanism_stuck",
      "all_docks_busy",
      "dock_contact_error"
    ]);

    for (let r of this.model.beliefRules.values()) {
      if (r.conditions) {
        for (let c of r.conditions) {
          let clean = c.replace(/^not\s+/, "").trim();
          if (clean && !clean.includes("(")) conditionSet.add(clean);
        }
      }
    }

    for (let cond of conditionSet) {
      const item = document.createElement("div");
      const isActive = this.beliefs.has(cond);
      item.className = `belief-toggle-item ${isActive ? "active" : ""}`;
      item.innerHTML = `
        <span class="code-font">${cond}</span>
        <div class="toggle-switch"></div>
      `;

      item.addEventListener("click", () => {
        if (this.beliefs.has(cond)) {
          this.beliefs.delete(cond);
          item.classList.remove("active");
          this.log(`- Xóa niềm tin lỗi: -${cond}`, "warn");
        } else {
          this.beliefs.add(cond);
          item.classList.add("active");
          this.log(`+ Ghi nhận niềm tin lỗi: +${cond}`, "error");
        }
        this.evaluateBeliefDeduction();
      });

      this.simBeliefsList.appendChild(item);
    }
  }

  renderParamSliders() {
    if (!this.simParamsList) return;
    this.simParamsList.innerHTML = "";

    // Render Battery Level Slider
    const batteryItem = document.createElement("div");
    batteryItem.className = "form-group";
    batteryItem.innerHTML = `
      <label>🔋 Mức pin (battery_level): <strong id="simBatteryVal">${this.contextVariables.battery_level}%</strong></label>
      <input type="range" min="5" max="100" value="${this.contextVariables.battery_level}" class="form-control" id="simBatterySlider" />
    `;
    this.simParamsList.appendChild(batteryItem);

    const slider = batteryItem.querySelector("#simBatterySlider");
    const valDisp = batteryItem.querySelector("#simBatteryVal");
    slider.addEventListener("input", (e) => {
      let v = parseInt(e.target.value);
      this.contextVariables.battery_level = v;
      valDisp.innerText = v + "%";
      if (v < 10) {
        this.beliefs.add("battery_below_10");
      } else {
        this.beliefs.delete("battery_below_10");
      }
      this.renderBeliefToggles();
      this.evaluateBeliefDeduction();
    });

    // Render Pickup Method selector
    const pickupItem = document.createElement("div");
    pickupItem.className = "form-group mt-4";
    pickupItem.innerHTML = `
      <label>📦 Phương thức lấy hàng (pickup_method):</label>
      <select class="form-control" id="simPickupSelect">
        <option value="robot_arm" ${this.contextVariables.pickup_method === "robot_arm" ? "selected" : ""}>robot_arm (Cánh tay robot)</option>
        <option value="manual_scan" ${this.contextVariables.pickup_method === "manual_scan" ? "selected" : ""}>manual_scan (Quét thủ công)</option>
      </select>
    `;
    this.simParamsList.appendChild(pickupItem);
    pickupItem.querySelector("#simPickupSelect").addEventListener("change", (e) => {
      this.contextVariables.pickup_method = e.target.value;
      this.log(`Cập nhật biến ngữ cảnh: pickup_method(${e.target.value})`, "info");
    });
  }

  evaluateBeliefDeduction() {
    const deduced = [];
    for (let r of this.model.beliefRules.values()) {
      if (r.body && r.body !== "true") {
        // Evaluate conditions
        let parts = r.body.split("&").map(s => s.trim());
        let allSatisfied = true;

        for (let p of parts) {
          if (p.startsWith("not ")) {
            let neg = p.substring(4).trim();
            if (this.beliefs.has(neg)) {
              allSatisfied = false;
              break;
            }
          } else {
            if (!this.beliefs.has(p)) {
              allSatisfied = false;
              break;
            }
          }
        }

        if (allSatisfied) {
          deduced.push(r.head);
        }
      }
    }

    if (deduced.length > 0) {
      this.log(`🧠 [belief.asl] Suy diễn được các lỗi: ${deduced.join(", ")}`, "warn");
    }
    return deduced;
  }

  /**
   * Execute single simulation step in MAS Goal-Plan Tree
   */
  step() {
    if (this.goalStack.length === 0) {
      this.log("🎉 Tất cả các mục tiêu đã hoàn tất xuất sắc!", "success");
      this.stopAutoPlay();
      return;
    }

    this.currentStepIndex++;
    if (this.simStepCounter) this.simStepCounter.innerText = `Bước: ${this.currentStepIndex}`;

    // 1. Check for Active Failure Conditions via Belief Deduction
    const deducedErrors = this.evaluateBeliefDeduction();
    if (deducedErrors.length > 0) {
      let errorCondition = deducedErrors[0];
      let mapping = this.model.failureMappings.find(f => f.beliefCondition === errorCondition);

      if (mapping) {
        this.log(`🚨 [FAILURE INTERCEPT] Phát hiện lỗi '${errorCondition}' trên mục tiêu '${mapping.targetGoal}'!`, "error");
        this.log(`⚡ [RECOVERY TRIGGER] Kích hoạt Recovery Plan: +!${mapping.recoveryPlan}`, "warn");

        const recPlan = Array.from(this.model.recoveryPlans.values()).find(r => r.goalName === mapping.recoveryPlan);
        if (recPlan) {
          this.log(`⚙️ [Hành động phục hồi]: ${recPlan.bodyText.replace(/\n/g, " ")}`, "warn");
        }

        // Add to timeline
        const stepEl = document.createElement("div");
        stepEl.className = "sim-step-item error";
        stepEl.innerHTML = `🚨 <strong>Failure Trigger:</strong> ${errorCondition} ➔ Kích hoạt <strong>+!${mapping.recoveryPlan}</strong>`;
        if (this.simTimeline) this.simTimeline.appendChild(stepEl);

        // Clear error condition after recovery handling
        this.beliefs.delete(errorCondition);
        for (let r of this.model.beliefRules.values()) {
          if (r.head === errorCondition && r.conditions) {
            for (let c of r.conditions) this.beliefs.delete(c.replace(/^not\s+/, "").trim());
          }
        }
        this.renderBeliefToggles();
        this.log(`✅ [Khôi phục] Đã xử lý lỗi xong và tiếp tục chu trình mục tiêu.`, "success");
        return;
      }
    }

    // 2. Process Current Goal on Stack
    let currentFrame = this.goalStack[this.goalStack.length - 1];
    let goal = currentFrame.goal;

    if (currentFrame.status === "pending") {
      this.log(`🎯 [Intention] Bắt đầu thực hiện mục tiêu: +!${goal.name}`, "info");

      // Select plan matching context
      const candidatePlans = Array.from(this.model.plans.values()).filter(p => p.goalName === goal.name);
      let chosenPlan = candidatePlans[0];

      // Match context variable
      for (let cp of candidatePlans) {
        if (cp.context) {
          if (cp.context.includes(this.contextVariables.pickup_method) ||
              cp.context.includes(this.contextVariables.avoidance_mode) ||
              cp.context.includes(this.contextVariables.auth_method)) {
            chosenPlan = cp;
            break;
          }
        }
      }

      currentFrame.plan = chosenPlan;
      currentFrame.status = "executing";

      const stepEl = document.createElement("div");
      stepEl.className = "sim-step-item running";
      stepEl.innerHTML = `▶ <strong>!${goal.name}</strong>: Kế hoạch <code>+!${goal.name}${chosenPlan && chosenPlan.context ? " : " + chosenPlan.context : ""}</code>`;
      if (this.simTimeline) this.simTimeline.appendChild(stepEl);

      // If goal has subgoals, push them to stack
      if (goal.subgoals && goal.subgoals.length > 0) {
        for (let i = goal.subgoals.length - 1; i >= 0; i--) {
          let subName = goal.subgoals[i];
          let subGNode = Array.from(this.model.goals.values()).find(g => g.name === subName);
          if (subGNode) {
            this.goalStack.push({
              goal: subGNode,
              status: "pending",
              plan: null,
              subgoalIndex: 0
            });
          }
        }
      }
    } else if (currentFrame.status === "executing") {
      // Complete goal
      this.goalStack.pop();
      this.log(`✅ [Achieved] Hoàn tất mục tiêu: !${goal.name}`, "success");

      const stepEl = document.createElement("div");
      stepEl.className = "sim-step-item success";
      stepEl.innerHTML = `✓ <strong>Đạt được:</strong> !${goal.name}`;
      if (this.simTimeline) this.simTimeline.appendChild(stepEl);
    }
  }

  toggleAutoPlay() {
    if (this.isRunningAuto) {
      this.stopAutoPlay();
    } else {
      this.startAutoPlay();
    }
  }

  startAutoPlay() {
    this.isRunningAuto = true;
    const btn = document.getElementById("btnSimAutoPlay");
    if (btn) {
      btn.innerText = "⏸ Tạm dừng";
      btn.className = "btn btn-danger";
    }
    this.autoTimer = setInterval(() => {
      this.step();
    }, 800);
  }

  stopAutoPlay() {
    this.isRunningAuto = false;
    if (this.autoTimer) clearInterval(this.autoTimer);
    const btn = document.getElementById("btnSimAutoPlay");
    if (btn) {
      btn.innerText = "⚡ Chạy tự động (Auto)";
      btn.className = "btn btn-success";
    }
  }
}
