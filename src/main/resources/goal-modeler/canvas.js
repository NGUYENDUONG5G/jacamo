/**
 * Interactive SVG Canvas Renderer for JaCaMo Goal Studio
 * Pure Goal Model Tree View (Root Goals, Sub-Goals, AND/OR Decomposition)
 */

class GoalCanvas {
  constructor(canvasSvgId, transformGroupId, model) {
    this.svg = document.getElementById(canvasSvgId);
    this.g = document.getElementById(transformGroupId);
    this.linksLayer = document.getElementById("linksLayer");
    this.nodesLayer = document.getElementById("nodesLayer");
    this.minimapSvg = document.getElementById("minimapSvg");
    this.minimapViewport = document.getElementById("minimapViewport");
    this.model = model;

    // Viewport transform state
    this.zoom = 0.95;
    this.panX = 80;
    this.panY = 40;
    this.isPanning = false;
    this.startPanX = 0;
    this.startPanY = 0;

    // Node dragging state
    this.draggedNode = null;
    this.dragOffsetX = 0;
    this.dragOffsetY = 0;

    // Selection
    this.selectedNodeId = null;
    this.onSelectNodeCallback = null;

    this.initEvents();
  }

  onSelectNode(cb) {
    this.onSelectNodeCallback = cb;
  }

  initEvents() {
    const wrapper = document.getElementById("canvasWrapper");

    // Mouse Pan & Zoom
    wrapper.addEventListener("mousedown", (e) => {
      // If clicking on background
      if (e.target === this.svg || e.target.tagName === "rect" || e.target.id === "canvasWrapper") {
        this.isPanning = true;
        this.startPanX = e.clientX - this.panX;
        this.startPanY = e.clientY - this.panY;
        this.deselect();
      }
    });

    window.addEventListener("mousemove", (e) => {
      if (this.isPanning) {
        this.panX = e.clientX - this.startPanX;
        this.panY = e.clientY - this.startPanY;
        this.updateTransform();
      } else if (this.draggedNode) {
        // Convert screen coord to canvas space
        const rect = this.svg.getBoundingClientRect();
        const mouseX = (e.clientX - rect.left - this.panX) / this.zoom;
        const mouseY = (e.clientY - rect.top - this.panY) / this.zoom;

        this.draggedNode.x = mouseX - this.dragOffsetX;
        this.draggedNode.y = mouseY - this.dragOffsetY;
        this.render();
      }
    });

    window.addEventListener("mouseup", () => {
      this.isPanning = false;
      if (this.draggedNode) {
        this.draggedNode = null;
      }
    });

    // Wheel Zoom
    wrapper.addEventListener("wheel", (e) => {
      e.preventDefault();
      const zoomFactor = e.deltaY < 0 ? 1.1 : 0.9;
      const newZoom = Math.min(Math.max(0.3, this.zoom * zoomFactor), 2.5);

      const rect = this.svg.getBoundingClientRect();
      const mouseX = e.clientX - rect.left;
      const mouseY = e.clientY - rect.top;

      this.panX = mouseX - (mouseX - this.panX) * (newZoom / this.zoom);
      this.panY = mouseY - (mouseY - this.panY) * (newZoom / this.zoom);
      this.zoom = newZoom;

      this.updateTransform();
      this.updateZoomDisplay();
    }, { passive: false });

    // Drag-and-drop from sidebar Palette
    wrapper.addEventListener("dragover", (e) => {
      e.preventDefault();
      e.dataTransfer.dropEffect = "copy";
    });

    wrapper.addEventListener("drop", (e) => {
      e.preventDefault();
      const type = e.dataTransfer.getData("text/plain");
      if (!type) return;

      const rect = this.svg.getBoundingClientRect();
      const dropX = (e.clientX - rect.left - this.panX) / this.zoom;
      const dropY = (e.clientY - rect.top - this.panY) / this.zoom;

      let id = this.model.addGoal("new_subgoal", "Mục tiêu mới");
      let n = this.model.goals.get(id);
      if (n) { n.x = dropX; n.y = dropY; }
      this.render();
    });
  }

