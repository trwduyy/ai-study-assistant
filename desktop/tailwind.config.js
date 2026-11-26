/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}"
    ],
    theme: {
        extend: {
            fontFamily: {
                sans: [
                    "Inter",
                    "-apple-system",
                    "BlinkMacSystemFont",
                    "Segoe UI",
                    "Roboto",
                    "Helvetica Neue",
                    "Arial",
                    "sans-serif"
                ]
            },
            colors: {
                primary: {
                    DEFAULT: "#2563EB",
                    hover: "#1D4ED8",
                    foreground: "#FFFFFF",
                    50: "#EFF6FF",
                    100: "#DBEAFE",
                    200: "#BFDBFE",
                    300: "#93C5FD",
                    400: "#60A5FA",
                    500: "#3B82F6",
                    600: "#2563EB",
                    700: "#1D4ED8",
                    800: "#1E40AF",
                    900: "#1E3A8A"
                },
                secondary: {
                    DEFAULT: "#0F766E",
                    hover: "#115E59",
                    foreground: "#FFFFFF",
                    50: "#F0FDFA",
                    100: "#CCFBF1",
                    600: "#0D9488",
                    700: "#0F766E"
                },
                background: "#F8FAFC",
                surface: "#FFFFFF",
                foreground: "#0F172A",
                muted: {
                    DEFAULT: "#F1F5F9",
                    foreground: "#475569"
                },
                border: "#E2E8F0",
                input: "#E2E8F0",
                ring: "#2563EB",
                success: {
                    DEFAULT: "#15803D",
                    bg: "#F0FDF4",
                    border: "#BBF7D0",
                    foreground: "#FFFFFF"
                },
                warning: {
                    DEFAULT: "#B45309",
                    bg: "#FFFBEB",
                    border: "#FDE68A",
                    foreground: "#FFFFFF"
                },
                danger: {
                    DEFAULT: "#B91C1C",
                    bg: "#FEF2F2",
                    border: "#FECACA",
                    foreground: "#FFFFFF"
                },
                info: {
                    DEFAULT: "#0369A1",
                    bg: "#F0F9FF",
                    border: "#BAE6FD",
                    foreground: "#FFFFFF"
                }
            },
            borderRadius: {
                lg: "12px",
                md: "8px",
                sm: "6px"
            },
            boxShadow: {
                card: "0 1px 2px 0 rgba(15, 23, 42, 0.05)",
                elevated: "0 10px 25px -5px rgba(15, 23, 42, 0.08), 0 8px 10px -6px rgba(15, 23, 42, 0.04)"
            }
        }
    },
    plugins: []
};
