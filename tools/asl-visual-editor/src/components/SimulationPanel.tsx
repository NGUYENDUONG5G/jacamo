import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Activity,
  Plus,
  Trash2,
  RefreshCw,
  Zap,
  Radio,
  Boxes,
  CheckCircle2,
  XCircle,
  X,
  Upload,
  FileCode,
  FileText,
  Sparkles,
  Check,
  Filter
} from 'lucide-react';
import { AslBeliefExtractor, ExtractedBelief } from '../core/asl-belief-extractor';

interface AgentState {
  name: string;
  beliefs: string[];
}

interface ObsProperty {
  name: string;
  value: string;
}

interface ArtifactInfo {
  name: string;
  type: string;
  properties: ObsProperty[];
}

interface WorkspaceInfo {
  name: string;
  artifacts: ArtifactInfo[];
}

interface SimulationState {
  agents: AgentState[];
  workspaces: WorkspaceInfo[];
}

interface AslFileItem {
  filename: string;
  path?: string;
  code: string;
}

interface SimulationPanelProps {
  isOpen: boolean;
  onClose: () => void;
  selectedAgentName: string;
  onSelectAgent?: (name: string) => void;
  importedAgentFile?: AslFileItem | null;
  importedBeliefFile?: AslFileItem | null;
  onOpenImportModal?: () => void;
}