  updateTransform() {
    this.g.setAttribute("transform", `translate(${this.panX}, ${this.panY}) scale(${this.zoom})`);
    this.updateMinimap();
  }

  updateZoomDisplay() {
    const disp = document.getElementById("zoomLevelDisplay");
    if (disp) {
      disp.innerText = Math.round(this.zoom * 100) + "%";
    }
  }

  setZoom(z) {
    this.zoom = Math.min(Math.max(0.3, z), 2.5);
    this.updateTransform();
    this.updateZoomDisplay();
  }

  zoomIn() { this.setZoom(this.zoom * 1.2); }
  zoomOut() { this.setZoom(this.zoom * 0.8); }

  zoomFit() {
    const visibleNodes = Array.from(this.model.goals.values());
    if (visibleNodes.length === 0) return;

    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (let n of visibleNodes) {
      if (n.x < minX) minX = n.x;
      if (n.y < minY) minY = n.y;
      if (n.x + n.width > maxX) maxX = n.x + n.width;
      if (n.y + n.height > maxY) maxY = n.y + n.height;
    }

    const rect = this.svg.getBoundingClientRect();
    const contentW = maxX - minX + 100;
    const contentH = maxY - minY + 100;

    const scaleX = (rect.width - 60) / contentW;
    const scaleY = (rect.height - 60) / contentH;
    this.zoom = Math.min(Math.max(0.5, Math.min(scaleX, scaleY)), 1.35);

    this.panX = (rect.width - contentW * this.zoom) / 2 - minX * this.zoom + 50;
    this.panY = (rect.height - contentH * this.zoom) / 2 - minY * this.zoom + 40;

    this.updateTransform();
    this.updateZoomDisplay();
  }

  deselect() {
    this.selectedNodeId = null;
    document.querySelectorAll(".canvas-node.selected").forEach(el => el.classList.remove("selected"));
    if (this.onSelectNodeCallback) {
      this.onSelectNodeCallback(null);
    }
  }

  selectNode(id, nodeData) {
    this.selectedNodeId = id;
    document.querySelectorAll(".canvas-node").forEach(el => {
      if (el.getAttribute("data-id") === id) {
        el.classList.add("selected");
      } else {
        el.classList.remove("selected");
      }
    });

    if (this.onSelectNodeCallback) {
      this.onSelectNodeCallback(nodeData);
    }
  }

  /**
   * Main Render function for the Goal Model Canvas (Pure Goals & Decomposition)
   */
  render() {
    this.linksLayer.innerHTML = "";
    this.nodesLayer.innerHTML = "";

    // 1. Render Goal-to-Goal Decomposition Links
    this.renderGoalLinks();

    // 2. Render Goal Nodes
    for (let [id, g] of this.model.goals.entries()) {
      this.renderGoalNode(g);
    }

    this.updateTransform();
  }

  renderGoalLinks() {
    for (let [id, g] of this.model.goals.entries()) {
      for (let subName of g.subgoals) {
        let subNode = Array.from(this.model.goals.values()).find(x => x.name === subName);
        if (subNode) {
          const isOr = g.decompType === "OR";
          const startX = g.x + g.width / 2;
          const startY = g.y + g.height;
          const endX = subNode.x + subNode.width / 2;
          const endY = subNode.y;

          this.drawBezierLink(startX, startY, endX, endY, isOr ? "link-or" : "link-and", isOr ? "url(#arrowOr)" : "url(#arrowAnd)");
        }
      }
    }
  }

  drawBezierLink(x1, y1, x2, y2, className, markerEnd) {
    const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
    const midY = (y1 + y2) / 2;
    const d = `M ${x1} ${y1} C ${x1} ${midY}, ${x2} ${midY}, ${x2} ${y2}`;
    path.setAttribute("d", d);
    path.setAttribute("class", `canvas-link ${className}`);
    if (markerEnd) {
      path.setAttribute("marker-end", markerEnd);
    }
    this.linksLayer.appendChild(path);
  }

