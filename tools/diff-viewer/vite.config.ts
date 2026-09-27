import { defineConfig } from 'vite';
import path from 'path';

export default defineConfig({
  base: './',
  resolve: {
    alias: [
      {
        find: /^shiki$/,
        replacement: path.resolve(__dirname, 'src/shiki-pruned.ts'),
      },
    ],
  },
  build: {
    target: 'es2022',
    outDir: path.resolve(__dirname, '../../compose-app/src/main/assets/diff-viewer'),
    emptyOutDir: true,
    minify: 'esbuild',
    rollupOptions: {
      input: {
        main: path.resolve(__dirname, 'index.html'),
      },
      output: {
        entryFileNames: 'bundle.js',
        chunkFileNames: 'chunks/[name]-[hash].js',
        assetFileNames: 'assets/[name].[ext]',
      },
    },
  },
});
