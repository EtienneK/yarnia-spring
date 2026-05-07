import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from '@tailwindcss/vite';

// https://vite.dev/config/
export default defineConfig({
    server: {
        port: 3000,
        host: "0.0.0.0",
        allowedHosts: ["3k.local.etkhome.com"],
        proxy: {
            "/ws": { target: "http://localhost:3001", ws: true },
            "/api": { target: "http://localhost:3001" },
        },
    },
    plugins: [
        react(),
        tailwindcss(),
    ],
});
