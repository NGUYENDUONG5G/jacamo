import React, { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import { Target, RefreshCw, Layers } from 'lucide-react';
import { GraphNodeData } from '../../adapters/ir-to-graph';

export const GoalNodeComponent = memo(({ data }: { data: GraphNodeData }) => {
  return (
    <div className={`goal-node-card ${data.isRoot ? 'root-goal' : ''} ${data.isRecursive ? 'recursive-goal' : ''}`}>
      {/* Incoming Sub-Goal Handle (AND branch from parent Plan) */}
      <Handle
        type="target"
        position={Position.Top}
        className="flow-handle"
      />

      <div className="goal-node-header">
        <div className="goal-icon-wrapper">
          {data.isRecursive ? (
            <RefreshCw size={14} className="text-amber-400 animate-spin" />
          ) : (
            <Target size={14} className="text-blue-400" />
          )}
        </div>
        <div className="goal-node-title">
          <span className="goal-type-badge">GOAL</span>
          <span className="goal-signature">{data.signature}</span>
        </div>
      </div>

      <div className="goal-node-body">
        <div className="goal-functor">{data.label}</div>
        <div className="goal-badges">
          <span className="or-branch-badge" title="OR-Decomposition: Alternative candidate plans">
            <Layers size={11} /> {data.or_branches?.length ?? data.planCount ?? 0} OR Branches
          </span>
          {data.isRoot && <span className="root-badge">Root Goal</span>}
          {data.isRecursive && <span className="recursive-badge">Recursive</span>}
        </div>
      </div>

      {/* Outgoing OR-Branch Handle (to alternative Plans) */}
      <Handle
        type="source"
        position={Position.Bottom}
        className="flow-handle or-handle"
      />
    </div>
  );
});

GoalNodeComponent.displayName = 'GoalNodeComponent';
