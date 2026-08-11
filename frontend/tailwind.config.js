/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    './index.html',
    './src/**/*.{vue,js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'system-ui', 'sans-serif'],
      },
      colors: {
        paper:        'var(--paper)',
        surface:      'var(--surface)',
        ink:          'var(--ink)',
        'ink-soft':   'var(--ink-soft)',
        'ink-faint':  'var(--ink-faint)',
        line:         'var(--line)',
        pink:         'var(--pink)',
        'pink-soft':  'var(--pink-soft)',
        blue:         'var(--blue)',
        green:        'var(--green)',
      },
      animation: {
        'fade-in':   'fade-in 0.35s ease-out forwards',
        'pop-in':    'pop-in 200ms cubic-bezier(0.22,1,0.36,1)',
        'breathing': 'breathing 1.8s cubic-bezier(0.4,0,0.6,1) infinite',
        'blink':     'blink 1s step-end infinite',
      },
      keyframes: {
        'fade-in':   { from: { opacity: '0', transform: 'translateY(8px)' }, to: { opacity: '1', transform: 'translateY(0)' } },
        'pop-in':    { from: { opacity: '0', transform: 'translateY(6px) scale(0.985)' }, to: { opacity: '1', transform: 'translateY(0) scale(1)' } },
        'breathing': { '0%,100%': { opacity: '0.45' }, '50%': { opacity: '1' } },
        'blink':     { '0%,100%': { opacity: '1' }, '50%': { opacity: '0' } },
      },
    },
  },
  plugins: [],
}

