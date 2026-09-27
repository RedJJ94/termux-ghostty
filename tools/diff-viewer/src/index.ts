import { FileDiff, parsePatchFiles, parseDiffFromFile, type FileDiffMetadata } from '@pierre/diffs';

interface RenderOptions {
  isDark?: boolean;
  diffStyle?: 'split' | 'unified';
  showLineNumbers?: boolean;
  isWordDiffEnabled?: boolean;
  fontSize?: string;
  fontFamily?: string;
}

interface AndroidBridge {
  onRenderComplete?: (fileCount: number, hunkCount: number) => void;
  onError?: (errorMessage: string) => void;
  onFileClick?: (fileName: string) => void;
}

declare global {
  interface Window {
    AndroidDiffBridge?: AndroidBridge;
    diffViewer: {
      renderPatch: (patchString: string, optionsJson?: string) => Promise<void>;
      renderFiles: (
        oldContent: string | null,
        newContent: string | null,
        filename: string,
        optionsJson?: string
      ) => Promise<void>;
      updateTheme: (isDark: boolean, bgColor?: string, fgColor?: string) => void;
      setDiffStyle: (style: 'split' | 'unified') => void;
      setLineNumbers: (show: boolean) => void;
      setWordDiff: (enabled: boolean) => void;
      clear: () => void;
    };
  }
}

let activeFileDiffInstances: FileDiff[] = [];
let currentOptions: RenderOptions = {
  isDark: true,
  diffStyle: 'unified',
  showLineNumbers: true,
  isWordDiffEnabled: true,
};

let lastRenderType: 'patch' | 'files' | null = null;
let lastPatchString: string = '';
let lastOldContent: string | null = null;
let lastNewContent: string | null = null;
let lastFilename: string = '';

const rootElement = document.getElementById('diff-root') || document.body;

function showMessage(text: string, isError = false) {
  rootElement.innerHTML = `
    <div style="
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 180px;
      padding: 32px 16px;
      color: ${isError ? '#ff6762' : 'var(--diffs-fg-number, #888)'};
      font-family: var(--diffs-font-family, monospace);
      font-size: 13px;
      text-align: center;
    ">
      <p style="margin: 0;">${text}</p>
    </div>
  `;
}

function createDiffInstance(options: RenderOptions): FileDiff {
  return new FileDiff({
    theme: {
      dark: 'pierre-dark',
      light: 'pierre-light',
    },
    themeType: options.isDark ? 'dark' : 'light',
    diffStyle: options.diffStyle || 'unified',
    disableLineNumbers: options.showLineNumbers === false,
    lineDiffType: options.isWordDiffEnabled === false ? 'none' : 'word',
    overflow: 'scroll',
    preferredHighlighter: 'shiki-js',
    tokenizeMaxLineLength: 1000,
    unsafeCSS: `
      [data-code] {
        touch-action: pan-x pan-y !important;
        -webkit-overflow-scrolling: touch !important;
      }
      [data-column-number], [data-gutter], [data-gutter-buffer] {
        touch-action: pan-x pan-y !important;
      }
    `,
  });
}

function cleanupActiveInstances() {
  for (const instance of activeFileDiffInstances) {
    try {
      instance.cleanUp?.();
    } catch {
      // ignore
    }
  }
  activeFileDiffInstances = [];
  rootElement.innerHTML = '';
}

async function renderPatch(patchString: string, optionsJson?: string) {
  lastRenderType = 'patch';
  lastPatchString = patchString;
  if (optionsJson) {
    try {
      const parsed = JSON.parse(optionsJson);
      currentOptions = { ...currentOptions, ...parsed };
    } catch {
      // ignore
    }
  }

  cleanupActiveInstances();

  if (!patchString || patchString.trim() === '') {
    showMessage('No changes detected in file.');
    window.AndroidDiffBridge?.onRenderComplete?.(0, 0);
    return;
  }

  // Check for binary diff
  if (patchString.includes('Binary files ') && patchString.includes(' differ')) {
    showMessage('Binary file changed (diff not available).');
    window.AndroidDiffBridge?.onRenderComplete?.(1, 0);
    return;
  }

  try {
    const patches = parsePatchFiles(patchString, undefined, false);
    if (!patches || patches.length === 0) {
      showMessage('No patch hunks found.');
      window.AndroidDiffBridge?.onRenderComplete?.(0, 0);
      return;
    }

    let totalFiles = 0;
    let totalHunks = 0;

    for (const patch of patches) {
      for (const fileDiff of patch.files) {
        totalFiles++;
        totalHunks += fileDiff.hunks?.length || 0;

        const instance = createDiffInstance(currentOptions);
        activeFileDiffInstances.push(instance);

        const container = document.createElement('div');
        container.className = 'file-diff-item';
        container.style.marginBottom = '16px';
        rootElement.appendChild(container);

        await instance.render({
          fileDiff,
          containerWrapper: container,
        });
      }
    }

    window.AndroidDiffBridge?.onRenderComplete?.(totalFiles, totalHunks);
  } catch (err: any) {
    console.error('Failed to parse or render patch:', err);
    showMessage(`Failed to render diff: ${err?.message || err}`, true);
    window.AndroidDiffBridge?.onError?.(String(err?.message || err));
  }
}

