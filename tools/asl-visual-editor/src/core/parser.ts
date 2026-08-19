import {
  AgentSpeakIR,
  GoalNode,
  PlanNode,
  BodyElement,
  BodyElementType,
  Trigger,
  TriggerOperator,
  TriggerEventType,
  Belief,
  Rule,
  UnmappedCode
} from '../types/asl-ir';

export class AgentSpeakParser {
  public parse(source: string): AgentSpeakIR {
    const directives: string[] = [];
    const beliefs: Belief[] = [];
    const rules: Rule[] = [];
    const parsedPlans: PlanNode[] = [];
    const unmapped: UnmappedCode[] = [];

    const statements = this.splitTopLevelStatements(source);

    for (let index = 0; index < statements.length; index++) {
      const stmt = statements[index].trim();
      if (!stmt) continue;

      // 1. Directives (e.g. { include("...") })
      if (stmt.startsWith('{') && stmt.endsWith('}')) {
        directives.push(stmt);
        continue;
      }

      // 2. Standalone Comments
      if (stmt.startsWith('//') || stmt.startsWith('/*')) {
        unmapped.push({
          id: `comment_${index}`,
          type: 'COMMENT',
          raw: stmt,
          position: index
        });
        continue;
      }

      // 3. Plans (Triggers start with +, -, ^, or plan label @)
      if (stmt.startsWith('@') || /^[+\-^]/.test(stmt)) {
        const plan = this.parsePlan(stmt, index);
        if (plan) {
          // Check if it's an achieve goal trigger (+! or +!^) or failure handler (-!)
          if (plan.trigger.eventType === '!' || plan.trigger.eventType === '!^') {
            parsedPlans.push(plan);
          } else {
            // Belief event triggers like +belief(X) : ... <- ...
            unmapped.push({
              id: `belief_plan_${index}`,
              type: 'BELIEF_EVENT_PLAN',
              raw: stmt,
              position: index
            });
          }
        } else {
          unmapped.push({
            id: `unknown_${index}`,
            type: 'UNKNOWN',
            raw: stmt,
            position: index
          });
        }
        continue;
      }

      // 4. Rules (containing ':-')
      if (stmt.includes(':-')) {
        const rule = this.parseRule(stmt, index);
        if (rule) rules.push(rule);
        continue;
      }

      // 5. Initial Beliefs (e.g. belief(a, b)[source(percept)].)
      if (stmt.endsWith('.')) {
        const belief = this.parseBelief(stmt, index);
        if (belief) beliefs.push(belief);
        else {
          unmapped.push({
            id: `unmapped_${index}`,
            type: 'UNKNOWN',
            raw: stmt,
            position: index
          });
        }
        continue;
      }

      unmapped.push({
        id: `unmapped_${index}`,
        type: 'UNKNOWN',
        raw: stmt,
        position: index
      });
    }

    // Group plans into GoalNodes (OR-branching)
    const goals = this.groupPlansIntoGoals(parsedPlans);

    return {
      directives,
      beliefs,
      rules,
      goals,
      unmapped
    };
  }

  // ==========================================================================
  // Plan Parser
  // ==========================================================================

  private parsePlan(stmt: string, index: number): PlanNode | null {
    try {
      let rest = stmt;
      let label: string | undefined;
      const annotations: string[] = [];

      // Extract label: @label[annotations]
      if (rest.startsWith('@')) {
        const colonIdx = rest.indexOf('+') !== -1 ? rest.indexOf('+') : rest.search(/[+\-^]/);
        if (colonIdx !== -1) {
          const labelPart = rest.substring(1, colonIdx).trim();
          rest = rest.substring(colonIdx).trim();

          const labelAnnotMatch = labelPart.match(/^([a-zA-Z0-9_]+)(\[(.*)\])?$/);
          if (labelAnnotMatch) {
            label = labelAnnotMatch[1];
            if (labelAnnotMatch[3]) {
              annotations.push(...this.splitTopLevel(labelAnnotMatch[3], ','));
            }
          } else {
            label = labelPart;
          }
        }
      }

      // Extract Trigger, Context, and Body
      // Format: Trigger : Context <- Body. OR Trigger <- Body. OR Trigger : Context.
      let triggerStr = '';
      let contextStr: string | null = null;
      let bodyStr = '';

      const arrowIdx = this.findTopLevelOperator(rest, '<-');
      const hasArrow = arrowIdx !== -1;

      let headerPart = hasArrow ? rest.substring(0, arrowIdx).trim() : rest.replace(/\.$/, '').trim();
      bodyStr = hasArrow ? rest.substring(arrowIdx + 2).replace(/\.$/, '').trim() : '';

      const colonIdx = this.findTopLevelOperator(headerPart, ':');
      if (colonIdx !== -1) {
        triggerStr = headerPart.substring(0, colonIdx).trim();
        contextStr = headerPart.substring(colonIdx + 1).trim();
      } else {
        triggerStr = headerPart.trim();
      }

      const trigger = this.parseTrigger(triggerStr);
      if (!trigger) return null;

      const body = this.parsePlanBody(bodyStr);

      return {
        id: label || `plan_${trigger.functor}_${index + 1}`,
        label,
        trigger,
        context: contextStr && contextStr.length > 0 ? contextStr : null,
        body,
        and_branches: body,
        annotations,
        isFailureRecovery: trigger.operator === '-' && trigger.eventType === '!',
        raw: stmt
      };
    } catch (e) {
      console.warn(`Failed to parse plan: ${stmt}`, e);
      return null;
    }
  }

