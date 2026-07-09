// public/app-config.js — Puter + runtime config BAKED into the app's functioning.
// Mirrors backend/config.json so the browser app knows the live endpoints
// without relying on external env at runtime.
window.APP_CONFIG = {
  puter: {
    sdk: "https://js.puter.com/v2/",
    apiBase: "https://api.puter.com",
    keyless: true,
    // Live models the app can call directly via puter.ai.chat()
    models: {
      openai:   { slug: "gpt-5.4-nano", web: false },
      deepseek: { slug: "deepseek-chat", web: false },
      research: { slug: "gpt-5.3-chat", web: true, tools: ["web_search"] },
    },
  },
  // Runtime tiers: preferred container (docker/alpine) else Termux shell.
  runtime: {
    strategy: "prefer-container-then-termux",
    preferred: { kind: "docker-alpine", note: "containerized busybox glibc alpine" },
    fallback:  { kind: "termux-shell",  note: "Termux/userland shell for code + artifacts" },
  },
  backend: { base: "" }, // same-origin
};
