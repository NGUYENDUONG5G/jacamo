import { AgentSpeakIR, GoalNode, PlanNode, BodyElement } from '../types/asl-ir';
import dagre from 'dagre';

export interface GraphNodeData {
  id: string;
  type: 'goal' | 'plan' | 'action';
  label: string;
  signature?: string;
  params?: string[];
  context?: string | null;
  bodyPreview?: string[];
  isRoot?: boolean;
  isRecursive?: boolean;
  isFailureRecovery?: boolean;
  planCount?: number;
  or_branches?: PlanNode[];
  and_branches?: BodyElement[];
  rawObject: GoalNode | PlanNode | BodyElement;
}

export interface GraphNode {
  id: string;
  type: 'goalNode' | 'actionNode';
  position: { x: number; y: number };
  data: GraphNodeData;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
  type?: 'smoothstep' | 'bezier' | 'straight' | 'default';
  animated?: boolean;
  style?: Record<string, any>;
  labelStyle?: Record<string, any>;
  labelBgStyle?: Record<string, any>;
  labelBgPadding?: [number, number];
  labelBgBorderRadius?: number;
  data?: {
    branchType: 'OR' | 'AND' | 'ACTION';
    isRecursive?: boolean;
  };
}

export interface FlowGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export class IRToGraphAdapter {
  private readonly NODE_WIDTH = 250;
  private readonly NODE_HEIGHT = 100;

  /**
   * Transforms AgentSpeakIR into a renderable React Flow graph containing ONLY Goals and Internal Actions
   */
  public transform(ir: AgentSpeakIR): FlowGraph {
    const rawNodes: GraphNode[] = [];
    const rawEdges: GraphEdge[] = [];

    const goalMap = new Map<string, GoalNode>();
    (ir.goals || []).forEach(g => {
      goalMap.set(g.signature, g);
      goalMap.set(g.functor, g);
    });

    const actionNodesCreated = new Set<string>();

    // 1. Create Goal Nodes
    for (const goal of ir.goals || []) {
      const goalNodeId = `node_${goal.id}`;
      const candidatePlans = goal.or_branches || goal.plans || [];

      rawNodes.push({
        id: goalNodeId,
        type: 'goalNode',
        position: { x: 0, y: 0 },
        data: {
          id: goal.id,
          type: 'goal',
          label: `${goal.functor}${goal.params.length > 0 ? `(${goal.params.join(', ')})` : ''}`,
          signature: goal.signature,
          params: goal.params,
          isRoot: goal.isRoot,
          isRecursive: goal.isRecursive,
          planCount: candidatePlans.length,
          or_branches: candidatePlans,
          rawObject: goal
        }
      });

      // 2. Process Plans: Connect Goal directly to Child Goals and Internal Actions
      let subGoalIndex = 0;
      let actionIndex = 0;
      const isMultiPlanGoal = candidatePlans.length > 1;

      for (let pIdx = 0; pIdx < candidatePlans.length; pIdx++) {
        const plan = candidatePlans[pIdx];
        const planSteps = plan.and_branches || plan.body || [];

        for (const elem of planSteps) {
          if (elem.functor === '.and_branches' || elem.functor === '.or_branches') {
            continue;
          }

          // A. Child Goals (AND / OR Branches)
          if (elem.type === 'SUB_GOAL' || elem.type === 'CONCURRENT_SUB_GOAL') {
            const targetSig = elem.targetGoalSignature || elem.functor;
            if (targetSig && goalMap.has(targetSig)) {
              subGoalIndex++;
              const childGoal = goalMap.get(targetSig)!;
              const targetGoalNodeId = `node_${childGoal.id}`;
              const isRecursive = childGoal.signature === goal.signature || childGoal.functor === goal.functor;

              const isOrBranch = isMultiPlanGoal || elem.branchType === 'OR';
              const edgeLabel = isOrBranch ? 'OR' : `AND #${subGoalIndex}`;

              rawEdges.push({
                id: `edge_${goalNodeId}_to_${targetGoalNodeId}_${pIdx}_${elem.id}`,
                source: goalNodeId,
                target: targetGoalNodeId,
                label: edgeLabel,
                type: 'smoothstep',
                animated: isRecursive,
                style: {
                  stroke: isRecursive ? '#ef4444' : isOrBranch ? '#3b82f6' : '#10b981',
                  strokeWidth: 2,
                  strokeDasharray: isRecursive ? '5,5' : undefined
                },
                labelStyle: {
                  fill: isRecursive ? '#f87171' : isOrBranch ? '#60a5fa' : '#34d399',
                  fontWeight: 700,
                  fontSize: 10,
                  fontFamily: 'monospace'
                },
                labelBgStyle: {
                  fill: '#0f172a',
                  stroke: isRecursive ? '#ef4444' : isOrBranch ? '#3b82f6' : '#10b981',
                  strokeWidth: 1
                },
                labelBgPadding: [4, 2],
                labelBgBorderRadius: 4,
                data: {
                  branchType: isOrBranch ? 'OR' : 'AND',
                  isRecursive
                }
              });
            }
          }
          // B. Internal Actions (Nodes & Edges)
          else if (elem.type === 'INTERNAL_ACTION') {
            actionIndex++;
            const actionNodeId = `node_action_${goal.id}_${pIdx}_${elem.id}`;

            if (!actionNodesCreated.has(actionNodeId)) {
              actionNodesCreated.add(actionNodeId);

              rawNodes.push({
                id: actionNodeId,
                type: 'actionNode',
                position: { x: 0, y: 0 },
                data: {
                  id: actionNodeId,
                  type: 'action',
                  label: elem.raw,
                  rawObject: elem
                }
              });

              rawEdges.push({
                id: `edge_${goalNodeId}_to_${actionNodeId}`,
                source: goalNodeId,
                target: actionNodeId,
                label: 'Action',
                type: 'smoothstep',
                style: { stroke: '#a855f7', strokeWidth: 1.5, strokeDasharray: '3,3' },
                labelStyle: { fill: '#c084fc', fontWeight: 600, fontSize: 9, fontFamily: 'monospace' },
                labelBgStyle: { fill: '#0f172a', stroke: '#a855f7', strokeWidth: 1 },
                labelBgPadding: [3, 1],
                labelBgBorderRadius: 3,
                data: { branchType: 'ACTION' }
              });
            }
          }
        }
      }
    }

    // 3. Apply Dagre Hierarchical Layout
    const { nodes, edges } = this.applyDagreLayout(rawNodes, rawEdges);

    return { nodes, edges };
  }

