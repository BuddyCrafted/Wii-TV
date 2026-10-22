// Calls into the desktop app (src-tauri/src/main.rs). Opened as a plain web page (for a quick look in a
// browser), it answers with sample data instead, so the menu can be developed without building the app.
const T = window.__TAURI__;
export const native = !!T;

const SAMPLE = [
  { key: 'steam:620', label: 'Portal 2', kind: 'steam', target: 'steam://rungameid/620' },
  { key: 'steam:413150', label: 'Stardew Valley', kind: 'steam', target: 'steam://rungameid/413150' },
  { key: 'steam:1086940', label: "Baldur's Gate 3", kind: 'steam', target: 'steam://rungameid/1086940' },
  { key: 'epic:Fortnite', label: 'Fortnite', kind: 'epic', target: 'com.epicgames.launcher://apps/Fortnite?action=launch' },
];

export async function invoke(cmd, args = {}) {
  if (T) return T.core.invoke(cmd, args);
  switch (cmd) {
    case 'channels': return SAMPLE;
    case 'info': return { build: 0, os: 'web', version: 'preview', autostart: false };
    case 'check_update': return null;
    default: console.log('preview:', cmd, args); return null;
  }
}

export function appWindow() {
  return T ? T.window.getCurrentWindow() : null;
}