  // ==========================================================================
  // Trigger Parser
  // ==========================================================================

  private parseTrigger(str: string): Trigger | null {
    const match = str.match(/^([+\-^])(!\^|!|\?)?([a-zA-Z0-9_]+)(\((.*)\))?(\[(.*)\])?$/);
    if (!match) return null;

    const operator = match[1] as TriggerOperator;
    const eventType = (match[2] || '') as TriggerEventType;
    const functor = match[3];
    const paramsStr = match[5];
    const params = paramsStr ? this.splitTopLevel(paramsStr, ',').map(s => s.trim()) : [];

    return {
      operator,
      eventType,
      functor,
      params,
      raw: str
    };
  }

  // ==========================================================================
  // Body Elements Parser
  // ==========================================================================

  private parsePlanBody(bodyStr: string): BodyElement[] {
    if (!bodyStr || !bodyStr.trim()) return [];

    const rawSteps = this.splitTopLevel(bodyStr, ';');
    const elements: BodyElement[] = [];

    for (let i = 0; i < rawSteps.length; i++) {
      const step = rawSteps[i].trim();
      if (!step) continue;

      let type: BodyElementType = 'ACTION';
      let functor = '';
      let params: string[] = [];
      let targetGoalSignature: string | undefined;

      if (step.startsWith('.and_branches') || step.startsWith('.or_branches')) {
        const isOr = step.startsWith('.or_branches');
        const branchType: 'AND' | 'OR' = isOr ? 'OR' : 'AND';

        // Extract inner subgoals/actions: .and_branches([g1, g2]) or .or_branches([g1, g2])
        const innerListMatch = step.match(/\(\s*\[(.*)\]\s*\)/);
        if (innerListMatch && innerListMatch[1]) {
          const subItems = this.splitTopLevel(innerListMatch[1], ',');
          for (let sIdx = 0; sIdx < subItems.length; sIdx++) {
            const rawSub = subItems[sIdx].trim();
            if (!rawSub) continue;

            let subType: BodyElementType = 'SUB_GOAL';
            let cleanSub = rawSub;

            if (cleanSub.startsWith('!!')) {
              subType = 'CONCURRENT_SUB_GOAL';
              cleanSub = cleanSub.substring(2).trim();
            } else if (cleanSub.startsWith('!')) {
              subType = 'SUB_GOAL';
              cleanSub = cleanSub.substring(1).trim();
            } else if (cleanSub.startsWith('?')) {
              subType = 'TEST_GOAL';
              cleanSub = cleanSub.substring(1).trim();
            } else if (cleanSub.startsWith('-+')) {
              subType = 'BELIEF_UPDATE';
              cleanSub = cleanSub.substring(2).trim();
            } else if (cleanSub.startsWith('+')) {
              subType = 'BELIEF_ADD';
              cleanSub = cleanSub.substring(1).trim();
            } else if (cleanSub.startsWith('-')) {
              subType = 'BELIEF_DEL';
              cleanSub = cleanSub.substring(1).trim();
            } else if (cleanSub.startsWith('.') || cleanSub.includes('::') || /^[a-zA-Z0-9_]+\.[a-zA-Z0-9_]+/.test(cleanSub)) {
              subType = 'INTERNAL_ACTION';
            } else {
              subType = 'SUB_GOAL';
            }

            const subParsed = this.parsePredicate(cleanSub);
            if (subParsed.functor) {
              const targetGoalSig = (subType === 'SUB_GOAL' || subType === 'CONCURRENT_SUB_GOAL')
                ? `${subParsed.functor}/${subParsed.params.length}`
                : undefined;

              elements.push({
                id: `elem_${i + 1}_sub_${sIdx + 1}_${subParsed.functor.replace(/[^a-zA-Z0-9_]/g, '_')}`,
                type: subType,
                functor: subParsed.functor,
                params: subParsed.params,
                targetGoalSignature: targetGoalSig,
                branchType,
                raw: subType === 'SUB_GOAL' && !rawSub.startsWith('!') ? `!${cleanSub}` : rawSub
              });
            }
          }
        }
        continue;
      } else if (step.startsWith('!!')) {
        type = 'CONCURRENT_SUB_GOAL';
        const parsed = this.parsePredicate(step.substring(2).trim());
        functor = parsed.functor;
        params = parsed.params;
        targetGoalSignature = `${functor}/${params.length}`;
      } else if (step.startsWith('!')) {
        type = 'SUB_GOAL';
        const parsed = this.parsePredicate(step.substring(1).trim());
        functor = parsed.functor;
        params = parsed.params;
        targetGoalSignature = `${functor}/${params.length}`;
      } else if (step.startsWith('?')) {
        type = 'TEST_GOAL';
        const parsed = this.parsePredicate(step.substring(1).trim());
        functor = parsed.functor;
        params = parsed.params;
      } else if (step.startsWith('-+')) {
        type = 'BELIEF_UPDATE';
        const parsed = this.parsePredicate(step.substring(2).trim());
        functor = parsed.functor;
        params = parsed.params;
      } else if (step.startsWith('+')) {
        type = 'BELIEF_ADD';
        const parsed = this.parsePredicate(step.substring(1).trim());
        functor = parsed.functor;
        params = parsed.params;
      } else if (step.startsWith('-')) {
        type = 'BELIEF_DEL';
        const parsed = this.parsePredicate(step.substring(1).trim());
        functor = parsed.functor;
        params = parsed.params;
      } else if (step.startsWith('.') || step.includes('::') || /^[a-zA-Z0-9_]+\.[a-zA-Z0-9_]+/.test(step)) {
        type = 'INTERNAL_ACTION';
        const parsed = this.parsePredicate(step);
        functor = parsed.functor;
        params = parsed.params;
      } else {
        type = 'ACTION';
        const parsed = this.parsePredicate(step);
        functor = parsed.functor;
        params = parsed.params;
      }

      elements.push({
        id: `elem_${i + 1}_${functor || 'act'}`,
        type,
        functor,
        params,
        targetGoalSignature,
        raw: step
      });
    }

    return elements;
  }