  /**
   * Applies Dagre hierarchical DAG layout to nodes and edges
   */
  private applyDagreLayout(nodes: GraphNode[], edges: GraphEdge[]): FlowGraph {
    if (nodes.length === 0) return { nodes: [], edges: [] };

    const dagreGraph = new dagre.graphlib.Graph();
    dagreGraph.setDefaultEdgeLabel(() => ({}));
    dagreGraph.setGraph({
      rankdir: 'TB',
      nodesep: 40,
      ranksep: 70
    });

    nodes.forEach(node => {
      dagreGraph.setNode(node.id, { width: this.NODE_WIDTH, height: this.NODE_HEIGHT });
    });

    edges.forEach(edge => {
      if (!edge.data?.isRecursive) {
        dagreGraph.setEdge(edge.source, edge.target);
      }
    });

    dagre.layout(dagreGraph);

    const layoutedNodes = nodes.map(node => {
      const nodeWithPosition = dagreGraph.node(node.id);
      return {
        ...node,
        position: {
          x: nodeWithPosition ? nodeWithPosition.x - this.NODE_WIDTH / 2 : 0,
          y: nodeWithPosition ? nodeWithPosition.y - this.NODE_HEIGHT / 2 : 0
        }
      };
    });

    return { nodes: layoutedNodes, edges };
  }

  public updatePlanContext(ir: AgentSpeakIR, planId: string, newContext: string): AgentSpeakIR {
    const updated = JSON.parse(JSON.stringify(ir)) as AgentSpeakIR;
    for (const goal of updated.goals) {
      if (goal.plans) {
        goal.plans.forEach(p => {
          if (p.id === planId || p.label === planId) {
            p.context = newContext.trim() || null;
          }
        });
      }
      if (goal.or_branches) {
        goal.or_branches.forEach(p => {
          if (p.id === planId || p.label === planId) {
            p.context = newContext.trim() || null;
          }
        });
      }
    }
    return updated;
  }

  public addSubGoalToPlan(ir: AgentSpeakIR, planId: string, subGoalFunctor: string, params: string[]): AgentSpeakIR {
    const updated = JSON.parse(JSON.stringify(ir)) as AgentSpeakIR;
    for (const goal of updated.goals) {
      const targetPlan = (goal.or_branches || goal.plans).find(p => p.id === planId);
      if (targetPlan) {
        const newElem: BodyElement = {
          id: `elem_${(targetPlan.body || []).length + 1}_${subGoalFunctor}`,
          type: 'SUB_GOAL',
          functor: subGoalFunctor,
          params,
          targetGoalSignature: `${subGoalFunctor}/${params.length}`,
          raw: `!${subGoalFunctor}(${params.join(', ')})`
        };
        if (!targetPlan.body) targetPlan.body = [];
        if (!targetPlan.and_branches) targetPlan.and_branches = [];
        targetPlan.body.push(newElem);
        targetPlan.and_branches.push(newElem);
        break;
      }
    }
    return updated;
  }
}
