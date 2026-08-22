import React, { useState, useEffect, useMemo } from 'react';
import {
  Zap,
  Boxes,
  RefreshCw,
  Plus,
  Trash2,
  CheckCircle2,
  XCircle,
  Users,
  Search,
  ArrowRight,
  Code2,
  Bot,
  Play,
  RotateCcw,
  Check,
  Shield,
  Layers,
  Workflow,
  Sparkles,
  Sliders,
  ChevronDown,
  ChevronUp,
  Filter,
  Folder,
  X
} from 'lucide-react';
import { AslFileItem } from './ImportAslModal';

interface AgentRole {
  org: string;
  group: string;
  role: string;
}

interface AgentIntention {
  id: number;
  isSuspended: boolean;
  rootGoal: string;
  currentGoal: string;
  stack: string[];
}

interface AgentState {
  name: string;
  roles?: AgentRole[];
  currentGoal?: string;
  intentions?: AgentIntention[];
  events?: string[];
  beliefs: string[];
}

interface ArtifactProp {
  name: string;
  value: string;
  values?: string[];
  arity?: number;
}

interface ArtPropInputState {
  name: string;
  values: string[];
}

interface ArtifactState {
  name: string;
  type: string;
  properties: ArtifactProp[];
}

interface WorkspaceState {
  name: string;
  artifacts: ArtifactState[];
}

interface OrgGroupPlayer {
  agent: string;
  role: string;
}

interface OrgGroupState {
  name: string;
  type: string;
  responsibleFor: string[];
  players: OrgGroupPlayer[];
}

interface OrgSchemeState {
  name: string;
  type: string;
}

interface OrganisationState {
  name: string;
  source: string;
  groups: OrgGroupState[];
  schemes: OrgSchemeState[];
}

interface SimulationState {
  agents: AgentState[];
  workspaces: WorkspaceState[];
  organisations?: OrganisationState[];
}

interface SimulationStudioPageProps {
  selectedAgentName: string;
  onSelectAgent?: (name: string) => void;
  importedAgentFile?: AslFileItem | null;
  importedBeliefFile?: AslFileItem | null;
  onOpenImportModal?: () => void;
  onNavigateToEditor?: (agentName?: string) => void;
}

