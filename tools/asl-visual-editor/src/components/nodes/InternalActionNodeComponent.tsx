import React, { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import { Terminal, Zap } from 'lucide-react';
import { GraphNodeData } from '../../adapters/ir-to-graph';

export const InternalActionNodeComponent = memo(({ data }: { data: GraphNodeData }) => {
  const isBranchMacro = data.label.startsWith('.and_branches') || data.label.startsWith('.or_branches');

  return (
    <div className={`action-node-card ${isBranchMacro ? 'branch-macro-action' : ''}`}>
      {/* Incoming Handle from Parent Goal */}
      <Handle
        type="target"
        position={Position.Top}
        className="flow-handle action-handle"
      />

      <div className="action-node-header">
        <div className="action-icon-wrapper">
          {isBranchMacro ? (
            <Zap size={13} className="text-amber-400" />
          ) : (
            <Terminal size={13} className="text-violet-400" />
          )}
        </div>
        <div className="action-node-title">
          <span className="action-type-badge">INTERNAL ACTION</span>
        </div>
      </div>

      <div className="action-node-body">
        <div className="action-code-text font-mono" title={data.label}>
          {data.label}
        </div>
      </div>

      {/* Outgoing Handle if chained */}
      <Handle
        type="source"
        position={Position.Bottom}
        className="flow-handle action-handle"
      />
    </div>
  );
});

InternalActionNodeComponent.displayName = 'InternalActionNodeComponent';
