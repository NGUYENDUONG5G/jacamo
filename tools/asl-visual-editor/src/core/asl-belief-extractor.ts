export interface ExtractedBelief {
  literal: string;
  functor: string;
  category: 'initial' | 'rule_premise' | 'plan_context' | 'body_action';
  sourceFile?: string;
  description?: string;
}

export class AslBeliefExtractor {
  public static extractFromFiles(
    files: Array<{ filename: string; code: string }>
  ): ExtractedBelief[] {
    const results: ExtractedBelief[] = [];
    const seen = new Set<string>();

    for (const file of files) {
      const extracted = this.extractFromCode(file.code, file.filename);
      for (const item of extracted) {
        if (!seen.has(item.literal)) {
          seen.add(item.literal);
          results.push(item);
        }
      }
    }

    return results;
  }

  public static extractFromCode(code: string, filename: string = 'agent.asl'): ExtractedBelief[] {
    const list: ExtractedBelief[] = [];
    const seen = new Set<string>();

    const addBelief = (
      literal: string,
      category: ExtractedBelief['category'],
      description?: string
    ) => {
      const cleanLit = literal.trim().replace(/;$/, '').replace(/\.$/, '');
      if (!cleanLit || seen.has(cleanLit)) return;

      if (
        cleanLit.startsWith('include') ||
        cleanLit.startsWith('{') ||
        cleanLit.startsWith('!') ||
        cleanLit.startsWith('?') ||
        cleanLit.startsWith('.') ||
        cleanLit === 'true' ||
        cleanLit === 'false' ||
        cleanLit.includes('>') ||
        cleanLit.includes('<') ||
        cleanLit.includes('==') ||
        cleanLit.includes('=')
      ) {
        return;
      }

      const match = cleanLit.match(/^([a-zA-Z0-9_]+)/);
      const functor = match ? match[1] : cleanLit;

      seen.add(cleanLit);
      list.push({
        literal: cleanLit,
        functor,
        category,
        sourceFile: filename,
        description
      });
    };

    const cleanCode = code
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/\/\/.*$/gm, '');

    const lines = cleanCode.split('\n');

    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line) continue;

      if (line.includes(':-')) {
        const parts = line.split(':-');
        const bodyPart = parts[1]?.replace(/\.$/, '').trim() || '';

        const terms = bodyPart.split('&').map(t => t.trim());
        for (const term of terms) {
          let cleanTerm = term;
          if (cleanTerm.startsWith('not ')) {
            continue;
          }
          if (cleanTerm) {
            addBelief(cleanTerm, 'rule_premise', `Nguyên nhân cơ sở [${filename}]`);
          }
        }
        continue;
      }

      if (
        line.endsWith('.') &&
        !line.startsWith('+') &&
        !line.startsWith('-') &&
        !line.startsWith('!') &&
        !line.startsWith('?') &&
        !line.startsWith('{') &&
        !line.includes('<-')
      ) {
        const fact = line.slice(0, -1).trim();
        if (fact) {
          addBelief(fact, 'initial', `Niềm tin khởi tạo [${filename}]`);
        }
      }
    }

    const planRegex = /[+-][!?]?\w+(?:\([^)]*\))?\s*:\s*([^<]+)\s*<-/g;
    let planMatch;
    while ((planMatch = planRegex.exec(cleanCode)) !== null) {
      const contextStr = planMatch[1].trim();
      if (contextStr && contextStr !== 'true') {
        const contextLiterals = contextStr.split('&').map(c => c.trim());
        for (const lit of contextLiterals) {
          if (!lit.startsWith('not ') && !lit.includes('>') && !lit.includes('<') && !lit.includes('=')) {
            addBelief(lit, 'plan_context', `Điều kiện ngữ cảnh plan [${filename}]`);
          }
        }
      }
    }

    const bodyAddBelRegex = /\+\s*([a-zA-Z0-9_]+(?:\([^)]*\))?)\s*(?:\[[^\]]*\])?\s*;/g;
    let addMatch;
    while ((addMatch = bodyAddBelRegex.exec(cleanCode)) !== null) {
      const bel = addMatch[1].trim();
      addBelief(bel, 'body_action', `Belief tạo từ action [${filename}]`);
    }

    return list;
  }
}