export const SimulationStudioPage: React.FC<SimulationStudioPageProps> = ({
  selectedAgentName,
  onSelectAgent,
  onNavigateToEditor
}) => {
  const [state, setState] = useState<SimulationState>({ agents: [], workspaces: [], organisations: [] });
  const [loading, setLoading] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [newBelief, setNewBelief] = useState('');
  const [artPropInputs, setArtPropInputs] = useState<Record<string, ArtPropInputState>>({});
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);
  const [activeTab, setActiveTab] = useState<'agents' | 'organisation' | 'artifacts'>('agents');

  const getPropInput = (artName: string): ArtPropInputState => {
    return artPropInputs[artName] || { name: '', values: [''] };
  };

  const updatePropName = (artName: string, name: string) => {
    setArtPropInputs(prev => {
      const cur = prev[artName] || { name: '', values: [''] };
      return { ...prev, [artName]: { ...cur, name } };
    });
  };

  const updatePropValue = (artName: string, index: number, val: string) => {
    setArtPropInputs(prev => {
      const cur = prev[artName] || { name: '', values: [''] };
      const newVals = [...cur.values];
      newVals[index] = val;
      return { ...prev, [artName]: { ...cur, values: newVals } };
    });
  };

  const addPropValueField = (artName: string) => {
    setArtPropInputs(prev => {
      const cur = prev[artName] || { name: '', values: [''] };
      return { ...prev, [artName]: { ...cur, values: [...cur.values, ''] } };
    });
  };

  const removePropValueField = (artName: string, index: number) => {
    setArtPropInputs(prev => {
      const cur = prev[artName] || { name: '', values: [''] };
      const newVals = cur.values.filter((_, i) => i !== index);
      return { ...prev, [artName]: { ...cur, values: newVals.length > 0 ? newVals : [''] } };
    });
  };

  const computeBeliefPreview = (name: string, values: string[]): string => {
    if (!name.trim()) return '';
    const cleanVals = values.filter(v => v !== undefined && v !== null && v.trim() !== '');
    if (cleanVals.length === 0) return name.trim();
    const formatted = cleanVals.map(v => {
      const tv = v.trim();
      if (tv.startsWith('"') || tv.startsWith("'") || /^-?\d+(\.\d+)?$/.test(tv) || /^[a-z][a-zA-Z0-9_]*$/.test(tv) || tv === 'true' || tv === 'false') {
        return tv;
      }
      return `"${tv.replace(/"/g, '\\"')}"`;
    });
    return `${name.trim()}(${formatted.join(', ')})`;
  };

  // Fetch simulation state
  const fetchSimulationState = async () => {
    try {
      const res = await fetch('/api/simulation/state');
      if (res.ok) {
        const data = await res.json();
        setState(data);
      }
    } catch {
      // offline
    }
  };

  const refreshAll = async () => {
    setLoading(true);
    await fetchSimulationState();
    setLoading(false);
  };

  useEffect(() => {
    refreshAll();
  }, []);

  useEffect(() => {
    if (!autoRefresh) return;
    const interval = setInterval(() => {
      fetchSimulationState();
    }, 1500);
    return () => clearInterval(interval);
  }, [autoRefresh]);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3500);
  };

  const currentAgent = state.agents.find(a => a.name === selectedAgentName) || state.agents[0];

  const handleInjectBelief = async (beliefStr: string, action: 'add' | 'remove' = 'add', targetAgName?: string) => {
    const targetAgent = targetAgName || (currentAgent ? currentAgent.name : selectedAgentName);
    if (!targetAgent || !beliefStr.trim()) {
      showToast('Vui lòng chọn tác tử và nhập chuỗi belief/goal hợp lệ', 'error');
      return;
    }

    setLoading(true);
    try {
      const res = await fetch('/api/simulation/inject-belief', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          agent: targetAgent,
          belief: beliefStr.trim(),
          action
        })
      });
      const data = await res.json();
      if (data.success) {
        showToast(
          action === 'add'
            ? `✅ Đã gửi '${beliefStr}' tới tác tử [${targetAgent}]`
            : `❌ Đã xóa '${beliefStr}' khỏi tác tử [${targetAgent}]`
        );
        if (action === 'add') {
          setNewBelief('');
        }
        await fetchSimulationState();
      } else {
        showToast(data.message || 'Thao tác thất bại', 'error');
      }
    } catch (err: any) {
      showToast(err.message || 'Lỗi kết nối', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleTriggerOperation = async (workspace: string, artifact: string, operation: string) => {
    setLoading(true);
    try {
      const res = await fetch('/api/simulation/artifact-op', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ workspace, artifact, operation })
      });
      const data = await res.json();
      if (data.success) {
        showToast(`⚙️ Đã thực thi thao tác '${operation}' trên artifact '${artifact}'`);
        await fetchSimulationState();
      } else {
        showToast(data.message || 'Lỗi thao tác', 'error');
      }
    } catch (err: any) {
      showToast(err.message || 'Lỗi kết nối', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleArtifactProperty = async (
    workspace: string,
    artifact: string,
    property: string,
    values: string | string[],
    action: 'define' | 'update' | 'remove' = 'define'
  ) => {
    if (!property.trim()) {
      showToast('Vui lòng nhập tên thuộc tính/belief cho artifact', 'error');
      return;
    }
    setLoading(true);
    try {
      const valuesList = Array.isArray(values) ? values.filter(v => v !== undefined && v !== null && v.trim() !== '') : (values ? [values.trim()] : []);
      const valueStr = valuesList.join(', ');
      const res = await fetch('/api/simulation/artifact-property', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          workspace,
          artifact,
          property: property.trim(),
          value: valueStr,
          values: valuesList,
          action
        })
      });
      const data = await res.json();
      if (data.success) {
        const displayLabel = data.belief || `${property}${valueStr ? `(${valueStr})` : ''}`;
        showToast(
          action === 'remove'
            ? `❌ Đã xóa thuộc tính '${property}' khỏi artifact '${artifact}'`
            : `✅ Đã tạo/cập nhật Observable Property '${displayLabel}' trên artifact '${artifact}' (Tạo Belief Môi trường!)`
        );
        await fetchSimulationState();
      } else {
        showToast(data.message || 'Lỗi thao tác', 'error');
      }
    } catch (err: any) {
      showToast(err.message || 'Lỗi kết nối', 'error');
    } finally {
      setLoading(false);
    }
  };

  // Filters & searches
  const [beliefSearch, setBeliefSearch] = useState('');
  const [selectedWspFilter, setSelectedWspFilter] = useState<string>('all');
  const [artSearch, setArtSearch] = useState<string>('');
  const [collapsedWsps, setCollapsedWsps] = useState<Record<string, boolean>>({});

  const toggleWspCollapse = (wspName: string) => {
    setCollapsedWsps(prev => ({ ...prev, [wspName]: !prev[wspName] }));
  };

  // Filtered beliefs for current agent
  const filteredBeliefs = useMemo(() => {
    if (!currentAgent || !currentAgent.beliefs) return [];
    if (!beliefSearch.trim()) return currentAgent.beliefs;
    const q = beliefSearch.toLowerCase();
    return currentAgent.beliefs.filter(b => b.toLowerCase().includes(q));
  }, [currentAgent, beliefSearch]);

  // Filtered Workspaces & Artifacts for Environment Tab
  const filteredWorkspaces = useMemo(() => {
    let list = state.workspaces || [];
    if (selectedWspFilter !== 'all') {
      list = list.filter(w => w.name === selectedWspFilter);
    }
    if (artSearch.trim()) {
      const q = artSearch.toLowerCase();
      list = list.map(w => ({
        ...w,
        artifacts: w.artifacts.filter(a =>
          a.name.toLowerCase().includes(q) ||
          a.type.toLowerCase().includes(q) ||
          a.properties.some(p => p.name.toLowerCase().includes(q) || p.value.toLowerCase().includes(q))
        )
      })).filter(w => w.artifacts.length > 0);
    }
    return list;
  }, [state.workspaces, selectedWspFilter, artSearch]);

  const totalArtifactCount = useMemo(() => {
    return (state.workspaces || []).reduce((acc, w) => acc + (w.artifacts?.length || 0), 0);
  }, [state.workspaces]);

  const totalObsPropsCount = useMemo(() => {
    return (state.workspaces || []).reduce(
      (acc, w) => acc + (w.artifacts || []).reduce((aAcc, a) => aAcc + (a.properties?.length || 0), 0),
      0
    );
  }, [state.workspaces]);

  const hasActiveGoals = Boolean(
    (currentAgent?.intentions && currentAgent.intentions.length > 0) ||
    currentAgent?.currentGoal
  );

  return (
    <div className="studio-fullpage-container">
      {/* Studio Header Toolbar */}
      <div className="studio-top-toolbar">
        <div className="studio-brand-section">
          <div className="studio-brand-icon">
            <Bot size={22} className="text-cyan-400" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="studio-brand-title">JaCaMo MAS Simulation Studio</h2>
              <span className="live-pulse-badge">
                <span className="live-dot" /> LIVE
              </span>
            </div>
            <p className="studio-brand-subtitle">
              Giám sát Mục tiêu Hiện tại, Danh sách Beliefs và Trạng thái Thực thi Tác tử Đa nhiệm
            </p>
          </div>
        </div>

        <div className="studio-actions-group">
          {/* Generic Agent Selector */}
          <div className="agent-quick-select-wrap">
            <span className="text-xs text-slate-400 font-medium">Tác tử đang chọn:</span>
            <select
              value={currentAgent?.name || selectedAgentName}
              onChange={(e) => onSelectAgent && onSelectAgent(e.target.value)}
              className="agent-quick-select font-mono"
            >
              {state.agents.map((ag) => (
                <option key={ag.name} value={ag.name}>
                  🤖 {ag.name} ({ag.beliefs?.length || 0} beliefs{ag.currentGoal ? ' • 🎯 ' + ag.currentGoal : ''})
                </option>
              ))}
            </select>
          </div>

          <button
            className={`studio-btn ${autoRefresh ? 'active-btn' : ''}`}
            onClick={() => setAutoRefresh(!autoRefresh)}
            title={autoRefresh ? 'Tắt tự động làm mới' : 'Bật tự động làm mới'}
          >
            <RefreshCw size={14} className={autoRefresh ? 'animate-spin text-cyan-400' : ''} />
            <span>{autoRefresh ? 'Auto 1.5s' : 'Paused'}</span>
          </button>

          <button className="studio-btn" onClick={refreshAll} disabled={loading}>
            <RefreshCw size={14} />
            <span>Làm mới</span>
          </button>

          {onNavigateToEditor && (
            <button
              className="studio-btn studio-editor-btn"
              onClick={() => onNavigateToEditor(currentAgent?.name)}
              title="Mở trong Goal Tree Editor"
            >
              <Code2 size={14} />
              <span>Goal Tree Editor</span>
              <ArrowRight size={13} />
            </button>
          )}
        </div>
      </div>

      {/* Toast Notification */}
      {toast && (
        <div className={`studio-toast ${toast.type}`}>
          {toast.type === 'success' ? <CheckCircle2 size={18} /> : <XCircle size={18} />}
          <span>{toast.message}</span>
        </div>
      )}

      {/* Main Tabs Navigation */}
      <div className="studio-tabs-bar">
        <button
          className={`studio-tab ${activeTab === 'agents' ? 'active' : ''}`}
          onClick={() => setActiveTab('agents')}
        >
          <Bot size={16} className="text-cyan-400" />
          <span>🤖 Tác tử (Agents) ({state.agents.length})</span>
        </button>

        <button
          className={`studio-tab ${activeTab === 'organisation' ? 'active' : ''}`}
          onClick={() => setActiveTab('organisation')}
        >
          <Users size={16} className="text-amber-400" />
          <span>🏢 Tổ chức (Organisation) ({state.organisations?.length || 1})</span>
        </button>

        <button
          className={`studio-tab ${activeTab === 'artifacts' ? 'active' : ''}`}
          onClick={() => setActiveTab('artifacts')}
        >
          <Boxes size={16} className="text-indigo-400" />
          <span>📦 Môi trường (CArtAgO Artifacts) ({state.workspaces.reduce((acc, w) => acc + w.artifacts.length, 0)})</span>
        </button>
      </div>

      {/* Studio Tab Contents */}
      <div className="studio-content-body">

        {/* TAB 1: GENERIC AGENT STATE, GOALS & BELIEF BASE FOR ALL AGENTS */}
        {activeTab === 'agents' && (
          <div className="studio-tab-view agents-view">
            {/* Top Dynamic Metrics Grid */}
            <div className="studio-metrics-grid">
              {/* Metric 1: Current Selected Agent */}
              <div className="metric-card agent-metric">
                <div className="metric-icon-box agent-box">
                  <Bot size={22} className="text-cyan-400" />
                </div>
                <div>
                  <span className="metric-label">Tác tử Hiện hành</span>
                  <strong className="metric-value text-cyan-300">
                    🤖 {currentAgent?.name || 'Chưa chọn'}
                  </strong>
                  <span className="metric-sub">
                    {currentAgent?.roles && currentAgent.roles.length > 0
                      ? `Vai trò: ${currentAgent.roles.map(r => `${r.role} (${r.group})`).join(', ')}`
                      : 'Đang hoạt động trong MAS'}
                  </span>
                </div>
              </div>

              {/* Metric 2: Current Active Goal */}
              <div className="metric-card org-metric">
                <div className="metric-icon-box org-box">
                  <Workflow size={22} className="text-emerald-400" />
                </div>
                <div>
                  <span className="metric-label">Mục tiêu Hiện tại (Current Goal)</span>
                  <strong className={`metric-value ${hasActiveGoals ? 'text-emerald-300 font-mono text-sm' : 'text-slate-400 text-xs'}`}>
                    {currentAgent?.currentGoal || (hasActiveGoals ? 'Đang thực thi ý định' : '💤 Chờ lệnh (Idle)')}
                  </strong>
                  <span className="metric-sub">
                    {currentAgent?.intentions && currentAgent.intentions.length > 0
                      ? `${currentAgent.intentions.length} Ý định đang hoạt động`
                      : 'Không có mục tiêu nào đang chạy'}
                  </span>
                </div>
              </div>

              {/* Metric 3: Belief Base Count */}
              <div className="metric-card total-metric">
                <div className="metric-icon-box total-box">
                  <Zap size={22} className="text-amber-400" />
                </div>
                <div>
                  <span className="metric-label">Niềm tin trong Belief Base</span>
                  <strong className="metric-value text-amber-300">
                    {currentAgent ? currentAgent.beliefs.length : 0} Facts / Beliefs
                  </strong>
                  <span className="metric-sub">Đồng bộ chu kỳ suy luận Jason BDI</span>
                </div>
              </div>
            </div>

            {/* SECTION 1: GENERIC CURRENT GOAL & INTENTION DECOMPOSITION */}
            <div className="bg-slate-900/80 border border-slate-700/60 rounded-xl p-5 shadow-lg backdrop-blur-md">
              <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-800">
                <div className="flex items-center gap-2">
                  <Workflow size={18} className="text-emerald-400" />
                  <h3 className="text-sm font-semibold text-slate-100 uppercase tracking-wider">
                    Mục tiêu Hiện tại & Ý định Thực thi (Current Goals & Intentions)
                  </h3>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`px-2.5 py-0.5 rounded-full text-[11px] font-semibold flex items-center gap-1.5 ${hasActiveGoals
                    ? 'bg-emerald-950/80 border border-emerald-500/60 text-emerald-300 animate-pulse'
                    : 'bg-slate-800 border border-slate-700 text-slate-400'
                    }`}>
                    <span className={`w-2 h-2 rounded-full ${hasActiveGoals ? 'bg-emerald-400' : 'bg-slate-500'}`} />
                    {hasActiveGoals ? 'Đang thực thi mục tiêu' : 'Trạng thái Chờ lệnh (Idle)'}
                  </span>
                </div>
              </div>

              {/* Active Intentions Cards */}
              {currentAgent?.intentions && currentAgent.intentions.length > 0 ? (
                <div className="space-y-3 mb-5">
                  {currentAgent.intentions.map((inItem) => (
                    <div
                      key={inItem.id}
                      className="p-4 bg-slate-950/80 border border-emerald-800/40 hover:border-emerald-600/60 rounded-xl transition-all shadow-inner"
                    >
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-2">
                          <span className="px-2 py-0.5 bg-emerald-950 border border-emerald-600 text-emerald-300 text-[10px] font-bold rounded font-mono">
                            INTENTION #{inItem.id}
                          </span>
                          <span className="text-xs font-semibold text-slate-200">
                            Mục tiêu gốc: <code className="text-cyan-300">{inItem.rootGoal || inItem.currentGoal}</code>
                          </span>
                        </div>
                        <span className="text-xs px-2 py-0.5 bg-emerald-900/50 text-emerald-300 rounded font-semibold">
                          {inItem.isSuspended ? '⏸️ Suspended' : '⚡ RUNNING'}
                        </span>
                      </div>

                      {/* Intention Decomposition Stack Visualizer */}
                      {inItem.stack && inItem.stack.length > 0 && (
                        <div className="mt-3">
                          <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide block mb-1.5">
                            Cây phân rã mục tiêu đang thực thi (Intention Stack):
                          </span>
                          <div className="flex flex-wrap items-center gap-1.5 bg-slate-900/90 p-2.5 rounded-lg border border-slate-800">
                            {inItem.stack.map((step, sIdx) => {
                              const isLeaf = sIdx === inItem.stack.length - 1;
                              return (
                                <React.Fragment key={sIdx}>
                                  <div className={`px-2.5 py-1 rounded text-xs font-mono flex items-center gap-1.5 ${isLeaf
                                    ? 'bg-emerald-950 border border-emerald-500 text-emerald-200 font-bold shadow'
                                    : 'bg-slate-800/80 border border-slate-700 text-slate-300'
                                    }`}>
                                    <span>{isLeaf ? '📍' : '🎯'}</span>
                                    <span>{step}</span>
                                  </div>
                                  {!isLeaf && <ArrowRight size={12} className="text-slate-500 shrink-0" />}
                                </React.Fragment>
                              );
                            })}
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="p-5 bg-slate-950/50 border border-slate-800 rounded-xl text-center">
                  <div className="inline-flex p-2.5 rounded-full bg-slate-900 border border-slate-800 text-slate-400 mb-2">
                    <Bot size={20} />
                  </div>
                  <h4 className="text-xs font-semibold text-slate-300">
                    Tác tử [{currentAgent?.name}] đang trong trạng thái Chờ lệnh (Idle)
                  </h4>
                  <p className="text-[11px] text-slate-500 mt-1">
                    Chưa có mục tiêu nào đang thực thi. Tác tử sẽ tự động chạy khi nhận được belief/sự kiện phù hợp.
                  </p>
                </div>
              )}
            </div>

            {/* SECTION 2: GENERIC BELIEF BASE (ALL BELIEFS OF CURRENT AGENT) */}
            <div className="bg-slate-900/80 border border-slate-700/60 rounded-xl p-5 shadow-lg backdrop-blur-md">
              <div className="flex items-center justify-between mb-4 flex-wrap gap-3 pb-3 border-b border-slate-800">
                <div className="flex items-center gap-2">
                  <Zap size={18} className="text-cyan-400" />
                  <h3 className="text-sm font-semibold text-slate-100 uppercase tracking-wider">
                    Danh sách Niềm tin Hiện có trong Belief Base ({filteredBeliefs.length})
                  </h3>
                </div>

                <div className="flex items-center gap-2 bg-slate-950/80 border border-slate-700 rounded-lg px-2.5 py-1 text-xs">
                  <Search size={14} className="text-slate-400" />
                  <input
                    type="text"
                    placeholder="Tìm kiếm belief..."
                    value={beliefSearch}
                    onChange={(e) => setBeliefSearch(e.target.value)}
                    className="bg-transparent border-none outline-none text-slate-200 text-xs w-48 font-mono"
                  />
                  {beliefSearch && (
                    <button onClick={() => setBeliefSearch('')} className="text-slate-400 hover:text-slate-200">
                      ✕
                    </button>
                  )}
                </div>
              </div>

              {/* Grid of All Current Beliefs */}
              {filteredBeliefs.length > 0 ? (
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-2.5">
                  {filteredBeliefs.map((bel, idx) => (
                    <div
                      key={idx}
                      className="flex items-center justify-between p-2.5 bg-slate-950/70 border border-slate-800 hover:border-cyan-700/60 rounded-lg group transition-all shadow-sm"
                    >
                      <div className="flex items-center gap-2 min-w-0 pr-2">
                        <span className="w-2 h-2 rounded-full bg-cyan-400 shrink-0" />
                        <span className="text-xs font-mono text-cyan-200 truncate select-all" title={bel}>
                          {bel}
                        </span>
                      </div>
                      <button
                        className="opacity-0 group-hover:opacity-100 p-1 hover:bg-rose-900/70 text-slate-400 hover:text-rose-300 rounded transition-all shrink-0"
                        onClick={() => handleInjectBelief(bel, 'remove')}
                        title={`Xóa belief '${bel}'`}
                      >
                        <Trash2 size={13} />
                      </button>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="p-8 text-center text-slate-500 text-xs bg-slate-950/40 border border-dashed border-slate-800 rounded-lg">
                  {beliefSearch ? 'Không có belief nào khớp với từ khóa tìm kiếm.' : 'Belief Base của tác tử đang trống. Hãy nạp belief ở form trên.'}
                </div>
              )}
            </div>
          </div>
        )}

        {/* TAB 2: ORGANISATION STATE & STRUCTURE */}
        {activeTab === 'organisation' && (
          <div className="studio-tab-view organisation-view">
            {/* Top Org Metrics Grid */}
            <div className="studio-metrics-grid">
              <div className="metric-card org-metric">
                <div className="metric-icon-box org-box">
                  <Users size={22} className="text-amber-400" />
                </div>
                <div>
                  <span className="metric-label">Tổ chức MoISE (Organisation)</span>
                  <strong className="metric-value text-amber-300">
                    🏛️ {state.organisations && state.organisations.length > 0 ? state.organisations[0].name : 'o1'}
                  </strong>
                  <span className="metric-sub">
                    {state.organisations && state.organisations.length > 0 ? state.organisations[0].source || 'my-org.xml' : 'src/org/my-org.xml'}
                  </span>
                </div>
              </div>

              <div className="metric-card agent-metric">
                <div className="metric-icon-box agent-box">
                  <Layers size={22} className="text-blue-400" />
                </div>
                <div>
                  <span className="metric-label">Nhóm Tổ chức (Groups)</span>
                  <strong className="metric-value text-blue-300">
                    {state.organisations?.reduce((acc, o) => acc + (o.groups?.length || 0), 0) || 1} Nhóm
                  </strong>
                  <span className="metric-sub">Nhóm [my_team] (Loại: team)</span>
                </div>
              </div>

              <div className="metric-card total-metric">
                <div className="metric-icon-box total-box">
                  <Workflow size={22} className="text-emerald-400" />
                </div>
                <div>
                  <span className="metric-label">Đồ hình Kế hoạch (Schemes)</span>
                  <strong className="metric-value text-emerald-300">
                    {state.organisations?.reduce((acc, o) => acc + (o.schemes?.length || 0), 0) || 1} Schemes
                  </strong>
                  <span className="metric-sub">Scheme [s1] (Loại: delivery_scheme)</span>
                </div>
              </div>
            </div>

            {/* Detailed Organisation Spec & Groups */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
              {/* Groups & Role Assignment Card */}
              <div className="bg-slate-900/80 border border-slate-700/60 rounded-xl p-5 shadow-lg backdrop-blur-md">
                <div className="flex items-center gap-2 mb-4 pb-3 border-b border-slate-800">
                  <Users size={18} className="text-amber-400" />
                  <h3 className="text-sm font-semibold text-slate-100 uppercase tracking-wider">
                    Nhóm Tổ chức & Phân công Vai trò (Groups & Players)
                  </h3>
                </div>

                <div className="space-y-4">
                  <div className="p-4 bg-slate-950/70 border border-slate-800 rounded-lg">
                    <div className="flex items-center justify-between mb-3">
                      <div className="flex items-center gap-2">
                        <span className="px-2 py-0.5 bg-amber-950 border border-amber-600/60 text-amber-300 text-[10px] font-bold rounded">
                          GROUP
                        </span>
                        <h4 className="text-sm font-bold text-slate-100 font-mono">my_team</h4>
                        <span className="text-xs text-slate-400">(Loại: <code>team</code>)</span>
                      </div>
                      <span className="text-xs px-2 py-0.5 bg-slate-800 text-slate-300 rounded font-mono">
                        Chịu trách nhiệm: <code>s1</code>
                      </span>
                    </div>

                    <span className="text-xs text-slate-400 font-semibold block mb-2 uppercase tracking-wide">
                      Phân công Vai trò Tác tử (Role Assignments):
                    </span>

                    <div className="space-y-2">
                      <div className="flex items-center justify-between p-2.5 bg-slate-900 border border-slate-800 rounded-lg">
                        <div className="flex items-center gap-2">
                          <Bot size={16} className="text-cyan-400" />
                          <strong className="text-xs font-semibold text-slate-200 font-mono">dispatcher</strong>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-[11px] text-slate-400">Đảm nhận vai trò:</span>
                          <span className="px-2.5 py-0.5 bg-blue-950 border border-blue-600/60 text-blue-300 text-xs font-semibold rounded font-mono">
                            dispatcher
                          </span>
                        </div>
                      </div>

                      <div className="flex items-center justify-between p-2.5 bg-slate-900 border border-slate-800 rounded-lg">
                        <div className="flex items-center gap-2">
                          <Bot size={16} className="text-cyan-400" />
                          <strong className="text-xs font-semibold text-slate-200 font-mono">delivery_truck</strong>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-[11px] text-slate-400">Đảm nhận vai trò:</span>
                          <span className="px-2.5 py-0.5 bg-emerald-950 border border-emerald-600/60 text-emerald-300 text-xs font-semibold rounded font-mono">
                            delivery_robot
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Schemes & Missions Card */}
              <div className="bg-slate-900/80 border border-slate-700/60 rounded-xl p-5 shadow-lg backdrop-blur-md">
                <div className="flex items-center gap-2 mb-4 pb-3 border-b border-slate-800">
                  <Workflow size={18} className="text-emerald-400" />
                  <h3 className="text-sm font-semibold text-slate-100 uppercase tracking-wider">
                    Đồ hình Kế hoạch Tổ chức (Schemes & Missions)
                  </h3>
                </div>

                <div className="space-y-4">
                  <div className="p-4 bg-slate-950/70 border border-slate-800 rounded-lg">
                    <div className="flex items-center justify-between mb-3">
                      <div className="flex items-center gap-2">
                        <span className="px-2 py-0.5 bg-emerald-950 border border-emerald-600/60 text-emerald-300 text-[10px] font-bold rounded">
                          SCHEME
                        </span>
                        <h4 className="text-sm font-bold text-slate-100 font-mono">s1</h4>
                        <span className="text-xs text-slate-400">(Loại: <code>delivery_scheme</code>)</span>
                      </div>
                      <span className="text-xs px-2 py-0.5 bg-emerald-900/60 text-emerald-300 rounded font-semibold">
                        Trạng thái: Active
                      </span>
                    </div>

                    <div className="space-y-2 text-xs text-slate-300">
                      <div className="p-2.5 bg-slate-900 border border-slate-800 rounded-lg flex items-center justify-between">
                        <span>🎯 Mục tiêu cấp Tổ chức:</span>
                        <span className="font-mono text-cyan-300">deliver_success</span>
                      </div>
                      <div className="p-2.5 bg-slate-900 border border-slate-800 rounded-lg flex items-center justify-between">
                        <span>📋 Nhóm phụ trách:</span>
                        <span className="font-mono text-amber-300">my_team</span>
                      </div>
                      <div className="p-2.5 bg-slate-900 border border-slate-800 rounded-lg flex items-center justify-between">
                        <span>🛡️ Cơ chế giám sát:</span>
                        <span className="font-mono text-slate-400">MoISE Group & Scheme Lifecycle</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: CARTAGO ARTIFACTS & WORKSPACE */}
        {activeTab === 'artifacts' && (
          <div className="studio-tab-view artifacts-view">
            {/* Top Workspace Stats Grid */}
            <div className="studio-metrics-grid">
              <div className="metric-card rules-metric">
                <div className="metric-icon-box rules-box">
                  <Boxes size={22} className="text-indigo-400" />
                </div>
                <div>
                  <span className="metric-label">Không gian làm việc (Workspaces)</span>
                  <strong className="metric-value text-indigo-300">
                    {state.workspaces.length} Workspaces
                  </strong>
                  <span className="metric-sub">{state.workspaces.map(w => w.name).join(', ')}</span>
                </div>
              </div>

              <div className="metric-card events-metric">
                <div className="metric-icon-box events-box">
                  <Zap size={22} className="text-emerald-400" />
                </div>
                <div>
                  <span className="metric-label">Tổng số Artifacts CArtAgO</span>
                  <strong className="metric-value text-emerald-300">
                    {totalArtifactCount} Artifacts
                  </strong>
                  <span className="metric-sub">Đang trực tuyến & phản hồi BDI</span>
                </div>
              </div>

              <div className="metric-card total-metric">
                <div className="metric-icon-box total-box">
                  <Sparkles size={22} className="text-cyan-400" />
                </div>
                <div>
                  <span className="metric-label">Thuộc tính Quan sát (Observable Properties)</span>
                  <strong className="metric-value text-cyan-300">
                    {totalObsPropsCount} Percepts / Properties
                  </strong>
                  <span className="metric-sub">Tạo niềm tin trực tiếp cho tác tử</span>
                </div>
              </div>
            </div>

            {/* Workspace Filter & Search Bar */}
            <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-4 flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3 shadow-md">
              {/* Workspace Filter Pills */}
              <div className="flex items-center gap-1.5 flex-wrap">
                <span className="text-xs font-semibold text-slate-400 flex items-center gap-1 mr-1">
                  <Filter size={13} className="text-indigo-400" /> Lọc Workspace:
                </span>
                <button
                  className={`px-3 py-1 rounded-lg text-xs font-semibold transition-all ${selectedWspFilter === 'all'
                    ? 'bg-indigo-600 text-white shadow'
                    : 'bg-slate-950 border border-slate-800 text-slate-300 hover:border-slate-700'
                    }`}
                  onClick={() => setSelectedWspFilter('all')}
                >
                  Tất cả ({state.workspaces.length})
                </button>
                {state.workspaces.map((w) => (
                  <button
                    key={w.name}
                    className={`px-3 py-1 rounded-lg text-xs font-mono font-semibold transition-all flex items-center gap-1.5 ${selectedWspFilter === w.name
                      ? 'bg-indigo-600 text-white shadow'
                      : 'bg-slate-950 border border-slate-800 text-slate-300 hover:border-slate-700'
                      }`}
                    onClick={() => setSelectedWspFilter(w.name)}
                  >
                    <Folder size={12} />
                    <span>{w.name}</span>
                    <span className="text-[10px] px-1.5 py-0.2 rounded-full bg-slate-900 border border-slate-700 text-cyan-300 font-bold">
                      {w.artifacts.length}
                    </span>
                  </button>
                ))}
              </div>

              {/* Artifact Search Bar */}
              <div className="flex items-center gap-2 bg-slate-950 border border-slate-700/80 rounded-lg px-3 py-1.5 focus-within:border-cyan-500 transition-all min-w-[260px]">
                <Search size={14} className="text-slate-400 shrink-0" />
                <input
                  type="text"
                  placeholder="Tìm kiếm artifact / thuộc tính..."
                  value={artSearch}
                  onChange={(e) => setArtSearch(e.target.value)}
                  className="bg-transparent border-none text-xs text-slate-100 placeholder-slate-500 outline-none w-full font-mono"
                />
                {artSearch && (
                  <button
                    onClick={() => setArtSearch('')}
                    className="text-slate-500 hover:text-slate-300"
                  >
                    <XCircle size={13} />
                  </button>
                )}
              </div>
            </div>

            {/* Workspaces & Artifacts List */}
            {filteredWorkspaces.length > 0 ? (
              <div className="space-y-6">
                {filteredWorkspaces.map((w, wIdx) => {
                  const isCollapsed = Boolean(collapsedWsps[w.name]);
                  return (
                    <div
                      key={wIdx}
                      className="bg-slate-900/80 border border-slate-700/60 rounded-xl p-4 sm:p-5 shadow-lg backdrop-blur-md transition-all"
                    >
                      {/* Collapsible Workspace Header */}
                      <div
                        className="flex items-center justify-between pb-3 border-b border-slate-800 cursor-pointer select-none group"
                        onClick={() => toggleWspCollapse(w.name)}
                        title="Click để thu gọn / mở rộng Workspace"
                      >
                        <div className="flex items-center gap-2.5">
                          <span className="px-2 py-0.5 bg-indigo-950 border border-indigo-600/60 text-indigo-300 text-[10px] font-bold rounded">
                            WORKSPACE
                          </span>
                          <h3 className="text-base font-bold text-slate-100 font-mono flex items-center gap-2">
                            📁 {w.name}
                          </h3>
                          <span className="text-xs px-2 py-0.5 rounded-full bg-slate-950 border border-slate-800 text-slate-400 font-mono">
                            {w.artifacts.length} Artifacts
                          </span>
                        </div>
                        <div className="flex items-center gap-2 text-slate-400 group-hover:text-slate-200">
                          <span className="text-xs font-semibold hidden sm:inline">
                            {isCollapsed ? 'Mở rộng' : 'Thu gọn'}
                          </span>
                          {isCollapsed ? <ChevronDown size={18} /> : <ChevronUp size={18} />}
                        </div>
                      </div>

                      {/* Artifacts Responsive Grid */}
                      {!isCollapsed && (
                        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4 mt-4">
                          {w.artifacts.map((art, aIdx) => {
                            const currentInput = getPropInput(art.name);
                            const preview = computeBeliefPreview(currentInput.name, currentInput.values);
                            const activeValCount = currentInput.values.filter(v => v.trim()).length;

                            const submitCurrentProp = () => {
                              if (currentInput.name.trim()) {
                                handleArtifactProperty(w.name, art.name, currentInput.name, currentInput.values, 'define');
                                setArtPropInputs(prev => ({ ...prev, [art.name]: { name: '', values: [''] } }));
                              }
                            };

                            return (
                              <div
                                key={aIdx}
                                className="p-4 bg-slate-950/70 border border-slate-800 hover:border-slate-700/80 rounded-xl flex flex-col justify-between transition-all shadow-md"
                              >
                                <div>
                                  {/* Artifact Card Header */}
                                  <div className="flex items-center justify-between mb-3 pb-2.5 border-b border-slate-800">
                                    <div className="min-w-0 pr-2">
                                      <strong className="text-sm font-bold text-slate-100 font-mono truncate block">
                                        📦 {art.name}
                                      </strong>
                                      <span className="text-[11px] text-slate-400 font-mono truncate block">
                                        Loại: <span className="text-indigo-300">{art.type}</span>
                                      </span>
                                    </div>
                                    <span className="px-2 py-0.5 bg-emerald-950 border border-emerald-600/60 text-emerald-300 text-[10px] font-bold rounded shrink-0">
                                      ONLINE
                                    </span>
                                  </div>

                                  {/* Form Tạo / Nạp Belief từ Môi trường vào Artifact */}
                                  <div className="bg-slate-900/90 border border-slate-800/90 rounded-xl p-3.5 sm:p-4 mb-4 shadow-inner">
                                    <div className="flex items-center justify-between mb-3">
                                      <span className="text-[11px] font-semibold text-cyan-300 uppercase tracking-wide flex items-center gap-1.5">
                                        <Sparkles size={13} className="text-amber-400 shrink-0" />
                                        Tạo Belief từ Môi trường (Observable Property):
                                      </span>
                                      <button
                                        type="button"
                                        onClick={() => addPropValueField(art.name)}
                                        className="text-[11px] px-2 py-0.5 bg-slate-800 hover:bg-slate-700 text-cyan-300 border border-slate-700 rounded flex items-center gap-1 font-medium transition-all cursor-pointer"
                                        title="Thêm một ô trường giá trị (Value field)"
                                      >
                                        <Plus size={11} /> + Thêm giá trị
                                      </button>
                                    </div>

                                    <div className="space-y-2.5">
                                      {/* Tên thuộc tính */}
                                      <div className="flex items-center gap-2">
                                        <input
                                          type="text"
                                          placeholder="Thuộc tính / Predicate (VD: package_info)"
                                          value={currentInput.name}
                                          onChange={(e) => updatePropName(art.name, e.target.value)}
                                          onKeyDown={(e) => {
                                            if (e.key === 'Enter' && currentInput.name.trim()) {
                                              submitCurrentProp();
                                            }
                                          }}
                                          className="flex-1 bg-slate-950 border border-slate-700/80 hover:border-slate-600 rounded-lg px-3 py-1.5 text-xs text-slate-100 placeholder-slate-500 font-mono outline-none focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500/40 transition-all"
                                        />
                                      </div>

                                      {/* Danh sách các trường Giá trị */}
                                      <div className="space-y-1.5">
                                        {currentInput.values.map((val, valIdx) => (
                                          <div key={valIdx} className="flex items-center gap-1.5">
                                            <input
                                              type="text"
                                              placeholder={
                                                currentInput.values.length === 1
                                                  ? 'Giá trị (VD: pkg_01 hoặc phân tách bằng dấu phẩy)'
                                                  : `Trường giá trị #${valIdx + 1} (VD: ${valIdx === 0 ? 'pkg_01' : valIdx === 1 ? 'HaNoi' : valIdx === 2 ? 'DaNang' : '12.5'})`
                                              }
                                              value={val}
                                              onChange={(e) => updatePropValue(art.name, valIdx, e.target.value)}
                                              onKeyDown={(e) => {
                                                if (e.key === 'Enter') {
                                                  if (e.shiftKey && valIdx === currentInput.values.length - 1) {
                                                    addPropValueField(art.name);
                                                  } else if (currentInput.name.trim()) {
                                                    submitCurrentProp();
                                                  }
                                                }
                                              }}
                                              className="flex-1 bg-slate-950 border border-slate-700/80 hover:border-slate-600 rounded-lg px-3 py-1.5 text-xs text-slate-100 placeholder-slate-500 font-mono outline-none focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500/40 transition-all"
                                            />
                                            {currentInput.values.length > 1 && (
                                              <button
                                                type="button"
                                                onClick={() => removePropValueField(art.name, valIdx)}
                                                className="p-1 text-slate-500 hover:text-rose-400 hover:bg-slate-800 rounded transition-all shrink-0 cursor-pointer"
                                                title="Xóa trường giá trị này"
                                              >
                                                <Trash2 size={13} />
                                              </button>
                                            )}
                                          </div>
                                        ))}
                                      </div>

                                      {/* Live Preview */}
                                      {preview && (
                                        <div className="p-2 bg-slate-950/80 border border-cyan-900/50 rounded-lg flex items-center justify-between text-xs font-mono">
                                          <span className="text-slate-400 text-[11px] flex items-center gap-1 shrink-0">
                                            <Sparkles size={11} className="text-amber-400" /> Xem trước:
                                          </span>
                                          <span className="text-cyan-300 font-semibold truncate pl-2 select-all">
                                            {preview}
                                          </span>
                                        </div>
                                      )}

                                      {/* Submit row */}
                                      <div className="flex items-center justify-between pt-1 gap-2">
                                        <button
                                          type="button"
                                          onClick={() => addPropValueField(art.name)}
                                          className="text-xs text-slate-400 hover:text-cyan-300 flex items-center gap-1 px-2 py-1 rounded hover:bg-slate-800/80 transition-all cursor-pointer"
                                        >
                                          <Plus size={12} className="text-cyan-400" />
                                          <span>+ Thêm trường</span>
                                        </button>
                                        <button
                                          type="button"
                                          className="px-3.5 py-1.5 bg-cyan-600 hover:bg-cyan-500 active:bg-cyan-700 text-white rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 transition-all shadow-md shrink-0 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                                          onClick={submitCurrentProp}
                                          disabled={!currentInput.name.trim() || loading}
                                        >
                                          <Plus size={13} />
                                          <span>
                                            Nạp Belief {activeValCount > 1 ? `(${activeValCount} giá trị)` : ''}
                                          </span>
                                        </button>
                                      </div>
                                    </div>
                                  </div>

                                  {/* Observable Properties (Percepts) */}
                                  <div className="space-y-1.5 mb-3">
                                    <div className="flex items-center justify-between">
                                      <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block">
                                        Observable Properties ({art.properties.length}):
                                      </span>
                                    </div>
                                    {art.properties.length > 0 ? (
                                      <div className="space-y-1.5 max-h-52 overflow-y-auto pr-1">
                                        {art.properties.map((prop, pIdx) => {
                                          const propValues = prop.values && prop.values.length > 0
                                            ? prop.values
                                            : (prop.value ? prop.value.split(',').map(v => v.trim()).filter(Boolean) : []);
                                          return (
                                            <div
                                              key={pIdx}
                                              className="flex items-center justify-between p-2 bg-slate-900 border border-slate-800/90 hover:border-slate-700 rounded-lg text-xs group transition-all"
                                            >
                                              <div className="flex flex-wrap items-center gap-1.5 min-w-0 pr-2">
                                                <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 shrink-0" />
                                                <span className="font-mono text-slate-200 font-semibold truncate" title={prop.name}>
                                                  {prop.name}
                                                </span>
                                                {propValues.length > 0 ? (
                                                  <div className="flex flex-wrap items-center gap-1 font-mono">
                                                    <span className="text-slate-500">(</span>
                                                    {propValues.map((pv, pvIdx) => (
                                                      <React.Fragment key={pvIdx}>
                                                        <span className="px-1.5 py-0.2 bg-slate-950 border border-slate-700/80 rounded text-cyan-300 font-medium text-[11px]">
                                                          {pv}
                                                        </span>
                                                        {pvIdx < propValues.length - 1 && <span className="text-slate-500">,</span>}
                                                      </React.Fragment>
                                                    ))}
                                                    <span className="text-slate-500">)</span>
                                                  </div>
                                                ) : (
                                                  <span className="text-slate-500 italic text-[11px]">(không có giá trị)</span>
                                                )}
                                              </div>
                                              <button
                                                className="opacity-0 group-hover:opacity-100 p-1 hover:bg-rose-900/60 text-slate-400 hover:text-rose-300 rounded transition-all shrink-0"
                                                onClick={() => handleArtifactProperty(w.name, art.name, prop.name, '', 'remove')}
                                                title={`Xóa thuộc tính '${prop.name}'`}
                                              >
                                                <Trash2 size={12} />
                                              </button>
                                            </div>
                                          );
                                        })}
                                      </div>
                                    ) : (
                                      <span className="text-xs text-slate-500 italic block p-2 bg-slate-900/40 rounded border border-slate-900 text-center">
                                        Chưa có Observable Property nào.
                                      </span>
                                    )}
                                  </div>
                                </div>

                                {/* Quick Artifact Operations */}
                                {art.name === 'navigation' && (
                                  <div className="pt-2.5 border-t border-slate-800/80">
                                    <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block mb-1.5">
                                      Thao tác trực tiếp (Operations):
                                    </span>
                                    <div className="flex flex-wrap gap-1.5">
                                      <button
                                        className="px-2 py-1 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded text-xs font-mono transition-all"
                                        onClick={() => handleTriggerOperation(w.name, art.name, 'reroute')}
                                      >
                                        reroute()
                                      </button>
                                      <button
                                        className="px-2 py-1 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded text-xs font-mono transition-all"
                                        onClick={() => handleTriggerOperation(w.name, art.name, 'unlock_cargo')}
                                      >
                                        unlock_cargo()
                                      </button>
                                      <button
                                        className="px-2 py-1 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded text-xs font-mono transition-all"
                                        onClick={() => handleTriggerOperation(w.name, art.name, 'lock_cargo')}
                                      >
                                        lock_cargo()
                                      </button>
                                      <button
                                        className="px-2 py-1 bg-rose-900/60 hover:bg-rose-800 text-rose-200 rounded text-xs font-mono transition-all"
                                        onClick={() => handleTriggerOperation(w.name, art.name, 'emergency_brake')}
                                      >
                                        emergency_brake()
                                      </button>
                                    </div>
                                  </div>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="p-8 bg-slate-900/60 border border-slate-800 rounded-xl text-center">
                <Boxes size={36} className="text-slate-500 mx-auto mb-2" />
                <h4 className="text-sm font-semibold text-slate-300">Không tìm thấy Workspace / Artifact phù hợp</h4>
                <p className="text-xs text-slate-500 mt-1">
                  {artSearch ? `Không có kết quả khớp với từ khóa "${artSearch}". Thử xóa bộ lọc tìm kiếm.` : 'Hệ thống chưa có Workspace/Artifact CArtAgO nào đang chạy.'}
                </p>
                {artSearch && (
                  <button
                    className="mt-3 px-3 py-1 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded text-xs transition-all"
                    onClick={() => { setArtSearch(''); setSelectedWspFilter('all'); }}
                  >
                    Xóa tất cả bộ lọc
                  </button>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
