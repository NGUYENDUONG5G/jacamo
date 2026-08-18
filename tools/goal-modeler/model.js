/**
 * Goal Model Data Structures, Validation, and Auto-Layout Engine
 */

class GoalModel {
  constructor() {
    this.agentName = "delivery_truck";
    this.initialGoal = "deliver_success";
    this.initialBeliefs = [];
    this.goals = new Map(); // id -> GoalNode
    this.plans = new Map(); // id -> PlanNode
    this.recoveryPlans = new Map(); // id -> RecoveryNode
    this.beliefRules = new Map(); // id -> BeliefRule
    this.failureMappings = []; // List of { targetGoal, errorName, beliefCondition, recoveryPlan, envAdaptation, orgAdaptation }
    this.listeners = [];
  }

  onChange(callback) {
    this.listeners.push(callback);
  }

  notify() {
    for (let cb of this.listeners) {
      cb(this);
    }
  }

  /**
   * Initializes Goal Model from parsed ASL and Belief Rules
   */
  loadFromParsed(agentAst, beliefAst, agentName = "delivery_truck") {
    this.agentName = agentName;
    this.initialGoal = agentAst.initialGoal || "main_goal";
    this.initialBeliefs = [...(agentAst.initialBeliefs || [])];
    this.goals.clear();
    this.plans.clear();
    this.recoveryPlans.clear();
    this.beliefRules.clear();
    this.failureMappings = [];

    // Load belief rules
    for (let r of beliefAst) {
      let bId = "belief_" + (r.head.replace(/[^a-zA-Z0-9_]/g, "_") || Math.random().toString(36).substr(2, 5));
      this.beliefRules.set(bId, {
        id: bId,
        head: r.head,
        body: r.body,
        conditions: r.conditions || []
      });
    }

    // Separate normal plans vs recovery plans
    const goalMap = new Map(); // goalName -> Array of plans

    for (let p of agentAst.plans) {
      if (p.isRecovery) {
        let rId = "rec_" + p.goalName;
        this.recoveryPlans.set(rId, {
          id: rId,
          type: "recovery",
          goalName: p.goalName,
          trigger: p.trigger,
          context: p.context || "",
          bodyText: p.bodyText || "",
          actions: p.actions || []
        });
      } else {
        if (!goalMap.has(p.goalName)) {
          goalMap.set(p.goalName, []);
        }
        goalMap.get(p.goalName).push(p);

        this.plans.set(p.id, {
          id: p.id,
          type: "plan",
          goalName: p.goalName,
          goalArgs: p.goalArgs || "",
          trigger: p.trigger,
          context: p.context || "",
          bodyText: p.bodyText || "",
          actions: p.actions || [],
          subgoals: p.subgoals || []
        });
      }
    }

    // Create Goal Nodes
    for (let [goalName, planList] of goalMap.entries()) {
      let gId = "goal_" + goalName;
      let allSubgoals = [];
      let isOr = planList.length > 1;

      for (let pl of planList) {
        if (pl.subgoals) {
          allSubgoals.push(...pl.subgoals);
        }
      }

      this.goals.set(gId, {
        id: gId,
        type: "goal",
        name: goalName,
        desc: this.generateGoalDesc(goalName),
        decompType: isOr ? "OR" : "AND",
        subgoals: [...new Set(allSubgoals)],
        planIds: planList.map(p => p.id),
        isRoot: goalName === this.initialGoal
      });
    }

    // Ensure root goal exists
    if (this.initialGoal && !this.goals.has("goal_" + this.initialGoal)) {
      let rId = "goal_" + this.initialGoal;
      this.goals.set(rId, {
        id: rId,
        type: "goal",
        name: this.initialGoal,
        desc: "Root Objective",
        decompType: "AND",
        subgoals: [],
        planIds: [],
        isRoot: true
      });
    }

    // Ensure all subgoals referenced in actions exist as Goal Nodes
    for (let [gId, gNode] of this.goals.entries()) {
      for (let subName of gNode.subgoals) {
        let subGId = "goal_" + subName;
        if (!this.goals.has(subGId)) {
          this.goals.set(subGId, {
            id: subGId,
            type: "goal",
            name: subName,
            desc: this.generateGoalDesc(subName),
            decompType: "AND",
            subgoals: [],
            planIds: [],
            isRoot: false
          });
        }
      }
    }

    // Build default Failure & Recovery mappings matching Belief rules and Recovery plans
    this.autoDeriveFailureMappings();
    this.computeLayout();
    this.notify();
  }

  generateGoalDesc(name) {
    const descMap = {
      "deliver_success": "G0: Hoàn tất quy trình giao hàng tự hành",
      "setup_workspace": "Thiết lập kết nối CArtAgO & MoISE",
      "pickup_package": "G1: Nhận & Lấy hàng tại kho",
      "move_to_pickup": "G1.1: Di chuyển đến điểm lấy hàng",
      "load_and_verify_package": "G1.2: Bốc xếp và kiểm tra hợp lệ",
      "transit_to_delivery": "G2: Di chuyển đến điểm giao hàng",
      "plan_global_path": "G2.1: Lập lộ trình toàn cục",
      "local_navigation": "G2.2: Tránh vật cản & di chuyển",
      "handover_package": "G3: Bàn giao hàng cho người nhận",
      "authenticate_receiver": "G3.1: Xác thực OTP / QR / FaceID",
      "unlock_and_release": "G3.2: Mở khoang hàng & giao khách",
      "complete_cycle": "G4: Kết thúc lượt (Sạc / Nhận đơn / Nghỉ)"
    };
    if (descMap[name]) return descMap[name];

    return name
      .replace(/^!/, "")
      .replace(/_/g, " ")
      .replace(/\b\w/g, l => l.toUpperCase());
  }

