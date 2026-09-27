import {
  createBundledHighlighter,
  createSingletonShorthands,
  guessEmbeddedLanguages,
} from '@shikijs/core';
import { createJavaScriptRegexEngine } from '@shikijs/engine-javascript';
import { createOnigurumaEngine } from '@shikijs/engine-oniguruma';

// Re-export everything from @shikijs/core
export * from '@shikijs/core';
export { createJavaScriptRegexEngine } from '@shikijs/engine-javascript';
export { createOnigurumaEngine } from '@shikijs/engine-oniguruma';

// Curated list of high-value programming & config languages
const coreLanguages: Record<string, () => Promise<{ default: any }>> = {
  kotlin: () => import('@shikijs/langs/kotlin'),
  java: () => import('@shikijs/langs/java'),
  rust: () => import('@shikijs/langs/rust'),
  c: () => import('@shikijs/langs/c'),
  cpp: () => import('@shikijs/langs/cpp'),
  python: () => import('@shikijs/langs/python'),
  go: () => import('@shikijs/langs/go'),
  javascript: () => import('@shikijs/langs/javascript'),
  typescript: () => import('@shikijs/langs/typescript'),
  jsx: () => import('@shikijs/langs/jsx'),
  tsx: () => import('@shikijs/langs/tsx'),
  shellscript: () => import('@shikijs/langs/shellscript'),
  bash: () => import('@shikijs/langs/bash'),
  zsh: () => import('@shikijs/langs/zsh'),
  html: () => import('@shikijs/langs/html'),
  css: () => import('@shikijs/langs/css'),
  scss: () => import('@shikijs/langs/scss'),
  json: () => import('@shikijs/langs/json'),
  yaml: () => import('@shikijs/langs/yaml'),
  toml: () => import('@shikijs/langs/toml'),
  markdown: () => import('@shikijs/langs/markdown'),
  sql: () => import('@shikijs/langs/sql'),
  xml: () => import('@shikijs/langs/xml'),
  diff: () => import('@shikijs/langs/diff'),
  zig: () => import('@shikijs/langs/zig'),
};

// Safe Proxy that provides curated languages and falls back gracefully to plain-text
export const bundledLanguages = new Proxy(coreLanguages, {
  get(target, prop) {
    if (typeof prop === 'string' && prop in target) {
      return target[prop];
    }
    return () =>
      Promise.resolve({
        default: {
          name: typeof prop === 'string' ? prop : 'text',
          scopeName: 'source.plain',
          patterns: [],
        },
      });
  },
  has() {
    return true;
  },
});

export const bundledThemes = {};

export const createHighlighter = createBundledHighlighter({
  langs: bundledLanguages as any,
  themes: bundledThemes,
  engine: () => createJavaScriptRegexEngine(),
});

const shorthands = createSingletonShorthands(createHighlighter, {
  guessEmbeddedLanguages,
});

export const {
  codeToHtml,
  codeToHast,
  codeToTokens,
  codeToTokensBase,
  codeToTokensWithThemes,
  getSingletonHighlighter,
  getLastGrammarState,
} = shorthands;
