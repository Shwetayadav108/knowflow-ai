import React from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  PieChart,
  Pie,
  Cell,
} from 'recharts';
import { Database } from 'lucide-react';
import { useWorkspaceStore } from '../../store/useWorkspaceStore';
import { analyticsApi } from '../../api/analytics';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { formatDate } from '../../lib/utils';

const COLORS = ['#10b981', '#6366f1', '#f43f5e'];

export const AnalyticsPage: React.FC = () => {
  const { currentWorkspace } = useWorkspaceStore();

  const { data: metrics, isLoading } = useQuery({
    queryKey: ['workspace-analytics', currentWorkspace?.id],
    queryFn: () => analyticsApi.getWorkspaceMetrics(currentWorkspace!.id),
    enabled: !!currentWorkspace?.id,
  });

  if (!currentWorkspace) {
    return (
      <Card className="p-12 text-center">
        <Database className="w-12 h-12 text-slate-400 mx-auto mb-3" />
        <h2 className="text-lg font-semibold text-slate-900 dark:text-white">
          No Workspace Active
        </h2>
        <p className="text-sm text-slate-500 max-w-md mx-auto mt-1">
          Select or create a workspace to view detailed analytics.
        </p>
      </Card>
    );
  }

  const pipelineData = [
    { name: 'Processed', value: metrics?.processedDocuments || 0 },
    {
      name: 'Processing',
      value: Math.max(
        0,
        (metrics?.totalDocuments || 0) -
          (metrics?.processedDocuments || 0) -
          (metrics?.failedDocuments || 0)
      ),
    },
    { name: 'Failed', value: metrics?.failedDocuments || 0 },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
          Workspace Analytics
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Operational metrics, ingestion throughput, and RAG accuracy for {currentWorkspace.name}.
        </p>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <Card key={i} className="p-6">
              <Skeleton className="h-6 w-1/3 mb-2" />
              <Skeleton className="h-8 w-2/3" />
            </Card>
          ))}
        </div>
      ) : (
        <>
          {/* Key Stat Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <Card className="p-5">
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                Retrieval Precision
              </div>
              <div className="mt-2 flex items-baseline gap-2">
                <span className="text-3xl font-extrabold text-slate-900 dark:text-white">
                  {metrics?.successRatePercent ?? 100}%
                </span>
                <span className="text-xs text-emerald-500 font-medium">Faithful Grounding</span>
              </div>
            </Card>

            <Card className="p-5">
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                Average Latency
              </div>
              <div className="mt-2 flex items-baseline gap-2">
                <span className="text-3xl font-extrabold text-slate-900 dark:text-white">
                  {metrics?.averageLatencyMs ?? 0}
                </span>
                <span className="text-xs text-slate-400">ms / query</span>
              </div>
            </Card>

            <Card className="p-5">
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                Tokens Consumed
              </div>
              <div className="mt-2 flex items-baseline gap-2">
                <span className="text-3xl font-extrabold text-slate-900 dark:text-white">
                  {(metrics?.totalTokensConsumed ?? 0).toLocaleString()}
                </span>
                <span className="text-xs text-slate-400">tokens</span>
              </div>
            </Card>

            <Card className="p-5">
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                Indexed Chunks
              </div>
              <div className="mt-2 flex items-baseline gap-2">
                <span className="text-3xl font-extrabold text-slate-900 dark:text-white">
                  {metrics?.totalChunks ?? 0}
                </span>
                <span className="text-xs text-slate-400">vectors (768-dim)</span>
              </div>
            </Card>
          </div>

          {/* Charts Row */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <Card className="lg:col-span-2">
              <CardHeader className="border-b border-slate-100 dark:border-slate-800 pb-3">
                <h3 className="font-semibold text-sm text-slate-900 dark:text-white">
                  Questions Handled Over Time
                </h3>
              </CardHeader>
              <CardContent className="pt-4 h-72">
                {metrics?.questionsOverTime && metrics.questionsOverTime.length > 0 ? (
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={metrics.questionsOverTime}>
                      <CartesianGrid strokeDasharray="3 3" opacity={0.1} />
                      <XAxis dataKey="date" stroke="#888888" fontSize={11} />
                      <YAxis stroke="#888888" fontSize={11} />
                      <Tooltip />
                      <Area
                        type="monotone"
                        dataKey="count"
                        stroke="#6366f1"
                        fill="#6366f1"
                        fillOpacity={0.2}
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                ) : (
                  <div className="h-full flex items-center justify-center text-xs text-slate-400">
                    No time series question data yet.
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="border-b border-slate-100 dark:border-slate-800 pb-3">
                <h3 className="font-semibold text-sm text-slate-900 dark:text-white">
                  Pipeline Status
                </h3>
              </CardHeader>
              <CardContent className="pt-4 h-72 flex flex-col items-center justify-center">
                {metrics && metrics.totalDocuments > 0 ? (
                  <>
                    <ResponsiveContainer width="100%" height={180}>
                      <PieChart>
                        <Pie
                          data={pipelineData}
                          innerRadius={50}
                          outerRadius={75}
                          paddingAngle={5}
                          dataKey="value"
                        >
                          {pipelineData.map((_, index) => (
                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                          ))}
                        </Pie>
                        <Tooltip />
                      </PieChart>
                    </ResponsiveContainer>
                    <div className="flex justify-center gap-4 text-xs mt-2">
                      <span className="flex items-center gap-1.5">
                        <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" /> Processed (
                        {metrics.processedDocuments})
                      </span>
                      <span className="flex items-center gap-1.5">
                        <span className="w-2.5 h-2.5 rounded-full bg-rose-500" /> Failed (
                        {metrics.failedDocuments})
                      </span>
                    </div>
                  </>
                ) : (
                  <div className="text-xs text-slate-400">No documents in pipeline</div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Audit Logs Table */}
          <Card>
            <CardHeader className="border-b border-slate-100 dark:border-slate-800">
              <h3 className="font-semibold text-sm text-slate-900 dark:text-white">
                Workspace Audit Trail
              </h3>
            </CardHeader>
            <CardContent className="p-0">
              {metrics?.recentActivity && metrics.recentActivity.length > 0 ? (
                <div className="divide-y divide-slate-100 dark:divide-slate-800 text-xs">
                  {metrics.recentActivity.map((log) => (
                    <div
                      key={log.id}
                      className="flex items-center justify-between p-3.5 hover:bg-slate-50 dark:hover:bg-slate-900/40"
                    >
                      <div>
                        <span className="font-semibold text-slate-900 dark:text-white">
                          {log.action}
                        </span>{' '}
                        <span className="text-slate-400">on {log.resourceType}</span>
                        {log.details && (
                          <div className="text-slate-500 mt-0.5">{log.details}</div>
                        )}
                      </div>
                      <div className="text-right text-slate-400 whitespace-nowrap">
                        {formatDate(log.createdAt)}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="p-8 text-center text-xs text-slate-400">
                  No audit logs recorded for this workspace.
                </div>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
};
