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

    monaco.editor.defineTheme('agentspeak-light', {
      base: 'vs',
      inherit: true,
      rules: [
        { token: 'comment', foreground: '6A737D', fontStyle: 'italic' },
        { token: 'tag', foreground: 'B45309', fontStyle: 'bold' },
        { token: 'type.goal', foreground: '0284C7', fontStyle: 'bold' },
        { token: 'type.testgoal', foreground: '059669' },
        { token: 'variable.trigger', foreground: 'D97706' },
        { token: 'operator.arrow', foreground: '7C3AED', fontStyle: 'bold' },
        { token: 'operator.context', foreground: 'E11D48', fontStyle: 'bold' },
        { token: 'keyword.action', foreground: '0891B2' },
        { token: 'variable.parameter', foreground: 'B45309' },
        { token: 'string', foreground: '059669' },
        { token: 'number', foreground: 'D97706' }
      ],
      colors: {
        'editor.background': '#ffffff',
        'editor.foreground': '#0f172a',
        'editor.lineHighlightBackground': '#f8fafc',
        'editorLineNumber.foreground': '#94a3b8',
        'editorGutter.background': '#ffffff'
      }
    });
  };

  return (
    <div className="monaco-wrapper">
      <Editor
        height="100%"
        defaultLanguage="agentspeak"
        language="agentspeak"
        theme="agentspeak-light"
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
