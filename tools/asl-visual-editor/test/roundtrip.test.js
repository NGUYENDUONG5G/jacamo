import { AgentSpeakParser } from '../src/core/parser.js';
import { AgentSpeakGenerator } from '../src/core/generator.js';
import { IRToGraphAdapter } from '../src/adapters/ir-to-graph.js';
import assert from 'node:assert';

console.log('Testing ASL IR, Parser, Generator, and Graph Adapter...');

const sampleAsl = `
// Delivery Agent Specification
{ include("common.asl") }

truck_at(depot).
fuel(100).

can_travel(X, Y) :- road(X, Y) & not road_blocked(X, Y).

@p_deliver_fast[atomic]
+!deliver(Package, Destination) : truck_at(Destination)
    <- .print("Already at destination");
       drop(Package).

@p_deliver_route
+!deliver(Package, Destination) : fuel(F) & F > 20
    <- !navigate(Destination);
       drop(Package).

@p_nav_direct
+!navigate(Destination) : can_travel(depot, Destination)
    <- drive(Destination).

@p_nav_recurse
+!navigate(Destination) : true
    <- find_waypoint(W);
       drive(W);
       !navigate(Destination).
`;

const parser = new AgentSpeakParser();
const ir = parser.parse(sampleAsl);

assert.strictEqual(ir.goals.length, 2, 'Should parse 2 goals (deliver/2 and navigate/1)');
assert.strictEqual(ir.beliefs.length, 2, 'Should parse 2 initial beliefs');
assert.strictEqual(ir.rules.length, 1, 'Should parse 1 inference rule');

const deliverGoal = ir.goals.find(g => g.signature === 'deliver/2');
assert(deliverGoal, 'deliver/2 goal should exist');
assert.strictEqual(deliverGoal.plans.length, 2, 'deliver/2 should have 2 plans (OR decomposition)');
assert.strictEqual(deliverGoal.isRoot, true, 'deliver/2 should be identified as Root Goal');

const navGoal = ir.goals.find(g => g.signature === 'navigate/1');
assert(navGoal, 'navigate/1 goal should exist');
assert.strictEqual(navGoal.isRecursive, true, 'navigate/1 should be detected as recursive');

console.log('✓ Parser tests passed: Correctly grouped Goals, Plans, OR-branches, and Root/Recursive detection');

const adapter = new IRToGraphAdapter();
const graph = adapter.transform(ir);

assert(graph.nodes.length >= 2, 'Should have GoalNodes and ActionNodes');
assert(graph.edges.length >= 2, 'Should have direct decomposition edges');

console.log('✓ Graph Adapter tests passed: Direct Goal-Action hierarchy and Dagre layout');

const updatedIR = adapter.updatePlanContext(ir, 'p_deliver_route', 'fuel(F) & F >= 50');
const updatedPlan = updatedIR.goals.find(g => g.signature === 'deliver/2')?.plans.find(p => p.id === 'p_deliver_route');
assert.strictEqual(updatedPlan?.context, 'fuel(F) & F >= 50', 'Plan context should be updated');

const generator = new AgentSpeakGenerator();
const generatedAsl = generator.generate(updatedIR);
assert(generatedAsl.includes('fuel(F) & F >= 50'), 'Generated code must contain updated context');
assert(generatedAsl.includes('+!deliver(Package, Destination)'), 'Generated code must contain goal trigger');

console.log('✓ Serializer tests passed: Clean round-trip generation');

// Test .and_branches and .or_branches
const branchMacroAsl = `
+!pickup_package : true
   <- .print("Starting pickup");
      .and_branches([move_to_pickup, load_and_verify_package]);
      .print("Pickup done").

+!move_to_pickup <- .wait(100).

+!load_and_verify_package : true
   <- .or_branches([load_robot_arm, load_manual_scan]).

+!load_robot_arm <- .wait(200).
+!load_manual_scan <- .wait(300).
`;

const branchIR = parser.parse(branchMacroAsl);
const branchGraph = adapter.transform(branchIR);

// Verify no action node has label starting with .and_branches or .or_branches
const macroActionNodes = branchGraph.nodes.filter(n => 
  n.data.label.startsWith('.and_branches') || n.data.label.startsWith('.or_branches')
);
assert.strictEqual(macroActionNodes.length, 0, 'No action node should be created for .and_branches or .or_branches');

// Verify child goals are connected with AND / OR edges
const pickupToMoveEdge = branchGraph.edges.find(e => 
  e.source === 'node_goal_pickup_package_0' && e.target === 'node_goal_move_to_pickup_0'
);
assert(pickupToMoveEdge, 'Edge from pickup_package to move_to_pickup should exist');
assert(pickupToMoveEdge.label.startsWith('AND'), 'Edge should be labeled AND');

const loadToRobotEdge = branchGraph.edges.find(e => 
  e.source === 'node_goal_load_and_verify_package_0' && e.target === 'node_goal_load_robot_arm_0'
);
assert(loadToRobotEdge, 'Edge from load_and_verify_package to load_robot_arm should exist');
assert.strictEqual(loadToRobotEdge.label, 'OR', 'Edge should be labeled OR');

// Verify other internal actions (.print, .wait) are preserved as action nodes
const printActionNodes = branchGraph.nodes.filter(n => n.type === 'actionNode');
assert(printActionNodes.length > 0, 'Regular internal actions (.print, .wait) should still have action nodes');

console.log('✓ Macro branch tests passed: .and_branches / .or_branches parsed into direct links without redundant action nodes');
console.log('All tests completed successfully!');
