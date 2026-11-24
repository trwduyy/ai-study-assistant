import { defineConfig, Plugin } from "vite";
import react from "@vitejs/plugin-react";
import { build as esbuildBuild } from "esbuild";

function electronBuilderPlugin(): Plugin {
    return {
        name: "vite-electron-builder",
        closeBundle: async () => {
            // Bundle Electron main process
            await esbuildBuild({
                entryPoints: ["electron/main.ts"],
                outfile: "dist-electron/main.js",
                bundle: true,
                platform: "node",
                format: "esm",
                external: ["electron"]
            });

            // Bundle Electron preload script (CommonJS for sandbox compatibility)
            await esbuildBuild({
                entryPoints: ["electron/preload.ts"],
                outfile: "dist-electron/preload.cjs",
                bundle: true,
                platform: "node",
                format: "cjs",
                external: ["electron"]
            });

            // Also generate preload.js for standard path resolution
            await esbuildBuild({
                entryPoints: ["electron/preload.ts"],
                outfile: "dist-electron/preload.js",
                bundle: true,
                platform: "node",
                format: "cjs",
                external: ["electron"]
            });
        }
    };
}

export default defineConfig({
    base: "./",
    plugins: [
        react(),
        electronBuilderPlugin()
    ],
    server: {
        port: 5173,
        strictPort: true
    }
});