  renderGoalNode(g) {
    const group = document.createElementNS("http://www.w3.org/2000/svg", "g");
    group.setAttribute("class", `canvas-node goal-node ${this.selectedNodeId === g.id ? "selected" : ""}`);
    group.setAttribute("data-id", g.id);
    group.setAttribute("transform", `translate(${g.x}, ${g.y})`);

    const isRoot = g.isRoot || g.name === this.model.initialGoal;

    // Background Card
    const rect = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    rect.setAttribute("width", g.width);
    rect.setAttribute("height", g.height);
    rect.setAttribute("rx", "12");
    rect.setAttribute("fill", isRoot ? "rgba(6, 182, 212, 0.25)" : "rgba(17, 24, 39, 0.92)");
    rect.setAttribute("stroke", isRoot ? "#00f2fe" : "#0ea5e9");
    rect.setAttribute("stroke-width", isRoot ? "2.2" : "1.3");
    group.appendChild(rect);

    // Goal Icon / Badge
    const icon = document.createElementNS("http://www.w3.org/2000/svg", "text");
    icon.setAttribute("x", "14");
    icon.setAttribute("y", "26");
    icon.setAttribute("font-size", "15");
    icon.textContent = isRoot ? "👑" : "🎯";
    group.appendChild(icon);

    // Goal Functor Title (!goal_name)
    const title = document.createElementNS("http://www.w3.org/2000/svg", "text");
    title.setAttribute("x", "38");
    title.setAttribute("y", "26");
    title.setAttribute("fill", "#38bdf8");
    title.setAttribute("font-family", "var(--font-code)");
    title.setAttribute("font-size", "13");
    title.setAttribute("font-weight", "700");
    let nameText = `!${g.name}`;
    title.textContent = nameText.length > 22 ? nameText.substring(0, 20) + ".." : nameText;
    group.appendChild(title);

    // Description text
    const desc = document.createElementNS("http://www.w3.org/2000/svg", "text");
    desc.setAttribute("x", "14");
    desc.setAttribute("y", "48");
    desc.setAttribute("fill", "#94a3b8");
    desc.setAttribute("font-size", "11");
    let cleanDesc = g.desc ? (g.desc.length > 30 ? g.desc.substring(0, 28) + "..." : g.desc) : "Sub-goal";
    desc.textContent = cleanDesc;
    group.appendChild(desc);

    // Decomposition Type Tag (AND / OR)
    const tag = document.createElementNS("http://www.w3.org/2000/svg", "text");
    tag.setAttribute("x", g.width - 12);
    tag.setAttribute("y", "25");
    tag.setAttribute("text-anchor", "end");
    tag.setAttribute("fill", g.decompType === "OR" ? "#34d399" : "#38bdf8");
    tag.setAttribute("font-size", "10");
    tag.setAttribute("font-weight", "800");
    tag.textContent = `[${g.decompType}]`;
    group.appendChild(tag);

    this.attachNodeInteractions(group, g);
    this.nodesLayer.appendChild(group);
  }

  attachNodeInteractions(group, nodeData) {
    group.addEventListener("mousedown", (e) => {
      e.stopPropagation();
      this.selectNode(nodeData.id, nodeData);

      // Start drag
      this.draggedNode = nodeData;
      const rect = this.svg.getBoundingClientRect();
      const mouseX = (e.clientX - rect.left - this.panX) / this.zoom;
      const mouseY = (e.clientY - rect.top - this.panY) / this.zoom;
      this.dragOffsetX = mouseX - nodeData.x;
      this.dragOffsetY = mouseY - nodeData.y;
    });
  }

  updateMinimap() {
    if (!this.minimapSvg || !this.minimapViewport) return;
    this.minimapSvg.innerHTML = "";

    const visibleNodes = Array.from(this.model.goals.values());
    if (visibleNodes.length === 0) return;

    const scale = 0.08;
    for (let n of visibleNodes) {
      const r = document.createElementNS("http://www.w3.org/2000/svg", "rect");
      r.setAttribute("x", n.x * scale + 10);
      r.setAttribute("y", n.y * scale + 10);
      r.setAttribute("width", n.width * scale);
      r.setAttribute("height", n.height * scale);
      r.setAttribute("rx", "1");
      r.setAttribute("fill", "#00f2fe");
      this.minimapSvg.appendChild(r);
    }
  }
}
