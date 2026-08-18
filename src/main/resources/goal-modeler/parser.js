/**
 * JaCaMo AgentSpeak & Belief Rules Parser and Serializer
 */

class AslParser {
  /**
   * Parses an Agent ASL string into a structured AST / Model
   * @param {string} text 
   * @returns {Object} { includes, initialBeliefs, initialGoal, plans, recoveryPlans }
   */
  static parseAgentAsl(text) {
    const lines = text.split(/\r?\n/);
    const includes = [];
    const initialBeliefs = [];
    let initialGoal = null;
    const plans = [];

    // Filter and strip multi-line comments & handle lines
    let inComment = false;
    let cleanCode = "";

    for (let rawLine of lines) {
      let line = rawLine.trim();
      if (!line) continue;

      // Handle block comments /* ... */
      if (line.startsWith("/*") && line.endsWith("*/")) {
        continue;
      }
      if (line.startsWith("/*")) {
        inComment = true;
        continue;
      }
      if (inComment) {
        if (line.endsWith("*/") || line.includes("*/")) {
          inComment = false;
          let idx = line.indexOf("*/");
          line = line.substring(idx + 2).trim();
          if (!line) continue;
        } else {
          continue;
        }
      }

      // Check includes { include("...") }
      let includeMatch = line.match(/^\{\s*include\("([^"]+)"\)\s*\}/);
      if (includeMatch) {
        includes.push(includeMatch[1]);
        continue;
      }

      cleanCode += line + "\n";
    }

    // Split statements by dot '.' that are outside quotes/parentheses
    const statements = this.splitAslStatements(cleanCode);

    for (let stmt of statements) {
      let trimmed = stmt.trim();
      if (!trimmed) continue;

      // Initial Goal: !goalName
      if (trimmed.startsWith("!") && !trimmed.startsWith("!+")) {
        let goalName = trimmed.substring(1).replace(/\.$/, "").trim();
        initialGoal = goalName;
        continue;
      }

      // Plans: +!goal, +!goal : ctx <- body.
      if (trimmed.startsWith("+!") || trimmed.startsWith("+?") || trimmed.startsWith("-!")) {
        const plan = this.parsePlanStatement(trimmed);
        if (plan) {
          plans.push(plan);
        }
        continue;
      }

      // Initial Belief Fact: predicate(arg). or predicate.
      if (/^[a-z][a-zA-Z0-9_]*(\(.*\))?(\s*\/\/[^\n]*)?$/.test(trimmed)) {
        let cleanBelief = trimmed.replace(/\s*\/\/.*$/, "").replace(/\.$/, "").trim();
        if (cleanBelief && !cleanBelief.includes("<-")) {
          initialBeliefs.push(cleanBelief);
        }
      }
    }

    return {
      includes,
      initialBeliefs,
      initialGoal,
      plans
    };
  }

  /**
   * Splits ASL statements safely by ending dot '.'
   */
  static splitAslStatements(code) {
    const stmts = [];
    let current = "";
    let inQuotes = false;
    let quoteChar = "";
    let parenDepth = 0;

    for (let i = 0; i < code.length; i++) {
      let c = code[i];
      let next = code[i + 1];

      // Handle inline comments //
      if (!inQuotes && c === "/" && next === "/") {
        let endLine = code.indexOf("\n", i);
        if (endLine === -1) endLine = code.length;
        current += code.substring(i, endLine);
        i = endLine - 1;
        continue;
      }

      if ((c === '"' || c === "'") && (i === 0 || code[i - 1] !== "\\")) {
        if (!inQuotes) {
          inQuotes = true;
          quoteChar = c;
        } else if (quoteChar === c) {
          inQuotes = false;
        }
      }

      if (!inQuotes) {
        if (c === "(" || c === "[" || c === "{") parenDepth++;
        else if (c === ")" || c === "]" || c === "}") parenDepth--;
      }

      if (c === "." && !inQuotes && parenDepth <= 0) {
        // End of statement
        current += c;
        stmts.push(current.trim());
        current = "";
      } else {
        current += c;
      }
    }

    if (current.trim()) {
      stmts.push(current.trim());
    }

    return stmts;
  }

  /**
   * Parses an individual plan statement
   */
  static parsePlanStatement(stmt) {
    // Remove trailing dot
    let clean = stmt.trim().replace(/\.$/, "");
    
    // Separate trigger & context from body '<-'
    let arrowIndex = clean.indexOf("<-");
    let header = "";
    let bodyText = "";

    if (arrowIndex !== -1) {
      header = clean.substring(0, arrowIndex).trim();
      bodyText = clean.substring(arrowIndex + 2).trim();
    } else {
      header = clean;
    }

    // Parse header: Trigger [: Context]
    let colonIndex = header.indexOf(":");
    let trigger = "";
    let context = "";

    if (colonIndex !== -1) {
      trigger = header.substring(0, colonIndex).trim();
      context = header.substring(colonIndex + 1).trim();
    } else {
      trigger = header.trim();
    }

    // Extract goal functor from trigger: +!goal_name(args) -> goal_name
    let triggerType = "+!";
    let goalFunctor = trigger;
    if (trigger.startsWith("+!")) {
      triggerType = "+!";
      goalFunctor = trigger.substring(2).trim();
    } else if (trigger.startsWith("-!")) {
      triggerType = "-!";
      goalFunctor = trigger.substring(2).trim();
    } else if (trigger.startsWith("+?")) {
      triggerType = "+?";
      goalFunctor = trigger.substring(2).trim();
    }

    // Separate arguments if any: goal_name(A, B) -> goal_name, [A, B]
    let goalName = goalFunctor;
    let goalArgs = "";
    let argMatch = goalFunctor.match(/^([a-zA-Z0-9_]+)\((.*)\)$/);
    if (argMatch) {
      goalName = argMatch[1];
      goalArgs = argMatch[2];
    }

    // Parse body actions split by ';'
    const actions = [];
    const subgoals = [];

    if (bodyText) {
      const rawActions = this.splitActionList(bodyText);
      for (let act of rawActions) {
        let a = act.trim();
        if (!a) continue;
        actions.push(a);

        // Check if action is an achievement goal: !subgoal
        if (a.startsWith("!") && !a.startsWith("!+")) {
          let subg = a.substring(1).trim();
          let subgName = subg;
          let subgMatch = subg.match(/^([a-zA-Z0-9_]+)/);
          if (subgMatch) subgName = subgMatch[1];
          subgoals.push(subgName);
        }
      }
    }

    // Determine if it's a recovery plan
    let isRecovery = false;
    let lowerName = goalName.toLowerCase();
    if (
      lowerName.includes("recovery") ||
      lowerName.includes("rescue") ||
      lowerName.includes("reroute") ||
      lowerName.includes("standby") ||
      lowerName.includes("cancel") ||
      lowerName.includes("emergency") ||
      lowerName.includes("return_to")
    ) {
      isRecovery = true;
    }

    return {
      id: "plan_" + Math.random().toString(36).substr(2, 9),
      trigger,
      triggerType,
      goalName,
      goalArgs,
      context,
      bodyText,
      actions,
      subgoals,
      isRecovery
    };
  }

  /**
   * Splits action list by ';' ignoring semicolons inside strings
   */
  static splitActionList(body) {
    const list = [];
    let current = "";
    let inQuotes = false;
    let quoteChar = "";

    for (let i = 0; i < body.length; i++) {
      let c = body[i];
      if ((c === '"' || c === "'") && (i === 0 || body[i - 1] !== "\\")) {
        if (!inQuotes) {
          inQuotes = true;
          quoteChar = c;
        } else if (quoteChar === c) {
          inQuotes = false;
        }
      }

      if (c === ";" && !inQuotes) {
        list.push(current.trim());
        current = "";
      } else {
        current += c;
      }
    }

    if (current.trim()) list.push(current.trim());
    return list;
  }

  /**
   * Parses Belief ASL (belief.asl)
   * e.g. "path_blocked :- road_blocked_detected & not reroute_done."
   * @param {string} text 
   * @returns {Array<Object>} List of belief rules
   */
  static parseBeliefAsl(text) {
    const rules = [];
    const lines = text.split(/\r?\n/);

    for (let rawLine of lines) {
      let line = rawLine.trim();
      if (!line || line.startsWith("//") || line.startsWith("/*")) continue;

      // Remove trailing comments & trailing dot
      let clean = line.replace(/\s*\/\/.*$/, "").replace(/\.$/, "").trim();
      if (!clean) continue;

      let ruleMatch = clean.match(/^([a-zA-Z0-9_]+(\(.*\))?)\s*:-\s*(.+)$/);
      if (ruleMatch) {
        let head = ruleMatch[1].trim();
        let body = ruleMatch[3].trim();

        // Extract atomic conditions from body
        let conditions = body.split(/&|\|/).map(s => s.trim()).filter(Boolean);

        rules.push({
          id: "rule_" + Math.random().toString(36).substr(2, 9),
          head,
          body,
          conditions,
          raw: line
        });
      } else {
        // Simple fact or initial belief
        rules.push({
          id: "fact_" + Math.random().toString(36).substr(2, 9),
          head: clean,
          body: "true",
          conditions: [],
          raw: line
        });
      }
    }

    return rules;
  }

  /**
   * Serializes Model into clean Agent ASL format
   */
  static generateAgentAsl(agentName, initialGoal, initialBeliefs, goals, plans, recoveryPlans) {
    let output = "";

    // Header includes
    output += `{ include("$jacamo/templates/common-moise.asl") }\n`;
    output += `{ include("$moise/asl/org-obedient.asl") }\n`;
    output += `{ include("${agentName}/belief.asl") }\n\n`;

    // Initial beliefs
    if (initialBeliefs && initialBeliefs.length > 0) {
      output += `// --- Initial Beliefs ---\n`;
      for (let b of initialBeliefs) {
        output += `${b}.\n`;
      }
      output += "\n";
    }

    // Initial Goal
    if (initialGoal) {
      output += `!${initialGoal}.\n\n`;
    }

    // Plans organized by Goal
    output += `// =================================================================\n`;
    output += `// GOAL PLANS EXECUTION FLOW\n`;
    output += `// =================================================================\n\n`;

    const normalPlans = plans.filter(p => !p.isRecovery);
    for (let plan of normalPlans) {
      output += `+!${plan.goalName}${plan.goalArgs ? `(${plan.goalArgs})` : ""}`;
      if (plan.context && plan.context.trim()) {
        output += ` : ${plan.context.trim()}`;
      }
      output += `\n   <- `;

      if (plan.bodyText && plan.bodyText.trim()) {
        const bodyLines = plan.bodyText.split(";").map(s => s.trim()).filter(Boolean);
        if (bodyLines.length <= 1) {
          output += `${bodyLines[0] || ""}.\n\n`;
        } else {
          output += bodyLines.join(";\n      ") + `.\n\n`;
        }
      } else if (plan.actions && plan.actions.length > 0) {
        output += plan.actions.join(";\n      ") + `.\n\n`;
      } else {
        output += `.print("Executing ${plan.goalName}").\n\n`;
      }
    }

    // Recovery Plans
    const recPlans = plans.filter(p => p.isRecovery).concat(recoveryPlans || []);
    const uniqueRecs = [];
    const seen = new Set();
    for (let r of recPlans) {
      if (!seen.has(r.goalName)) {
        seen.add(r.goalName);
        uniqueRecs.push(r);
      }
    }

    if (uniqueRecs.length > 0) {
      output += `// =================================================================\n`;
      output += `// RECOVERY PLANS (FAILURE & ADAPTATION HANDLERS)\n`;
      output += `// =================================================================\n\n`;

      for (let r of uniqueRecs) {
        output += `+!${r.goalName}${r.goalArgs ? `(${r.goalArgs})` : ""}`;
        if (r.context && r.context.trim()) {
          output += ` : ${r.context.trim()}`;
        }
        output += `\n   <- `;

        if (r.bodyText && r.bodyText.trim()) {
          const bodyLines = r.bodyText.split(";").map(s => s.trim()).filter(Boolean);
          output += bodyLines.join(";\n      ") + `.\n\n`;
        } else if (r.actions && r.actions.length > 0) {
          output += r.actions.join(";\n      ") + `.\n\n`;
        } else {
          output += `.print("Recovery action for ${r.goalName}").\n\n`;
        }
      }
    }

    return output;
  }

  /**
   * Serializes Belief Rules into clean belief.asl
   */
  static generateBeliefAsl(beliefRules) {
    let output = "";
    output += `// =================================================================\n`;
    output += `// BELIEF DEDUCTION RULES (Horn-Clauses)\n`;
    output += `// =================================================================\n\n`;

    for (let r of beliefRules) {
      if (r.body && r.body !== "true") {
        output += `${r.head.padEnd(20)} :- ${r.body}.\n`;
      } else {
        output += `${r.head}.\n`;
      }
    }

    return output;
  }

  /**
   * Generates JaCaMo .jcm Failure Model configuration block
   */
  static generateJcmFailureConfig(agentName, failureMappings) {
    let output = `// JaCaMo Failure Model Configuration for Agent: ${agentName}\n\n`;

    // Group mappings by Target Goal
    const byGoal = {};
    for (let m of failureMappings) {
      if (!byGoal[m.targetGoal]) byGoal[m.targetGoal] = [];
      byGoal[m.targetGoal].push(m);
    }

    for (let goal in byGoal) {
      output += `failure ${goal} {\n`;
      for (let err of byGoal[goal]) {
        output += `    error ${err.errorName || err.beliefCondition + "_error"} {\n`;
        output += `        conditions: ${err.beliefCondition}\n`;
        output += `        recovery-activities: \n`;
        output += `            "goal:${err.recoveryPlan}",\n`;
        if (err.envAdaptation) {
          output += `            "env:${err.envAdaptation}",\n`;
        }
        if (err.orgAdaptation) {
          output += `            "org:${err.orgAdaptation}",\n`;
        }
        output += `            "goal:${goal}" // Retry target goal\n`;
        output += `    }\n`;
      }
      output += `}\n\n`;
    }

    return output;
  }
}
