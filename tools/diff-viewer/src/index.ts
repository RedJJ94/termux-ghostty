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

  try {
    const patches = parsePatchFiles(patchString, undefined, false);
    if (!patches || patches.length === 0) {
      if (/^Binary files .+ differ\s*$/m.test(patchString) || /^GIT binary patch/m.test(patchString)) {
        showMessage('Binary file changed (diff not available).');
        window.AndroidDiffBridge?.onRenderComplete?.(1, 0);
        return;
      }
      showMessage('No patch hunks found.');
      window.AndroidDiffBridge?.onRenderComplete?.(0, 0);
      return;
    }

    let totalFiles = 0;
    let totalHunks = 0;

    for (const patch of patches) {
      for (const fileDiff of patch.files) {
        totalFiles++;
        const hunkCount = fileDiff.hunks?.length || 0;
        totalHunks += hunkCount;

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

        if (hunkCount === 0) {
          const isBinary = /^Binary files .+ differ\s*$/m.test(patchString) || /^GIT binary patch/m.test(patchString);
          const notice = document.createElement('div');
          notice.style.padding = '16px';
          notice.style.color = 'var(--diffs-fg-number, #888)';
          notice.style.fontFamily = 'var(--diffs-font-family, monospace)';
          notice.style.fontSize = '12px';
          notice.textContent = isBinary
            ? 'Binary file changed (diff not available).'
            : 'No content changes (mode change or empty file).';
          container.appendChild(notice);
        }
      }
    }

    applyLineNumbersToDOM(currentOptions.showLineNumbers !== false);
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

    applyLineNumbersToDOM(currentOptions.showLineNumbers !== false);
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

function applyLineNumbersToDOM(show: boolean) {
  // 1. Update options and pre attributes on active FileDiff instances
  for (const instance of activeFileDiffInstances) {
    try {
      const anyInst = instance as any;
      if (anyInst.options) {
        anyInst.options.disableLineNumbers = !show;
      }
      if (anyInst.appliedPreAttributes) {
        anyInst.appliedPreAttributes.disableLineNumbers = !show;
      }
      if (typeof anyInst.mergeOptions === 'function') {
        anyInst.mergeOptions({ disableLineNumbers: !show });
      }
      const pre = anyInst.pre || anyInst.fileContainer?.shadowRoot?.querySelector('pre');
      if (pre) {
        if (!show) {
          pre.setAttribute('data-disable-line-numbers', '');
        } else {
          pre.removeAttribute('data-disable-line-numbers');
        }
      }
    } catch (e) {
      console.warn('Failed to update line numbers on FileDiff instance:', e);
    }
  }

  // 2. Direct DOM update on all diffs-containers in rootElement
  const containers = rootElement.querySelectorAll('diffs-container');
  for (const container of containers) {
    const pre = container.shadowRoot?.querySelector('pre');
    if (pre) {
      if (!show) {
        pre.setAttribute('data-disable-line-numbers', '');
      } else {
        pre.removeAttribute('data-disable-line-numbers');
      }
    }
  }

  // 3. Direct DOM update on any .file-diff-item shadow hosts
  const items = rootElement.querySelectorAll('.file-diff-item');
  for (const item of items) {
    for (const child of item.children) {
      const pre = child.shadowRoot?.querySelector('pre');
      if (pre) {
        if (!show) {
          pre.setAttribute('data-disable-line-numbers', '');
        } else {
          pre.removeAttribute('data-disable-line-numbers');
        }
      }
    }
  }

  // 4. Fallback for any pre tags directly in rootElement
  const pres = rootElement.querySelectorAll('pre');
  for (const pre of pres) {
    if (!show) {
      pre.setAttribute('data-disable-line-numbers', '');
    } else {
      pre.removeAttribute('data-disable-line-numbers');
    }
  }
}

function setDiffStyle(style: 'split' | 'unified') {
  if (currentOptions.diffStyle === style) return;
  currentOptions.diffStyle = style;
  const scrollX = window.scrollX;
  const scrollY = window.scrollY;
  for (const instance of activeFileDiffInstances) {
    try {
      const anyInst = instance as any;
      if (anyInst.options) {
        anyInst.options.diffStyle = style;
      }
      if (typeof anyInst.mergeOptions === 'function') {
        anyInst.mergeOptions({ diffStyle: style });
      }
      anyInst.rerender?.();
    } catch {
      // ignore
    }
  }
  requestAnimationFrame(() => {
    window.scrollTo(scrollX, scrollY);
  });
}

function setLineNumbers(show: boolean) {
  currentOptions.showLineNumbers = show;
  applyLineNumbersToDOM(show);
}

function setWordDiff(enabled: boolean) {
  if (currentOptions.isWordDiffEnabled === enabled) return;
  currentOptions.isWordDiffEnabled = enabled;
  const lineDiffType = enabled ? 'word' : 'none';
  const scrollX = window.scrollX;
  const scrollY = window.scrollY;
  for (const instance of activeFileDiffInstances) {
    try {
      const anyInst = instance as any;
      if (anyInst.options) {
        anyInst.options.lineDiffType = lineDiffType;
      }
      if (typeof anyInst.mergeOptions === 'function') {
        anyInst.mergeOptions({ lineDiffType });
      }
      anyInst.rerender?.();
    } catch {
      // ignore
    }
  }
  requestAnimationFrame(() => {
    window.scrollTo(scrollX, scrollY);
  });
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
