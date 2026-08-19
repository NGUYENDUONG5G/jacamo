import { create } from 'zustand';
import { AgentSpeakIR } from '../types/asl-ir';
import { AgentSpeakParser } from '../core/parser';
import { AgentSpeakGenerator } from '../core/generator';
import { IRToGraphAdapter, FlowGraph, GraphNodeData } from '../adapters/ir-to-graph';
import { SAMPLES } from '../samples/asl-samples';

const parser = new AgentSpeakParser();
const generator = new AgentSpeakGenerator();
const adapter = new IRToGraphAdapter();

interface EditorState {
  code: string;
  ir: AgentSpeakIR;
  graph: FlowGraph;
  selectedNodeData: GraphNodeData | null;
  error: string | null;

  setCode: (code: string) => void;
  selectNode: (nodeData: GraphNodeData | null) => void;
  updatePlanContext: (planId: string, newContext: string) => void;
  addSubGoalToPlan: (planId: string, functor: string, params: string[]) => void;
  deletePlanStep: (planId: string, stepIndex: number) => void;
}

const initialCode = '';
const initialIR = parser.parse(initialCode);
const initialGraph = adapter.transform(initialIR);

export const useEditorStore = create<EditorState>((set, get) => ({
  code: initialCode,
  ir: initialIR,
  graph: initialGraph,
  selectedNodeData: null,
  error: null,

  setCode: (newCode: string) => {
    try {
      const newIR = parser.parse(newCode);
      const newGraph = adapter.transform(newIR);

      let updatedSelected = get().selectedNodeData;
      if (updatedSelected) {
        const found = newGraph.nodes.find(n => n.data.id === updatedSelected!.id);
        updatedSelected = found ? found.data : null;
      }

      set({
        code: newCode,
        ir: newIR,
        graph: newGraph,
        selectedNodeData: updatedSelected,
        error: null
      });
    } catch (e: any) {
      set({ code: newCode, error: e.message || 'Syntax error parsing ASL code' });
    }
  },

  selectNode: (nodeData: GraphNodeData | null) => {
    set({ selectedNodeData: nodeData });
  },

  updatePlanContext: (planId: string, newContext: string) => {
    const currentIR = get().ir;
    const updatedIR = adapter.updatePlanContext(currentIR, planId, newContext);
    const newCode = generator.generate(updatedIR);
    const newGraph = adapter.transform(updatedIR);

    const found = newGraph.nodes.find(n => n.data.id === planId);

    set({
      ir: updatedIR,
      code: newCode,
      graph: newGraph,
      selectedNodeData: found ? found.data : null,
      error: null
    });
  },

  addSubGoalToPlan: (planId: string, functor: string, params: string[]) => {
    const currentIR = get().ir;
    const updatedIR = adapter.addSubGoalToPlan(currentIR, planId, functor, params);
    const newCode = generator.generate(updatedIR);
    const newGraph = adapter.transform(updatedIR);

    const found = newGraph.nodes.find(n => n.data.id === planId);

    set({
      ir: updatedIR,
      code: newCode,
      graph: newGraph,
      selectedNodeData: found ? found.data : null,
      error: null
    });
  },

  deletePlanStep: (planId: string, stepIndex: number) => {
    const currentIR = JSON.parse(JSON.stringify(get().ir)) as AgentSpeakIR;
    for (const goal of currentIR.goals) {
      const plan = goal.plans.find(p => p.id === planId);
      if (plan && plan.body[stepIndex]) {
        plan.body.splice(stepIndex, 1);
        break;
      }
    }

    const newCode = generator.generate(currentIR);
    const newGraph = adapter.transform(currentIR);
    const found = newGraph.nodes.find(n => n.data.id === planId);

    set({
      ir: currentIR,
      code: newCode,
      graph: newGraph,
      selectedNodeData: found ? found.data : null,
      error: null
    });
  },

  loadSample: (index: number) => {
    if (SAMPLES[index]) {
      const sampleCode = SAMPLES[index].code;
      const newIR = parser.parse(sampleCode);
      const newGraph = adapter.transform(newIR);
      set({
        code: sampleCode,
        ir: newIR,
        graph: newGraph,
        selectedNodeData: null,
        error: null
      });
    }
  }
}));
