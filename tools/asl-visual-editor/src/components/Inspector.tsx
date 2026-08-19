import React from 'react';
import { GraphNodeData } from '../adapters/ir-to-graph';
import { PlanNode, GoalNode, BodyElement } from '../types/asl-ir';
import { Sliders, Shield, Code, Plus, Trash2, ArrowRight } from 'lucide-react';

interface InspectorProps {
  selectedNodeData: GraphNodeData | null;
  onUpdatePlanContext: (planId: string, newContext: string) => void;
  onAddSubGoal: (planId: string, functor: string, params: string[]) => void;
  onDeletePlanStep: (planId: string, stepIndex: number) => void;
}

export const Inspector: React.FC<InspectorProps> = ({
  selectedNodeData,
  onUpdatePlanContext,
  onAddSubGoal,
  onDeletePlanStep
}) => {
  const [subgoalName, setSubgoalName] = React.useState('');
  const [subgoalParams, setSubgoalParams] = React.useState('');

  if (!selectedNodeData) {
    return (
      <div className="inspector-empty">
        <Sliders size={32} className="text-slate-600 mb-2" />
        <p className="text-sm text-slate-400 font-medium">Select a Goal or Plan node to inspect and edit its properties.</p>
      </div>
    );
  }

  if (selectedNodeData.type === 'goal') {
    const goal = selectedNodeData.rawObject as GoalNode;
    return (
      <div className="inspector-content">
        <div className="inspector-header">
          <div className="inspector-tag goal-tag">GOAL METAMODEL</div>
          <h3 className="inspector-title">{goal.functor}</h3>
        </div>

        <div className="inspector-section">
          <label className="inspector-label">Signature</label>
          <div className="inspector-value">{goal.signature}</div>
        </div>

        <div className="inspector-section">
          <label className="inspector-label">Parameters ({goal.params.length})</label>
          <div className="params-list">
            {goal.params.length === 0 ? (
              <span className="text-xs text-slate-500 italic">No parameters (arity 0)</span>
            ) : (
              goal.params.map((p, idx) => (
                <span key={idx} className="param-pill">{p}</span>
              ))
            )}
          </div>
        </div>

        <div className="inspector-section">
          <label className="inspector-label">OR-Decomposition (or_branches)</label>
          <div className="plan-list">
            {(goal.or_branches || goal.plans || []).map((p, idx) => (
              <div key={idx} className="plan-summary-item">
                <div className="plan-summary-title">
                  <span className="or-tag">OR Branch #{idx + 1}</span> {p.label ? `@${p.label}` : p.id}
                </div>
                <div className="plan-summary-context">
                  Context: <span className="font-mono text-emerald-400">{p.context || 'true'}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (selectedNodeData.type === 'plan') {
    const plan = selectedNodeData.rawObject as PlanNode;
    const planSteps = plan.and_branches || plan.body || [];

    const handleAddSubgoalSubmit = (e: React.FormEvent) => {
      e.preventDefault();
      if (!subgoalName.trim()) return;
      const params = subgoalParams
        ? subgoalParams.split(',').map(s => s.trim()).filter(Boolean)
        : [];
      onAddSubGoal(plan.id, subgoalName.trim(), params);
      setSubgoalName('');
      setSubgoalParams('');
    };

    return (
      <div className="inspector-content">
        <div className="inspector-header">
          <div className="inspector-tag plan-tag">PLAN METAMODEL</div>
          <h3 className="inspector-title">{plan.label ? `@${plan.label}` : plan.id}</h3>
        </div>

        <div className="inspector-section">
          <label className="inspector-label">Trigger Event</label>
          <div className="inspector-value font-mono text-emerald-300">
            {plan.trigger.raw}
          </div>
        </div>

        <div className="inspector-section">
          <label className="inspector-label">Context / Preconditions</label>
          <textarea
            className="context-editor-textarea"
            defaultValue={plan.context || ''}
            placeholder="e.g. truck_fuel(F) & F > 20 & not busy"
            rows={3}
            onBlur={(e) => onUpdatePlanContext(plan.id, e.target.value)}
          />
          <span className="text-xs text-slate-500 mt-1 block">
            Changes are live-compiled back to AgentSpeak source code.
          </span>
        </div>

        <div className="inspector-section">
          <label className="inspector-label">AND-Decomposition (and_branches)</label>
          <div className="steps-editor-list">
            {planSteps.map((step, idx) => (
              <div key={idx} className="step-editor-item">
                <span className={`step-type-pill ${step.type.toLowerCase()}`}>
                  {step.type === 'SUB_GOAL' ? `AND Sub-Goal` : step.type}
                </span>
                <span className="step-content font-mono">{step.raw}</span>
                <button
                  className="step-delete-btn"
                  onClick={() => onDeletePlanStep(plan.id, idx)}
                  title="Remove step"
                >
                  <Trash2 size={12} />
                </button>
              </div>
            ))}
          </div>
        </div>

        <div className="inspector-section">
          <label className="inspector-label">Add Sub-Goal (!sub_goal)</label>
          <form onSubmit={handleAddSubgoalSubmit} className="add-subgoal-form">
            <input
              type="text"
              className="inspector-input"
              placeholder="Functor (e.g. navigate)"
              value={subgoalName}
              onChange={(e) => setSubgoalName(e.target.value)}
            />
            <input
              type="text"
              className="inspector-input"
              placeholder="Params: Package, Dest"
              value={subgoalParams}
              onChange={(e) => setSubgoalParams(e.target.value)}
            />
            <button type="submit" className="add-subgoal-btn">
              <Plus size={14} /> Add Sub-Goal
            </button>
          </form>
        </div>
      </div>
    );
  }

  if (selectedNodeData.type === 'action') {
    const elem = selectedNodeData.rawObject as BodyElement;
    return (
      <div className="inspector-content">
        <div className="inspector-header">
          <div className="inspector-tag" style={{ background: '#8b5cf6', color: '#fff' }}>INTERNAL ACTION</div>
          <h3 className="inspector-title">{elem.functor || selectedNodeData.label}</h3>
        </div>

        <div className="inspector-section">
          <label className="inspector-label">Statement Code</label>
          <div className="inspector-value font-mono text-violet-300" style={{ wordBreak: 'break-all' }}>
            {elem.raw || selectedNodeData.label}
          </div>
        </div>

        {elem.params && elem.params.length > 0 && (
          <div className="inspector-section">
            <label className="inspector-label">Arguments ({elem.params.length})</label>
            <div className="params-list">
              {elem.params.map((p, idx) => (
                <span key={idx} className="param-pill" style={{ borderColor: '#8b5cf6', color: '#c4b5fd' }}>{p}</span>
              ))}
            </div>
          </div>
        )}
      </div>
    );
  }

  return null;
};