async function renderFiles(
  oldContent: string | null,
  newContent: string | null,
  filename: string,
  optionsJson?: string
) {
  lastRenderType = 'files';
  lastOldContent = oldContent;
  lastNewContent = newContent;
  lastFilename = filename;

  if (optionsJson) {
    try {
      const parsed = JSON.parse(optionsJson);
      currentOptions = { ...currentOptions, ...parsed };
    } catch {
      // ignore
    }
  }

  cleanupActiveInstances();

  if (oldContent == null && newContent == null) {
    showMessage('No content available for diff.');
    window.AndroidDiffBridge?.onRenderComplete?.(0, 0);
    return;
  }

  try {
    const oldFile = oldContent != null ? { name: filename, contents: oldContent } : null;
    const newFile = newContent != null ? { name: filename, contents: newContent } : null;

    let fileDiff: FileDiffMetadata;
    try {
      fileDiff = parseDiffFromFile(oldFile, newFile, undefined, false);
    } catch {
      // If parsing fails (e.g. both files empty or identical), fallback
      showMessage('No changes detected between file versions.');
      window.AndroidDiffBridge?.onRenderComplete?.(0, 0);
      return;
    }

    const instance = createDiffInstance(currentOptions);
    activeFileDiffInstances.push(instance);

    const container = document.createElement('div');
    container.className = 'file-diff-item';
    rootElement.appendChild(container);

    await instance.render({
      fileDiff,
      containerWrapper: container,
    });

    window.AndroidDiffBridge?.onRenderComplete?.(1, fileDiff.hunks?.length || 0);
  } catch (err: any) {
    console.error('Failed to render files diff:', err);
    showMessage(`Failed to render diff: ${err?.message || err}`, true);
    window.AndroidDiffBridge?.onError?.(String(err?.message || err));
  }
}

function updateTheme(isDark: boolean, bgColor?: string, fgColor?: string) {
  currentOptions.isDark = isDark;
  document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light');
  document.documentElement.style.colorScheme = isDark ? 'dark' : 'light';

  if (bgColor) {
    document.documentElement.style.setProperty('--diffs-bg', bgColor);
    document.body.style.backgroundColor = bgColor;
  } else {
    document.body.style.backgroundColor = isDark ? '#121212' : '#ffffff';
  }

  if (fgColor) {
    document.documentElement.style.setProperty('--diffs-fg', fgColor);
    document.body.style.color = fgColor;
  } else {
    document.body.style.color = isDark ? '#e0e0e0' : '#1a1a1a';
  }

  for (const instance of activeFileDiffInstances) {
    try {
      instance.setThemeType?.(isDark ? 'dark' : 'light');
    } catch {
      // ignore
    }
  }
}

function setDiffStyle(style: 'split' | 'unified') {
  if (currentOptions.diffStyle === style) return;
  currentOptions.diffStyle = style;
  for (const instance of activeFileDiffInstances) {
    try {
      instance.setOptions?.({ diffStyle: style });
    } catch {
      // ignore
    }
  }
}

function setLineNumbers(show: boolean) {
  if (currentOptions.showLineNumbers === show) return;
  currentOptions.showLineNumbers = show;
  for (const instance of activeFileDiffInstances) {
    try {
      instance.setOptions?.({ disableLineNumbers: !show });
    } catch {
      // ignore
    }
  }
}

function setWordDiff(enabled: boolean) {
  if (currentOptions.isWordDiffEnabled === enabled) return;
  currentOptions.isWordDiffEnabled = enabled;
  for (const instance of activeFileDiffInstances) {
    try {
      instance.setOptions?.({ lineDiffType: enabled ? 'word' : 'none' });
    } catch {
      // ignore
    }
  }
}

function clear() {
  cleanupActiveInstances();
  lastRenderType = null;
  lastPatchString = '';
  lastOldContent = null;
  lastNewContent = null;
  lastFilename = '';
}

window.diffViewer = {
  renderPatch,
  renderFiles,
  updateTheme,
  setDiffStyle,
  setLineNumbers,
  setWordDiff,
  clear,
};
