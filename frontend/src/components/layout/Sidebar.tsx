import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  Sparkles,
  LayoutDashboard,
  FileText,
  MessageSquare,
  Search,
  Users,
  BarChart3,
  Shield,
  Settings,
  UserCheck,
} from 'lucide-react';
import { cn } from '../../lib/utils';
import { useAuthStore } from '../../store/useAuthStore';
import { useWorkspaceStore } from '../../store/useWorkspaceStore';
import { RoleBadge } from '../ui/Badge';

export const Sidebar: React.FC = () => {
  const { user } = useAuthStore();
  const { currentWorkspace } = useWorkspaceStore();

  const isWorkspaceAdmin = currentWorkspace?.userRole === 'WORKSPACE_ADMIN';
  const isSuperAdmin = user?.globalRole === 'SUPER_ADMIN';

  const workspaceSection = [
    { label: 'Overview', to: '/', icon: LayoutDashboard },
    { label: 'Knowledge Chat', to: '/chat', icon: MessageSquare },
    { label: 'Documents', to: '/documents', icon: FileText },
    { label: 'Semantic Search', to: '/search', icon: Search },
  ];

  const managementSection = [
    { label: 'Workspaces', to: '/workspaces', icon: Users },
    ...(isWorkspaceAdmin && currentWorkspace
      ? [{ label: 'Team & Invites', to: `/workspaces/${currentWorkspace.id}/members`, icon: UserCheck }]
      : []),
    ...(isWorkspaceAdmin
      ? [{ label: 'Analytics', to: '/analytics', icon: BarChart3 }]
      : []),
  ];

  const adminSection = isSuperAdmin
    ? [{ label: 'Admin Console', to: '/admin', icon: Shield, highlight: true }]
    : [];

  const personalSection = [
    { label: 'Settings', to: '/settings', icon: Settings },
  ];

  return (
    <aside className="hidden md:flex h-screen w-64 flex-col border-r border-slate-200/80 bg-slate-50/50 dark:border-slate-800/80 dark:bg-slate-950 shrink-0">
      {/* Brand Header */}
      <div className="flex h-16 items-center gap-2.5 px-6 border-b border-slate-200/80 dark:border-slate-800/80">
        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-tr from-indigo-500 via-indigo-600 to-cyan-400 shadow-md shadow-indigo-500/20 text-white">
          <Sparkles className="w-5 h-5" />
        </div>
        <div>
          <div className="flex items-center gap-1.5">
            <span className="font-bold tracking-tight text-slate-900 dark:text-slate-100">
              KnowFlow
            </span>
            <span className="rounded bg-indigo-500/10 px-1 py-0.5 text-[9px] font-bold text-indigo-600 dark:text-indigo-400">
              AI
            </span>
          </div>
          <span className="text-[10px] text-slate-400 dark:text-slate-500 block leading-none">
            Enterprise RAG 2027
          </span>
        </div>
      </div>

      {/* Workspace Context Box */}
      {currentWorkspace && (
        <div className="mx-4 my-3 rounded-xl border border-slate-200/80 bg-white p-3 shadow-xs dark:border-slate-800 dark:bg-slate-900/60">
          <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">
            Active Workspace
          </div>
          <div className="mt-1 flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-800 dark:text-slate-200 truncate max-w-[120px]" title={currentWorkspace.name}>
              {currentWorkspace.name}
            </span>
            <RoleBadge role={currentWorkspace.userRole} />
          </div>
        </div>
      )}

      {/* Structured Nav Links */}
      <nav className="flex-1 space-y-4 px-3 py-3 overflow-y-auto">
        {/* Workspace section */}
        <div>
          <div className="px-3 pb-1 text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
            Knowledge
          </div>
          <div className="space-y-1">
            {workspaceSection.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/'}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all',
                      isActive
                        ? 'bg-indigo-50 text-indigo-700 shadow-xs dark:bg-indigo-950/60 dark:text-indigo-300'
                        : 'text-slate-600 hover:bg-slate-100/80 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-900 dark:hover:text-slate-200'
                    )
                  }
                >
                  <Icon className="w-4 h-4 shrink-0" />
                  <span>{item.label}</span>
                </NavLink>
              );
            })}
          </div>
        </div>

        {/* Management section */}
        {managementSection.length > 0 && (
          <div>
            <div className="px-3 pb-1 text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
              Management
            </div>
            <div className="space-y-1">
              {managementSection.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    className={({ isActive }) =>
                      cn(
                        'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all',
                        isActive
                          ? 'bg-indigo-50 text-indigo-700 shadow-xs dark:bg-indigo-950/60 dark:text-indigo-300'
                          : 'text-slate-600 hover:bg-slate-100/80 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-900 dark:hover:text-slate-200'
                      )
                    }
                  >
                    <Icon className="w-4 h-4 shrink-0" />
                    <span>{item.label}</span>
                  </NavLink>
                );
              })}
            </div>
          </div>
        )}

        {/* Admin section */}
        {adminSection.length > 0 && (
          <div>
            <div className="px-3 pb-1 text-[10px] font-bold uppercase tracking-wider text-amber-500/80 dark:text-amber-400/80">
              Platform Admin
            </div>
            <div className="space-y-1">
              {adminSection.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    className={({ isActive }) =>
                      cn(
                        'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all',
                        isActive
                          ? 'bg-amber-50 text-amber-700 shadow-xs dark:bg-amber-950/50 dark:text-amber-300'
                          : 'text-amber-600 dark:text-amber-400 hover:bg-amber-50/50 dark:hover:bg-amber-950/30'
                      )
                    }
                  >
                    <Icon className="w-4 h-4 shrink-0" />
                    <span>{item.label}</span>
                  </NavLink>
                );
              })}
            </div>
          </div>
        )}

        {/* Personal section */}
        <div>
          <div className="px-3 pb-1 text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
            Account
          </div>
          <div className="space-y-1">
            {personalSection.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all',
                      isActive
                        ? 'bg-indigo-50 text-indigo-700 shadow-xs dark:bg-indigo-950/60 dark:text-indigo-300'
                        : 'text-slate-600 hover:bg-slate-100/80 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-900 dark:hover:text-slate-200'
                    )
                  }
                >
                  <Icon className="w-4 h-4 shrink-0" />
                  <span>{item.label}</span>
                </NavLink>
              );
            })}
          </div>
        </div>
      </nav>

      {/* Footer Info */}
      <div className="p-4 border-t border-slate-200/80 dark:border-slate-800/80">
        <div className="flex items-center justify-between text-xs text-slate-400 dark:text-slate-500">
          <span>Enterprise v1.0</span>
          <span className="flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400 font-medium">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
            Active
          </span>
        </div>
      </div>
    </aside>
  );
};
