export interface SampleAgent {
  name: string;
  description: string;
  code: string;
}

export const SAMPLES: SampleAgent[] = [
  {
    name: 'Delivery Truck Agent',
    description: 'Autonomous logistics truck with navigation subgoals, refueling, and atomic drop-offs.',
    code: `// Delivery Truck Autonomous Agent
{ include("common.asl") }

// === Initial Beliefs ===
truck_at(depot).
fuel(100).
battery_level(95).
max_capacity(500).

// === Inference Rules ===
can_travel(X, Y) :- road(X, Y) & not road_blocked(X, Y).
low_fuel(F) :- fuel(F) & F < 20.

// ============================================================================
// Plans & Goal Decompositions
// ============================================================================

// --- Goal: deliver/2 (Root Goal) ---
@p_deliver_fast[atomic]
+!deliver(Package, Destination) : truck_at(Destination)
    <- .print("Already at destination!");
       drop(Package).

@p_deliver_route
+!deliver(Package, Destination) : fuel(F) & F >= 20
    <- !navigate(Destination);
       drop(Package);
       -+last_delivery(Destination).

@p_deliver_refuel
+!deliver(Package, Destination) : low_fuel(F)
    <- !refuel;
       !deliver(Package, Destination).

// --- Goal: navigate/1 ---
@p_nav_direct
+!navigate(Destination) : truck_at(Current) & can_travel(Current, Destination)
    <- drive(Destination);
       -+truck_at(Destination).

@p_nav_indirect
+!navigate(Destination) : truck_at(Current)
    <- find_waypoint(Current, Destination, Waypoint);
       !navigate(Waypoint);
       !navigate(Destination).

// --- Goal: refuel/0 ---
@p_refuel_depot
+!refuel : truck_at(depot)
    <- pump_fuel(100);
       -+fuel(100).

@p_refuel_travel
+!refuel : true
    <- !navigate(depot);
       !refuel.
`
  },
  {
    name: 'Smart Warehouse Picker',
    description: 'Automated Guided Vehicle (AGV) fulfilling orders, sorting items, and checking inventory.',
    code: `// AGV Warehouse Picker Agent
{ include("protocols.asl") }

// Initial Beliefs
shelf_located(item_a, bay_1).
shelf_located(item_b, bay_3).
gripper_ready.

// Goals & Plans
@p_fulfill_single
+!fulfill_order(Order, Item) : shelf_located(Item, Bay) & gripper_ready
    <- !travel_to(Bay);
       !pick_item(Item);
       !deliver_to_pack_station(Order).

@p_travel
+!travel_to(Bay) : true
    <- align_tracks(Bay);
       move_forward(Bay).

@p_pick
+!pick_item(Item) : gripper_ready
    <- activate_suction(Item);
       verify_weight(Item).

@p_deliver_pack
+!deliver_to_pack_station(Order) : true
    <- !travel_to(packing_dock);
       release_item(Order);
       .broadcast(tell, order_packed(Order)).
`
  }
];
