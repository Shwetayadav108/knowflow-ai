import { create } from 'zustand';
import { AuthResponse, UserDto } from '../types';

interface AuthState {
  user: UserDto | null;
  token: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  setAuth: (auth: AuthResponse) => void;
  setToken: (token: string, refreshToken?: string) => void;
  setUser: (user: UserDto) => void;
  completeOnboarding: () => void;
  logout: () => void;
}

const STORAGE_TOKEN_KEY = 'knowflow_token';
const STORAGE_REFRESH_KEY = 'knowflow_refresh_token';
const STORAGE_USER_KEY = 'knowflow_user';

export const useAuthStore = create<AuthState>((set) => {
  const initialToken = localStorage.getItem(STORAGE_TOKEN_KEY);
  const initialRefreshToken = localStorage.getItem(STORAGE_REFRESH_KEY);
  let initialUser: UserDto | null = null;
  try {
    const storedUser = localStorage.getItem(STORAGE_USER_KEY);
    if (storedUser) {
      initialUser = JSON.parse(storedUser);
    }
  } catch {
    initialUser = null;
  }

  return {
    user: initialUser,
    token: initialToken,
    refreshToken: initialRefreshToken,
    isAuthenticated: !!initialToken,

    setAuth: (auth: AuthResponse) => {
      localStorage.setItem(STORAGE_TOKEN_KEY, auth.token);
      localStorage.setItem(STORAGE_REFRESH_KEY, auth.refreshToken);
      localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(auth.user));
      set({
        token: auth.token,
        refreshToken: auth.refreshToken,
        user: auth.user,
        isAuthenticated: true,
      });
    },

    setToken: (token: string, refreshToken?: string) => {
      localStorage.setItem(STORAGE_TOKEN_KEY, token);
      if (refreshToken) {
        localStorage.setItem(STORAGE_REFRESH_KEY, refreshToken);
      }
      set((state) => ({
        token,
        refreshToken: refreshToken || state.refreshToken,
        isAuthenticated: true,
      }));
    },

    setUser: (user: UserDto) => {
      localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(user));
      set({ user });
    },

    completeOnboarding: () => {
      set((state) => {
        if (!state.user) return state;
        const updated = { ...state.user, onboardingCompleted: true };
        localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(updated));
        return { user: updated };
      });
    },

    logout: () => {
      localStorage.removeItem(STORAGE_TOKEN_KEY);
      localStorage.removeItem(STORAGE_REFRESH_KEY);
      localStorage.removeItem(STORAGE_USER_KEY);
      set({
        user: null,
        token: null,
        refreshToken: null,
        isAuthenticated: false,
      });
    },
  };
});
