import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  FileText,
  MessageSquare,
  Zap,
  Clock,
  Sparkles,
  Search,
  Upload,
  ArrowRight,
  TrendingUp,
  Database,
  Users,
  Shield,
  Activity,
  UserCheck,
  UserPlus,
  Mail,
  Cpu,
  DollarSign,
  HelpCircle,
} from 'lucide-react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
} from 'recharts';
import { useWorkspaceStore } from '../store/useWorkspaceStore';
import { useAuthStore } from '../store/useAuthStore';
import { analyticsApi } from '../api/analytics';
import { adminApi } from '../api/admin';
import { Card, CardHeader, CardContent } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { StatusBadge, RoleBadge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { formatDate } from '../lib/utils';
import { DocumentDto, AuditLogDto } from '../types';

export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const { currentWorkspace } = useWorkspaceStore();
  const { user } = useAuthStore();

  const isSuperAdmin = user?.globalRole === 'SUPER_ADMIN';
  const role = currentWorkspace?.userRole || 'VIEWER';
  const [viewMode, setViewMode] = useState<'workspace' | 'platform'>(
    isSuperAdmin && !currentWorkspace ? 'platform' : 'workspace'
  );

  // Workspace metrics query
  const { data: metrics, isLoading: isMetricsLoading } = useQuery({
    queryKey: ['dashboard-metrics', currentWorkspace?.id],
    queryFn: () => analyticsApi.getWorkspaceMetrics(currentWorkspace!.id),
    enabled: !!currentWorkspace?.id && viewMode === 'workspace',
  });

  // Global platform admin stats query (if super admin)
  const { data: adminStats, isLoading: isAdminStatsLoading } = useQuery({
    queryKey: ['admin-stats'],
    queryFn: adminApi.getSystemStats,
    enabled: isSuperAdmin && viewMode === 'platform',
  });

  return (
    <div className="space-y-8">
      {/* Role & Mode Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2.5">
            <span>Welcome, {user?.fullName}</span>
            {user?.globalRole === 'SUPER_ADMIN' ? (
              <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-500 border border-amber-500/20">
                Super Admin
              </span>
            ) : (
              <RoleBadge role={role} />
            )}
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
            {viewMode === 'platform'
              ? 'Global Enterprise Infrastructure & AI Consumption'
              : `Workspace: ${currentWorkspace?.name || 'None selected'}`}
          </p>
        </div>

        {isSuperAdmin && (
          <div className="flex items-center p-1 rounded-xl bg-slate-200/60 dark:bg-slate-900 border border-slate-300/60 dark:border-slate-800 self-start sm:self-auto">
            <button
              onClick={() => setViewMode('workspace')}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition ${
                viewMode === 'workspace'
                  ? 'bg-white dark:bg-slate-800 text-indigo-600 dark:text-indigo-400 shadow-xs'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
              }`}
            >
              Workspace View
            </button>
            <button
              onClick={() => setViewMode('platform')}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition ${
                viewMode === 'platform'
                  ? 'bg-white dark:bg-slate-800 text-amber-600 dark:text-amber-400 shadow-xs'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
              }`}
            >
              Platform Telemetry
            </button>
          </div>
        )}
      </div>

      {/* PLATFORM ADMIN VIEW (SUPER_ADMIN) */}
      {viewMode === 'platform' && isSuperAdmin ? (
        <div className="space-y-6">
          {isAdminStatsLoading ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {[1, 2, 3, 4].map((i) => (
                <Card key={i} className="p-6">
                  <Skeleton className="h-8 w-1/3 mb-2" />
                  <Skeleton className="h-4 w-2/3" />
                </Card>
              ))}
            </div>
          ) : (
            <>
              {/* Platform Metrics */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <Card className="p-5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                      Active Users
                    </span>
                    <span className="p-2 rounded-lg bg-indigo-50 dark:bg-indigo-950/40 text-indigo-600 dark:text-indigo-400">
                      <Users className="w-4 h-4" />
                    </span>
                  </div>
                  <div className="mt-3">
                    <div className="text-2xl font-bold text-slate-900 dark:text-white">
                      {adminStats?.activeUsers ?? adminStats?.totalUsers ?? 0}
                    </div>
                    <div className="text-xs text-slate-500 mt-1">
                      {adminStats?.totalUsers ?? 0} total registered • {adminStats?.suspendedUsers ?? 0} suspended
                    </div>
                  </div>
                </Card>

                <Card className="p-5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                      Workspaces
                    </span>
                    <span className="p-2 rounded-lg bg-cyan-50 dark:bg-cyan-950/40 text-cyan-600 dark:text-cyan-400">
                      <Database className="w-4 h-4" />
                    </span>
                  </div>
                  <div className="mt-3">
                    <div className="text-2xl font-bold text-slate-900 dark:text-white">
                      {adminStats?.totalWorkspaces ?? 0}
                    </div>
                    <div className="text-xs text-slate-500 mt-1">
                      {adminStats?.totalDocuments ?? 0} documents ingested
                    </div>
                  </div>
                </Card>

                <Card className="p-5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                      Global Tokens & Requests
                    </span>
                    <span className="p-2 rounded-lg bg-violet-50 dark:bg-violet-950/40 text-violet-600 dark:text-violet-400">
                      <Cpu className="w-4 h-4" />
                    </span>
                  </div>
                  <div className="mt-3">
                    <div className="text-2xl font-bold text-slate-900 dark:text-white">
                      {(adminStats?.totalGlobalTokens ?? 0).toLocaleString()}
                    </div>
                    <div className="text-xs text-slate-500 mt-1">
                      {adminStats?.aiRequestVolume ?? 0} total AI RAG operations
                    </div>
                  </div>
                </Card>

                <Card className="p-5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                      Estimated Cost (USD)
                    </span>
                    <span className="p-2 rounded-lg bg-emerald-50 dark:bg-emerald-950/40 text-emerald-600 dark:text-emerald-400">
                      <DollarSign className="w-4 h-4" />
                    </span>
                  </div>
                  <div className="mt-3">
                    <div className="text-2xl font-bold text-slate-900 dark:text-white">
                      ${adminStats?.estimatedCostUsd ?? 0.0}
                    </div>
                    <div className="text-xs text-slate-500 mt-1">
                      Gemini 1.5 Flash token consumption
                    </div>
                  </div>
                </Card>
              </div>

              {/* System Infrastructure Details */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 text-slate-200">
                  <div className="text-xs text-slate-400 font-medium">System Health</div>
                  <div className="text-base font-bold text-emerald-400 mt-1 flex items-center gap-2">
                    <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
                    {adminStats?.systemHealth || 'OPERATIONAL'}
                  </div>
                </div>
                <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 text-slate-200">
                  <div className="text-xs text-slate-400 font-medium">Active Foundation Model</div>
                  <div className="text-base font-bold text-white mt-1 flex items-center gap-2">
                    <Sparkles className="h-4 w-4 text-indigo-400" />
                    {adminStats?.activeAiModel || 'gemini-1.5-flash'}
                  </div>
                </div>
                <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 text-slate-200">
                  <div className="text-xs text-slate-400 font-medium">Vector Storage Engine</div>
                  <div className="text-base font-bold text-white mt-1 flex items-center gap-2">
                    <Database className="h-4 w-4 text-cyan-400" />
                    {adminStats?.storageEngine || 'PGVECTOR'}
                  </div>
                </div>
              </div>

              {/* Quick Admin Actions */}
              <div className="flex gap-3">
                <Button onClick={() => navigate('/admin')} className="bg-amber-600 hover:bg-amber-500 text-white">
                  <Shield className="w-4 h-4 mr-2" /> Open Full Admin Console
                </Button>
              </div>
            </>
          )}
        </div>
      ) : (
        /* WORKSPACE-BASED DASHBOARD (Tailored by Role: Admin, Editor, Viewer) */
        <>
          {/* Welcome Banner */}
          <div className="relative overflow-hidden rounded-2xl bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 p-8 border border-slate-800 text-white shadow-xl">
            <div className="relative z-10 max-w-2xl space-y-3">
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-xs font-semibold text-indigo-300">
                <Sparkles className="w-3.5 h-3.5" />
                {role === 'WORKSPACE_ADMIN'
                  ? 'Workspace Admin Control'
                  : role === 'EDITOR'
                  ? 'Content & Ingestion Studio'
                  : 'Grounded Knowledge Explorer'}
              </div>
              <h2 className="text-3xl font-extrabold tracking-tight">
                {currentWorkspace ? currentWorkspace.name : 'No Workspace Selected'}
              </h2>
              <p className="text-slate-300 text-sm">
                {role === 'WORKSPACE_ADMIN'
                  ? 'Manage team access, oversee semantic indexing health, and monitor retrieval accuracy.'
                  : role === 'EDITOR'
                  ? 'Upload documents, monitor chunking and vector embeddings, and curate knowledge.'
                  : 'Search corporate documents, inspect citations, and ask questions with 0% hallucinations.'}
              </p>

              {/* Action Buttons Tailored by Role */}
              <div className="flex flex-wrap items-center gap-3 pt-2">
                <Button
                  onClick={() => navigate('/chat')}
                  className="bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg shadow-indigo-600/30"
                >
                  <MessageSquare className="w-4 h-4 mr-2" /> Ask Question
                </Button>

                {(role === 'WORKSPACE_ADMIN' || role === 'EDITOR') && (
                  <Button
                    variant="outline"
                    onClick={() => navigate('/documents')}
                    className="bg-slate-800/80 border-slate-700 text-slate-200 hover:bg-slate-700"
                  >
                    <Upload className="w-4 h-4 mr-2" /> Upload Documents
                  </Button>
                )}

                {role === 'WORKSPACE_ADMIN' && currentWorkspace && (
                  <Button
                    variant="ghost"
                    onClick={() => navigate(`/workspaces/${currentWorkspace.id}/members`)}
                    className="text-slate-300 hover:text-white"
                  >
                    <UserPlus className="w-4 h-4 mr-2" /> Invite Members
                  </Button>
                )}

                <Button
                  variant="ghost"
                  onClick={() => navigate('/search')}
                  className="text-slate-300 hover:text-white"
                >
                  <Search className="w-4 h-4 mr-2" /> Semantic Search
                </Button>
              </div>
            </div>

            <div className="absolute right-0 top-0 bottom-0 w-1/3 bg-gradient-to-l from-indigo-500/10 to-transparent pointer-events-none" />
          </div>

          {!currentWorkspace ? (
            <Card className="p-12 text-center">
              <Database className="w-12 h-12 text-slate-400 mx-auto mb-3" />
              <h2 className="text-lg font-semibold text-slate-900 dark:text-white">
                No Workspace Selected
              </h2>
              <p className="text-sm text-slate-500 max-w-md mx-auto mt-1 mb-4">
                You need an active workspace to view documents and ask questions.
              </p>
              <Button onClick={() => navigate('/workspaces')}>Go to Workspaces</Button>
            </Card>
          ) : isMetricsLoading ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {[1, 2, 3, 4].map((i) => (
                <Card key={i} className="p-6">
                  <Skeleton className="h-8 w-1/3 mb-2" />
                  <Skeleton className="h-4 w-2/3" />
                </Card>
              ))}
            </div>
          ) : (
            <>
              {/* Role-Specific Metric Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <Card className="p-5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                      Documents
                    </span>
                    <span className="p-2 rounded-lg bg-indigo-50 dark:bg-indigo-950/40 text-indigo-600 dark:text-indigo-400">
                      <FileText className="w-4 h-4" />
                    </span>
                  </div>
                  <div className="mt-3">
                    <div className="text-2xl font-bold text-slate-900 dark:text-white">
                      {metrics?.totalDocuments ?? 0}
                    </div>
                    <div className="text-xs text-slate-500 mt-1 flex items-center gap-1">
                      <span>{metrics?.totalChunks ?? 0} chunks indexed</span>
                    </div>
                  </div>
                </Card>

                <Card className="p-5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                      Questions Asked
                    </span>
                    <span className="p-2 rounded-lg bg-emerald-50 dark:bg-emerald-950/40 text-emerald-600 dark:text-emerald-400">
                      <MessageSquare className="w-4 h-4" />
                    </span>
                  </div>
                  <div className="mt-3">
                    <div className="text-2xl font-bold text-slate-900 dark:text-white">
                      {metrics?.totalQuestions ?? 0}
                    </div>
                    <div className="text-xs text-slate-500 mt-1 flex items-center gap-1">
                      <TrendingUp className="w-3 h-3 text-emerald-500" />
                      <span>{metrics?.successRatePercent ?? 100}% retrieval precision</span>
                    </div>
                  </div>
                </Card>

                {role === 'WORKSPACE_ADMIN' ? (
                  <Card className="p-5">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                        Team Breakdown
                      </span>
                      <span className="p-2 rounded-lg bg-cyan-50 dark:bg-cyan-950/40 text-cyan-600 dark:text-cyan-400">
                        <UserCheck className="w-4 h-4" />
                      </span>
                    </div>
                    <div className="mt-3">
                      <div className="text-2xl font-bold text-slate-900 dark:text-white">
                        {metrics?.totalMembers ?? 1} Members
                      </div>
                      <div className="text-xs text-slate-500 mt-1">
                        {metrics?.adminCount ?? 1} Admins • {metrics?.editorCount ?? 0} Editors • {metrics?.viewerCount ?? 0} Viewers
                      </div>
                    </div>
                  </Card>
                ) : (
                  <Card className="p-5">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                        Avg Latency
                      </span>
                      <span className="p-2 rounded-lg bg-amber-50 dark:bg-amber-950/40 text-amber-600 dark:text-amber-400">
                        <Clock className="w-4 h-4" />
                      </span>
                    </div>
                    <div className="mt-3">
                      <div className="text-2xl font-bold text-slate-900 dark:text-white">
                        {metrics?.averageLatencyMs ?? 0} ms
                      </div>
                      <div className="text-xs text-slate-500 mt-1">Grounded RAG stream</div>
                    </div>
                  </Card>
                )}

                <Card className="p-5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                      {role === 'WORKSPACE_ADMIN' ? 'Pending Invitations' : 'Tokens Consumed'}
                    </span>
                    <span className="p-2 rounded-lg bg-violet-50 dark:bg-violet-950/40 text-violet-600 dark:text-violet-400">
                      {role === 'WORKSPACE_ADMIN' ? <Mail className="w-4 h-4" /> : <Zap className="w-4 h-4" />}
                    </span>
                  </div>
                  <div className="mt-3">
                    <div className="text-2xl font-bold text-slate-900 dark:text-white">
                      {role === 'WORKSPACE_ADMIN'
                        ? metrics?.pendingInvitationsCount ?? 0
                        : (metrics?.totalTokensConsumed ?? 0).toLocaleString()}
                    </div>
                    <div className="text-xs text-slate-500 mt-1">
                      {role === 'WORKSPACE_ADMIN' ? 'Awaiting response' : 'Prompt & completion'}
                    </div>
                  </div>
                </Card>
              </div>

              {/* Activity Charts (Admins and Editors) */}
              {(role === 'WORKSPACE_ADMIN' || role === 'EDITOR') && (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
                      <h3 className="font-semibold text-sm text-slate-900 dark:text-white">
                        Questions Asked Over Time
                      </h3>
                    </CardHeader>
                    <CardContent className="pt-4 h-64">
                      {metrics?.questionsOverTime && metrics.questionsOverTime.length > 0 ? (
                        <ResponsiveContainer width="100%" height="100%">
                          <AreaChart data={metrics.questionsOverTime}>
                            <defs>
                              <linearGradient id="qColor" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="#6366f1" stopOpacity={0.8} />
                                <stop offset="95%" stopColor="#6366f1" stopOpacity={0} />
                              </linearGradient>
                            </defs>
                            <CartesianGrid strokeDasharray="3 3" opacity={0.1} />
                            <XAxis dataKey="date" stroke="#888888" fontSize={11} />
                            <YAxis stroke="#888888" fontSize={11} />
                            <Tooltip />
                            <Area
                              type="monotone"
                              dataKey="count"
                              stroke="#6366f1"
                              fillOpacity={1}
                              fill="url(#qColor)"
                            />
                          </AreaChart>
                        </ResponsiveContainer>
                      ) : (
                        <div className="h-full flex items-center justify-center text-xs text-slate-400">
                          No question activity recorded yet.
                        </div>
                      )}
                    </CardContent>
                  </Card>

                  <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
                      <h3 className="font-semibold text-sm text-slate-900 dark:text-white">
                        Documents Ingested Over Time
                      </h3>
                    </CardHeader>
                    <CardContent className="pt-4 h-64">
                      {metrics?.documentsOverTime && metrics.documentsOverTime.length > 0 ? (
                        <ResponsiveContainer width="100%" height="100%">
                          <AreaChart data={metrics.documentsOverTime}>
                            <defs>
                              <linearGradient id="dColor" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="#10b981" stopOpacity={0.8} />
                                <stop offset="95%" stopColor="#10b981" stopOpacity={0} />
                              </linearGradient>
                            </defs>
                            <CartesianGrid strokeDasharray="3 3" opacity={0.1} />
                            <XAxis dataKey="date" stroke="#888888" fontSize={11} />
                            <YAxis stroke="#888888" fontSize={11} />
                            <Tooltip />
                            <Area
                              type="monotone"
                              dataKey="count"
                              stroke="#10b981"
                              fillOpacity={1}
                              fill="url(#dColor)"
                            />
                          </AreaChart>
                        </ResponsiveContainer>
                      ) : (
                        <div className="h-full flex items-center justify-center text-xs text-slate-400">
                          No ingestion data yet.
                        </div>
                      )}
                    </CardContent>
                  </Card>
                </div>
              )}

              {/* Viewer Suggested Prompts & Shortcuts */}
              {role === 'VIEWER' && (
                <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4">
                  <div className="flex items-center gap-2">
                    <HelpCircle className="h-5 w-5 text-indigo-400" />
                    <h3 className="text-base font-semibold text-white">Suggested Knowledge Inquiries</h3>
                  </div>
                  <p className="text-xs text-slate-400">
                    Click any prompt to instantly launch an AI query grounded in this workspace's documents:
                  </p>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-1">
                    {[
                      'What is our annual paid leave policy?',
                      'Explain the enterprise onboarding workflow and steps.',
                      'What are the security compliance rules for customer data?',
                    ].map((prompt, idx) => (
                      <button
                        key={idx}
                        onClick={() => navigate('/chat')}
                        className="p-3 text-left rounded-xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700/60 hover:border-indigo-500/50 text-xs text-slate-200 transition space-y-1"
                      >
                        <span className="font-semibold text-indigo-400 block">Prompt {idx + 1}</span>
                        <span>"{prompt}"</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* Recent Documents & Activity */}
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <Card>
                  <CardHeader className="flex flex-row items-center justify-between pb-3 border-b border-slate-100 dark:border-slate-800">
                    <h3 className="font-semibold text-sm text-slate-900 dark:text-white">
                      Recent Documents
                    </h3>
                    <Link
                      to="/documents"
                      className="text-xs text-indigo-500 hover:text-indigo-600 flex items-center gap-1 font-medium"
                    >
                      View All <ArrowRight className="w-3 h-3" />
                    </Link>
                  </CardHeader>
                  <CardContent className="p-0">
                    {metrics?.recentDocuments && metrics.recentDocuments.length > 0 ? (
                      <div className="divide-y divide-slate-100 dark:divide-slate-800">
                        {metrics.recentDocuments.slice(0, 5).map((doc: DocumentDto) => (
                          <div
                            key={doc.id}
                            className="flex items-center justify-between p-3.5 hover:bg-slate-50 dark:hover:bg-slate-900/40"
                          >
                            <div className="min-w-0 pr-2">
                              <div className="text-sm font-medium text-slate-900 dark:text-white truncate">
                                {doc.title}
                              </div>
                              <div className="text-xs text-slate-400">
                                {doc.fileType} • {doc.totalChunks} chunks • {formatDate(doc.createdAt)}
                              </div>
                            </div>
                            <StatusBadge status={doc.status} />
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="p-8 text-center text-xs text-slate-400">
                        No documents uploaded in this workspace yet.
                      </div>
                    )}
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader className="flex flex-row items-center justify-between pb-3 border-b border-slate-100 dark:border-slate-800">
                    <h3 className="font-semibold text-sm text-slate-900 dark:text-white">
                      Recent Audit Activity
                    </h3>
                    {role === 'WORKSPACE_ADMIN' && (
                      <Link
                        to="/analytics"
                        className="text-xs text-indigo-500 hover:text-indigo-600 flex items-center gap-1 font-medium"
                      >
                        View Full Logs <ArrowRight className="w-3 h-3" />
                      </Link>
                    )}
                  </CardHeader>
                  <CardContent className="p-0">
                    {metrics?.recentActivity && metrics.recentActivity.length > 0 ? (
                      <div className="divide-y divide-slate-100 dark:divide-slate-800">
                        {metrics.recentActivity.slice(0, 5).map((log: AuditLogDto) => (
                          <div key={log.id} className="p-3.5 text-xs space-y-1">
                            <div className="flex items-center justify-between">
                              <span className="font-medium text-slate-800 dark:text-slate-200">
                                {log.action}
                              </span>
                              <span className="text-slate-400">{formatDate(log.createdAt)}</span>
                            </div>
                            <div className="text-slate-500">
                              {log.resourceType}: {log.details || log.resourceId}
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="p-8 text-center text-xs text-slate-400">
                        No recent activity logged.
                      </div>
                    )}
                  </CardContent>
                </Card>
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
};
