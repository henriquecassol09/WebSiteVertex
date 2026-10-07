/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        vertex: {
          orange: '#FF5500',
          'orange-hover': '#E04A00',
          'orange-light': '#FFF4EE',
          'orange-glow': 'rgba(255, 85, 0, 0.12)',
          dark: '#111315',
          charcoal: '#1A1D20',
          body: '#3A3F45',
          muted: '#717882',
          border: '#E5E8EB',
          'border-light': '#F0F2F5',
          bg: '#F8F9FA',
          card: '#FFFFFF',
          // Dark Mode Tokens (Deep obsidian, high contrast, clean contrast ratio)
          'dark-bg': '#0A0B0E',
          'dark-card': '#111318',
          'dark-surface': '#171A21',
          'dark-border': '#1F242D',
          'dark-border-light': '#181C23',
          'dark-text': '#F3F4F6',
          'dark-muted': '#94A3B8',
        }
      },
      fontFamily: {
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
      },
      boxShadow: {
        subtle: '0 1px 2px 0 rgba(0, 0, 0, 0.03)',
        card: '0 1px 3px 0 rgba(0, 0, 0, 0.04), 0 6px 16px 0 rgba(0, 0, 0, 0.02)',
        elevated: '0 12px 28px -4px rgba(0, 0, 0, 0.08), 0 4px 10px -2px rgba(0, 0, 0, 0.03)',
        modal: '0 25px 50px -12px rgba(0, 0, 0, 0.16)',
        'dark-subtle': '0 1px 2px 0 rgba(0, 0, 0, 0.5)',
        'dark-card': '0 4px 20px 0 rgba(0, 0, 0, 0.65), 0 0 0 1px rgba(255, 255, 255, 0.04)',
        'dark-modal': '0 25px 50px -12px rgba(0, 0, 0, 0.85), 0 0 0 1px rgba(255, 255, 255, 0.06)',
        'glow-orange': '0 0 20px -3px rgba(255, 85, 0, 0.35)',
      },
      borderRadius: {
        'sharp': '4px',
      }
    },
  },
  plugins: [],
}