  // ==========================================================================
  // Goal Grouping & Dependency Extraction (OR/AND Branching)
  // ==========================================================================

  private groupPlansIntoGoals(plans: PlanNode[]): GoalNode[] {
    const goalMap = new Map<string, GoalNode>();

    // 1. Group plans by signature: functor/arity
    for (const plan of plans) {
      const signature = `${plan.trigger.functor}/${plan.trigger.params.length}`;
      if (!goalMap.has(signature)) {
        goalMap.set(signature, {
          id: `goal_${plan.trigger.functor}_${plan.trigger.params.length}`,
          signature,
          functor: plan.trigger.functor,
          arity: plan.trigger.params.length,
          params: plan.trigger.params,
          triggerType: plan.trigger.eventType === '?' ? 'test' : 'achieve',
          plans: [],
          or_branches: [],
          isRoot: true,
          isRecursive: false
        });
      }
      goalMap.get(signature)!.plans.push(plan);
      goalMap.get(signature)!.or_branches.push(plan);
    }

    // 2. Identify Sub-goal calls to compute root nodes & recursion
    const subGoalSignatures = new Set<string>();

    for (const goal of goalMap.values()) {
      for (const plan of goal.plans) {
        for (const elem of plan.body) {
          if (elem.type === 'SUB_GOAL' || elem.type === 'CONCURRENT_SUB_GOAL') {
            if (elem.targetGoalSignature) {
              subGoalSignatures.add(elem.targetGoalSignature);
              if (elem.targetGoalSignature === goal.signature) {
                goal.isRecursive = true;
              }
            }
          }
        }
      }
    }

    for (const goal of goalMap.values()) {
      goal.isRoot = !subGoalSignatures.has(goal.signature);
    }

    return Array.from(goalMap.values());
  }

  // ==========================================================================
  // Helper Utilities (Lexical & Expression Splitting)
  // ==========================================================================

  private parsePredicate(str: string): { functor: string; params: string[] } {
    const match = str.match(/^([.\w:]+)(\((.*)\))?(\[(.*)\])?$/);
    if (!match) return { functor: str.trim(), params: [] };

    const functor = match[1];
    const params = match[3] ? this.splitTopLevel(match[3], ',').map(s => s.trim()) : [];
    return { functor, params };
  }

  private parseBelief(stmt: string, index: number): Belief | null {
    const clean = stmt.replace(/\.$/, '').trim();
    const match = clean.match(/^([a-zA-Z0-9_]+)(\((.*)\))?(\[(.*)\])?$/);
    if (!match) return null;

    const functor = match[1];
    const params = match[3] ? this.splitTopLevel(match[3], ',').map(s => s.trim()) : [];
    const annotations = match[5] ? this.splitTopLevel(match[5], ',').map(s => s.trim()) : undefined;

    return {
      id: `belief_${functor}_${index}`,
      functor,
      params,
      annotations,
      raw: stmt
    };
  }