  /**
   * Automatically pairs Belief rules to Goals & Recovery Plans
   */
  autoDeriveFailureMappings() {
    this.failureMappings = [];
    const recList = Array.from(this.recoveryPlans.values());
    const beliefList = Array.from(this.beliefRules.values());
    const goalsList = Array.from(this.goals.values());

    for (let b of beliefList) {
      // Find matching recovery plan
      let matchedRec = recList.find(r => 
        r.goalName.toLowerCase().includes(b.head.toLowerCase().split("_")[0]) ||
        b.head.toLowerCase().includes(r.goalName.toLowerCase().split("_")[0])
      );

      if (!matchedRec && recList.length > 0) {
        matchedRec = recList[0];
      }

      // Find matching goal
      let matchedGoal = goalsList.find(g => 
        g.name.toLowerCase().includes(b.head.toLowerCase().split("_")[0])
      );
      if (!matchedGoal) {
        matchedGoal = goalsList.find(g => g.name !== this.initialGoal) || goalsList[0];
      }

      if (matchedRec && matchedGoal) {
        this.failureMappings.push({
          targetGoal: matchedGoal.name,
          errorName: b.head + "_error",
          beliefCondition: b.head,
          recoveryPlan: matchedRec.goalName,
          adaptationType: "Goal & Env Adaptation",
          envAdaptation: "navigation.reroute",
          orgAdaptation: "my_team.notify_status"
        });
      }
    }
  }

  /**
   * Health & Consistency Validator
   */
  validate() {
    const issues = [];
    let hasRoot = false;

    for (let [id, g] of this.goals.entries()) {
      if (g.isRoot || g.name === this.initialGoal) hasRoot = true;

      // Check if goal has plans
      const plans = Array.from(this.plans.values()).filter(p => p.goalName === g.name);
      if (plans.length === 0 && !this.recoveryPlans.has("rec_" + g.name)) {
        issues.push({ type: "warn", msg: `Goal [!${g.name}] chưa có kế hoạch (+!${g.name}) cụ thể.` });
      }
    }

    if (!hasRoot) {
      issues.push({ type: "error", msg: `Chưa có Root Goal nào được thiết lập (ví dụ !${this.initialGoal}).` });
    }

    if (this.recoveryPlans.size === 0) {
      issues.push({ type: "warn", msg: "Chưa định nghĩa Recovery Plan nào cho hệ thống tự phục hồi." });
    }

    return {
      isValid: issues.filter(i => i.type === "error").length === 0,
      issues
    };
  }

  /**
   * Calculates 2D Coordinates for all Nodes (Hierarchical Dagre-like Layout)
   */
  computeLayout(onlyGoals = false) {
    const rootGoalNode = Array.from(this.goals.values()).find(g => g.isRoot || g.name === this.initialGoal) || Array.from(this.goals.values())[0];
    if (!rootGoalNode) return;

    const layers = [];
    const visited = new Set();

    // BFS Hierarchy Layering
    let queue = [{ node: rootGoalNode, depth: 0 }];
    visited.add(rootGoalNode.name);

    while (queue.length > 0) {
      let { node, depth } = queue.shift();
      if (!layers[depth]) layers[depth] = [];
      layers[depth].push(node);

      for (let subName of node.subgoals) {
        let subG = Array.from(this.goals.values()).find(g => g.name === subName);
        if (subG && !visited.has(subG.name)) {
          visited.add(subG.name);
          queue.push({ node: subG, depth: depth + 1 });
        }
      }
    }

    // Add unvisited goals to last layer
    for (let g of this.goals.values()) {
      if (!visited.has(g.name)) {
        let d = 1;
        if (!layers[d]) layers[d] = [];
        layers[d].push(g);
      }
    }

    const LEVEL_HEIGHT = 160;
    const NODE_WIDTH = 230;
    const NODE_GAP_X = 40;

    // Position Goal Nodes neatly centered per level
    let maxNodesInLayer = Math.max(...layers.map(l => l.length), 1);
    let canvasMaxWidth = Math.max(1200, maxNodesInLayer * (NODE_WIDTH + NODE_GAP_X) + 120);

    for (let d = 0; d < layers.length; d++) {
      let nodesInLayer = layers[d];
      let totalWidth = nodesInLayer.length * NODE_WIDTH + (nodesInLayer.length - 1) * NODE_GAP_X;
      let startX = Math.max(60, (canvasMaxWidth - totalWidth) / 2);

      for (let i = 0; i < nodesInLayer.length; i++) {
        let n = nodesInLayer[i];
        n.x = startX + i * (NODE_WIDTH + NODE_GAP_X);
        n.y = 60 + d * LEVEL_HEIGHT;
        n.width = NODE_WIDTH;
        n.height = 68;
      }
    }

    // Position Alternative Plans below corresponding Goals
    for (let [pId, p] of this.plans.entries()) {
      let g = Array.from(this.goals.values()).find(g => g.name === p.goalName);
      if (g) {
        let siblings = Array.from(this.plans.values()).filter(x => x.goalName === p.goalName);
        let sIdx = siblings.indexOf(p);
        let pWidth = 190;
        let pTotalW = siblings.length * pWidth + (siblings.length - 1) * 20;
        let pStartX = g.x + (g.width - pTotalW) / 2;

        p.x = pStartX + sIdx * (pWidth + 20);
        p.y = g.y + g.height + 25;
        p.width = pWidth;
        p.height = 58;
      } else {
        p.x = 100;
        p.y = 500;
        p.width = 180;
        p.height = 55;
      }
    }

    // Position Recovery Plans on the Right side
    let recIdx = 0;
    for (let [rId, r] of this.recoveryPlans.entries()) {
      r.x = canvasMaxWidth + 40;
      r.y = 60 + recIdx * 85;
      r.width = 200;
      r.height = 65;
      recIdx++;
    }

    // Position Belief Rules on the far Left
    let bIdx = 0;
    for (let [bId, b] of this.beliefRules.entries()) {
      b.x = 40;
      b.y = 60 + bIdx * 80;
      b.width = 210;
      b.height = 60;
      bIdx++;
    }
  }

