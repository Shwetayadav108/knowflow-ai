import React from 'react';
import { cn } from '../../lib/utils';
import { DocumentStatus, WorkspaceRole, DocumentAccessLevel } from '../../types';

interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: 'default' | 'success' | 'warning' | 'destructive' | 'info' | 'outline' | 'purple';
  size?: 'sm' | 'md';
}

export const Badge: React.FC<BadgeProps> = ({
  className,
  variant = 'default',
  size = 'md',
  children,
  ...props
}) => {
  const variants = {
    default: 'bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-200 border-slate-200 dark:border-slate-700',
    success: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-800/60',
    warning: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800/60',
    destructive: 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-950/40 dark:text-rose-400 dark:border-rose-800/60',
    info: 'bg-sky-50 text-sky-700 border-sky-200 dark:bg-sky-950/40 dark:text-sky-400 dark:border-sky-800/60',
    purple: 'bg-brand-50 text-brand-700 border-brand-200 dark:bg-brand-950/40 dark:text-brand-300 dark:border-brand-800/60',
    outline: 'border border-slate-200 text-slate-600 dark:border-slate-800 dark:text-slate-400',
  };

  const sizes = {
    sm: 'text-[10px] px-1.5 py-0.5 leading-none font-medium',
    md: 'text-xs px-2.5 py-1 font-medium',
  };

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full border tracking-wide uppercase',
        variants[variant],
        sizes[size],
        className
      )}
      {...props}
    >
      {children}
    </span>
  );
};

export const StatusBadge: React.FC<{ status: DocumentStatus }> = ({ status }) => {
  switch (status) {
    case 'READY':
    case 'COMPLETED':
      return (
        <Badge variant="success">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 mr-1" />
          Ready
        </Badge>
      );
    case 'PROCESSING':
    case 'EXTRACTING':
    case 'CHUNKING':
    case 'EMBEDDING':
    case 'INDEXING':
      return (
        <Badge variant="purple" className="animate-pulse">
          <span className="w-1.5 h-1.5 rounded-full bg-brand-500 mr-1 animate-ping" />
          {status.toLowerCase()}
        </Badge>
      );
    case 'FAILED':
      return (
        <Badge variant="destructive">
          <span className="w-1.5 h-1.5 rounded-full bg-rose-500 mr-1" />
          Failed
        </Badge>
      );
    case 'UPLOADED':
    default:
      return (
        <Badge variant="default">
          <span className="w-1.5 h-1.5 rounded-full bg-slate-400 mr-1" />
          Queued
        </Badge>
      );
  }
};

export const RoleBadge: React.FC<{ role: WorkspaceRole }> = ({ role }) => {
  switch (role) {
    case 'WORKSPACE_ADMIN':
      return <Badge variant="purple">Admin</Badge>;
    case 'EDITOR':
      return <Badge variant="info">Editor</Badge>;
    case 'VIEWER':
    default:
      return <Badge variant="outline">Viewer</Badge>;
  }
};

export const AccessLevelBadge: React.FC<{ level: DocumentAccessLevel }> = ({ level }) => {
  switch (level) {
    case 'WORKSPACE_SHARED':
      return <Badge variant="default" size="sm">Workspace</Badge>;
    case 'RESTRICTED_ROLES':
      return <Badge variant="warning" size="sm">Restricted</Badge>;
    case 'PRIVATE':
      return <Badge variant="outline" size="sm">Private</Badge>;
  }
};
