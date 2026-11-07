import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/useAuthStore';
import { useWorkspaceStore } from '../../store/useWorkspaceStore';
import { useThemeStore } from '../../store/useThemeStore';
import {
  Moon,
  Sun,
  ChevronDown,
  Plus,
  LogOut,
  Shield,
  User,
  Building,
  Check,
  Search,
} from 'lucide-react';

interface NavbarProps {
  onOpenNewWorkspaceModal?: () => void;
  onOpenMobileSidebar?: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ onOpenNewWorkspaceModal }) => {
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const { currentWorkspace, workspaces, setCurrentWorkspace } = useWorkspaceStore();
  const { theme, toggleTheme } = useThemeStore();

  const [isWorkspaceMenuOpen, setIsWorkspaceMenuOpen] = useState(false);
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);

  return (
    <header className="sticky top-0 z-30 flex h-16 w-full items-center justify-between border-b border-slate-200/80 bg-white/80 px-4 md:px-6 backdrop-blur-md dark:border-slate-800/80 dark:bg-slate-950/80">
      {/* Left: Workspace Selector */}
      <div className="flex items-center gap-3">
        <div className="relative">
          <button
            onClick={() => setIsWorkspaceMenuOpen(!isWorkspaceMenuOpen)}
            className="flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50/60 px-3 py-1.5 text-sm font-medium text-slate-800 hover:bg-slate-100 dark:border-slate-800 dark:bg-slate-900/60 dark:text-slate-200 dark:hover:bg-slate-800 transition-colors"
          >
            <Building className="w-4 h-4 text-brand-500" />
            <span className="max-w-[140px] truncate md:max-w-[200px]">
              {currentWorkspace ? currentWorkspace.name : 'Select Workspace'}
            </span>
            <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
          </button>

          {isWorkspaceMenuOpen && (
            <>
              <div
                className="fixed inset-0 z-40"
                onClick={() => setIsWorkspaceMenuOpen(false)}
              />
              <div className="absolute left-0 mt-2 z-50 w-64 rounded-xl border border-slate-200 bg-white p-2 shadow-xl dark:border-slate-800 dark:bg-slate-900 animate-fade-in">
                <div className="px-2 py-1.5 text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Workspaces
                </div>
                <div className="max-h-48 overflow-y-auto space-y-1">
                  {workspaces.map((ws) => (
                    <button
                      key={ws.id}
                      onClick={() => {
                        setCurrentWorkspace(ws);
                        setIsWorkspaceMenuOpen(false);
                      }}
                      className="flex w-full items-center justify-between rounded-lg px-2.5 py-2 text-sm text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-800"
                    >
                      <span className="truncate">{ws.name}</span>
                      {currentWorkspace?.id === ws.id && (
                        <Check className="w-4 h-4 text-brand-500 shrink-0" />
                      )}
                    </button>
                  ))}
                </div>
                <div className="mt-2 pt-2 border-t border-slate-100 dark:border-slate-800">
                  <button
                    onClick={() => {
                      setIsWorkspaceMenuOpen(false);
                      onOpenNewWorkspaceModal?.();
                    }}
                    className="flex w-full items-center gap-2 rounded-lg px-2.5 py-1.5 text-xs font-medium text-brand-600 hover:bg-brand-50 dark:text-brand-400 dark:hover:bg-brand-950/40"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    Create New Workspace
                  </button>
                </div>
              </div>
            </>
          )}
        </div>

        {/* Quick Search Shortcut */}
        <button
          onClick={() => navigate('/search')}
          className="hidden sm:flex items-center gap-2 rounded-lg border border-slate-200/80 bg-slate-50/50 px-3 py-1.5 text-xs text-slate-500 hover:border-slate-300 dark:border-slate-800 dark:bg-slate-900/40 dark:text-slate-400 transition-colors"
        >
          <Search className="w-3.5 h-3.5" />
          <span>Semantic search docs...</span>
          <kbd className="rounded bg-slate-200/80 px-1.5 py-0.5 text-[10px] font-mono text-slate-600 dark:bg-slate-800 dark:text-slate-400">
            Ctrl+K
          </kbd>
        </button>
      </div>

      {/* Right: Actions & User Menu */}
      <div className="flex items-center gap-2.5">
        {/* Dark/Light toggle */}
        <button
          onClick={toggleTheme}
          className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800 transition-colors"
          aria-label="Toggle theme"
        >
          {theme === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
        </button>

        {/* User avatar menu */}
        <div className="relative">
          <button
            onClick={() => setIsUserMenuOpen(!isUserMenuOpen)}
            className="flex items-center gap-2 rounded-full p-1 hover:ring-2 hover:ring-brand-500/20 transition-all"
          >
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-tr from-brand-600 to-indigo-500 text-xs font-bold text-white shadow-sm">
              {user?.fullName?.charAt(0) || 'U'}
            </div>
          </button>

          {isUserMenuOpen && (
            <>
              <div
                className="fixed inset-0 z-40"
                onClick={() => setIsUserMenuOpen(false)}
              />
              <div className="absolute right-0 mt-2 z-50 w-56 rounded-xl border border-slate-200 bg-white p-2 shadow-xl dark:border-slate-800 dark:bg-slate-900 animate-fade-in">
                <div className="px-3 py-2 border-b border-slate-100 dark:border-slate-800">
                  <p className="text-sm font-semibold text-slate-900 dark:text-slate-100 truncate">
                    {user?.fullName}
                  </p>
                  <p className="text-xs text-slate-500 dark:text-slate-400 truncate">
                    {user?.email}
                  </p>
                  {user?.globalRole === 'SUPER_ADMIN' && (
                    <span className="mt-1 inline-flex items-center gap-1 rounded bg-amber-500/10 px-1.5 py-0.5 text-[10px] font-semibold text-amber-600 dark:text-amber-400">
                      <Shield className="w-3 h-3" />
                      Super Admin
                    </span>
                  )}
                </div>

                <div className="py-1">
                  <button
                    onClick={() => {
                      setIsUserMenuOpen(false);
                      navigate('/settings');
                    }}
                    className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-800"
                  >
                    <User className="w-4 h-4" />
                    Profile & Settings
                  </button>

                  {user?.globalRole === 'SUPER_ADMIN' && (
                    <button
                      onClick={() => {
                        setIsUserMenuOpen(false);
                        navigate('/admin');
                      }}
                      className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-amber-600 hover:bg-amber-50 dark:text-amber-400 dark:hover:bg-amber-950/30"
                    >
                      <Shield className="w-4 h-4" />
                      Admin Console
                    </button>
                  )}
                </div>

                <div className="pt-1 border-t border-slate-100 dark:border-slate-800">
                  <button
                    onClick={() => {
                      setIsUserMenuOpen(false);
                      logout();
                      navigate('/auth/login');
                    }}
                    className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-rose-600 hover:bg-rose-50 dark:text-rose-400 dark:hover:bg-rose-950/30"
                  >
                    <LogOut className="w-4 h-4" />
                    Sign out
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </header>
  );
};
