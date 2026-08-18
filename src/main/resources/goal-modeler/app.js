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

  // Load Initial Sample (Delivery Truck MAS Demo)
  loadSample("delivery_truck");

  // 3. Model Change Listener (Auto Sync & Refresh)
  model.onChange(() => {
    updateStats();
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
    let totalSubgoals = 0;
    for (let g of model.goals.values()) {
      totalSubgoals += g.subgoals.length;
    }
    modelStats.innerText = `Goals: ${goalCount} | Decompositions: ${totalSubgoals}`;
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

  // 7. Failure & Recovery Matrix View Table
  function renderRecoveryMatrix() {
    const tbody = document.getElementById("recoveryMatrixTableBody");
    if (!tbody) return;
    tbody.innerHTML = "";

    for (let i = 0; i < model.failureMappings.length; i++) {
      let m = model.failureMappings[i];
      const tr = document.createElement("tr");

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

  // 8. Node Selection & Inspector Modal on Node Click
  const modalGoalInspector = document.getElementById("modalGoalInspector");
  const modalInspectorTitle = document.getElementById("modalInspectorTitle");
  const modalGoalName = document.getElementById("modalGoalName");
  const modalGoalDesc = document.getElementById("modalGoalDesc");
  const modalGoalDecomp = document.getElementById("modalGoalDecomp");
  const modalSubgoalsList = document.getElementById("modalSubgoalsList");
  const btnCloseInspectorModal = document.getElementById("btnCloseInspectorModal");
  const btnCancelGoalEdit = document.getElementById("btnCancelGoalEdit");
  const btnSaveGoalEdit = document.getElementById("btnSaveGoalEdit");
  const btnDeleteSelectedGoal = document.getElementById("btnDeleteSelectedGoal");

  canvas.onSelectNode((nodeData) => {
    currentSelectedNode = nodeData;
    if (!nodeData) return;

    // Open Quick Goal Inspector Modal
    modalInspectorTitle.innerText = `Chỉnh Sửa Goal: !${nodeData.name}`;
    modalGoalName.value = nodeData.name;
    modalGoalDesc.value = nodeData.desc || "";
    modalGoalDecomp.value = nodeData.decompType || "AND";

    // Render Subgoals chips
    modalSubgoalsList.innerHTML = "";
    if (nodeData.subgoals && nodeData.subgoals.length > 0) {
      for (let subName of nodeData.subgoals) {
        const chip = document.createElement("span");
        chip.className = "subgoal-chip";
        chip.innerHTML = `!${subName} <span class="subgoal-chip-remove" data-sub="${subName}" title="Gỡ liên kết">✕</span>`;
        modalSubgoalsList.appendChild(chip);
      }

      modalSubgoalsList.querySelectorAll(".subgoal-chip-remove").forEach(rmBtn => {
        rmBtn.addEventListener("click", (e) => {
          let sName = rmBtn.getAttribute("data-sub");
          nodeData.subgoals = nodeData.subgoals.filter(s => s !== sName);
          rmBtn.parentElement.remove();
        });
      });
    } else {
      modalSubgoalsList.innerHTML = "<small style='color:var(--text-muted)'>Chưa có Sub-goal nào được phân rã</small>";
    }

    modalGoalInspector.classList.add("active");
  });

  function closeGoalInspector() {
    modalGoalInspector.classList.remove("active");
  }
  btnCloseInspectorModal.addEventListener("click", closeGoalInspector);
  btnCancelGoalEdit.addEventListener("click", closeGoalInspector);

  btnSaveGoalEdit.addEventListener("click", () => {
    if (!currentSelectedNode) return;

    let oldName = currentSelectedNode.name;
    let newName = modalGoalName.value.trim().replace(/[^a-zA-Z0-9_]/g, "");
    currentSelectedNode.name = newName;
    currentSelectedNode.desc = modalGoalDesc.value.trim();
    currentSelectedNode.decompType = modalGoalDecomp.value;

    if (oldName !== newName) {
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

    closeGoalInspector();
    model.computeLayout();
    model.notify();
    showToast("Đã lưu thay đổi Goal!", "success");
  });

  btnDeleteSelectedGoal.addEventListener("click", () => {
    if (!currentSelectedNode) return;
    if (confirm(`Bạn có chắc chắn muốn xóa Goal "!${currentSelectedNode.name}" khỏi sơ đồ?`)) {
      model.deleteNode(currentSelectedNode.id);
      closeGoalInspector();
      canvas.deselect();
      showToast("Đã xóa Goal.", "info");
    }
  });

  // 9. FAB (Floating Action Button) Speed Dial & Modals
  const canvasFabContainer = document.getElementById("canvasFabContainer");
  const fabMainBtn = document.getElementById("fabMainBtn");
  const btnFabAddGoal = document.getElementById("btnFabAddGoal");
  const btnFabAddLink = document.getElementById("btnFabAddLink");
  const btnFabAddSubgoal = document.getElementById("btnFabAddSubgoal");

  fabMainBtn.addEventListener("click", (e) => {
    e.stopPropagation();
    canvasFabContainer.classList.toggle("active");
  });

  window.addEventListener("click", (e) => {
    if (!canvasFabContainer.contains(e.target)) {
      canvasFabContainer.classList.remove("active");
    }
  });

  // Modal 1: Add Goal
  const modalAddGoal = document.getElementById("modalAddGoal");
  const btnCloseAddGoalModal = document.getElementById("btnCloseAddGoalModal");
  const btnCancelAddGoal = document.getElementById("btnCancelAddGoal");
  const btnConfirmAddGoal = document.getElementById("btnConfirmAddGoal");
  const newGoalNameInput = document.getElementById("newGoalNameInput");
  const newGoalDescInput = document.getElementById("newGoalDescInput");
  const newGoalDecompSelect = document.getElementById("newGoalDecompSelect");
  const newGoalParentSelect = document.getElementById("newGoalParentSelect");

  btnFabAddGoal.addEventListener("click", () => {
    canvasFabContainer.classList.remove("active");
    populateParentDropdown(newGoalParentSelect, null);
    newGoalNameInput.value = "";
    newGoalDescInput.value = "";
    modalAddGoal.classList.add("active");
  });

  function closeAddGoalModal() {
    modalAddGoal.classList.remove("active");
  }
  btnCloseAddGoalModal.addEventListener("click", closeAddGoalModal);
  btnCancelAddGoal.addEventListener("click", closeAddGoalModal);

  btnConfirmAddGoal.addEventListener("click", () => {
    let name = newGoalNameInput.value.trim().replace(/[^a-zA-Z0-9_]/g, "");
    if (!name) {
      alert("Vui lòng nhập tên Goal!");
      return;
    }

    let desc = newGoalDescInput.value.trim();
    let decomp = newGoalDecompSelect.value;
    let parent = newGoalParentSelect.value || null;

    model.addGoal(name, desc, decomp, parent);
    closeAddGoalModal();
    canvas.zoomFit();
    showToast(`Đã thêm Goal "!${name}" thành công!`, "success");
  });

  // Modal 2: Add Link / Decomposition
  const modalAddLink = document.getElementById("modalAddLink");
  const btnCloseAddLinkModal = document.getElementById("btnCloseAddLinkModal");
  const btnCancelAddLink = document.getElementById("btnCancelAddLink");
  const btnConfirmAddLink = document.getElementById("btnConfirmAddLink");
  const linkParentSelect = document.getElementById("linkParentSelect");
  const linkChildSelect = document.getElementById("linkChildSelect");
  const linkDecompTypeSelect = document.getElementById("linkDecompTypeSelect");

  btnFabAddLink.addEventListener("click", () => {
    canvasFabContainer.classList.remove("active");
    populateGoalDropdown(linkParentSelect, currentSelectedNode ? currentSelectedNode.name : null);
    populateGoalDropdown(linkChildSelect, null);
    modalAddLink.classList.add("active");
  });

  btnFabAddSubgoal.addEventListener("click", () => {
    canvasFabContainer.classList.remove("active");
    populateParentDropdown(newGoalParentSelect, currentSelectedNode ? currentSelectedNode.name : null);
    newGoalNameInput.value = "";
    newGoalDescInput.value = "";
    modalAddGoal.classList.add("active");
  });

  function closeAddLinkModal() {
    modalAddLink.classList.remove("active");
  }
  btnCloseAddLinkModal.addEventListener("click", closeAddLinkModal);
  btnCancelAddLink.addEventListener("click", closeAddLinkModal);

  btnConfirmAddLink.addEventListener("click", () => {
    let parentName = linkParentSelect.value;
    let childName = linkChildSelect.value;
    let decompType = linkDecompTypeSelect.value;

    if (!parentName || !childName) {
      alert("Vui lòng chọn đầy đủ Goal Cha và Goal Con!");
      return;
    }
    if (parentName === childName) {
      alert("Goal Cha và Goal Con không được trùng nhau!");
      return;
    }

    let parentGoal = Array.from(model.goals.values()).find(g => g.name === parentName);
    if (parentGoal) {
      parentGoal.decompType = decompType;
      if (!parentGoal.subgoals.includes(childName)) {
        parentGoal.subgoals.push(childName);
      }
      model.computeLayout();
      model.notify();
      closeAddLinkModal();
      canvas.zoomFit();
      showToast(`Đã tạo liên kết: !${parentName} ➔ !${childName} [${decompType}]`, "success");
    }
  });

  function populateParentDropdown(selectEl, selectedName) {
    selectEl.innerHTML = '<option value="">-- Không có (Mục tiêu độc lập) --</option>';
    for (let g of model.goals.values()) {
      let opt = document.createElement("option");
      opt.value = g.name;
      opt.textContent = `!${g.name} (${g.desc || "Goal"})`;
      if (selectedName && g.name === selectedName) opt.selected = true;
      selectEl.appendChild(opt);
    }
  }

  function populateGoalDropdown(selectEl, selectedName) {
    selectEl.innerHTML = "";
    for (let g of model.goals.values()) {
      let opt = document.createElement("option");
      opt.value = g.name;
      opt.textContent = `!${g.name} (${g.desc || "Goal"})`;
      if (selectedName && g.name === selectedName) opt.selected = true;
      selectEl.appendChild(opt);
    }
  }

  // 10. View Mode Tab Switching
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

  // 11. Dropdown Menus Toggling
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

  // 12. Canvas Zoom & Layout Controls
  document.getElementById("btnZoomIn").addEventListener("click", () => canvas.zoomIn());
  document.getElementById("btnZoomOut").addEventListener("click", () => canvas.zoomOut());
  document.getElementById("btnZoomFit").addEventListener("click", () => canvas.zoomFit());
  document.getElementById("btnAutoLayout").addEventListener("click", () => {
    model.computeLayout();
    canvas.render();
    canvas.zoomFit();
    showToast("Đã tự động sắp xếp lại cây mục tiêu Goal!", "success");
  });

  // 13. Dual File Import Modal Handling
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

  setupDropzone(dropzoneAgent, fileInputAgent, (content, name) => {
    importedAgentText = content;
    agentDropStatus.innerText = `✓ Đã chọn: ${name}`;
    dropzoneAgent.classList.add("has-file");
  });

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

  // 14. Dual Code Sync Buttons
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

  // 15. Export Actions
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

  // 16. Simulator Buttons Binding
  document.getElementById("btnSimStep").addEventListener("click", () => simulator.step());
  document.getElementById("btnSimReset").addEventListener("click", () => simulator.reset());
  document.getElementById("btnSimAutoPlay").addEventListener("click", () => simulator.toggleAutoPlay());
  document.getElementById("btnClearSimLog").addEventListener("click", () => {
    document.getElementById("simConsole").innerHTML = "";
  });

  // 17. Toast Notification Utility
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
