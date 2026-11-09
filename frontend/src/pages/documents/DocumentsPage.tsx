import React, { useState, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  UploadCloud,
  Search,
  Filter,
  RefreshCw,
  Download,
  Trash2,
  Edit2,
  Folder,
  File,
  Eye,
  Copy,
  Check,
  Shield,
} from 'lucide-react';
import { useAuthStore } from '../../store/useAuthStore';
import { useWorkspaceStore } from '../../store/useWorkspaceStore';
import { documentApi, DocumentQueryParams, UpdateDocumentParams } from '../../api/documents';
import { DocumentDto, DocumentAccessLevel, DocumentPreviewDto } from '../../types';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';
import { StatusBadge, AccessLevelBadge } from '../../components/ui/Badge';
import { Skeleton } from '../../components/ui/Skeleton';
import { Card, CardContent } from '../../components/ui/Card';
import { toast } from '../../components/ui/Toast';
import { formatBytes, formatDate } from '../../lib/utils';

export const DocumentsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { user } = useAuthStore();
  const { currentWorkspace } = useWorkspaceStore();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const role = currentWorkspace?.userRole || 'VIEWER';
  const canUpload = role === 'WORKSPACE_ADMIN' || role === 'EDITOR';
  const canManageDoc = (doc: DocumentDto) =>
    role === 'WORKSPACE_ADMIN' || (role === 'EDITOR' && doc.uploadedBy === user?.id);

  // Filter and pagination states
  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [selectedFolder, setSelectedFolder] = useState<string>('');
  const [selectedStatus, setSelectedStatus] = useState<string>('');
  const [page, setPage] = useState(0);

  // Upload states
  const [isDragging, setIsDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadCategory, setUploadCategory] = useState('General');
  const [uploadFolderPath, setUploadFolderPath] = useState('/');
  const [uploadAccessLevel, setUploadAccessLevel] = useState<DocumentAccessLevel>('WORKSPACE_SHARED');

  // Modal states
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [previewModalOpen, setPreviewModalOpen] = useState(false);
  const [activeDoc, setActiveDoc] = useState<DocumentDto | null>(null);
  const [previewData, setPreviewData] = useState<DocumentPreviewDto | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [previewTab, setPreviewTab] = useState<'text' | 'chunks'>('text');
  const [copiedChunkId, setCopiedChunkId] = useState<string | null>(null);

  // Edit form states
  const [editTitle, setEditTitle] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [editCategory, setEditCategory] = useState('');
  const [editFolderPath, setEditFolderPath] = useState('');
  const [editAccessLevel, setEditAccessLevel] = useState<DocumentAccessLevel>('WORKSPACE_SHARED');

  const queryParams: DocumentQueryParams = {
    search: search.trim() || undefined,
    category: selectedCategory || undefined,
    status: selectedStatus || undefined,
    page,
    size: 15,
  };

  const { data: pageData, isLoading, refetch } = useQuery({
    queryKey: ['documents', currentWorkspace?.id, queryParams],
    queryFn: () => documentApi.getDocuments(currentWorkspace!.id, queryParams),
    enabled: !!currentWorkspace?.id,
    refetchInterval: (query) => {
      const items = query.state.data?.items;
      if (!items) return false;
      const isProcessing = items.some((d: DocumentDto) =>
        ['UPLOADED', 'PROCESSING', 'EXTRACTING', 'CHUNKING', 'EMBEDDING', 'INDEXING'].includes(d.status)
      );
      return isProcessing ? 3000 : false;
    },
  });

  const { data: categories } = useQuery({
    queryKey: ['document-categories', currentWorkspace?.id],
    queryFn: () => documentApi.getCategories(currentWorkspace!.id),
    enabled: !!currentWorkspace?.id,
  });

  const { data: folders } = useQuery({
    queryKey: ['document-folders', currentWorkspace?.id],
    queryFn: () => documentApi.getFolders(currentWorkspace!.id),
    enabled: !!currentWorkspace?.id,
  });

  const handleUploadFiles = async (fileList: FileList | File[]) => {
    if (!currentWorkspace) return;
    const files = Array.from(fileList);
    if (files.length === 0) return;

    setUploading(true);
    setUploadProgress(0);

    let successCount = 0;
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      const formData = new FormData();
      formData.append('file', file);
      formData.append('category', uploadCategory);
      formData.append('folderPath', uploadFolderPath);
      formData.append('accessLevel', uploadAccessLevel);

      try {
        await documentApi.uploadDocument(currentWorkspace.id, formData, (percent) => {
          const overallProgress = Math.round(((i + percent / 100) / files.length) * 100);
          setUploadProgress(overallProgress);
        });
        successCount++;
      } catch (err: any) {
        toast.error(`Upload failed: ${file.name}`, err.response?.data?.message || err.message);
      }
    }

    if (successCount > 0) {
      toast.success(
        'Upload complete',
        `${successCount} file${successCount > 1 ? 's' : ''} queued for async processing.`
      );
      queryClient.invalidateQueries({ queryKey: ['documents', currentWorkspace.id] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-metrics', currentWorkspace.id] });
      queryClient.invalidateQueries({ queryKey: ['document-categories', currentWorkspace.id] });
      queryClient.invalidateQueries({ queryKey: ['document-folders', currentWorkspace.id] });
    }

    setUploading(false);
    setUploadProgress(0);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const onDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    if (canUpload) setIsDragging(true);
  };

  const onDragLeave = () => {
    setIsDragging(false);
  };

  const onDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (canUpload && e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleUploadFiles(e.dataTransfer.files);
    }
  };

  const openPreview = async (doc: DocumentDto) => {
    if (!currentWorkspace) return;
    setActiveDoc(doc);
    setPreviewModalOpen(true);
    setLoadingPreview(true);
    try {
      const data = await documentApi.getDocumentPreview(currentWorkspace.id, doc.id);
      setPreviewData(data);
    } catch (err: any) {
      toast.error('Preview error', err.response?.data?.message || err.message);
    } finally {
      setLoadingPreview(false);
    }
  };

  const copyChunk = (id: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedChunkId(id);
    setTimeout(() => setCopiedChunkId(null), 2000);
  };

  const deleteMutation = useMutation({
    mutationFn: () =>
      documentApi.deleteDocument(currentWorkspace!.id, activeDoc!.id, true),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents', currentWorkspace?.id] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-metrics', currentWorkspace?.id] });
      toast.success('Document deleted', 'Document and its vector embeddings removed.');
      setDeleteModalOpen(false);
    },
    onError: (err: any) => {
      toast.error('Delete failed', err.response?.data?.message || err.message);
    },
  });

  const reprocessMutation = useMutation({
    mutationFn: (docId: string) =>
      documentApi.reprocessDocument(currentWorkspace!.id, docId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents', currentWorkspace?.id] });
      toast.info('Reprocessing queued', 'Document will be re-extracted and re-embedded.');
    },
    onError: (err: any) => {
      toast.error('Failed to reprocess', err.response?.data?.message || err.message);
    },
  });

  const updateMutation = useMutation({
    mutationFn: (params: UpdateDocumentParams) =>
      documentApi.updateDocument(currentWorkspace!.id, activeDoc!.id, params),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents', currentWorkspace?.id] });
      toast.success('Metadata updated', 'Document attributes saved.');
      setEditModalOpen(false);
    },
    onError: (err: any) => {
      toast.error('Update failed', err.response?.data?.message || err.message);
    },
  });

  const openEdit = (doc: DocumentDto) => {
    setActiveDoc(doc);
    setEditTitle(doc.title);
    setEditDescription(doc.description || '');
    setEditCategory(doc.category || 'General');
    setEditFolderPath(doc.folderPath || '/');
    setEditAccessLevel(doc.accessLevel);
    setEditModalOpen(true);
  };

  const openDelete = (doc: DocumentDto) => {
    setActiveDoc(doc);
    setDeleteModalOpen(true);
  };

  const handleDownload = (doc: DocumentDto) => {
    if (!currentWorkspace) return;
    documentApi.downloadDocument(currentWorkspace.id, doc.id, doc.originalFilename);
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
            Documents Repository
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
            Manage, parse, and verify knowledge vectors in {currentWorkspace?.name || 'Workspace'}.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            className="text-xs"
          >
            <RefreshCw className="w-3.5 h-3.5 mr-1.5" /> Refresh
          </Button>
          {canUpload && (
            <Button
              size="sm"
              onClick={() => fileInputRef.current?.click()}
              className="bg-indigo-600 hover:bg-indigo-500 text-white text-xs"
            >
              <UploadCloud className="w-3.5 h-3.5 mr-1.5" /> Upload Files
            </Button>
          )}
        </div>
      </div>

      {/* Upload Drop Zone Card (Only for WORKSPACE_ADMIN and EDITOR) */}
      {canUpload ? (
        <Card className="p-6">
          <div
            onDragOver={onDragOver}
            onDragLeave={onDragLeave}
            onDrop={onDrop}
            className={`border-2 border-dashed rounded-xl p-8 text-center transition-all ${
              isDragging
                ? 'border-indigo-500 bg-indigo-50/50 dark:bg-indigo-950/20 scale-[0.99]'
                : 'border-slate-300 dark:border-slate-800 hover:border-indigo-400 dark:hover:border-indigo-600 bg-slate-50/50 dark:bg-slate-900/40'
            }`}
          >
            <input
              ref={fileInputRef}
              type="file"
              multiple
              className="hidden"
              onChange={(e) => {
                if (e.target.files) handleUploadFiles(e.target.files);
              }}
              accept=".pdf,.docx,.txt,.csv,.pptx"
            />

            <UploadCloud className="w-10 h-10 text-indigo-500 mx-auto mb-3" />
            <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">
              Drag & Drop files here, or{' '}
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="text-indigo-600 dark:text-indigo-400 hover:underline"
              >
                browse
              </button>
            </h3>
            <p className="text-xs text-slate-500 mt-1">
              Supports multi-file upload for PDF, DOCX, TXT, CSV, and PPTX up to 50MB each.
            </p>

            {uploading && (
              <div className="mt-4 max-w-xs mx-auto space-y-1.5">
                <div className="h-2 w-full bg-slate-200 dark:bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-indigo-600 transition-all duration-300 rounded-full"
                    style={{ width: `${uploadProgress}%` }}
                  />
                </div>
                <div className="text-[11px] text-slate-500 font-medium">
                  Uploading files: {uploadProgress}%
                </div>
              </div>
            )}
          </div>

          {/* Quick Upload Options */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mt-4 pt-4 border-t border-slate-100 dark:border-slate-800/80">
            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-400 mb-1">
                Category
              </label>
              <input
                type="text"
                value={uploadCategory}
                onChange={(e) => setUploadCategory(e.target.value)}
                placeholder="e.g. Policies, Engineering"
                className="w-full px-2.5 py-1.5 text-xs rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-400 mb-1">
                Folder Path
              </label>
              <input
                type="text"
                value={uploadFolderPath}
                onChange={(e) => setUploadFolderPath(e.target.value)}
                placeholder="e.g. /hr or /technical"
                className="w-full px-2.5 py-1.5 text-xs rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-600 dark:text-slate-400 mb-1">
                Access Level
              </label>
              <select
                value={uploadAccessLevel}
                onChange={(e) => setUploadAccessLevel(e.target.value as DocumentAccessLevel)}
                className="w-full px-2.5 py-1.5 text-xs rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              >
                <option value="WORKSPACE_SHARED">Workspace Shared</option>
                <option value="RESTRICTED_ROLES">Restricted Roles</option>
                <option value="PRIVATE">Private (Only You)</option>
              </select>
            </div>
          </div>
        </Card>
      ) : (
        <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 flex items-center gap-3 text-xs text-slate-400">
          <Shield className="h-4 w-4 text-indigo-400 shrink-0" />
          <span>
            You have <strong>Viewer</strong> access to this workspace. You can read, preview, and download documents, but cannot upload or delete.
          </span>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <Search className="w-4 h-4 absolute left-3 top-2.5 text-slate-400" />
          <input
            type="text"
            placeholder="Search documents by title or filename..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <select
          value={selectedCategory}
          onChange={(e) => {
            setSelectedCategory(e.target.value);
            setPage(0);
          }}
          className="px-3 py-2 text-sm rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        >
          <option value="">All Categories</option>
          {categories?.map((cat) => (
            <option key={cat} value={cat}>
              {cat}
            </option>
          ))}
        </select>

        <select
          value={selectedStatus}
          onChange={(e) => {
            setSelectedStatus(e.target.value);
            setPage(0);
          }}
          className="px-3 py-2 text-sm rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        >
          <option value="">All Statuses</option>
          <option value="READY">Ready</option>
          <option value="PROCESSING">Processing</option>
          <option value="FAILED">Failed</option>
        </select>
      </div>

      {/* Documents Table */}
      <Card>
        <CardContent className="p-0 overflow-x-auto">
          {isLoading ? (
            <div className="p-6 space-y-3">
              {[1, 2, 3, 4, 5].map((i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : pageData?.items && pageData.items.length > 0 ? (
            <table className="w-full text-left text-sm text-slate-600 dark:text-slate-300">
              <thead className="bg-slate-50 dark:bg-slate-900/60 text-xs uppercase font-semibold text-slate-500 dark:text-slate-400 border-b border-slate-200 dark:border-slate-800">
                <tr>
                  <th className="px-4 py-3">Document</th>
                  <th className="px-4 py-3">Category</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Size / Chunks</th>
                  <th className="px-4 py-3">Access</th>
                  <th className="px-4 py-3">Uploaded</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {pageData.items.map((doc: DocumentDto) => (
                  <tr
                    key={doc.id}
                    className="hover:bg-slate-50 dark:hover:bg-slate-900/40 transition-colors"
                  >
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <span className="p-2 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 font-bold text-xs">
                          {doc.fileType}
                        </span>
                        <div className="min-w-0">
                          <div className="font-medium text-slate-900 dark:text-white truncate max-w-xs">
                            {doc.title}
                          </div>
                          <div className="text-xs text-slate-400 truncate max-w-xs">
                            {doc.originalFilename}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-xs">{doc.category || '—'}</td>
                    <td className="px-4 py-3">
                      <StatusBadge status={doc.status} />
                    </td>
                    <td className="px-4 py-3 text-xs">
                      <div>{formatBytes(doc.fileSizeBytes)}</div>
                      <div className="text-slate-400">
                        {doc.totalChunks} chunks • {doc.totalPages} pages
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <AccessLevelBadge level={doc.accessLevel} />
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-400 whitespace-nowrap">
                      {formatDate(doc.createdAt)}
                    </td>
                    <td className="px-4 py-3 text-right whitespace-nowrap">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => openPreview(doc)}
                          title="Preview Text & Chunks"
                        >
                          <Eye className="w-3.5 h-3.5" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDownload(doc)}
                          title="Download Original"
                        >
                          <Download className="w-3.5 h-3.5" />
                        </Button>
                        {canManageDoc(doc) && (
                          <>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => reprocessMutation.mutate(doc.id)}
                              title="Reprocess Pipeline"
                            >
                              <RefreshCw className="w-3.5 h-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => openEdit(doc)}
                              title="Edit Metadata"
                            >
                              <Edit2 className="w-3.5 h-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              className="text-rose-500 hover:text-rose-600"
                              onClick={() => openDelete(doc)}
                              title="Delete"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </Button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="py-16 text-center text-slate-500">
              <File className="w-10 h-10 mx-auto text-slate-400 mb-2" />
              <p className="font-medium text-slate-900 dark:text-white">No documents found</p>
              <p className="text-xs text-slate-500 mt-1">
                Upload your company SOPs, manuals, or policies above.
              </p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Pagination Footer */}
      {pageData && pageData.totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-slate-500">
          <div>
            Showing Page {pageData.page + 1} of {pageData.totalPages} ({pageData.totalElements} total)
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={!pageData.hasPrevious}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={!pageData.hasNext}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}

      {/* Document Preview Modal */}
      <Modal
        isOpen={previewModalOpen}
        onClose={() => setPreviewModalOpen(false)}
        title={activeDoc ? `Preview: ${activeDoc.title}` : 'Document Preview'}
        description="Inspect raw parsed text and chunk vectors used by the retrieval engine."
      >
        <div className="space-y-4">
          {/* Tab switchers */}
          <div className="flex items-center gap-2 border-b border-slate-800 pb-2">
            <button
              onClick={() => setPreviewTab('text')}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition ${
                previewTab === 'text'
                  ? 'bg-indigo-600 text-white'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800'
              }`}
            >
              Extracted Text Preview
            </button>
            <button
              onClick={() => setPreviewTab('chunks')}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition ${
                previewTab === 'chunks'
                  ? 'bg-indigo-600 text-white'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800'
              }`}
            >
              Indexed Chunks ({previewData?.chunks?.length || 0})
            </button>
          </div>

          {loadingPreview ? (
            <div className="p-8 text-center text-xs text-slate-400 space-y-2">
              <div className="h-6 w-6 animate-spin rounded-full border-2 border-indigo-500 border-t-transparent mx-auto" />
              <p>Loading document preview...</p>
            </div>
          ) : previewTab === 'text' ? (
            <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 text-xs text-slate-300 font-mono whitespace-pre-wrap max-h-96 overflow-y-auto leading-relaxed">
              {previewData?.extractedTextPreview || 'No text content available.'}
            </div>
          ) : (
            <div className="space-y-3 max-h-96 overflow-y-auto pr-1">
              {previewData?.chunks && previewData.chunks.length > 0 ? (
                previewData.chunks.map((chk) => (
                  <div
                    key={chk.id}
                    className="p-3 rounded-xl bg-slate-950 border border-slate-800 text-xs space-y-2"
                  >
                    <div className="flex items-center justify-between text-[11px] text-slate-400">
                      <span className="font-semibold text-indigo-400">
                        Chunk #{chk.chunkIndex + 1} {chk.sectionHeading ? `• ${chk.sectionHeading}` : ''}
                      </span>
                      <div className="flex items-center gap-2">
                        <span>Page {chk.pageNumber} • {chk.tokenCount} tokens</span>
                        <button
                          onClick={() => copyChunk(chk.id, chk.content)}
                          className="p-1 hover:text-white transition"
                          title="Copy chunk text"
                        >
                          {copiedChunkId === chk.id ? (
                            <Check className="w-3.5 h-3.5 text-emerald-400" />
                          ) : (
                            <Copy className="w-3.5 h-3.5" />
                          )}
                        </button>
                      </div>
                    </div>
                    <p className="text-slate-300 font-sans leading-relaxed text-xs">
                      {chk.content}
                    </p>
                  </div>
                ))
              ) : (
                <div className="p-8 text-center text-xs text-slate-400">
                  No chunk embeddings found. Try reprocessing this document.
                </div>
              )}
            </div>
          )}

          <div className="flex justify-end pt-2">
            <Button variant="outline" size="sm" onClick={() => setPreviewModalOpen(false)}>
              Close
            </Button>
          </div>
        </div>
      </Modal>

      {/* Edit Metadata Modal */}
      <Modal
        isOpen={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        title="Edit Document Metadata"
      >
        <div className="space-y-4">
          <Input
            label="Title"
            value={editTitle}
            onChange={(e) => setEditTitle(e.target.value)}
          />
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
              Description
            </label>
            <textarea
              className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              rows={2}
              value={editDescription}
              onChange={(e) => setEditDescription(e.target.value)}
            />
          </div>
          <Input
            label="Category"
            value={editCategory}
            onChange={(e) => setEditCategory(e.target.value)}
          />
          <Input
            label="Folder Path"
            value={editFolderPath}
            onChange={(e) => setEditFolderPath(e.target.value)}
          />
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
              Access Level
            </label>
            <select
              value={editAccessLevel}
              onChange={(e) => setEditAccessLevel(e.target.value as DocumentAccessLevel)}
              className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="WORKSPACE_SHARED">Workspace Shared</option>
              <option value="RESTRICTED_ROLES">Restricted Roles</option>
              <option value="PRIVATE">Private</option>
            </select>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setEditModalOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={() =>
                updateMutation.mutate({
                  title: editTitle,
                  description: editDescription,
                  category: editCategory,
                  folderPath: editFolderPath,
                  accessLevel: editAccessLevel,
                })
              }
              isLoading={updateMutation.isPending}
            >
              Save Changes
            </Button>
          </div>
        </div>
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={deleteModalOpen}
        onClose={() => setDeleteModalOpen(false)}
        title="Delete Document"
        description="Are you sure you want to delete this document? All parsed vector chunks and citations will be permanently removed."
      >
        <div className="flex justify-end gap-2 pt-4">
          <Button variant="outline" onClick={() => setDeleteModalOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="danger"
            onClick={() => deleteMutation.mutate()}
            isLoading={deleteMutation.isPending}
          >
            Delete Permanently
          </Button>
        </div>
      </Modal>
    </div>
  );
};
