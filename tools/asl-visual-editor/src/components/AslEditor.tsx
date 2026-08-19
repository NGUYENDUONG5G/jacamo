import React from 'react';
import Editor from '@monaco-editor/react';

interface AslEditorProps {
  value: string;
  onChange: (value: string) => void;
}

export const AslEditor: React.FC<AslEditorProps> = ({ value, onChange }) => {
  const handleEditorChange = (val: string | undefined) => {
    if (val !== undefined) {
      onChange(val);
    }
  };

  const handleEditorWillMount = (monaco: any) => {
    monaco.languages.register({ id: 'agentspeak' });

    monaco.languages.setMonarchTokensProvider('agentspeak', {
      tokenizer: {
        root: [
          [/\/\/.*$/, 'comment'],
          [/\/\*/, 'comment', '@comment'],
          [/\{.*?\}/, 'keyword.directive'],
          [/@[a-zA-Z0-9_]+(\[.*?\])?/, 'tag'],
          [/[+\-^]!(\^)?([a-zA-Z0-9_]+)/, 'type.goal'],
          [/[+\-^]\?([a-zA-Z0-9_]+)/, 'type.testgoal'],
          [/[+\-^]([a-zA-Z0-9_]+)/, 'variable.trigger'],
          [/<-/, 'operator.arrow'],
          [/:/, 'operator.context'],
          [/&|\||not/, 'keyword.operator'],
          [/\.[a-zA-Z0-9_]+/, 'keyword.action'],
          [/\b[A-Z][a-zA-Z0-9_]*\b/, 'variable.parameter'],
          [/\b[a-z][a-zA-Z0-9_]*\b/, 'identifier'],
          [/"([^"\\]|\\.)*"/, 'string'],
          [/\b\d+(\.\d+)?\b/, 'number']
        ],
        comment: [
          [/[^/*]+/, 'comment'],
          [/\*\//, 'comment', '@pop'],
          [/[/*]/, 'comment']
        ]
      }
    });

    monaco.editor.defineTheme('agentspeak-dark', {
      base: 'vs-dark',
      inherit: true,
      rules: [
        { token: 'comment', foreground: '6A737D', fontStyle: 'italic' },
        { token: 'tag', foreground: 'E5C07B', fontStyle: 'bold' },
        { token: 'type.goal', foreground: '61AFEF', fontStyle: 'bold' },
        { token: 'type.testgoal', foreground: '98C379' },
        { token: 'variable.trigger', foreground: 'D19A66' },
        { token: 'operator.arrow', foreground: 'C678DD', fontStyle: 'bold' },
        { token: 'operator.context', foreground: 'E06C75', fontStyle: 'bold' },
        { token: 'keyword.action', foreground: '56B6C2' },
        { token: 'variable.parameter', foreground: 'E5C07B' },
        { token: 'string', foreground: '98C379' },
        { token: 'number', foreground: 'D19A66' }
      ],
      colors: {
        'editor.background': '#0f172a',
        'editor.foreground': '#f8fafc',
        'editor.lineHighlightBackground': '#1e293b50',
        'editorLineNumber.foreground': '#475569'
      }
    });
  };

  return (
    <div className="monaco-wrapper">
      <Editor
        height="100%"
        defaultLanguage="agentspeak"
        language="agentspeak"
        theme="agentspeak-dark"
        value={value}
        onChange={handleEditorChange}
        beforeMount={handleEditorWillMount}
        options={{
          minimap: { enabled: false },
          fontSize: 13,
          lineNumbers: 'on',
          roundedSelection: false,
          scrollBeyondLastLine: false,
          automaticLayout: true,
          fontFamily: "'Fira Code', 'Cascadia Code', monospace",
          renderWhitespace: 'selection',
          tabSize: 4
        }}
      />
    </div>
  );
};