export const SimulationPanel: React.FC<SimulationPanelProps> = ({
  isOpen,
  onClose,
  selectedAgentName,
  onSelectAgent,
  importedAgentFile,
  importedBeliefFile,
  onOpenImportModal
}) => {
  const [state, setState] = useState<SimulationState>({ agents: [], workspaces: [] });
  const [loading, setLoading] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [newBelief, setNewBelief] = useState('');
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);
  const [activeTab, setActiveTab] = useState<'beliefs' | 'artifacts'>('beliefs');

  // Import Dialog State & 2 Slots
  const [isImportDialogOpen, setIsImportDialogOpen] = useState<boolean>(false);
  const [agentSlotFile, setAgentSlotFile] = useState<AslFileItem | null>(importedAgentFile || null);
  const [beliefSlotFile, setBeliefSlotFile] = useState<AslFileItem | null>(importedBeliefFile || null);

  useEffect(() => {
    if (importedAgentFile) setAgentSlotFile(importedAgentFile);
  }, [importedAgentFile]);

  useEffect(() => {
    if (importedBeliefFile) setBeliefSlotFile(importedBeliefFile);
  }, [importedBeliefFile]);

  const agentFileInputRef = useRef<HTMLInputElement>(null);
  const beliefFileInputRef = useRef<HTMLInputElement>(null);

  const [extractedBeliefs, setExtractedBeliefs] = useState<ExtractedBelief[]>([]);
  const [categoryFilter, setCategoryFilter] = useState<'all' | 'initial' | 'rule_premise' | 'plan_context'>('all');

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  };

  // Fetch simulation state
  const fetchSimulationState = useCallback(async () => {
    try {
      const res = await fetch('/api/simulation/state');
      if (res.ok) {
        const data = await res.json();
        setState(data);
      }
    } catch {
      // Backend not running or offline
    }
  }, []);

  // Fetch project files from backend and populate the 2 slots if available
  const fetchProjectFiles = useCallback(async () => {
    try {
      const res = await fetch('/api/project-asl');
      if (res.ok) {
        const data = await res.json();
        const files: AslFileItem[] = [];

        if (Array.isArray(data.allFiles) && data.allFiles.length > 0) {
          for (const f of data.allFiles) {
            files.push({
              filename: f.filename,
              path: f.path,
              code: f.code
            });
          }
        } else if (Array.isArray(data.agents)) {
          for (const ag of data.agents) {
            if (ag.aslCode) {
              files.push({
                filename: `${ag.name}.asl`,
                path: ag.aslSource,
                code: ag.aslCode
              });
            }
          }
        }

        if (files.length > 0) {
          // Identify belief.asl vs agent main file
          const bFile = files.find(f => f.filename.toLowerCase().includes('belief') || f.filename.toLowerCase() === 'belief.asl');
          const aFile = files.find(f => !f.filename.toLowerCase().includes('belief') && (f.filename.includes(selectedAgentName) || !bFile || f !== bFile)) || files[0];

          if (aFile) setAgentSlotFile(aFile);
          if (bFile && bFile !== aFile) setBeliefSlotFile(bFile);

          const activeFiles = [aFile, bFile].filter(Boolean) as AslFileItem[];
          const parsed = AslBeliefExtractor.extractFromFiles(activeFiles.length > 0 ? activeFiles : files);
          setExtractedBeliefs(parsed);
        }
      }
    } catch {
      // Standalone mode
    }
  }, [selectedAgentName]);

  useEffect(() => {
    if (!isOpen) return;
    fetchSimulationState();
    fetchProjectFiles();

    if (autoRefresh) {
      const timer = setInterval(fetchSimulationState, 1500);
      return () => clearInterval(timer);
    }
  }, [isOpen, autoRefresh, fetchSimulationState, fetchProjectFiles]);

  const currentAgent = state.agents.find(a => a.name === selectedAgentName) || state.agents[0];

  // Re-extract whenever agentSlotFile or beliefSlotFile changes
  const updateExtractedBeliefs = useCallback(() => {
    const activeFiles = [agentSlotFile, beliefSlotFile].filter(Boolean) as AslFileItem[];
    if (activeFiles.length > 0) {
      const parsed = AslBeliefExtractor.extractFromFiles(activeFiles);
      setExtractedBeliefs(parsed);
    } else {
      setExtractedBeliefs([]);
    }
  }, [agentSlotFile, beliefSlotFile]);

  useEffect(() => {
    updateExtractedBeliefs();
  }, [updateExtractedBeliefs]);

  // Slot 1 Upload: Agent ASL File
  const handleAgentSlotUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const code = (event.target?.result as string) || '';
      setAgentSlotFile({
        filename: file.name,
        code
      });
      showToast(`Đã nạp file Agent: ${file.name}`);
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  // Slot 2 Upload: Belief Rules File
  const handleBeliefSlotUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const code = (event.target?.result as string) || '';
      setBeliefSlotFile({
        filename: file.name,
        code
      });
      showToast(`Đã nạp file Belief: ${file.name}`);
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  const handleInjectBelief = async (beliefText: string, action: 'add' | 'remove' = 'add') => {
    if (!beliefText.trim()) return;
    const targetAg = currentAgent ? currentAgent.name : selectedAgentName;
    if (!targetAg) {
      showToast('Chưa chọn agent!', 'error');
      return;
    }

    try {
      setLoading(true);
      const res = await fetch('/api/simulation/inject-belief', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          agent: targetAg,
          belief: beliefText.trim(),
          action
        })
      });
      const resData = await res.json();
      if (resData.success) {
        showToast(action === 'add' ? `Đã nạp belief: ${beliefText}` : `Đã xóa belief: ${beliefText}`);
        if (action === 'add') setNewBelief('');
        await fetchSimulationState();
      } else {
        showToast(resData.message || 'Lỗi nạp belief', 'error');
      }
    } catch (err: any) {
      showToast(err.message || 'Lỗi kết nối server', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleArtifactAction = async (wsp: string, art: string, op: string) => {
    try {
      setLoading(true);
      const res = await fetch('/api/simulation/artifact-op', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ workspace: wsp, artifact: art, operation: op })
      });
      const data = await res.json();
      if (data.success) {
        showToast(`Đã kích hoạt ${op} trên artifact ${art}`);
        await fetchSimulationState();
      } else {
        showToast(data.message || 'Lỗi thực thi artifact op', 'error');
      }
    } catch (err: any) {
      showToast(err.message || 'Lỗi kết nối', 'error');
    } finally {
      setLoading(false);
    }
  };

  const isBeliefActive = (literal: string) => {
    if (!currentAgent || !currentAgent.beliefs) return false;
    const clean = literal.trim();
    return currentAgent.beliefs.some(b => b === clean || b.startsWith(clean.split('(')[0]));
  };

  const filteredExtractedBeliefs = extractedBeliefs.filter(b => {
    if (categoryFilter === 'all') return true;
    return b.category === categoryFilter;
  });

  if (!isOpen) return null;

  return (
    <div className="simulation-drawer-overlay" onClick={onClose}>
      <div className="simulation-drawer" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="sim-header">
          <div className="sim-header-title">
            <Activity className="text-cyan-400" size={20} />
            <div>
              <h3>Agent & Artifact Simulator</h3>
              <p>Mô phỏng runtime, tương tác Observable Properties & Belief Base</p>
            </div>
          </div>
          <div className="sim-header-actions">
            <button
              className={`sim-poll-btn ${autoRefresh ? 'active' : ''}`}
              onClick={() => setAutoRefresh(!autoRefresh)}
              title={autoRefresh ? 'Tắt tự động cập nhật' : 'Bật tự động cập nhật'}
            >
              <Radio size={14} className={autoRefresh ? 'animate-pulse' : ''} />
              <span>{autoRefresh ? 'LIVE' : 'PAUSED'}</span>
            </button>
            <button
              className="sim-icon-btn"
              onClick={fetchSimulationState}
              disabled={loading}
              title="Làm mới dữ liệu"
            >
              <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
            </button>
            <button className="sim-icon-btn" onClick={onClose}>
              <X size={18} />
            </button>
          </div>
        </div>

        {/* Toast Notification */}
        {toast && (
          <div className={`sim-toast ${toast.type}`}>
            {toast.type === 'success' ? <CheckCircle2 size={16} /> : <XCircle size={16} />}
            <span>{toast.message}</span>
          </div>
        )}

        {/* Tabs */}
        <div className="sim-tabs">
          <button
            className={`sim-tab ${activeTab === 'beliefs' ? 'active' : ''}`}
            onClick={() => setActiveTab('beliefs')}
          >
            <Zap size={14} />
            <span>Belief Base ({currentAgent ? currentAgent.beliefs.length : 0})</span>
          </button>
          <button
            className={`sim-tab ${activeTab === 'artifacts' ? 'active' : ''}`}
            onClick={() => setActiveTab('artifacts')}
          >
            <Boxes size={14} />
            <span>Artifacts ({state.workspaces.reduce((acc, w) => acc + w.artifacts.length, 0)})</span>
          </button>
        </div>

        {/* Tab 1: Live Beliefs Management */}
        {activeTab === 'beliefs' && (
          <div className="sim-tab-content">
            {/* Agent Select Bar & Open Import Dialog Button */}
            <div className="sim-toolbar-row">
              {state.agents.length > 1 && (
                <div className="sim-agent-selector">
                  <span className="text-xs text-slate-400">Agent:</span>
                  <select
                    value={currentAgent?.name || selectedAgentName}
                    onChange={(e) => onSelectAgent && onSelectAgent(e.target.value)}
                    className="sim-select"
                  >
                    {state.agents.map((ag) => (
                      <option key={ag.name} value={ag.name}>
                        🤖 {ag.name} ({ag.beliefs.length} beliefs)
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <button
                className="sim-import-asl-btn"
                onClick={() => setIsImportDialogOpen(true)}
                title="Mở hộp thoại nạp 2 file (agent_name.asl và belief.asl)"
              >
                <Upload size={13} />
                <span>Import ASL / Belief Files</span>
              </button>
            </div>

            {/* Loaded Files Status Bar */}
            {(agentSlotFile || beliefSlotFile) && (
              <div className="sim-loaded-files-bar">
                <span className="text-xs text-slate-400 font-semibold flex items-center gap-1">
                  <FileCode size={12} className="text-cyan-400" /> Files đã nạp:
                </span>
                <div className="loaded-files-list">
                  {agentSlotFile && (
                    <span className="file-badge agent-badge" title={agentSlotFile.path || agentSlotFile.filename}>
                      🤖 Agent: {agentSlotFile.filename}
                    </span>
                  )}
                  {beliefSlotFile && (
                    <span className="file-badge belief-badge" title={beliefSlotFile.path || beliefSlotFile.filename}>
                      🧠 Belief: {beliefSlotFile.filename}
                    </span>
                  )}
                </div>
              </div>
            )}

            {/* Custom Belief Input Form */}
            <div className="sim-inject-form">
              <input
                type="text"
                placeholder="Nhập belief tùy ý (vd: battery_level(15), arm_disconnected)..."
                value={newBelief}
                onChange={(e) => setNewBelief(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleInjectBelief(newBelief)}
                className="sim-input"
              />
              <button
                className="sim-primary-btn"
                onClick={() => handleInjectBelief(newBelief)}
                disabled={!newBelief.trim() || loading}
              >
                <Plus size={15} />
                <span>Nạp</span>
              </button>
            </div>

            {/* Dynamic Clickable Beliefs Section */}
            <div className="sim-extracted-section">
              <div className="sim-extracted-header">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Sparkles size={14} className="text-amber-400" />
                    <span className="font-semibold text-slate-200 text-xs">
                      Beliefs trích xuất ({extractedBeliefs.length} khả dụng - Click để nạp/xóa):
                    </span>
                  </div>
                  <button
                    className="sim-reopen-dialog-btn"
                    onClick={() => setIsImportDialogOpen(true)}
                    title="Đổi file ASL"
                  >
                    Đổi Files
                  </button>
                </div>
                
                {/* Category Filter */}
                <div className="sim-filter-group">
                  <Filter size={12} className="text-slate-400" />
                  <button
                    className={`filter-btn ${categoryFilter === 'all' ? 'active' : ''}`}
                    onClick={() => setCategoryFilter('all')}
                  >
                    Tất cả
                  </button>
                  <button
                    className={`filter-btn ${categoryFilter === 'initial' ? 'active' : ''}`}
                    onClick={() => setCategoryFilter('initial')}
                  >
                    Khởi tạo
                  </button>
                  <button
                    className={`filter-btn ${categoryFilter === 'rule_premise' ? 'active' : ''}`}
                    onClick={() => setCategoryFilter('rule_premise')}
                    title="Sự kiện cơ sở từ belief.asl (không lấy belief sau suy diễn)"
                  >
                    Sự kiện Quy tắc
                  </button>
                  <button
                    className={`filter-btn ${categoryFilter === 'plan_context' ? 'active' : ''}`}
                    onClick={() => setCategoryFilter('plan_context')}
                  >
                    Ngữ cảnh Plan
                  </button>
                </div>
              </div>

              {filteredExtractedBeliefs.length > 0 ? (
                <div className="sim-clickable-chips-grid">
                  {filteredExtractedBeliefs.map((b, idx) => {
                    const active = isBeliefActive(b.literal);
                    return (
                      <button
                        key={idx}
                        className={`sim-extract-chip ${active ? 'is-active' : ''} ${b.category}`}
                        onClick={() => handleInjectBelief(b.literal, active ? 'remove' : 'add')}
                        title={`${active ? 'Đang có trong Agent -> Click để XÓA' : 'Chưa có -> Click để NẠP'}\nNguồn: ${b.sourceFile || 'ASL'}`}
                      >
                        <span className="chip-indicator">
                          {active ? <Check size={10} className="text-emerald-400" /> : <Plus size={10} />}
                        </span>
                        <span className="chip-label">{b.literal}</span>
                        {b.sourceFile && (
                          <span className="chip-source">[{b.sourceFile.replace('.asl', '')}]</span>
                        )}
                      </button>
                    );
                  })}
                </div>
              ) : (
                <div className="sim-empty-state-mini">
                  <span>Chưa có file ASL nào được nạp. Hãy nhấn <strong>"Import ASL / Belief Files"</strong> để chọn 2 file.</span>
                </div>
              )}
            </div>

            {/* Current Active Beliefs List */}
            <div className="sim-section-header">
              <span className="font-semibold text-slate-200">
                Belief Base đang hoạt động của Agent {currentAgent?.name || selectedAgentName} ({currentAgent?.beliefs.length || 0}):
              </span>
            </div>

            {currentAgent && currentAgent.beliefs.length > 0 ? (
              <div className="sim-beliefs-grid">
                {currentAgent.beliefs.map((bel, idx) => (
                  <div key={idx} className="sim-belief-badge">
                    <span className="belief-text">{bel}</span>
                    <button
                      className="del-belief-btn"
                      onClick={() => handleInjectBelief(bel, 'remove')}
                      title={`Xóa belief ${bel}`}
                    >
                      <Trash2 size={12} />
                    </button>
                  </div>
                ))}
              </div>
            ) : (
              <div className="sim-empty-state">
                <p>Belief Base đang trống hoặc hệ thống đang ở chế độ độc lập (offline).</p>
              </div>
            )}
          </div>
        )}

        {/* Tab 2: Artifacts & Observable Properties */}
        {activeTab === 'artifacts' && (
          <div className="sim-tab-content">
            <div className="sim-artifacts-container">
              {state.workspaces.length > 0 ? (
                state.workspaces.map((wsp) => (
                  <div key={wsp.name} className="sim-workspace-card">
                    <div className="wsp-title">
                      <span className="text-xs text-indigo-400 font-mono">Workspace:</span>
                      <strong className="text-slate-100">{wsp.name}</strong>
                    </div>

                    <div className="artifacts-list">
                      {wsp.artifacts.map((art) => (
                        <div key={art.name} className="art-card">
                          <div className="art-header">
                            <Boxes size={15} className="text-cyan-400" />
                            <span className="art-name">{art.name}</span>
                            <span className="art-type">({art.type})</span>
                          </div>

                          {/* Observable Properties */}
                          <div className="art-props-section">
                            <span className="text-xs text-slate-400 font-semibold">
                              Observable Properties (Percepts):
                            </span>
                            {art.properties.length > 0 ? (
                              <div className="art-props-list">
                                {art.properties.map((p, pIdx) => (
                                  <div key={pIdx} className="prop-row">
                                    <span className="prop-name">{p.name}:</span>
                                    <span className="prop-value">{p.value}</span>
                                  </div>
                                ))}
                              </div>
                            ) : (
                              <span className="text-xs text-slate-500 italic block mt-1">
                                Không có Observable Property công khai
                              </span>
                            )}
                          </div>

                          {/* Quick Ops Controls */}
                          <div className="art-actions">
                            <span className="text-xs text-slate-400">Thao tác nhanh:</span>
                            <div className="art-actions-buttons">
                              <button
                                className="art-action-btn"
                                onClick={() => handleArtifactAction(wsp.name, art.name, 'reroute')}
                              >
                                🔄 reroute()
                              </button>
                              <button
                                className="art-action-btn"
                                onClick={() => handleArtifactAction(wsp.name, art.name, 'emergency_brake')}
                              >
                                🚨 emergency_brake()
                              </button>
                              <button
                                className="art-action-btn"
                                onClick={() => handleArtifactAction(wsp.name, art.name, 'lock_cargo')}
                              >
                                🔒 lock_cargo()
                              </button>
                              <button
                                className="art-action-btn"
                                onClick={() => handleArtifactAction(wsp.name, art.name, 'unlock_cargo')}
                              >
                                🔓 unlock_cargo()
                              </button>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                ))
              ) : (
                <div className="sim-empty-state">
                  <p>Không tìm thấy Workspace/Artifact CArtAgO đang chạy.</p>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* 2-Slot Import Modal Dialog */}
      {isImportDialogOpen && (
        <div className="sim-modal-overlay" onClick={() => setIsImportDialogOpen(false)}>
          <div className="sim-modal-dialog" onClick={(e) => e.stopPropagation()}>
            {/* Hidden file inputs */}
            <input
              type="file"
              ref={agentFileInputRef}
              accept=".asl,.txt"
              style={{ display: 'none' }}
              onChange={handleAgentSlotUpload}
            />
            <input
              type="file"
              ref={beliefFileInputRef}
              accept=".asl,.txt"
              style={{ display: 'none' }}
              onChange={handleBeliefSlotUpload}
            />

            <div className="sim-modal-header">
              <div className="flex items-center gap-2">
                <Upload size={18} className="text-cyan-400" />
                <h3 className="sim-modal-title">Import ASL & Belief Files</h3>
              </div>
              <button className="sim-modal-close" onClick={() => setIsImportDialogOpen(false)}>
                <X size={18} />
              </button>
            </div>

            <p className="sim-modal-subtitle">
              Nạp 2 file mã nguồn để trích xuất đầy đủ danh sách Beliefs cho quá trình mô phỏng:
            </p>

            <div className="sim-slots-modal-grid">
              {/* Slot 1: Agent ASL File */}
              <div className="sim-slot-card slot-agent">
                <div className="slot-card-header">
                  <div className="slot-icon-box agent-box">
                    <FileText size={18} />
                  </div>
                  <div>
                    <h4 className="slot-card-title">1. File Agent ASL</h4>
                    <p className="slot-card-desc">Chứa initial beliefs (facts) và plan contexts</p>
                  </div>
                </div>

                <div className="slot-card-body">
                  {agentSlotFile ? (
                    <div className="slot-loaded-info">
                      <FileCode size={16} className="text-cyan-400" />
                      <div className="slot-file-meta">
                        <span className="slot-file-name">{agentSlotFile.filename}</span>
                        <span className="slot-file-size">({agentSlotFile.code.split('\n').length} dòng mã)</span>
                      </div>
                      <button
                        className="slot-action-btn change-btn"
                        onClick={() => agentFileInputRef.current?.click()}
                      >
                        Đổi file
                      </button>
                      <button
                        className="slot-action-btn remove-btn"
                        onClick={() => setAgentSlotFile(null)}
                      >
                        <X size={14} />
                      </button>
                    </div>
                  ) : (
                    <button
                      className="slot-dropzone-btn"
                      onClick={() => agentFileInputRef.current?.click()}
                    >
                      <Upload size={18} className="text-cyan-400" />
                      <span>Chọn file <strong>agent_name.asl</strong></span>
                      <small className="text-slate-400">hoặc click để duyệt file từ máy tính</small>
                    </button>
                  )}
                </div>
              </div>

              {/* Slot 2: Belief Rules File */}
              <div className="sim-slot-card slot-belief">
                <div className="slot-card-header">
                  <div className="slot-icon-box belief-box">
                    <FileCode size={18} />
                  </div>
                  <div>
                    <h4 className="slot-card-title">2. File Belief Rules ASL</h4>
                    <p className="slot-card-desc">Chứa quy tắc `Head :- Body` (chỉ lấy sự kiện cơ sở trong Body)</p>
                  </div>
                </div>

                <div className="slot-card-body">
                  {beliefSlotFile ? (
                    <div className="slot-loaded-info">
                      <FileCode size={16} className="text-purple-400" />
                      <div className="slot-file-meta">
                        <span className="slot-file-name">{beliefSlotFile.filename}</span>
                        <span className="slot-file-size">({beliefSlotFile.code.split('\n').length} dòng mã)</span>
                      </div>
                      <button
                        className="slot-action-btn change-btn"
                        onClick={() => beliefFileInputRef.current?.click()}
                      >
                        Đổi file
                      </button>
                      <button
                        className="slot-action-btn remove-btn"
                        onClick={() => setBeliefSlotFile(null)}
                      >
                        <X size={14} />
                      </button>
                    </div>
                  ) : (
                    <button
                      className="slot-dropzone-btn"
                      onClick={() => beliefFileInputRef.current?.click()}
                    >
                      <Upload size={18} className="text-purple-400" />
                      <span>Chọn file <strong>belief.asl</strong></span>
                      <small className="text-slate-400">hoặc click để duyệt file quy tắc từ máy tính</small>
                    </button>
                  )}
                </div>
              </div>
            </div>

            <div className="sim-modal-footer">
              <button
                className="sim-modal-cancel-btn"
                onClick={() => setIsImportDialogOpen(false)}
              >
                Đóng
              </button>
              <button
                className="sim-modal-apply-btn"
                onClick={() => {
                  updateExtractedBeliefs();
                  setIsImportDialogOpen(false);
                  showToast(`Đã áp dụng và trích xuất ${extractedBeliefs.length} beliefs`);
                }}
              >
                <Check size={15} />
                <span>Xác Nhận & Trích Xuất ({extractedBeliefs.length} beliefs)</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
