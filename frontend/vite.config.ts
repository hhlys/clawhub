import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { resolve } from "node:path";

export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: resolve(__dirname, "../src/main/resources/static"),
    emptyOutDir: true,
    assetsDir: "assets"
  },
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080"
    }
  }
});