  /**
   * Adds a new Goal node
   */
  addGoal(name, desc = "", decompType = "AND", parentGoalName = null) {
    let cleanName = name.replace(/[^a-zA-Z0-9_]/g, "");
    let id = "goal_" + cleanName;
    if (this.goals.has(id)) {
      cleanName = cleanName + "_" + Math.floor(Math.random() * 100);
      id = "goal_" + cleanName;
    }

    this.goals.set(id, {
      id,
      type: "goal",
      name: cleanName,
      desc: desc || this.generateGoalDesc(cleanName),
      decompType,
      subgoals: [],
      planIds: [],
      isRoot: false
    });

    if (parentGoalName) {
      let parent = Array.from(this.goals.values()).find(g => g.name === parentGoalName);
      if (parent) {
        if (!parent.subgoals.includes(cleanName)) {
          parent.subgoals.push(cleanName);
        }
      }
    }

    this.computeLayout();
    this.notify();
    return id;
  }

  /**
   * Adds a new Plan node
   */
  addPlan(goalName, context = "", body = "") {
    let id = "plan_" + Math.random().toString(36).substr(2, 8);
    this.plans.set(id, {
      id,
      type: "plan",
      goalName,
      trigger: "+!" + goalName,
      context,
      bodyText: body || `.print("Running ${goalName}").`,
      actions: [body || `.print("Running ${goalName}").`],
      subgoals: []
    });

    this.computeLayout();
    this.notify();
    return id;
  }

  /**
   * Adds a Recovery Plan node
   */
  addRecoveryPlan(goalName, errorName, beliefCondition) {
    let id = "rec_" + goalName;
    this.recoveryPlans.set(id, {
      id,
      type: "recovery",
      goalName,
      trigger: "+!" + goalName,
      context: "",
      bodyText: `.print("Handling recovery: ${errorName}");\n+recovery_handled.`,
      actions: [`.print("Handling recovery: ${errorName}")`, `+recovery_handled`]
    });

    this.failureMappings.push({
      targetGoal: this.initialGoal,
      errorName: errorName || goalName + "_err",
      beliefCondition: beliefCondition || "failure_detected",
      recoveryPlan: goalName,
      adaptationType: "Goal Adaptation",
      envAdaptation: "",
      orgAdaptation: ""
    });

    this.computeLayout();
    this.notify();
    return id;
  }

  /**
   * Adds a Belief Rule
   */
  addBeliefRule(head, body) {
    let id = "belief_" + head.replace(/[^a-zA-Z0-9_]/g, "");
    this.beliefRules.set(id, {
      id,
      head,
      body,
      conditions: body.split("&").map(s => s.trim())
    });

    this.computeLayout();
    this.notify();
    return id;
  }

  /**
   * Deletes a node by ID
   */
  deleteNode(id) {
    if (this.goals.has(id)) {
      let g = this.goals.get(id);
      this.goals.delete(id);
      // Remove from parents' subgoals
      for (let otherG of this.goals.values()) {
        otherG.subgoals = otherG.subgoals.filter(s => s !== g.name);
      }
      // Remove associated plans
      for (let [pId, p] of this.plans.entries()) {
        if (p.goalName === g.name) this.plans.delete(pId);
      }
    } else if (this.plans.has(id)) {
      this.plans.delete(id);
    } else if (this.recoveryPlans.has(id)) {
      this.recoveryPlans.delete(id);
    } else if (this.beliefRules.has(id)) {
      this.beliefRules.delete(id);
    }

    this.computeLayout();
    this.notify();
  }
}
