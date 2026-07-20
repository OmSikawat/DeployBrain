/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        base: "#0F1115",
        panel: "#171A21",
        border: "#2A2E38",
        signal: {
          green: "#3FB950",
          amber: "#D9A441",
          red: "#E5534B",
          blue: "#4A9EFF",
        },
      },
      fontFamily: {
        mono: ["JetBrains Mono", "ui-monospace", "monospace"],
        sans: ["Inter", "ui-sans-serif", "system-ui"],
      },
    },
  },
  plugins: [],
};