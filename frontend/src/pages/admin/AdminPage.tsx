import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ShieldAlert,
  Users,
  FolderKanban,
  FileText,
  Activity,
  Cpu,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { adminApi } from '../../api/admin';
import { GlobalRole, UserStatus, UserDto, WorkspaceDto, AuditLogDto } from '../../types';
import { Card, CardContent } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Skeleton } from '../../components/ui/Skeleton';
import { toast } from '../../components/ui/Toast';
import { formatDate } from '../../lib/utils';

export const AdminPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'users' | 'workspaces' | 'audit'>('users');
  const [auditPage, setAuditPage] = useState(0);

  // Stats query
  const { data: stats, isLoading: loadingStats } = useQuery({
    queryKey: ['admin-stats'],
    queryFn: adminApi.getStats,
  });

  // Users query
  const { data: users, isLoading: loadingUsers } = useQuery({
    queryKey: ['admin-users'],
    queryFn: adminApi.getUsers,
    enabled: activeTab === 'users',
  });

  // Workspaces query
  const { data: workspaces, isLoading: loadingWorkspaces } = useQuery({
    queryKey: ['admin-workspaces'],
    queryFn: adminApi.getAllWorkspaces,
    enabled: activeTab === 'workspaces',
  });

  // Audit query
  const { data: auditData, isLoading: loadingAudit } = useQuery({
    queryKey: ['admin-audit', auditPage],
    queryFn: () => adminApi.getAuditLogs({ page: auditPage, size: 20 }),
    enabled: activeTab === 'audit',
  });

  // Mutations
  const updateRoleMutation = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: GlobalRole }) =>
      adminApi.updateUserRole(userId, role),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      toast.success('User role updated');
    },
    onError: (err: any) => {
      toast.error('Role update failed', err.response?.data?.message || err.message);
    },
  });

  const updateStatusMutation = useMutation({
    mutationFn: ({ userId, status }: { userId: string; status: UserStatus }) =>
      adminApi.updateUserStatus(userId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      toast.success('User status updated');
    },
    onError: (err: any) => {
      toast.error('Status update failed', err.response?.data?.message || err.message);
    },
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2 text-indigo-500 font-semibold text-xs tracking-wider uppercase">
          <ShieldAlert className="w-4 h-4" /> Global Administration
        </div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white mt-1">
          System Console
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Manage platform tenants, user privileges, infrastructure health, and global audit trails.
        </p>
      </div>

      {/* System Metrics */}
      {loadingStats ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <Card key={i} className="p-5">
              <Skeleton className="h-6 w-1/3 mb-2" />
              <Skeleton className="h-8 w-2/3" />
            </Card>
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <Card className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500 uppercase">Total Users</span>
              <Users className="w-4 h-4 text-indigo-500" />
            </div>
            <div className="mt-2 text-2xl font-bold text-slate-900 dark:text-white">
              {stats?.totalUsers ?? 0}
            </div>
          </Card>

          <Card className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500 uppercase">Workspaces</span>
              <FolderKanban className="w-4 h-4 text-indigo-500" />
            </div>
            <div className="mt-2 text-2xl font-bold text-slate-900 dark:text-white">
              {stats?.totalWorkspaces ?? 0}
            </div>
          </Card>

          <Card className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500 uppercase">Documents</span>
              <FileText className="w-4 h-4 text-indigo-500" />
            </div>
            <div className="mt-2 text-2xl font-bold text-slate-900 dark:text-white">
              {stats?.totalDocuments ?? 0}
            </div>
            <div className="text-xs text-slate-400 mt-1">
              {stats?.totalChunks ?? 0} vector chunks
            </div>
          </Card>

          <Card className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500 uppercase">AI Engine</span>
              <Cpu className="w-4 h-4 text-emerald-500" />
            </div>
            <div className="mt-2 text-sm font-bold text-slate-900 dark:text-white truncate">
              {stats?.activeAiModel || 'Gemini 1.5 Pro'}
            </div>
            <div className="text-xs text-emerald-500 mt-1 flex items-center gap-1">
              <Activity className="w-3 h-3" /> System: {stats?.systemHealth || 'HEALTHY'}
            </div>
          </Card>
        </div>
      )}

      {/* Tabs */}
      <div className="border-b border-slate-200 dark:border-slate-800">
        <nav className="flex space-x-6">
          <button
            onClick={() => setActiveTab('users')}
            className={`py-3 text-sm font-medium border-b-2 transition-colors ${
              activeTab === 'users'
                ? 'border-indigo-600 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            Users & Roles
          </button>
          <button
            onClick={() => setActiveTab('workspaces')}
            className={`py-3 text-sm font-medium border-b-2 transition-colors ${
              activeTab === 'workspaces'
                ? 'border-indigo-600 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            All Workspaces
          </button>
          <button
            onClick={() => setActiveTab('audit')}
            className={`py-3 text-sm font-medium border-b-2 transition-colors ${
              activeTab === 'audit'
                ? 'border-indigo-600 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            Global Audit Logs
          </button>
        </nav>
      </div>

      {/* Tab 1: Users */}
      {activeTab === 'users' && (
        <Card>
          <CardContent className="p-0 overflow-x-auto">
            {loadingUsers ? (
              <div className="p-6 space-y-3">
                {[1, 2, 3].map((i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : users && users.length > 0 ? (
              <table className="w-full text-left text-sm text-slate-600 dark:text-slate-300">
                <thead className="bg-slate-50 dark:bg-slate-900/60 text-xs uppercase font-semibold text-slate-500 border-b border-slate-200 dark:border-slate-800">
                  <tr>
                    <th className="px-4 py-3">User</th>
                    <th className="px-4 py-3">Global Role</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Email Verified</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {users.map((u: UserDto) => (
                    <tr key={u.id} className="hover:bg-slate-50 dark:hover:bg-slate-900/40">
                      <td className="px-4 py-3">
                        <div className="font-medium text-slate-900 dark:text-white">
                          {u.fullName}
                        </div>
                        <div className="text-xs text-slate-400">{u.email}</div>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                            u.globalRole === 'SUPER_ADMIN'
                              ? 'bg-rose-50 text-rose-600 dark:bg-rose-950/50 dark:text-rose-400'
                              : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300'
                          }`}
                        >
                          {u.globalRole}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                            u.status === 'ACTIVE'
                              ? 'bg-emerald-50 text-emerald-600 dark:bg-emerald-950/50 dark:text-emerald-400'
                              : 'bg-amber-50 text-amber-600 dark:bg-amber-950/50 dark:text-amber-400'
                          }`}
                        >
                          {u.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs">
                        {u.emailVerified ? 'Verified' : 'Pending'}
                      </td>
                      <td className="px-4 py-3 text-right space-x-2">
                        <select
                          value={u.globalRole}
                          onChange={(e) =>
                            updateRoleMutation.mutate({
                              userId: u.id,
                              role: e.target.value as GlobalRole,
                            })
                          }
                          className="text-xs rounded border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-2 py-1"
                        >
                          <option value="USER">USER</option>
                          <option value="SUPER_ADMIN">SUPER_ADMIN</option>
                        </select>

                        <select
                          value={u.status}
                          onChange={(e) =>
                            updateStatusMutation.mutate({
                              userId: u.id,
                              status: e.target.value as UserStatus,
                            })
                          }
                          className="text-xs rounded border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-2 py-1"
                        >
                          <option value="ACTIVE">ACTIVE</option>
                          <option value="SUSPENDED">SUSPENDED</option>
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : null}
          </CardContent>
        </Card>
      )}

      {/* Tab 2: Workspaces */}
      {activeTab === 'workspaces' && (
        <Card>
          <CardContent className="p-0 overflow-x-auto">
            {loadingWorkspaces ? (
              <div className="p-6 space-y-3">
                {[1, 2, 3].map((i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : workspaces && workspaces.length > 0 ? (
              <table className="w-full text-left text-sm text-slate-600 dark:text-slate-300">
                <thead className="bg-slate-50 dark:bg-slate-900/60 text-xs uppercase font-semibold text-slate-500 border-b border-slate-200 dark:border-slate-800">
                  <tr>
                    <th className="px-4 py-3">Workspace Name</th>
                    <th className="px-4 py-3">Slug</th>
                    <th className="px-4 py-3">Documents</th>
                    <th className="px-4 py-3">Members</th>
                    <th className="px-4 py-3">Created</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {workspaces.map((ws: WorkspaceDto) => (
                    <tr key={ws.id} className="hover:bg-slate-50 dark:hover:bg-slate-900/40">
                      <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">
                        {ws.name}
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-400 font-mono">{ws.slug}</td>
                      <td className="px-4 py-3 text-xs">{ws.documentCount || 0}</td>
                      <td className="px-4 py-3 text-xs">{ws.memberCount || 1}</td>
                      <td className="px-4 py-3 text-xs text-slate-400 whitespace-nowrap">
                        {formatDate(ws.createdAt)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : null}
          </CardContent>
        </Card>
      )}

      {/* Tab 3: Global Audit Logs */}
      {activeTab === 'audit' && (
        <Card>
          <CardContent className="p-0 overflow-x-auto">
            {loadingAudit ? (
              <div className="p-6 space-y-3">
                {[1, 2, 3, 4].map((i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : auditData?.items && auditData.items.length > 0 ? (
              <>
                <table className="w-full text-left text-sm text-slate-600 dark:text-slate-300">
                  <thead className="bg-slate-50 dark:bg-slate-900/60 text-xs uppercase font-semibold text-slate-500 border-b border-slate-200 dark:border-slate-800">
                    <tr>
                      <th className="px-4 py-3">Timestamp</th>
                      <th className="px-4 py-3">Action</th>
                      <th className="px-4 py-3">Resource</th>
                      <th className="px-4 py-3">User ID</th>
                      <th className="px-4 py-3">Details</th>
                      <th className="px-4 py-3">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800 text-xs">
                    {auditData.items.map((log: AuditLogDto) => (
                      <tr key={log.id} className="hover:bg-slate-50 dark:hover:bg-slate-900/40">
                        <td className="px-4 py-3 text-slate-400 whitespace-nowrap">
                          {formatDate(log.createdAt)}
                        </td>
                        <td className="px-4 py-3 font-semibold text-slate-800 dark:text-slate-200">
                          {log.action}
                        </td>
                        <td className="px-4 py-3 text-slate-500">{log.resourceType}</td>
                        <td className="px-4 py-3 font-mono text-[11px] text-slate-400">
                          {log.userId?.slice(0, 8) || 'SYSTEM'}
                        </td>
                        <td className="px-4 py-3 text-slate-600 dark:text-slate-400 max-w-xs truncate">
                          {log.details || '—'}
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                              log.status === 'SUCCESS'
                                ? 'bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40 dark:text-emerald-400'
                                : 'bg-rose-50 text-rose-600 dark:bg-rose-950/40 dark:text-rose-400'
                            }`}
                          >
                            {log.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {/* Audit Pagination */}
                {auditData.totalPages > 1 && (
                  <div className="flex items-center justify-between p-4 border-t border-slate-100 dark:border-slate-800 text-xs text-slate-500">
                    <span>
                      Page {auditData.page + 1} of {auditData.totalPages}
                    </span>
                    <div className="flex items-center gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={!auditData.hasPrevious}
                        onClick={() => setAuditPage((p) => Math.max(0, p - 1))}
                      >
                        <ChevronLeft className="w-4 h-4 mr-1" /> Previous
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={!auditData.hasNext}
                        onClick={() => setAuditPage((p) => p + 1)}
                      >
                        Next <ChevronRight className="w-4 h-4 ml-1" />
                      </Button>
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="p-8 text-center text-xs text-slate-400">
                No audit entries found.
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
};
