/**
 * Intermediate Representation (IR) for AgentSpeak / Jason (.asl)
 * Supports full round-trip engineering between ASL code and Visual Goal-Plan Trees.
 */

// ============================================================================
// 1. Goal & Plan Node Metamodel
// ============================================================================

export type TriggerOperator = '+' | '-' | '^';
export type TriggerEventType = '!' | '?' | '!^' | ''; // '!' = achieve, '?' = test, '!^' = achieve with new focus, '' = belief

export interface Trigger {
  operator: TriggerOperator;       // '+' or '-'
  eventType: TriggerEventType;     // '!' (achieve), '?' (test), '' (belief)
  functor: string;                 // e.g. "deliver_packages"
  params: string[];                // e.g. ["Package", "Destination"]
  raw: string;                     // e.g. "+!deliver_packages(Package, Destination)"
}

export type BodyElementType =
  | 'SUB_GOAL'              // !sub_goal(Args)
  | 'CONCURRENT_SUB_GOAL'   // !!sub_goal(Args)
  | 'TEST_GOAL'             // ?test_goal(Args)
  | 'ACTION'                // environment action: drive(Dest)
  | 'INTERNAL_ACTION'       // .print("hello"), .send(...)
  | 'BELIEF_ADD'            // +belief(Args)
  | 'BELIEF_DEL'            // -belief(Args)
  | 'BELIEF_UPDATE';        // -+belief(Args)

export interface BodyElement {
  id: string;
  type: BodyElementType;
  functor: string;
  params: string[];
  annotations?: string[];
  targetGoalSignature?: string;    // Links SUB_GOAL to a GoalNode (e.g. "navigate/1")
  branchType?: 'AND' | 'OR';
  raw: string;                     // e.g. "!navigate(Destination)"
  comment?: string;
}

export interface PlanNode {
  id: string;                      // Unique ID (e.g. "plan_deliver_1")
  label?: string;                  // Optional Jason plan label: @p1
  trigger: Trigger;
  context: string | null;          // Context/precondition string: e.g. "truck_fuel(F) & F > 20"
  body: BodyElement[];             // Sequence of steps
  and_branches: BodyElement[];     // AND-Decomposition: all sub-goals & actions required to achieve plan
  annotations: string[];           // e.g. ["atomic", "breakpoint"]
  isFailureRecovery?: boolean;     // True if trigger is -!goal (failure plan)
  raw: string;                     // Full plan string
  lineStart?: number;
  lineEnd?: number;
}

export interface GoalNode {
  id: string;                      // Signature-based ID: e.g. "goal_deliver_packages_2"
  signature: string;               // e.g. "deliver_packages/2"
  functor: string;                 // e.g. "deliver_packages"
  arity: number;                   // Number of parameters
  params: string[];                // Representative parameter names e.g. ["P", "Dest"]
  triggerType: 'achieve' | 'test'; // Goal type
  plans: PlanNode[];               // Alternative plans for this goal
  or_branches: PlanNode[];         // OR-Decomposition: alternative candidate plans
  isRoot: boolean;                 // True if no other plan in the IR invokes this as a sub-goal
  isRecursive?: boolean;           // True if invoked recursively by one of its own descendant plans
}

// ============================================================================
// 2. Beliefs, Rules & Non-Goal Code Metamodel
// ============================================================================

export interface Belief {
  id: string;
  functor: string;
  params: string[];
  annotations?: string[];
  raw: string;                     // e.g. "truck_at(depot)[source(percept)]."
}

export interface Rule {
  id: string;
  head: string;                    // e.g. "can_travel(X, Y)"
  body: string;                    // e.g. "road(X, Y) & not road_blocked(X, Y)"
  raw: string;
}

export interface UnmappedCode {
  id: string;
  type: 'BELIEF_EVENT_PLAN' | 'COMMENT' | 'DIRECTIVE' | 'UNKNOWN';
  raw: string;
  position: number;                // Preserves relative positioning in file
}

// ============================================================================
// 3. Root Intermediate Representation (AgentSpeakIR)
// ============================================================================

export interface AgentSpeakIR {
  agentName?: string;
  directives: string[];            // e.g. ['{ include("common.asl") }']
  beliefs: Belief[];
  rules: Rule[];
  goals: GoalNode[];               // Grouped goal-plan hierarchies
  unmapped: UnmappedCode[];        // Preserved non-goal triggers and directives
}
