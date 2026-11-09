import React, { useState, useEffect, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import ReactMarkdown from 'react-markdown';
import {
  MessageSquare,
  Plus,
  Send,
  Square,
  Trash2,
  Edit2,
  Search,
  BookOpen,
  ThumbsUp,
  ThumbsDown,
  Copy,
  Check,
  AlertTriangle,
  Bot,
  User,
  Sparkles,
  ChevronRight,
  Scale,
  Download,
} from 'lucide-react';
import { useWorkspaceStore } from '../../store/useWorkspaceStore';
import { chatApi } from '../../api/chat';
import { documentApi } from '../../api/documents';
import { CitationDto, DocumentDto } from '../../types';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';
import { Card } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { toast } from '../../components/ui/Toast';
import { formatDate } from '../../lib/utils';

export const ChatPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { currentWorkspace } = useWorkspaceStore();

  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [conversationSearch, setConversationSearch] = useState('');
  const [inputMessage, setInputMessage] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamingContent, setStreamingContent] = useState('');
  const [streamingCitations, setStreamingCitations] = useState<CitationDto[]>([]);
  const abortControllerRef = useRef<AbortController | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Citations drawer state
  const [activeCitation, setActiveCitation] = useState<CitationDto | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Rename modal
  const [renameModalOpen, setRenameModalOpen] = useState(false);
  const [renameTitle, setRenameTitle] = useState('');
  const [renameTargetId, setRenameTargetId] = useState<string | null>(null);

  // Document comparison modal state
  const [compareModalOpen, setCompareModalOpen] = useState(false);
  const [compareDocId1, setCompareDocId1] = useState('');
  const [compareDocId2, setCompareDocId2] = useState('');
  const [compareInstruction, setCompareInstruction] = useState('');
  const [isComparing, setIsComparing] = useState(false);

  // Conversations query
  const { data: conversations, isLoading: loadingConvs } = useQuery({
    queryKey: ['conversations', currentWorkspace?.id, conversationSearch],
    queryFn: () =>
      conversationSearch.trim()
        ? chatApi.searchConversations(currentWorkspace!.id, conversationSearch)
        : chatApi.getConversations(currentWorkspace!.id),
    enabled: !!currentWorkspace?.id,
  });

  // Workspace documents query for document comparison selector
  const { data: availableDocs } = useQuery({
    queryKey: ['documents-list-all', currentWorkspace?.id],
    queryFn: () => documentApi.getDocuments(currentWorkspace!.id, { size: 100 }),
    enabled: !!currentWorkspace?.id && compareModalOpen,
  });

  // Automatically select the first conversation if none selected
  useEffect(() => {
    if (conversations && conversations.length > 0 && !activeConversationId) {
      setActiveConversationId(conversations[0].id);
    }
  }, [conversations, activeConversationId]);

  // Messages query for active conversation
  const { data: messages, isLoading: loadingMessages } = useQuery({
    queryKey: ['messages', currentWorkspace?.id, activeConversationId],
    queryFn: () => chatApi.getMessages(currentWorkspace!.id, activeConversationId!),
    enabled: !!currentWorkspace?.id && !!activeConversationId,
  });

  // Auto scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamingContent]);

  // Create conversation
  const createConvMutation = useMutation({
    mutationFn: () =>
      chatApi.createConversation(currentWorkspace!.id, { title: 'New Conversation' }),
    onSuccess: (newConv) => {
      queryClient.invalidateQueries({ queryKey: ['conversations', currentWorkspace?.id] });
      setActiveConversationId(newConv.id);
    },
    onError: (err: any) => {
      toast.error('Failed to create conversation', err.response?.data?.message || err.message);
    },
  });

  // Rename conversation
  const renameConvMutation = useMutation({
    mutationFn: () =>
      chatApi.renameConversation(currentWorkspace!.id, renameTargetId!, renameTitle.trim()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['conversations', currentWorkspace?.id] });
      toast.success('Conversation renamed', 'Title updated.');
      setRenameModalOpen(false);
    },
    onError: (err: any) => {
      toast.error('Rename failed', err.response?.data?.message || err.message);
    },
  });

  // Delete conversation
  const deleteConvMutation = useMutation({
    mutationFn: (convId: string) =>
      chatApi.deleteConversation(currentWorkspace!.id, convId),
    onSuccess: (_, deletedId) => {
      queryClient.invalidateQueries({ queryKey: ['conversations', currentWorkspace?.id] });
      toast.success('Conversation deleted', 'History removed.');
      if (activeConversationId === deletedId) {
        setActiveConversationId(null);
      }
    },
    onError: (err: any) => {
      toast.error('Delete failed', err.response?.data?.message || err.message);
    },
  });

  const handleCopy = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleFeedback = async (messageId: string, rating: number) => {
    if (!currentWorkspace) return;
    try {
      await chatApi.submitFeedback(currentWorkspace.id, messageId, { rating });
      toast.success('Feedback recorded', 'Thank you for helping evaluate AI groundedness.');
    } catch (err: any) {
      toast.error('Feedback failed', err.response?.data?.message || err.message);
    }
  };

  const handleExportConversation = () => {
    if (!messages || messages.length === 0) {
      toast.info('No messages to export', 'Start a conversation first.');
      return;
    }

    const title = conversations?.find((c) => c.id === activeConversationId)?.title || 'KnowFlow_Conversation';
    let md = `# KnowFlow AI Conversation: ${title}\n`;
    md += `Workspace: ${currentWorkspace?.name || 'Workspace'}\n`;
    md += `Exported: ${new Date().toISOString()}\n\n---\n\n`;

    messages.forEach((msg, idx) => {
      md += `### ${msg.sender === 'USER' ? '👤 User' : '🤖 KnowFlow Assistant'} (${formatDate(msg.createdAt)})\n\n`;
      md += `${msg.content}\n\n`;
      if (msg.citations && msg.citations.length > 0) {
        md += `**Citations:**\n`;
        msg.citations.forEach((c, cIdx) => {
          md += `* [${cIdx + 1}] **${c.documentTitle}** (Page ${c.pageNumber}): "${c.supportingPassage}"\n`;
        });
        md += `\n`;
      }
      md += `---\n\n`;
    });

    const blob = new Blob([md], { type: 'text/markdown;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `${title.replace(/[^a-z0-9]/gi, '_').toLowerCase()}.md`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    toast.success('Conversation exported', 'Markdown file downloaded.');
  };

  const handleDocumentComparison = async () => {
    if (!currentWorkspace || !compareDocId1 || !compareDocId2) {
      toast.error('Select documents', 'Please select two documents to compare.');
      return;
    }
    if (compareDocId1 === compareDocId2) {
      toast.error('Different documents required', 'Please select two different documents to compare.');
      return;
    }

    setIsComparing(true);
    try {
      const resultMessage = await chatApi.compareDocuments(currentWorkspace.id, {
        docId1: compareDocId1,
        docId2: compareDocId2,
        instruction: compareInstruction.trim() || undefined,
      });

      await queryClient.invalidateQueries({ queryKey: ['conversations', currentWorkspace.id] });
      setActiveConversationId(resultMessage.conversationId);
      await queryClient.invalidateQueries({
        queryKey: ['messages', currentWorkspace.id, resultMessage.conversationId],
      });

      toast.success('Comparison complete', 'Document comparison analysis generated.');
      setCompareModalOpen(false);
      setCompareDocId1('');
      setCompareDocId2('');
      setCompareInstruction('');
    } catch (err: any) {
      toast.error('Comparison failed', err.response?.data?.message || err.message);
    } finally {
      setIsComparing(false);
    }
  };

  const handleSend = async (customPrompt?: string) => {
    const question = (customPrompt || inputMessage).trim();
    if (!question || !currentWorkspace || isStreaming) return;

    setInputMessage('');
    setIsStreaming(true);
    setStreamingContent('');
    setStreamingCitations([]);

    let convId = activeConversationId;
    if (!convId) {
      try {
        const newConv = await chatApi.createConversation(currentWorkspace.id, {
          title: question.slice(0, 30) + '...',
        });
        convId = newConv.id;
        setActiveConversationId(convId);
        queryClient.invalidateQueries({ queryKey: ['conversations', currentWorkspace.id] });
      } catch (err: any) {
        toast.error('Could not start conversation', err.message);
        setIsStreaming(false);
        return;
      }
    }

    const controller = new AbortController();
    abortControllerRef.current = controller;

    try {
      await chatApi.streamMessage(
        currentWorkspace.id,
        convId,
        { content: question },
        {
          onToken: (tok) => {
            setStreamingContent((prev) => prev + tok);
          },
          onCitations: (cites) => {
            setStreamingCitations(cites);
          },
          onDone: () => {
            setIsStreaming(false);
            setStreamingContent('');
            setStreamingCitations([]);
            queryClient.invalidateQueries({
              queryKey: ['messages', currentWorkspace.id, convId],
            });
            queryClient.invalidateQueries({
              queryKey: ['conversations', currentWorkspace.id],
            });
          },
          onError: async () => {
            // Fallback to non-streaming endpoint
            try {
              await chatApi.sendMessage(currentWorkspace.id, convId!, { content: question });
              setIsStreaming(false);
              setStreamingContent('');
              setStreamingCitations([]);
              queryClient.invalidateQueries({
                queryKey: ['messages', currentWorkspace.id, convId],
              });
            } catch (fallbackErr: any) {
              toast.error(
                'AI response failed',
                fallbackErr.response?.data?.message || fallbackErr.message
              );
              setIsStreaming(false);
              setStreamingContent('');
              setStreamingCitations([]);
            }
          },
        },
        controller.signal
      );
    } catch {
      setIsStreaming(false);
      setStreamingContent('');
      setStreamingCitations([]);
    }
  };

  const handleStopStreaming = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      setIsStreaming(false);
      setStreamingContent('');
      setStreamingCitations([]);
    }
  };

  if (!currentWorkspace) {
    return (
      <Card className="p-12 text-center">
        <MessageSquare className="w-12 h-12 text-slate-400 mx-auto mb-3" />
        <h2 className="text-lg font-semibold text-slate-900 dark:text-white">
          No Workspace Active
        </h2>
        <p className="text-sm text-slate-500 max-w-md mx-auto mt-1">
          Select or create a workspace to begin natural language conversation.
        </p>
      </Card>
    );
  }

  const activeConv = conversations?.find((c) => c.id === activeConversationId);

  return (
    <div className="flex h-[calc(100vh-8rem)] rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 overflow-hidden shadow-sm">
      {/* Conversations Sidebar */}
      <div className="w-80 flex-shrink-0 border-r border-slate-200 dark:border-slate-800 flex flex-col bg-slate-50/50 dark:bg-slate-950/40">
        <div className="p-3 border-b border-slate-200 dark:border-slate-800 flex flex-col gap-2">
          <Button
            onClick={() => createConvMutation.mutate()}
            className="w-full justify-center gap-2"
            size="sm"
          >
            <Plus className="w-4 h-4" /> New Conversation
          </Button>

          <Button
            variant="outline"
            onClick={() => setCompareModalOpen(true)}
            className="w-full justify-center gap-2 text-xs border-indigo-500/30 text-indigo-600 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-950/40"
            size="sm"
          >
            <Scale className="w-3.5 h-3.5" /> Compare 2 Documents
          </Button>
        </div>

        {/* Search within conversations */}
        <div className="p-2 border-b border-slate-200 dark:border-slate-800">
          <div className="relative">
            <Search className="w-3.5 h-3.5 absolute left-2.5 top-2.5 text-slate-400" />
            <input
              type="text"
              placeholder="Search conversations..."
              value={conversationSearch}
              onChange={(e) => setConversationSearch(e.target.value)}
              className="w-full pl-8 pr-2 py-1.5 text-xs rounded-md border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
          </div>
        </div>

        {/* Conversations list */}
        <div className="flex-1 overflow-y-auto divide-y divide-slate-100 dark:divide-slate-800/60 p-1">
          {loadingConvs ? (
            <div className="p-3 space-y-2">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : conversations && conversations.length > 0 ? (
            conversations.map((conv) => {
              const isSelected = conv.id === activeConversationId;
              return (
                <div
                  key={conv.id}
                  onClick={() => setActiveConversationId(conv.id)}
                  className={`group relative flex items-center justify-between p-3 rounded-lg cursor-pointer text-xs transition-colors ${
                    isSelected
                      ? 'bg-indigo-50 dark:bg-indigo-950/60 text-indigo-900 dark:text-indigo-200 font-medium'
                      : 'hover:bg-slate-100 dark:hover:bg-slate-800/60 text-slate-700 dark:text-slate-300'
                  }`}
                >
                  <div className="min-w-0 flex-1 pr-2">
                    <div className="truncate">{conv.title || 'Untitled Conversation'}</div>
                    <div className="text-[10px] text-slate-400 mt-0.5">
                      {formatDate(conv.updatedAt || conv.createdAt)}
                    </div>
                  </div>

                  <div className="opacity-0 group-hover:opacity-100 flex items-center gap-1 transition-opacity">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        setRenameTargetId(conv.id);
                        setRenameTitle(conv.title);
                        setRenameModalOpen(true);
                      }}
                      className="p-1 hover:text-indigo-600 rounded"
                      title="Rename"
                    >
                      <Edit2 className="w-3 h-3" />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        deleteConvMutation.mutate(conv.id);
                      }}
                      className="p-1 hover:text-rose-600 rounded"
                      title="Delete"
                    >
                      <Trash2 className="w-3 h-3" />
                    </button>
                  </div>
                </div>
              );
            })
          ) : (
            <div className="p-6 text-center text-xs text-slate-400">
              No conversations yet.
            </div>
          )}
        </div>
      </div>

      {/* Main Chat Area */}
      <div className="flex-1 flex flex-col min-w-0 bg-white dark:bg-slate-900">
        {/* Chat Header */}
        <div className="h-14 px-6 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2 min-w-0">
            <Sparkles className="w-4 h-4 text-indigo-500" />
            <h2 className="text-sm font-semibold text-slate-900 dark:text-white truncate">
              {activeConv ? activeConv.title : 'New AI Consultation'}
            </h2>
          </div>
          <div className="flex items-center gap-2 text-xs">
            <Button
              variant="ghost"
              size="sm"
              onClick={handleExportConversation}
              className="text-xs text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
              title="Export Conversation as Markdown"
            >
              <Download className="w-3.5 h-3.5 mr-1" /> Export
            </Button>
            <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-emerald-50 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400 font-medium">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" /> Gemini RAG Ready
            </span>
          </div>
        </div>

        {/* Message Viewport */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {loadingMessages ? (
            <div className="space-y-4">
              <Skeleton className="h-16 w-3/4" />
              <Skeleton className="h-24 w-5/6 ml-auto" />
            </div>
          ) : messages && messages.length > 0 ? (
            messages.map((msg) => (
              <div
                key={msg.id}
                className={`flex gap-3 max-w-3xl ${
                  msg.sender === 'USER' ? 'ml-auto flex-row-reverse' : ''
                }`}
              >
                {/* Avatar */}
                <div
                  className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 text-white font-medium text-xs ${
                    msg.sender === 'USER'
                      ? 'bg-gradient-to-tr from-slate-700 to-slate-900'
                      : 'bg-gradient-to-tr from-indigo-600 to-violet-600'
                  }`}
                >
                  {msg.sender === 'USER' ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
                </div>

                {/* Message Bubble */}
                <div
                  className={`group relative rounded-2xl p-4 text-sm ${
                    msg.sender === 'USER'
                      ? 'bg-indigo-600 text-white'
                      : 'bg-slate-50 dark:bg-slate-800/80 border border-slate-100 dark:border-slate-800 text-slate-800 dark:text-slate-100'
                  }`}
                >
                  {/* Warning banner if retrieval is insufficient */}
                  {msg.retrievalInsufficient && (
                    <div className="mb-3 p-2.5 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-600 dark:text-amber-400 flex items-start gap-2 text-xs">
                      <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                      <span>
                        The documents available in this workspace do not contain sufficient evidence to answer this question conclusively.
                      </span>
                    </div>
                  )}

                  {/* Markdown Body */}
                  <div className="prose dark:prose-invert max-w-none prose-sm leading-relaxed">
                    <ReactMarkdown>{msg.content}</ReactMarkdown>
                  </div>

                  {/* Citations Footer */}
                  {msg.citations && msg.citations.length > 0 && (
                    <div className="mt-4 pt-3 border-t border-slate-200 dark:border-slate-700/60">
                      <div className="text-xs font-semibold text-slate-500 dark:text-slate-400 mb-2 flex items-center gap-1.5">
                        <BookOpen className="w-3.5 h-3.5" /> Verifiable Citations ({msg.citations.length}):
                      </div>
                      <div className="flex flex-wrap gap-1.5">
                        {msg.citations.map((cite, idx) => (
                          <button
                            key={cite.id || idx}
                            onClick={() => setActiveCitation(cite)}
                            className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 hover:border-indigo-400 text-xs text-indigo-600 dark:text-indigo-400 transition-colors shadow-sm"
                          >
                            <span className="font-bold">[{idx + 1}]</span>
                            <span className="max-w-[140px] truncate">{cite.documentTitle}</span>
                            <span className="text-[10px] text-slate-400">p.{cite.pageNumber}</span>
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Assistant Message Actions */}
                  {msg.sender === 'ASSISTANT' && (
                    <div className="mt-3 flex items-center justify-between text-xs text-slate-400">
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => handleFeedback(msg.id, 1)}
                          className="p-1 hover:text-emerald-500 rounded"
                          title="Accurate & helpful"
                        >
                          <ThumbsUp className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => handleFeedback(msg.id, -1)}
                          className="p-1 hover:text-rose-500 rounded"
                          title="Inaccurate or unhelpful"
                        >
                          <ThumbsDown className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => handleCopy(msg.content, msg.id)}
                          className="p-1 hover:text-slate-200 rounded"
                          title="Copy message"
                        >
                          {copiedId === msg.id ? (
                            <Check className="w-3.5 h-3.5 text-emerald-500" />
                          ) : (
                            <Copy className="w-3.5 h-3.5" />
                          )}
                        </button>
                      </div>

                      {msg.tokensUsed > 0 && (
                        <span>
                          {msg.tokensUsed} tokens • {msg.latencyMs}ms
                        </span>
                      )}
                    </div>
                  )}
                </div>
              </div>
            ))
          ) : !isStreaming ? (
            <div className="h-full flex flex-col items-center justify-center text-center max-w-lg mx-auto space-y-4">
              <div className="w-12 h-12 rounded-2xl bg-indigo-50 dark:bg-indigo-950/50 text-indigo-600 dark:text-indigo-400 flex items-center justify-center">
                <Sparkles className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-semibold text-slate-900 dark:text-white">
                  Enterprise Knowledge Intelligence
                </h3>
                <p className="text-xs text-slate-500 mt-1">
                  Ask questions across uploaded documents. Every answer is grounded with citations.
                </p>
              </div>

              {/* Suggested Questions Grid */}
              <div className="w-full space-y-2 pt-2">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400 block">
                  Suggested Prompts
                </span>
                <div className="grid grid-cols-1 gap-2">
                  {[
                    'What is our annual leave policy?',
                    'Explain the enterprise onboarding workflow.',
                    'Compare technical requirements across documents.',
                  ].map((prompt, idx) => (
                    <button
                      key={idx}
                      onClick={() => handleSend(prompt)}
                      className="w-full text-left p-2.5 rounded-xl border border-slate-200 dark:border-slate-800 hover:border-indigo-500 dark:hover:border-indigo-500 bg-slate-50 dark:bg-slate-950/40 text-xs text-slate-700 dark:text-slate-300 transition flex items-center justify-between"
                    >
                      <span>"{prompt}"</span>
                      <ChevronRight className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                    </button>
                  ))}
                </div>
              </div>
            </div>
          ) : null}

          {/* Active Streaming Message Bubble */}
          {isStreaming && (
            <div className="flex gap-3 max-w-3xl">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-indigo-600 to-violet-600 flex items-center justify-center flex-shrink-0 text-white font-medium text-xs">
                <Bot className="w-4 h-4" />
              </div>
              <div className="rounded-2xl p-4 text-sm bg-slate-50 dark:bg-slate-800/80 border border-slate-100 dark:border-slate-800 text-slate-800 dark:text-slate-100 min-w-[200px]">
                <div className="prose dark:prose-invert max-w-none prose-sm leading-relaxed">
                  <ReactMarkdown>{streamingContent || 'Synthesizing verified answer...'}</ReactMarkdown>
                </div>

                {streamingCitations.length > 0 && (
                  <div className="mt-4 pt-3 border-t border-slate-200 dark:border-slate-700/60">
                    <div className="text-xs font-semibold text-slate-500 dark:text-slate-400 mb-2 flex items-center gap-1.5">
                      <BookOpen className="w-3.5 h-3.5" /> Citations:
                    </div>
                    <div className="flex flex-wrap gap-1.5">
                      {streamingCitations.map((cite, idx) => (
                        <button
                          key={cite.id || idx}
                          onClick={() => setActiveCitation(cite)}
                          className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 text-xs text-indigo-600 dark:text-indigo-400"
                        >
                          <span className="font-bold">[{idx + 1}]</span>
                          <span className="max-w-[120px] truncate">{cite.documentTitle}</span>
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                <div className="mt-2 flex items-center gap-1.5 text-xs text-indigo-500 animate-pulse">
                  <span className="w-2 h-2 rounded-full bg-indigo-500" /> Generating answer...
                </div>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input Bar */}
        <div className="p-4 border-t border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
          <div className="relative flex items-center">
            <textarea
              value={inputMessage}
              onChange={(e) => setInputMessage(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSend();
                }
              }}
              placeholder="Ask anything about your workspace documents (e.g. What is the annual leave policy?)..."
              rows={1}
              className="w-full pl-4 pr-24 py-3 text-sm rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none max-h-32"
            />

            <div className="absolute right-2 flex items-center gap-1">
              {isStreaming ? (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={handleStopStreaming}
                  className="text-rose-500 hover:text-rose-600"
                >
                  <Square className="w-3.5 h-3.5 fill-rose-500 mr-1" /> Stop
                </Button>
              ) : (
                <Button
                  size="sm"
                  disabled={!inputMessage.trim()}
                  onClick={() => handleSend()}
                  className="rounded-lg"
                >
                  <Send className="w-3.5 h-3.5" />
                </Button>
              )}
            </div>
          </div>
          <div className="mt-2 flex items-center justify-between text-[11px] text-slate-400 px-1">
            <span>Press Enter to send, Shift+Enter for new line</span>
            <span>Hallucination Detection Active • pgvector Cosine Search</span>
          </div>
        </div>
      </div>

      {/* Document Comparison Modal */}
      <Modal
        isOpen={compareModalOpen}
        onClose={() => setCompareModalOpen(false)}
        title="Compare Two Documents"
        description="Select two workspace documents to analyze side-by-side differences, overlaps, and key changes."
      >
        <div className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
              Document 1 (Baseline)
            </label>
            <select
              value={compareDocId1}
              onChange={(e) => setCompareDocId1(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="">Select first document...</option>
              {availableDocs?.items?.map((doc: DocumentDto) => (
                <option key={doc.id} value={doc.id}>
                  {doc.title} ({doc.originalFilename})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
              Document 2 (Comparison Target)
            </label>
            <select
              value={compareDocId2}
              onChange={(e) => setCompareDocId2(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="">Select second document...</option>
              {availableDocs?.items?.map((doc: DocumentDto) => (
                <option key={doc.id} value={doc.id}>
                  {doc.title} ({doc.originalFilename})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
              Specific Instruction (Optional)
            </label>
            <textarea
              rows={2}
              value={compareInstruction}
              onChange={(e) => setCompareInstruction(e.target.value)}
              placeholder="e.g. Compare policy differences regarding remote work, approval chains, and PTO."
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" size="sm" onClick={() => setCompareModalOpen(false)}>
              Cancel
            </Button>
            <Button
              size="sm"
              disabled={!compareDocId1 || !compareDocId2 || isComparing}
              isLoading={isComparing}
              onClick={handleDocumentComparison}
              className="bg-indigo-600 hover:bg-indigo-500 text-white"
            >
              <Scale className="w-3.5 h-3.5 mr-1" /> Compare Documents
            </Button>
          </div>
        </div>
      </Modal>

      {/* Citation Inspector Modal / Slideover */}
      {activeCitation && (
        <Modal
          isOpen={!!activeCitation}
          onClose={() => setActiveCitation(null)}
          title="Verifiable Source Citation"
          description={`Retrieved passage from ${activeCitation.documentTitle}`}
        >
          <div className="space-y-4">
            <div className="flex items-center justify-between text-xs text-slate-500 pb-2 border-b border-slate-100 dark:border-slate-800">
              <span className="font-semibold text-slate-900 dark:text-white">
                {activeCitation.documentTitle}
              </span>
              <span>Page {activeCitation.pageNumber}</span>
            </div>

            {activeCitation.sectionHeading && (
              <div className="text-xs font-medium text-indigo-600 dark:text-indigo-400">
                Section: {activeCitation.sectionHeading}
              </div>
            )}

            <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 text-sm leading-relaxed text-slate-800 dark:text-slate-200 font-mono text-xs whitespace-pre-wrap">
              {activeCitation.supportingPassage}
            </div>

            <div className="flex items-center justify-between text-xs text-slate-400 pt-2">
              <span>
                Cosine Relevance Score:{' '}
                <strong className="text-emerald-500">
                  {Math.round(activeCitation.relevanceScore * 100)}%
                </strong>
              </span>
              <Button size="sm" onClick={() => setActiveCitation(null)}>
                Close
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* Rename Conversation Modal */}
      <Modal
        isOpen={renameModalOpen}
        onClose={() => setRenameModalOpen(false)}
        title="Rename Conversation"
      >
        <div className="space-y-4">
          <Input
            label="Title"
            value={renameTitle}
            onChange={(e) => setRenameTitle(e.target.value)}
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => setRenameModalOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={!renameTitle.trim() || renameConvMutation.isPending}
              isLoading={renameConvMutation.isPending}
              onClick={() => renameConvMutation.mutate()}
            >
              Save Title
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
