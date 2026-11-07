import { create } from 'zustand';

export type Theme = 'dark' | 'light' | 'system';

interface ThemeState {
  theme: Theme;
  toggleTheme: () => void;
  setTheme: (theme: Theme) => void;
}

const STORAGE_THEME_KEY = 'knowflow_theme';

const applyTheme = (theme: Theme) => {
  if (typeof document === 'undefined') return;
  const isDark =
    theme === 'dark' ||
    (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);

  if (isDark) {
    document.documentElement.classList.add('dark');
  } else {
    document.documentElement.classList.remove('dark');
  }
};

export const useThemeStore = create<ThemeState>((set) => {
  const storedTheme = (localStorage.getItem(STORAGE_THEME_KEY) as Theme) || 'dark';
  applyTheme(storedTheme);

  return {
    theme: storedTheme,
    toggleTheme: () => {
      set((state) => {
        const next: Theme = state.theme === 'dark' ? 'light' : 'dark';
        localStorage.setItem(STORAGE_THEME_KEY, next);
        applyTheme(next);
        return { theme: next };
      });
    },
    setTheme: (theme: Theme) => {
      localStorage.setItem(STORAGE_THEME_KEY, theme);
      applyTheme(theme);
      set({ theme });
    },
  };
});
