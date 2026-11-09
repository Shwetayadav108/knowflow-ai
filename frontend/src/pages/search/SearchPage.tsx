import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Search as SearchIcon,
  SlidersHorizontal,
  BookOpen,
  Sparkles,
  Database,
} from 'lucide-react';
import { useWorkspaceStore } from '../../store/useWorkspaceStore';
import { searchApi } from '../../api/search';
import { RetrievedPassage } from '../../types';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Card } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';

export const SearchPage: React.FC = () => {
  const { currentWorkspace } = useWorkspaceStore();

  const [query, setQuery] = useState('');
  const [activeQuery, setActiveQuery] = useState('');
  const [searchType, setSearchType] = useState<'SEMANTIC' | 'HYBRID' | 'KEYWORD'>('SEMANTIC');
  const [topK, setTopK] = useState(5);
  const [threshold, setThreshold] = useState(0.65);
  const [showFilters, setShowFilters] = useState(false);

  const { data: results, isLoading, isFetching } = useQuery({
    queryKey: ['semantic-search', currentWorkspace?.id, activeQuery, searchType, topK, threshold],
    queryFn: () =>
      searchApi.search(currentWorkspace!.id, {
        query: activeQuery,
        searchType,
        topK,
        similarityThreshold: threshold,
      }),
    enabled: !!currentWorkspace?.id && !!activeQuery.trim(),
  });

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;
    setActiveQuery(query.trim());
  };

  if (!currentWorkspace) {
    return (
      <Card className="p-12 text-center">
        <Database className="w-12 h-12 text-slate-400 mx-auto mb-3" />
        <h2 className="text-lg font-semibold text-slate-900 dark:text-white">
          No Workspace Active
        </h2>
        <p className="text-sm text-slate-500 max-w-md mx-auto mt-1">
          Select or create a workspace to perform semantic vector searches.
        </p>
      </Card>
    );
  }

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
          Semantic Search
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Probe high-dimensional vector embeddings across all documents in {currentWorkspace.name}.
        </p>
      </div>

      {/* Search Input Box */}
      <Card className="p-4 shadow-sm">
        <form onSubmit={handleSearch} className="space-y-4">
          <div className="relative flex items-center">
            <SearchIcon className="w-5 h-5 absolute left-3.5 text-slate-400" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search across company knowledge (e.g. data retention policy, annual leave entitlement)..."
              className="w-full pl-11 pr-28 py-3 text-sm rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 shadow-inner"
            />
            <div className="absolute right-2 flex items-center gap-1.5">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => setShowFilters(!showFilters)}
                className={showFilters ? 'bg-slate-100 dark:bg-slate-800' : ''}
              >
                <SlidersHorizontal className="w-4 h-4" />
              </Button>
              <Button type="submit" size="sm" disabled={!query.trim() || isFetching}>
                Search
              </Button>
            </div>
          </div>

          {/* Configurable Filters & Parameters */}
          {showFilters && (
            <div className="pt-3 border-t border-slate-100 dark:border-slate-800 grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
              <div>
                <label className="block font-medium text-slate-600 dark:text-slate-400 mb-1">
                  Search Engine
                </label>
                <select
                  value={searchType}
                  onChange={(e) => setSearchType(e.target.value as any)}
                  className="w-full px-2.5 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100"
                >
                  <option value="SEMANTIC">Dense Semantic (pgvector cosine)</option>
                  <option value="HYBRID">Hybrid (Dense + Keyword)</option>
                  <option value="KEYWORD">Keyword Match</option>
                </select>
              </div>

              <div>
                <label className="block font-medium text-slate-600 dark:text-slate-400 mb-1">
                  Top-K Passages: {topK}
                </label>
                <input
                  type="range"
                  min="1"
                  max="20"
                  value={topK}
                  onChange={(e) => setTopK(Number(e.target.value))}
                  className="w-full accent-indigo-600"
                />
              </div>

              <div>
                <label className="block font-medium text-slate-600 dark:text-slate-400 mb-1">
                  Similarity Threshold: {Math.round(threshold * 100)}%
                </label>
                <input
                  type="range"
                  min="0.3"
                  max="0.95"
                  step="0.05"
                  value={threshold}
                  onChange={(e) => setThreshold(Number(e.target.value))}
                  className="w-full accent-indigo-600"
                />
              </div>
            </div>
          )}
        </form>
      </Card>

      {/* Results View */}
      {isLoading || isFetching ? (
        <div className="space-y-4">
          {[1, 2, 3].map((i) => (
            <Card key={i} className="p-6 space-y-3">
              <Skeleton className="h-5 w-1/3" />
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-4 w-1/4" />
            </Card>
          ))}
        </div>
      ) : results && results.length > 0 ? (
        <div className="space-y-4">
          <div className="flex items-center justify-between text-xs text-slate-500 px-1">
            <span>
              Found {results.length} relevant passages matching "{activeQuery}"
            </span>
            <span className="flex items-center gap-1 text-emerald-600 dark:text-emerald-400">
              <Sparkles className="w-3.5 h-3.5" /> High Confidence Results
            </span>
          </div>

          {results.map((passage: RetrievedPassage, idx: number) => {
            const scorePercent = Math.round(passage.relevanceScore * 100);
            return (
              <Card
                key={passage.chunkId || idx}
                className="p-5 hover:border-indigo-400 transition-colors"
              >
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-3 border-b border-slate-100 dark:border-slate-800 gap-2">
                  <div className="flex items-center gap-2">
                    <span className="w-6 h-6 rounded-md bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 text-xs font-bold flex items-center justify-center">
                      #{idx + 1}
                    </span>
                    <h3 className="font-semibold text-sm text-slate-900 dark:text-white">
                      {passage.documentTitle}
                    </h3>
                    <span className="text-xs text-slate-400">• Page {passage.pageNumber}</span>
                  </div>

                  <div className="flex items-center gap-2 text-xs">
                    <span className="text-slate-500">Relevance:</span>
                    <div className="w-20 bg-slate-200 dark:bg-slate-700 h-2 rounded-full overflow-hidden">
                      <div
                        className="bg-emerald-500 h-2 rounded-full"
                        style={{ width: `${scorePercent}%` }}
                      />
                    </div>
                    <span className="font-semibold text-emerald-600 dark:text-emerald-400">
                      {scorePercent}%
                    </span>
                  </div>
                </div>

                {passage.sectionHeading && (
                  <div className="text-xs font-medium text-indigo-600 dark:text-indigo-400 mt-2">
                    Section: {passage.sectionHeading}
                  </div>
                )}

                <div className="mt-2 text-sm text-slate-700 dark:text-slate-300 leading-relaxed font-mono text-xs whitespace-pre-wrap bg-slate-50/50 dark:bg-slate-950/40 p-3 rounded-lg border border-slate-100 dark:border-slate-800/60">
                  {passage.content}
                </div>
              </Card>
            );
          })}
        </div>
      ) : activeQuery ? (
        <Card className="p-12 text-center text-slate-500">
          <BookOpen className="w-10 h-10 mx-auto text-slate-400 mb-2" />
          <p className="font-medium text-slate-900 dark:text-white">
            No matching passages found above the {Math.round(threshold * 100)}% similarity threshold
          </p>
          <p className="text-xs text-slate-500 mt-1">
            Try adjusting your query, lowering the threshold, or uploading additional documents.
          </p>
        </Card>
      ) : (
        <div className="py-16 text-center text-slate-400 text-sm">
          Enter a question or topic above to explore semantic embeddings.
        </div>
      )}
    </div>
  );
};
