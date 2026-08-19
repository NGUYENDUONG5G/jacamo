import React, { useMemo } from 'react';
import {
  ReactFlow,
  Controls,
  Background,
  MiniMap,
  useNodesState,
  useEdgesState,
  NodeTypes,
  Panel
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { useEditorStore } from './store/editor-store';
import { GoalNodeComponent } from './components/nodes/GoalNodeComponent';
import { InternalActionNodeComponent } from './components/nodes/InternalActionNodeComponent';
import { AslEditor } from './components/AslEditor';
import { Inspector } from './components/Inspector';
import { SimulationPanel } from './components/SimulationPanel';
import { ImportAslModal, AslFileItem } from './components/ImportAslModal';
import {
  GitFork,
  Zap,
  Download,
  Upload,
  FileCode,
  LayoutGrid,
  CheckCircle2,
  Activity
} from 'lucide-react';

export const App: React.FC = () => {
  const {
    code,
    graph,
    selectedNodeData,
    error,
    setCode,
    selectNode,
    updatePlanContext,
    addSubGoalToPlan,
    deletePlanStep
  } = useEditorStore();

  interface AgentInfo {
    name: string;
    aslSource: string;
    aslCode: string;
  }

  const [importNotice, setImportNotice] = React.useState<string | null>(null);
  const [projectAgents, setProjectAgents] = React.useState<AgentInfo[]>([]);
  const [selectedAgentName, setSelectedAgentName] = React.useState<string>('');
  const [isSimulationOpen, setIsSimulationOpen] = React.useState<boolean>(false);
  const [isImportModalOpen, setIsImportModalOpen] = React.useState<boolean>(false);

  const [agentFile, setAgentFile] = React.useState<AslFileItem | null>(null);
  const [beliefFile, setBeliefFile] = React.useState<AslFileItem | null>(null);

  const nodeTypes: NodeTypes = useMemo(() => ({
    goalNode: GoalNodeComponent,
    actionNode: InternalActionNodeComponent
  }), []);

  const [nodes, setNodes, onNodesChange] = useNodesState(graph.nodes as any);
  const [edges, setEdges, onEdgesChange] = useEdgesState(graph.edges as any);

  // Resizable Editor Pane State (like VS Code split panes)
  const defaultWidth = React.useMemo(() => Math.max(340, Math.min(window.innerWidth * 0.38, 700)), []);
  const [editorWidth, setEditorWidth] = React.useState<number>(() => {
    const saved = localStorage.getItem('asl_editor_width');
    if (saved) {
      const parsed = parseInt(saved, 10);
      if (!isNaN(parsed) && parsed >= 240 && parsed <= window.innerWidth * 0.8) {
        return parsed;
      }
    }
    return Math.max(340, Math.min(window.innerWidth * 0.38, 700));
  });
  const [isResizing, setIsResizing] = React.useState<boolean>(false);
  const isResizingRef = React.useRef(false);

  const handleStartResizing = React.useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setIsResizing(true);
    isResizingRef.current = true;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  }, []);

  const handleResetWidth = React.useCallback(() => {
    setEditorWidth(defaultWidth);
    localStorage.setItem('asl_editor_width', String(defaultWidth));
  }, [defaultWidth]);

  React.useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isResizingRef.current) return;
      const minWidth = 240;
      const maxWidth = window.innerWidth - 380;
      const newWidth = Math.max(minWidth, Math.min(e.clientX, maxWidth));
      setEditorWidth(newWidth);
    };

    const handleMouseUp = () => {
      if (isResizingRef.current) {
        isResizingRef.current = false;
        setIsResizing(false);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
        localStorage.setItem('asl_editor_width', String(editorWidth));
      }
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [editorWidth]);

  React.useEffect(() => {
    setNodes(graph.nodes as any);
    setEdges(graph.edges as any);
  }, [graph, setNodes, setEdges]);

  // Auto-connect to running JaCaMo project and load live .asl code
  React.useEffect(() => {
    fetch('/api/project-asl')
      .then(res => {
        if (!res.ok) throw new Error('API not available');
        return res.json();
      })
      .then(data => {
        if (data && Array.isArray(data.agents) && data.agents.length > 0) {
          setProjectAgents(data.agents);
          const firstWithCode = data.agents.find((a: AgentInfo) => a.aslCode && a.aslCode.trim());
          if (firstWithCode) {
            setSelectedAgentName(firstWithCode.name);
            setCode(firstWithCode.aslCode);
            setImportNotice(`Đã tải AgentSpeak: ${firstWithCode.name}`);
            setTimeout(() => setImportNotice(null), 3500);
          }
        }
      })
      .catch(() => {
        // Standalone mode - defaults to sample code
      });
  }, [setCode]);

  const handleAgentChange = (agentName: string) => {
    setSelectedAgentName(agentName);
    const ag = projectAgents.find(a => a.name === agentName);
    if (ag && ag.aslCode) {
      setCode(ag.aslCode);
      setImportNotice(`Đã chuyển sang Agent: ${ag.name}`);
      setTimeout(() => setImportNotice(null), 3000);
    }
  };

  const handleNodeClick = (_: React.MouseEvent, node: any) => {
    selectNode(node.data);
  };

  const handlePaneClick = () => {
    selectNode(null);
  };

  const handleExportAsl = () => {
    const blob = new Blob([code], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${selectedAgentName || 'agent_model'}.asl`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const handleImportClick = () => {
    setIsImportModalOpen(true);
  };

  const handleApplyFiles = (aFile: AslFileItem | null, bFile: AslFileItem | null) => {
    setAgentFile(aFile);
    setBeliefFile(bFile);
    if (aFile) {
      setCode(aFile.code);
      const agName = aFile.filename.replace('.asl', '');
      setSelectedAgentName(agName);
      setImportNotice(`Đã nạp file: ${aFile.filename}${bFile ? ` & ${bFile.filename}` : ''}`);
      setTimeout(() => setImportNotice(null), 3500);
    } else if (bFile) {
      setImportNotice(`Đã nạp file: ${bFile.filename}`);
      setTimeout(() => setImportNotice(null), 3500);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const file = e.dataTransfer.files?.[0];
    if (file && (file.name.endsWith('.asl') || file.name.endsWith('.txt'))) {
      const reader = new FileReader();
      reader.onload = (event) => {
        const text = event.target?.result as string;
        if (text !== undefined) {
          setCode(text);
          setImportNotice(`Đã import thành công: ${file.name}`);
          setTimeout(() => setImportNotice(null), 3000);
        }
      };
      reader.readAsText(file);
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
  };

  return (
    <div className="app-container" onDrop={handleDrop} onDragOver={handleDragOver}>

      {/* Top Header Navbar */}
      <header className="app-header">
        <div className="header-brand">
          <div className="brand-icon">
            <GitFork size={20} className="text-cyan-400" />
          </div>
          <div>
            <h1 className="brand-title">AgentSpeak Goal Tree Studio</h1>
            <p className="brand-subtitle">Bi-directional Visual Goal Tree Editor & Compiler (Port 3274)</p>
          </div>
        </div>

        {importNotice && (
          <div className="import-success-toast">
            <CheckCircle2 size={14} className="text-emerald-400" />
            <span>{importNotice}</span>
          </div>
        )}

        <div className="header-actions">
          {projectAgents.length > 0 && (
            <div className="sample-selector-group agent-selector-group">
              <span className="text-xs text-cyan-400 font-semibold">🤖 Agent:</span>
              <select
                className="sample-select agent-select"
                value={selectedAgentName}
                onChange={(e) => handleAgentChange(e.target.value)}
              >
                {projectAgents.map((ag, idx) => (
                  <option key={idx} value={ag.name}>
                    {ag.name} ({ag.aslSource || '.asl'})
                  </option>
                ))}
              </select>
            </div>
          )}

          <button
            className={`header-btn simulation-btn ${isSimulationOpen ? 'active' : ''}`}
            onClick={() => setIsSimulationOpen(!isSimulationOpen)}
            title="Mở Bảng điều khiển Mô phỏng & Nạp Belief"
          >
            <Activity size={14} className="text-cyan-400" />
            <span>Mô phỏng & Beliefs</span>
          </button>

          <button className="header-btn import-btn" onClick={handleImportClick} title="Import file .asl từ máy tính">
            <Upload size={14} /> Import .asl
          </button>

          <button className="header-btn export-btn" onClick={handleExportAsl} title="Tải về file .asl">
            <Download size={14} /> Export .asl
          </button>
        </div>
      </header>

      {/* Main Split Screen Area */}
      <main className={`split-workspace ${isResizing ? 'is-resizing' : ''}`}>
        {/* Left Pane: Code Editor */}
        <section className="editor-pane" style={{ width: `${editorWidth}px`, flex: 'none' }}>
          <div className="pane-header">
            <div className="pane-title">
              <FileCode size={15} className="text-blue-400" />
              <span>AgentSpeak Source Code (.asl)</span>
            </div>
            <span className="live-sync-indicator">
              <Zap size={12} className="text-amber-400 fill-amber-400" /> Live Round-trip
            </span>
          </div>

          {error && (
            <div className="error-banner">
              <strong>Compiler Warning:</strong> {error}
            </div>
          )}

          <div className="editor-container">
            <AslEditor value={code} onChange={setCode} />
          </div>
        </section>

        {/* Draggable Splitter Divider (VS Code style) */}
        <div
          className={`split-resizer ${isResizing ? 'active' : ''}`}
          onMouseDown={handleStartResizing}
          onDoubleClick={handleResetWidth}
          title="Kéo sang trái/phải để đổi kích thước (Click đúp để reset)"
        >
          <div className="resizer-handle-line" />
        </div>

        {/* Right Pane: React Flow Visual Canvas */}
        <section className="canvas-pane">
          <div className="pane-header">
            <div className="pane-title">
              <LayoutGrid size={15} className="text-cyan-400" />
              <span>Hierarchical Goal-Action Tree</span>
            </div>
            <div className="legend-items">
              <span className="legend-badge goal-legend">Goal (AND/OR)</span>
              <span className="legend-badge action-legend">Internal Action</span>
              <span className="legend-badge recursive-legend">Recursive</span>
            </div>
          </div>

          <div className="flow-canvas-wrapper">
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onNodeClick={handleNodeClick}
              onPaneClick={handlePaneClick}
              nodeTypes={nodeTypes}
              fitView
              minZoom={0.2}
              maxZoom={1.8}
            >
              <Background color="#334155" gap={20} size={1} />
              <Controls className="react-flow-controls" />
              <MiniMap
                nodeColor={(node: any) =>
                  node.data.type === 'goal' ? '#3b82f6' : '#a855f7'
                }
                maskColor="rgba(15, 23, 42, 0.7)"
                className="react-flow-minimap"
              />

              <Panel position="top-left" className="stats-panel">
                <div className="stat-pill">
                  <span className="stat-label">Goals:</span>
                  <span className="stat-value">
                    {nodes.filter((n: any) => n.data.type === 'goal').length}
                  </span>
                </div>
                <div className="stat-pill">
                  <span className="stat-label">Internal Actions:</span>
                  <span className="stat-value">
                    {nodes.filter((n: any) => n.data.type === 'action').length}
                  </span>
                </div>
              </Panel>
            </ReactFlow>
          </div>
        </section>

        {/* Rightmost Slide-in Properties Inspector */}
        <aside className="inspector-sidebar">
          <Inspector
            selectedNodeData={selectedNodeData}
            onUpdatePlanContext={updatePlanContext}
            onAddSubGoal={addSubGoalToPlan}
            onDeletePlanStep={deletePlanStep}
          />
        </aside>
      </main>

      {/* 2-Slot Import Modal Dialog */}
      <ImportAslModal
        isOpen={isImportModalOpen}
        onClose={() => setIsImportModalOpen(false)}
        initialAgentFile={agentFile}
        initialBeliefFile={beliefFile}
        onApply={handleApplyFiles}
      />

      {/* Slide-over Simulation Panel */}
      <SimulationPanel
        isOpen={isSimulationOpen}
        onClose={() => setIsSimulationOpen(false)}
        selectedAgentName={selectedAgentName}
        onSelectAgent={(name) => setSelectedAgentName(name)}
        importedAgentFile={agentFile}
        importedBeliefFile={beliefFile}
        onOpenImportModal={() => setIsImportModalOpen(true)}
      />
    </div>
  );
};
