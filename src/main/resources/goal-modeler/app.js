/**
 * JaCaMo Goal Studio - Main Application Controller
 */

document.addEventListener("DOMContentLoaded", () => {
  // 1. Instantiate Core Subsystems
  const model = new GoalModel();
  const canvas = new GoalCanvas("goalCanvas", "canvasTransformGroup", model);
  const simulator = new GoalSimulator(model);

  // Global State
  let currentSelectedNode = null;
  let importedAgentText = null;
  let importedBeliefText = null;

  // 2. UI Elements
  const agentNameInput = document.getElementById("agentNameInput");
  const modelStats = document.getElementById("modelStats");
  const agentFileNameLabel = document.getElementById("agentFileNameLabel");
  const agentEditorFileName = document.getElementById("agentEditorFileName");

  // Code Areas
  const agentAslCodeArea = document.getElementById("agentAslCodeArea");
  const beliefAslCodeArea = document.getElementById("beliefAslCodeArea");
  const jcmConfigPreview = document.getElementById("jcmConfigPreview");

  // Inspector Elements
  const inspectorForm = document.getElementById("inspectorForm");
  const inspectorEmptyState = document.getElementById("inspectorEmptyState");
  const inspectorTitle = document.getElementById("inspectorTitle");
  const selectedNodeTypeBadge = document.getElementById("selectedNodeTypeBadge");

  // Form Fields
  const propGoalName = document.getElementById("propGoalName");
  const propGoalDesc = document.getElementById("propGoalDesc");
  const propDecompType = document.getElementById("propDecompType");
  const propPlanTrigger = document.getElementById("propPlanTrigger");
  const propPlanContext = document.getElementById("propPlanContext");
  const propPlanBody = document.getElementById("propPlanBody");
  const propRecoveryTargetGoal = document.getElementById("propRecoveryTargetGoal");
  const propRecoveryErrorName = document.getElementById("propRecoveryErrorName");
  const propRecoveryConditions = document.getElementById("propRecoveryConditions");
  const propBeliefHead = document.getElementById("propBeliefHead");
  const propBeliefBody = document.getElementById("propBeliefBody");

  // Load Initial Sample (Delivery Truck MAS Demo)
  loadSample("delivery_truck");

  // 3. Model Change Listener (Auto Sync & Refresh)
  model.onChange(() => {
    updateStats();
    renderTreeExplorer();
    updateHealthCheck();
    renderRecoveryMatrix();
    syncCodeEditors();
    canvas.render();
  });

  // 4. Sample Loader
  function loadSample(sampleKey) {
    const sample = SAMPLES[sampleKey];
    if (!sample) return;

    agentNameInput.value = sample.agentName;
    agentFileNameLabel.innerText = sample.agentFileName;
    agentEditorFileName.innerText = sample.agentFileName;

    const agentAst = AslParser.parseAgentAsl(sample.agentAsl);
    const beliefAst = AslParser.parseBeliefAsl(sample.beliefAsl);

    model.loadFromParsed(agentAst, beliefAst, sample.agentName);
    simulator.init();
    canvas.zoomFit();
    showToast(`Đã tải mẫu "${sample.agentName}" thành công!`, "success");
  }

  // 5. Update Statistics and Labels
  function updateStats() {
    const goalCount = model.goals.size;
    const planCount = model.plans.size;
    const recCount = model.recoveryPlans.size;
    const beliefCount = model.beliefRules.size;
    modelStats.innerText = `Goals: ${goalCount} | Plans: ${planCount} | Recovery: ${recCount} | Beliefs: ${beliefCount}`;
  }

  // 6. Sync Code Editors
  function syncCodeEditors() {
    const agentCode = AslParser.generateAgentAsl(
      model.agentName,
      model.initialGoal,
      model.initialBeliefs,
      Array.from(model.goals.values()),
      Array.from(model.plans.values()),
      Array.from(model.recoveryPlans.values())
    );

    const beliefCode = AslParser.generateBeliefAsl(Array.from(model.beliefRules.values()));
    const jcmCode = AslParser.generateJcmFailureConfig(model.agentName, model.failureMappings);

    if (agentAslCodeArea) agentAslCodeArea.value = agentCode;
    if (beliefAslCodeArea) beliefAslCodeArea.value = beliefCode;
    if (jcmConfigPreview) jcmConfigPreview.textContent = jcmCode;
  }

  // 7. Render Tree Explorer in Left Sidebar
  function renderTreeExplorer() {
    const treeContainer = document.getElementById("goalTreeExplorer");
    if (!treeContainer) return;
    treeContainer.innerHTML = "";

    const rootGoal = Array.from(model.goals.values()).find(g => g.isRoot || g.name === model.initialGoal) || Array.from(model.goals.values())[0];
    if (!rootGoal) return;

    function renderNode(g, depth = 0) {
      const item = document.createElement("div");
      item.className = `tree-node-item tree-indent-${Math.min(depth, 3)} ${currentSelectedNode && currentSelectedNode.id === g.id ? "active" : ""}`;
      item.innerHTML = `<span>${depth === 0 ? "👑" : "🎯"}</span> <span>!${g.name}</span> <small style="color:var(--text-muted)">[${g.decompType}]</small>`;
      item.addEventListener("click", () => {
        canvas.selectNode(g.id, g);
      });
      treeContainer.appendChild(item);

      for (let subName of g.subgoals) {
        let subG = Array.from(model.goals.values()).find(x => x.name === subName);
        if (subG) {
          renderNode(subG, depth + 1);
        }
      }
    }

    renderNode(rootGoal, 0);
  }

  // 8. Health Check Badge & List
  function updateHealthCheck() {
    const val = model.validate();
    const badge = document.getElementById("healthBadge");
    const list = document.getElementById("healthList");
    if (!badge || !list) return;

    list.innerHTML = "";
    if (val.isValid && val.issues.length === 0) {
      badge.className = "health-status-badge ok";
      badge.innerText = "Hợp lệ (100%)";
      list.innerHTML = `
        <div class="health-item ok">✓ Đã thiết lập Root Goal (!${model.initialGoal})</div>
        <div class="health-item ok">✓ Tất cả Subgoals có Plan tương ứng</div>
        <div class="health-item ok">✓ Đã cấu hình ${model.recoveryPlans.size} Recovery Plans</div>
      `;
    } else {
      badge.className = "health-status-badge warn";
      badge.innerText = `${val.issues.length} Cảnh báo`;
      for (let iss of val.issues) {
        const item = document.createElement("div");
        item.className = `health-item ${iss.type}`;
        item.innerText = (iss.type === "error" ? "✕ " : "⚠ ") + iss.msg;
        list.appendChild(item);
      }
    }
  }

  // 9. Failure & Recovery Matrix View Table
  function renderRecoveryMatrix() {
    const tbody = document.getElementById("recoveryMatrixTableBody");
    if (!tbody) return;
    tbody.innerHTML = "";

    for (let i = 0; i < model.failureMappings.length; i++) {
      let m = model.failureMappings[i];
      const tr = document.createElement("tr");

      // Find matching belief rule body
      let matchingRule = Array.from(model.beliefRules.values()).find(r => r.head === m.beliefCondition);
      let ruleExpr = matchingRule ? `${matchingRule.head} :- ${matchingRule.body}` : m.beliefCondition;

      tr.innerHTML = `
        <td><span class="code-badge">!${m.targetGoal}</span></td>
        <td><strong>${m.errorName}</strong></td>
        <td><span class="code-badge" style="color:var(--accent-rose)">${m.beliefCondition}</span></td>
        <td><small class="code-font" style="color:var(--accent-purple)">${ruleExpr}</small></td>
        <td><span class="code-badge" style="color:var(--accent-emerald)">+!${m.recoveryPlan}</span></td>
        <td><span class="badge-hint">${m.adaptationType || "Goal Adaptation"}</span></td>
        <td>
          <button class="btn-icon btn-sm btn-delete-row" data-index="${i}" title="Xóa">🗑️</button>
        </td>
      `;
      tbody.appendChild(tr);
    }

    tbody.querySelectorAll(".btn-delete-row").forEach(btn => {
      btn.addEventListener("click", (e) => {
        let idx = parseInt(btn.getAttribute("data-index"));
        model.failureMappings.splice(idx, 1);
        model.notify();
        showToast("Đã xóa ánh xạ lỗi!", "info");
      });
    });
  }

  // 10. Node Selection & Property Inspector Binding
  canvas.onSelectNode((nodeData) => {
    currentSelectedNode = nodeData;

    if (!nodeData) {
      inspectorForm.classList.add("hidden");
      inspectorEmptyState.classList.remove("hidden");
      selectedNodeTypeBadge.innerText = "Không chọn";
      inspectorTitle.innerText = "Chi Tiết Phần Tử";
      return;
    }

    inspectorEmptyState.classList.add("hidden");
    inspectorForm.classList.remove("hidden");

    // Toggle form field visibility based on node type
    document.querySelectorAll(".goal-only, .plan-only, .recovery-only, .belief-rule-only").forEach(el => {
      el.classList.add("hidden");
    });

    if (nodeData.type === "goal") {
      selectedNodeTypeBadge.innerText = "Goal Node";
      selectedNodeTypeBadge.style.color = "var(--accent-cyan)";
      inspectorTitle.innerText = `Goal: !${nodeData.name}`;
      document.querySelectorAll(".goal-only").forEach(el => el.classList.remove("hidden"));

      propGoalName.value = nodeData.name;
      propGoalDesc.value = nodeData.desc || "";
      propDecompType.value = nodeData.decompType || "AND";
    } else if (nodeData.type === "plan") {
      selectedNodeTypeBadge.innerText = "Plan Node";
      selectedNodeTypeBadge.style.color = "var(--accent-emerald)";
      inspectorTitle.innerText = `Plan: +!${nodeData.goalName}`;
      document.querySelectorAll(".plan-only").forEach(el => el.classList.remove("hidden"));

      propPlanTrigger.value = nodeData.goalName;
      propPlanContext.value = nodeData.context || "";
      propPlanBody.value = nodeData.bodyText || (nodeData.actions ? nodeData.actions.join(";\n") : "");
    } else if (nodeData.type === "recovery") {
      selectedNodeTypeBadge.innerText = "Recovery Plan";
      selectedNodeTypeBadge.style.color = "var(--accent-rose)";
      inspectorTitle.innerText = `Recovery: +!${nodeData.goalName}`;
      document.querySelectorAll(".recovery-only").forEach(el => el.classList.remove("hidden"));

      let mapping = model.failureMappings.find(m => m.recoveryPlan === nodeData.goalName);
      propPlanTrigger.value = nodeData.goalName;
      propRecoveryTargetGoal.value = mapping ? mapping.targetGoal : model.initialGoal;
      propRecoveryErrorName.value = mapping ? mapping.errorName : nodeData.goalName + "_err";
      propRecoveryConditions.value = mapping ? mapping.beliefCondition : "failure_condition";
      propPlanBody.value = nodeData.bodyText || "";
    } else if (nodeData.head !== undefined) {
      // Belief Rule
      selectedNodeTypeBadge.innerText = "Belief Rule";
      selectedNodeTypeBadge.style.color = "var(--accent-purple)";
      inspectorTitle.innerText = `Rule: ${nodeData.head}`;
      document.querySelectorAll(".belief-rule-only").forEach(el => el.classList.remove("hidden"));

      propBeliefHead.value = nodeData.head;
      propBeliefBody.value = nodeData.body || "";
    }
  });

  // Apply Changes from Inspector Form
  document.getElementById("btnApplyProperties").addEventListener("click", () => {
    if (!currentSelectedNode) return;

    if (currentSelectedNode.type === "goal") {
      let oldName = currentSelectedNode.name;
      let newName = propGoalName.value.trim().replace(/[^a-zA-Z0-9_]/g, "");
      currentSelectedNode.name = newName;
      currentSelectedNode.desc = propGoalDesc.value.trim();
      currentSelectedNode.decompType = propDecompType.value;

      if (oldName !== newName) {
        // Update references in plans and other goals
        for (let g of model.goals.values()) {
          g.subgoals = g.subgoals.map(s => s === oldName ? newName : s);
        }
        for (let p of model.plans.values()) {
          if (p.goalName === oldName) p.goalName = newName;
        }
        if (model.initialGoal === oldName) {
          model.initialGoal = newName;
        }
      }
    } else if (currentSelectedNode.type === "plan") {
      currentSelectedNode.goalName = propPlanTrigger.value.trim();
      currentSelectedNode.context = propPlanContext.value.trim();
      currentSelectedNode.bodyText = propPlanBody.value.trim();
      currentSelectedNode.actions = propPlanBody.value.split(";").map(s => s.trim()).filter(Boolean);
    } else if (currentSelectedNode.type === "recovery") {
      currentSelectedNode.goalName = propPlanTrigger.value.trim();
      currentSelectedNode.bodyText = propPlanBody.value.trim();
      currentSelectedNode.actions = propPlanBody.value.split(";").map(s => s.trim()).filter(Boolean);

      // Update mapping
      let mapping = model.failureMappings.find(m => m.recoveryPlan === currentSelectedNode.goalName);
      if (mapping) {
        mapping.targetGoal = propRecoveryTargetGoal.value.trim();
        mapping.errorName = propRecoveryErrorName.value.trim();
        mapping.beliefCondition = propRecoveryConditions.value.trim();
      }
    } else if (currentSelectedNode.head !== undefined) {
      currentSelectedNode.head = propBeliefHead.value.trim();
      currentSelectedNode.body = propBeliefBody.value.trim();
      currentSelectedNode.conditions = propBeliefBody.value.split("&").map(s => s.trim());
    }

    model.computeLayout();
    model.notify();
    showToast("Đã cập nhật phần tử thành công!", "success");
  });

  // Delete Node
  document.getElementById("btnDeleteNode").addEventListener("click", () => {
    if (!currentSelectedNode) return;
    if (confirm(`Bạn có chắc chắn muốn xóa phần tử này khỏi Goal Model?`)) {
      model.deleteNode(currentSelectedNode.id);
      canvas.deselect();
      showToast("Đã xóa phần tử.", "info");
    }
  });

  // 11. View Mode Tab Switching
  const tabs = document.querySelectorAll(".view-tab");
  const viewContainers = document.querySelectorAll(".view-container");
  const canvasToolbar = document.getElementById("canvasToolbar");

  tabs.forEach(tab => {
    tab.addEventListener("click", () => {
      tabs.forEach(t => t.classList.remove("active"));
      viewContainers.forEach(c => c.classList.remove("active"));

      tab.classList.add("active");
      const targetView = tab.getAttribute("data-view");
      const activeContainer = document.getElementById(targetView + "View");
      if (activeContainer) activeContainer.classList.add("active");

      if (targetView === "canvas") {
        canvasToolbar.style.display = "flex";
        canvas.render();
      } else {
        canvasToolbar.style.display = "none";
      }

      if (targetView === "code") {
        syncCodeEditors();
      } else if (targetView === "simulator") {
        simulator.init();
      }
    });
  });

  // 12. Dropdown Menus Toggling
  document.querySelectorAll(".dropdown-toggle").forEach(btn => {
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      const parent = btn.closest(".dropdown");
      document.querySelectorAll(".dropdown").forEach(d => {
        if (d !== parent) d.classList.remove("active");
      });
      parent.classList.toggle("active");
    });
  });

  window.addEventListener("click", () => {
    document.querySelectorAll(".dropdown").forEach(d => d.classList.remove("active"));
  });

  // Sample Selection
  document.querySelectorAll("#sampleDropdownMenu .dropdown-item").forEach(item => {
    item.addEventListener("click", (e) => {
      e.preventDefault();
      document.querySelectorAll("#sampleDropdownMenu .dropdown-item").forEach(x => x.classList.remove("active"));
      item.classList.add("active");
      const sampleKey = item.getAttribute("data-sample");
      loadSample(sampleKey);
    });
  });

  // 13. Canvas Zoom & Layout Controls
  document.getElementById("btnZoomIn").addEventListener("click", () => canvas.zoomIn());
  document.getElementById("btnZoomOut").addEventListener("click", () => canvas.zoomOut());
  document.getElementById("btnZoomFit").addEventListener("click", () => canvas.zoomFit());
  document.getElementById("btnAutoLayout").addEventListener("click", () => {
    model.computeLayout();
    canvas.render();
    canvas.zoomFit();
    showToast("Đã tự động sắp xếp lại cây mục tiêu Goal!", "success");
  });

  // 14. Dual File Import Modal Handling
  const importModal = document.getElementById("importModal");
  const btnOpenImport = document.getElementById("btnOpenImport");
  const btnCloseImportModal = document.getElementById("btnCloseImportModal");
  const btnCancelImport = document.getElementById("btnCancelImport");
  const btnConfirmImport = document.getElementById("btnConfirmImport");

  const dropzoneAgent = document.getElementById("dropzoneAgent");
  const dropzoneBelief = document.getElementById("dropzoneBelief");
  const fileInputAgent = document.getElementById("fileInputAgent");
  const fileInputBelief = document.getElementById("fileInputBelief");
  const agentDropStatus = document.getElementById("agentDropStatus");
  const beliefDropStatus = document.getElementById("beliefDropStatus");

  btnOpenImport.addEventListener("click", () => {
    importModal.classList.add("active");
  });

  function closeImport() {
    importModal.classList.remove("active");
  }
  btnCloseImportModal.addEventListener("click", closeImport);
  btnCancelImport.addEventListener("click", closeImport);

  // Drag and Drop for Agent File
  setupDropzone(dropzoneAgent, fileInputAgent, (content, name) => {
    importedAgentText = content;
    agentDropStatus.innerText = `✓ Đã chọn: ${name}`;
    dropzoneAgent.classList.add("has-file");
  });

  // Drag and Drop for Belief File
  setupDropzone(dropzoneBelief, fileInputBelief, (content, name) => {
    importedBeliefText = content;
    beliefDropStatus.innerText = `✓ Đã chọn: ${name}`;
    dropzoneBelief.classList.add("has-file");
  });

  function setupDropzone(dropzone, fileInput, onLoaded) {
    dropzone.addEventListener("click", () => fileInput.click());
    fileInput.addEventListener("change", (e) => {
      if (e.target.files && e.target.files[0]) {
        readFile(e.target.files[0], onLoaded);
      }
    });

    dropzone.addEventListener("dragover", (e) => {
      e.preventDefault();
      dropzone.classList.add("dragover");
    });
    dropzone.addEventListener("dragleave", () => {
      dropzone.classList.remove("dragover");
    });
    dropzone.addEventListener("drop", (e) => {
      e.preventDefault();
      dropzone.classList.remove("dragover");
      if (e.dataTransfer.files && e.dataTransfer.files[0]) {
        readFile(e.dataTransfer.files[0], onLoaded);
      }
    });
  }

  function readFile(file, callback) {
    const reader = new FileReader();
    reader.onload = (e) => {
      callback(e.target.result, file.name);
    };
    reader.readAsText(file);
  }

  // Confirm Import
  btnConfirmImport.addEventListener("click", () => {
    let agentCode = importedAgentText || document.getElementById("pasteAgentText").value;
    let beliefCode = importedBeliefText || document.getElementById("pasteBeliefText").value;

    if (!agentCode && !beliefCode) {
      alert("Vui lòng tải lên hoặc dán nội dung ít nhất một file .asl!");
      return;
    }

    agentCode = agentCode || SAMPLES.empty_template.agentAsl;
    beliefCode = beliefCode || SAMPLES.empty_template.beliefAsl;

    const agentAst = AslParser.parseAgentAsl(agentCode);
    const beliefAst = AslParser.parseBeliefAsl(beliefCode);

    model.loadFromParsed(agentAst, beliefAst, agentNameInput.value.trim() || "imported_agent");
    simulator.init();
    closeImport();
    canvas.zoomFit();
    showToast("Import và khởi tạo Goal Model thành công!", "success");
  });

  // 15. Dual Code Sync Buttons
  document.getElementById("btnSyncFromAgentCode").addEventListener("click", () => {
    const rawCode = agentAslCodeArea.value;
    const ast = AslParser.parseAgentAsl(rawCode);
    const bAst = Array.from(model.beliefRules.values());
    model.loadFromParsed(ast, bAst, model.agentName);
    showToast("Đã đồng bộ hóa thay đổi từ mã Agent ASL lên sơ đồ!", "success");
  });

  document.getElementById("btnSyncFromBeliefCode").addEventListener("click", () => {
    const rawCode = beliefAslCodeArea.value;
    const bAst = AslParser.parseBeliefAsl(rawCode);
    model.beliefRules.clear();
    for (let r of bAst) {
      let id = "belief_" + (r.head.replace(/[^a-zA-Z0-9_]/g, "") || Math.random().toString(36).substr(2, 5));
      model.beliefRules.set(id, { id, head: r.head, body: r.body, conditions: r.conditions });
    }
    model.computeLayout();
    model.notify();
    showToast("Đã cập nhật các luật suy diễn belief.asl!", "success");
  });

  // Copy Code Buttons
  document.getElementById("btnCopyAgentCode").addEventListener("click", () => {
    navigator.clipboard.writeText(agentAslCodeArea.value);
    showToast("Đã sao chép mã Agent ASL vào clipboard!", "info");
  });
  document.getElementById("btnCopyBeliefCode").addEventListener("click", () => {
    navigator.clipboard.writeText(beliefAslCodeArea.value);
    showToast("Đã sao chép mã belief.asl vào clipboard!", "info");
  });
  document.getElementById("btnCopyJcm").addEventListener("click", () => {
    navigator.clipboard.writeText(jcmConfigPreview.textContent);
    showToast("Đã sao chép khối cấu hình Failure Model .jcm!", "info");
  });

  // 16. Export Actions
  document.getElementById("btnExportAgentAsl").addEventListener("click", () => {
    const content = AslParser.generateAgentAsl(
      model.agentName,
      model.initialGoal,
      model.initialBeliefs,
      Array.from(model.goals.values()),
      Array.from(model.plans.values()),
      Array.from(model.recoveryPlans.values())
    );
    downloadFile(content, `${model.agentName}.asl`, "text/plain");
  });

  document.getElementById("btnExportBeliefAsl").addEventListener("click", () => {
    const content = AslParser.generateBeliefAsl(Array.from(model.beliefRules.values()));
    downloadFile(content, "belief.asl", "text/plain");
  });

  document.getElementById("btnExportBothZip").addEventListener("click", () => {
    // Download both files consecutively
    document.getElementById("btnExportAgentAsl").click();
    setTimeout(() => {
      document.getElementById("btnExportBeliefAsl").click();
    }, 400);
  });

  document.getElementById("btnExportJcmConfig").addEventListener("click", () => {
    const content = AslParser.generateJcmFailureConfig(model.agentName, model.failureMappings);
    downloadFile(content, `${model.agentName}_failure_model.jcm`, "text/plain");
  });

  document.getElementById("btnExportJson").addEventListener("click", () => {
    const data = {
      agentName: model.agentName,
      initialGoal: model.initialGoal,
      initialBeliefs: model.initialBeliefs,
      goals: Array.from(model.goals.values()),
      plans: Array.from(model.plans.values()),
      recoveryPlans: Array.from(model.recoveryPlans.values()),
      beliefRules: Array.from(model.beliefRules.values()),
      failureMappings: model.failureMappings
    };
    downloadFile(JSON.stringify(data, null, 2), `${model.agentName}_goal_model.json`, "application/json");
  });

  document.getElementById("btnExportSvg").addEventListener("click", () => {
    const svgEl = document.getElementById("goalCanvas");
    const serializer = new XMLSerializer();
    const source = serializer.serializeToString(svgEl);
    downloadFile(source, `${model.agentName}_goal_diagram.svg`, "image/svg+xml");
  });

  function downloadFile(content, filename, type) {
    const blob = new Blob([content], { type });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    showToast(`Đã xuất file: ${filename}`, "success");
  }

  // 17. Simulator Buttons Binding
  document.getElementById("btnSimStep").addEventListener("click", () => simulator.step());
  document.getElementById("btnSimReset").addEventListener("click", () => simulator.reset());
  document.getElementById("btnSimAutoPlay").addEventListener("click", () => simulator.toggleAutoPlay());
  document.getElementById("btnClearSimLog").addEventListener("click", () => {
    document.getElementById("simConsole").innerHTML = "";
  });

  // 18. Toast Notification Utility
  function showToast(message, type = "info") {
    const container = document.getElementById("toastContainer");
    if (!container) return;

    const toast = document.createElement("div");
    toast.className = `toast ${type}`;
    toast.innerHTML = `<span>${type === "success" ? "✓" : (type === "error" ? "✕" : "ℹ")}</span> <span>${message}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = "0";
      toast.style.transform = "translateY(10px)";
      toast.style.transition = "all 0.3s ease";
      setTimeout(() => toast.remove(), 300);
    }, 3000);
  }
});