  private parseRule(stmt: string, index: number): Rule | null {
    const clean = stmt.replace(/\.$/, '').trim();
    const parts = clean.split(':-');
    if (parts.length < 2) return null;

    return {
      id: `rule_${index}`,
      head: parts[0].trim(),
      body: parts.slice(1).join(':-').trim(),
      raw: stmt
    };
  }

  private splitTopLevelStatements(source: string): string[] {
    const statements: string[] = [];
    let current = '';
    let inParen = 0;
    let inBracket = 0;
    let inBrace = 0;
    let inString = false;
    let stringChar = '';
    let inLineComment = false;
    let inBlockComment = false;

    for (let i = 0; i < source.length; i++) {
      const char = source[i];
      const nextChar = i + 1 < source.length ? source[i + 1] : '';
      const prevChar = i > 0 ? source[i - 1] : '';

      // Handle Line Comment
      if (inLineComment) {
        current += char;
        if (char === '\n') {
          inLineComment = false;
          if (current.trim()) {
            statements.push(current.trim());
            current = '';
          }
        }
        continue;
      }

      // Handle Block Comment
      if (inBlockComment) {
        current += char;
        if (char === '/' && prevChar === '*') {
          inBlockComment = false;
          if (current.trim()) {
            statements.push(current.trim());
            current = '';
          }
        }
        continue;
      }

      // Handle Strings
      if (inString) {
        current += char;
        if (char === stringChar && prevChar !== '\\') inString = false;
        continue;
      }

      if (char === '"' || char === "'") {
        inString = true;
        stringChar = char;
        current += char;
        continue;
      }

      // Start comments
      if (char === '/' && nextChar === '/') {
        if (current.trim()) {
          statements.push(current.trim());
          current = '';
        }
        inLineComment = true;
        current += char;
        continue;
      }

      if (char === '/' && nextChar === '*') {
        if (current.trim()) {
          statements.push(current.trim());
          current = '';
        }
        inBlockComment = true;
        current += char;
        continue;
      }

      if (char === '(') inParen++;
      else if (char === ')') inParen--;
      else if (char === '[') inBracket++;
      else if (char === ']') inBracket--;
      else if (char === '{') inBrace++;
      else if (char === '}') inBrace--;

      current += char;

      // Handle Directives { include(...) }
      if (inBrace === 0 && char === '}' && current.trim().startsWith('{') && inParen === 0 && inBracket === 0) {
        statements.push(current.trim());
        current = '';
        continue;
      }

      // Handle dot-terminated statement
      // A statement terminator '.' must NOT be the leading dot of an internal action like .print or .and_branches
      const isInternalActionDot = char === '.' && /[a-zA-Z_]/.test(nextChar);
      if (char === '.' && !isInternalActionDot && inParen === 0 && inBracket === 0 && inBrace === 0) {
        statements.push(current.trim());
        current = '';
        continue;
      }
    }

    if (current.trim()) statements.push(current.trim());
    return statements;
  }

  private findTopLevelOperator(str: string, op: string): number {
    let inParen = 0;
    let inBracket = 0;
    let inString = false;

    for (let i = 0; i <= str.length - op.length; i++) {
      const char = str[i];
      if (char === '"' || char === "'") {
        inString = !inString;
        continue;
      }
      if (inString) continue;

      if (char === '(') inParen++;
      else if (char === ')') inParen--;
      else if (char === '[') inBracket++;
      else if (char === ']') inBracket--;

      if (inParen === 0 && inBracket === 0 && str.substring(i, i + op.length) === op) {
        return i;
      }
    }
    return -1;
  }

  private splitTopLevel(str: string, separator: string): string[] {
    const result: string[] = [];
    let current = '';
    let inParen = 0;
    let inBracket = 0;
    let inBrace = 0;
    let inString = false;

    for (let i = 0; i < str.length; i++) {
      const char = str[i];
      if (char === '"' || char === "'") inString = !inString;
      if (inString) { current += char; continue; }

      if (char === '(') inParen++;
      else if (char === ')') inParen--;
      else if (char === '[') inBracket++;
      else if (char === ']') inBracket--;
      else if (char === '{') inBrace++;
      else if (char === '}') inBrace--;

      if (char === separator && inParen === 0 && inBracket === 0 && inBrace === 0) {
        result.push(current.trim());
        current = '';
        continue;
      }
      current += char;
    }
    if (current.trim()) result.push(current.trim());
    return result;
  }
}
