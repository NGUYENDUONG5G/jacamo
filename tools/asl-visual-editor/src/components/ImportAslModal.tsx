import React, { useRef, useState, useEffect } from 'react';
import { Upload, FileCode, FileText, Check, X } from 'lucide-react';

export interface AslFileItem {
  filename: string;
  path?: string;
  code: string;
}

interface ImportAslModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialAgentFile?: AslFileItem | null;
  initialBeliefFile?: AslFileItem | null;
  onApply: (agentFile: AslFileItem | null, beliefFile: AslFileItem | null) => void;
}

export const ImportAslModal: React.FC<ImportAslModalProps> = ({
  isOpen,
  onClose,
  initialAgentFile,
  initialBeliefFile,
  onApply
}) => {
  const [agentSlot, setAgentSlot] = useState<AslFileItem | null>(null);
  const [beliefSlot, setBeliefSlot] = useState<AslFileItem | null>(null);

  const agentInputRef = useRef<HTMLInputElement>(null);
  const beliefInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setAgentSlot(initialAgentFile || null);
      setBeliefSlot(initialBeliefFile || null);
    }
  }, [isOpen, initialAgentFile, initialBeliefFile]);

  if (!isOpen) return null;

  const handleAgentUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      setAgentSlot({
        filename: file.name,
        code: (event.target?.result as string) || ''
      });
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  const handleBeliefUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      setBeliefSlot({
        filename: file.name,
        code: (event.target?.result as string) || ''
      });
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  const handleConfirm = () => {
    onApply(agentSlot, beliefSlot);
    onClose();
  };

  return (
    <div className="sim-modal-overlay" onClick={onClose}>
      <div className="sim-modal-dialog" onClick={(e) => e.stopPropagation()}>
        {/* Hidden inputs */}
        <input
          type="file"
          ref={agentInputRef}
          accept=".asl,.txt"
          style={{ display: 'none' }}
          onChange={handleAgentUpload}
        />
        <input
          type="file"
          ref={beliefInputRef}
          accept=".asl,.txt"
          style={{ display: 'none' }}
          onChange={handleBeliefUpload}
        />

        <div className="sim-modal-header">
          <div className="flex items-center gap-2">
            <Upload size={18} className="text-cyan-400" />
            <h3 className="sim-modal-title">Import ASL & Belief Files</h3>
          </div>
          <button className="sim-modal-close" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <p className="sim-modal-subtitle">
          Chọn 2 file mã nguồn để nạp cây mục tiêu (Goal Tree) và trích xuất Beliefs mô phỏng:
        </p>

        <div className="sim-slots-modal-grid">
          {/* Slot 1: Agent ASL File */}
          <div className="sim-slot-card slot-agent">
            <div className="slot-card-header">
              <div className="slot-icon-box agent-box">
                <FileText size={18} />
              </div>
              <div>
                <h4 className="slot-card-title">1. File Agent ASL (vd: agent_name.asl)</h4>
                <p className="slot-card-desc">Cập nhật mã nguồn ASL, cây mục tiêu và initial facts</p>
              </div>
            </div>

            <div className="slot-card-body">
              {agentSlot ? (
                <div className="slot-loaded-info">
                  <FileCode size={16} className="text-cyan-400" />
                  <div className="slot-file-meta">
                    <span className="slot-file-name">{agentSlot.filename}</span>
                    <span className="slot-file-size">({agentSlot.code.split('\n').length} dòng mã)</span>
                  </div>
                  <button
                    className="slot-action-btn change-btn"
                    onClick={() => agentInputRef.current?.click()}
                  >
                    Đổi file
                  </button>
                  <button
                    className="slot-action-btn remove-btn"
                    onClick={() => setAgentSlot(null)}
                  >
                    <X size={14} />
                  </button>
                </div>
              ) : (
                <button
                  className="slot-dropzone-btn"
                  onClick={() => agentInputRef.current?.click()}
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
                <h4 className="slot-card-title">2. File Belief Rules ASL (vd: belief.asl)</h4>
                <p className="slot-card-desc">Chứa quy tắc `Head :- Body` (chỉ lấy sự kiện cơ sở trong Body)</p>
              </div>
            </div>

            <div className="slot-card-body">
              {beliefSlot ? (
                <div className="slot-loaded-info">
                  <FileCode size={16} className="text-purple-400" />
                  <div className="slot-file-meta">
                    <span className="slot-file-name">{beliefSlot.filename}</span>
                    <span className="slot-file-size">({beliefSlot.code.split('\n').length} dòng mã)</span>
                  </div>
                  <button
                    className="slot-action-btn change-btn"
                    onClick={() => beliefInputRef.current?.click()}
                  >
                    Đổi file
                  </button>
                  <button
                    className="slot-action-btn remove-btn"
                    onClick={() => setBeliefSlot(null)}
                  >
                    <X size={14} />
                  </button>
                </div>
              ) : (
                <button
                  className="slot-dropzone-btn"
                  onClick={() => beliefInputRef.current?.click()}
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
          <button className="sim-modal-cancel-btn" onClick={onClose}>
            Đóng
          </button>
          <button className="sim-modal-apply-btn" onClick={handleConfirm}>
            <Check size={15} />
            <span>Xác Nhận & Áp Dụng</span>
          </button>
        </div>
      </div>
    </div>
  );
};
