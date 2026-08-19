import { AgentSpeakIR, GoalNode, PlanNode, BodyElement } from '../types/asl-ir';

export class AgentSpeakGenerator {
  public generate(ir: AgentSpeakIR): string {
    const lines: string[] = [];

    // 1. Directives (e.g. { include("...") })
    if (ir.directives && ir.directives.length > 0) {
      lines.push('// === Directives ===');
      ir.directives.forEach(d => lines.push(d));
      lines.push('');
    }

    // 2. Initial Beliefs
    if (ir.beliefs && ir.beliefs.length > 0) {
      lines.push('// === Initial Beliefs ===');
      for (const belief of ir.beliefs) {
        lines.push(this.formatBelief(belief));
      }
      lines.push('');
    }

    // 3. Logical Rules
    if (ir.rules && ir.rules.length > 0) {
      lines.push('// === Inference Rules ===');
      for (const rule of ir.rules) {
        lines.push(`${rule.head} :- ${rule.body}.`);
      }
      lines.push('');
    }

    // 4. Goal Plans (Grouped Hierarchically by Goal)
    if (ir.goals && ir.goals.length > 0) {
      lines.push('// ============================================================================');
      lines.push('// Plans & Goal Decompositions');
      lines.push('// ============================================================================');
      lines.push('');

      for (const goal of ir.goals) {
        lines.push(`// --- Goal: ${goal.signature} ${goal.isRoot ? '(Root Goal)' : ''} ---`);
        for (const plan of goal.plans) {
          lines.push(this.formatPlan(plan));
          lines.push('');
        }
      }
    }

    // 5. Unmapped & Preserved Code (Belief events, unknown blocks)
    if (ir.unmapped && ir.unmapped.length > 0) {
      const nonCommentUnmapped = ir.unmapped.filter(u => u.type !== 'COMMENT');
      if (nonCommentUnmapped.length > 0) {
        lines.push('// === Belief Event Handlers & Additional Code ===');
        for (const item of nonCommentUnmapped) {
          lines.push(item.raw);
        }
        lines.push('');
      }
    }

    return lines.join('\n').trim() + '\n';
  }

  private formatBelief(b: { functor: string; params: string[]; annotations?: string[] }): string {
    const paramsStr = b.params.length > 0 ? `(${b.params.join(', ')})` : '';
    const annotStr = b.annotations && b.annotations.length > 0 ? `[${b.annotations.join(', ')}]` : '';
    return `${b.functor}${paramsStr}${annotStr}.`;
  }

  private formatPlan(plan: PlanNode): string {
    const lines: string[] = [];

    // Plan label and annotations
    if (plan.label) {
      const annots = plan.annotations.length > 0 ? `[${plan.annotations.join(', ')}]` : '';
      lines.push(`@${plan.label}${annots}`);
    }

    // Trigger
    const paramStr = plan.trigger.params.length > 0 ? `(${plan.trigger.params.join(', ')})` : '';
    const triggerStr = `${plan.trigger.operator}${plan.trigger.eventType}${plan.trigger.functor}${paramStr}`;

    // Context / Preconditions
    let header = triggerStr;
    if (plan.context && plan.context.trim() && plan.context.trim() !== 'true') {
      header += ` : ${plan.context.trim()}`;
    }

    // Body Elements
    if (plan.body.length === 0) {
      lines.push(`${header}.`);
    } else {
      lines.push(`${header} <-`);
      const bodyLines = plan.body.map((elem, idx) => {
        const isLast = idx === plan.body.length - 1;
        const separator = isLast ? '.' : ';';
        return `    ${this.formatBodyElement(elem)}${separator}`;
      });
      lines.push(bodyLines.join('\n'));
    }

    return lines.join('\n');
  }

  private formatBodyElement(elem: BodyElement): string {
    const paramStr = elem.params.length > 0 ? `(${elem.params.join(', ')})` : '';

    switch (elem.type) {
      case 'SUB_GOAL':
        return `!${elem.functor}${paramStr}`;
      case 'CONCURRENT_SUB_GOAL':
        return `!!${elem.functor}${paramStr}`;
      case 'TEST_GOAL':
        return `?${elem.functor}${paramStr}`;
      case 'BELIEF_ADD':
        return `+${elem.functor}${paramStr}`;
      case 'BELIEF_DEL':
        return `-${elem.functor}${paramStr}`;
      case 'BELIEF_UPDATE':
        return `-+${elem.functor}${paramStr}`;
      case 'INTERNAL_ACTION':
      case 'ACTION':
      default:
        return `${elem.functor}${paramStr}`;
    }
  }
}
