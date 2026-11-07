import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  User,
  Mail,
  Shield,
  Moon,
  Sun,
  Laptop,
  LogOut,
  Key,
  Database,
  CheckCircle2,
} from 'lucide-react';
import { useAuthStore } from '../../store/useAuthStore';
import { useThemeStore } from '../../store/useThemeStore';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { toast } from '../../components/ui/Toast';

export const SettingsPage: React.FC = () => {
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const { theme, setTheme } = useThemeStore();

  const handleLogout = () => {
    logout();
    toast.info('Signed out', 'You have been logged out of KnowFlow AI.');
    navigate('/auth/login');
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
          Account & Preferences
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Manage your personal profile, appearance, and workspace credentials.
        </p>
      </div>

      {/* Profile Card */}
      <Card>
        <CardHeader className="border-b border-slate-100 dark:border-slate-800">
          <h2 className="text-base font-semibold text-slate-900 dark:text-white">
            Profile Details
          </h2>
        </CardHeader>
        <CardContent className="p-6 space-y-4">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-tr from-indigo-500 to-violet-600 flex items-center justify-center text-white font-bold text-2xl shadow-md">
              {user?.fullName?.charAt(0) || 'U'}
            </div>
            <div>
              <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
                {user?.fullName}
              </h3>
              <p className="text-sm text-slate-500">{user?.email}</p>
              <div className="flex items-center gap-2 mt-1">
                <span className="text-xs px-2 py-0.5 rounded-full bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 font-medium">
                  {user?.globalRole}
                </span>
                {user?.emailVerified && (
                  <span className="text-xs text-emerald-500 flex items-center gap-1 font-medium">
                    <CheckCircle2 className="w-3.5 h-3.5" /> Email Verified
                  </span>
                )}
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Appearance Card */}
      <Card>
        <CardHeader className="border-b border-slate-100 dark:border-slate-800">
          <h2 className="text-base font-semibold text-slate-900 dark:text-white">Appearance</h2>
        </CardHeader>
        <CardContent className="p-6">
          <div className="grid grid-cols-3 gap-4 max-w-md">
            <button
              onClick={() => setTheme('light')}
              className={`flex flex-col items-center justify-center p-4 rounded-xl border transition-all ${
                theme === 'light'
                  ? 'border-indigo-600 bg-indigo-50/50 dark:bg-indigo-950/40 text-indigo-600 font-semibold ring-1 ring-indigo-500'
                  : 'border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-400 hover:border-slate-300'
              }`}
            >
              <Sun className="w-6 h-6 mb-2" />
              <span className="text-xs">Light</span>
            </button>

            <button
              onClick={() => setTheme('dark')}
              className={`flex flex-col items-center justify-center p-4 rounded-xl border transition-all ${
                theme === 'dark'
                  ? 'border-indigo-600 bg-indigo-50/50 dark:bg-indigo-950/40 text-indigo-600 font-semibold ring-1 ring-indigo-500'
                  : 'border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-400 hover:border-slate-300'
              }`}
            >
              <Moon className="w-6 h-6 mb-2" />
              <span className="text-xs">Dark</span>
            </button>

            <button
              onClick={() => setTheme('system')}
              className={`flex flex-col items-center justify-center p-4 rounded-xl border transition-all ${
                theme === 'system'
                  ? 'border-indigo-600 bg-indigo-50/50 dark:bg-indigo-950/40 text-indigo-600 font-semibold ring-1 ring-indigo-500'
                  : 'border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-400 hover:border-slate-300'
              }`}
            >
              <Laptop className="w-6 h-6 mb-2" />
              <span className="text-xs">System</span>
            </button>
          </div>
        </CardContent>
      </Card>

      {/* Security & Session */}
      <Card>
        <CardHeader className="border-b border-slate-100 dark:border-slate-800">
          <h2 className="text-base font-semibold text-slate-900 dark:text-white">
            Security & Session
          </h2>
        </CardHeader>
        <CardContent className="p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <div className="font-medium text-sm text-slate-900 dark:text-white">
                Active Session
              </div>
              <div className="text-xs text-slate-500">
                JWT bearer authenticated with refresh token rotation.
              </div>
            </div>
            <Button variant="danger" size="sm" onClick={handleLogout}>
              <LogOut className="w-4 h-4 mr-1.5" /> Sign Out
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};
