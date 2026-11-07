import { apiClient } from './client';
import { DocumentDto, DocumentPreviewDto, PageResponse } from '../types';

export interface DocumentQueryParams {
  category?: string;
  status?: string;
  search?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  direction?: 'ASC' | 'DESC';
}

export interface UpdateDocumentParams {
  title?: string;
  description?: string;
  folderPath?: string;
  category?: string;
  tags?: string;
  accessLevel?: string;
  allowedRoles?: string;
}

export const documentApi = {
  getDocuments: (
    workspaceId: string,
    params?: DocumentQueryParams
  ): Promise<PageResponse<DocumentDto>> => {
    return apiClient.get(`/workspaces/${workspaceId}/documents`, { params });
  },

  uploadDocument: (
    workspaceId: string,
    formData: FormData,
    onProgress?: (percent: number) => void
  ): Promise<DocumentDto> => {
    return apiClient.post(`/workspaces/${workspaceId}/documents/upload`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress(percent);
        }
      },
    });
  },

  getDocument: (workspaceId: string, documentId: string): Promise<DocumentDto> => {
    return apiClient.get(`/workspaces/${workspaceId}/documents/${documentId}`);
  },

  getDocumentPreview: (workspaceId: string, documentId: string): Promise<DocumentPreviewDto> => {
    return apiClient.get(`/workspaces/${workspaceId}/documents/${documentId}/preview`);
  },

  downloadDocument: async (workspaceId: string, documentId: string, filename: string): Promise<void> => {
    const response = await apiClient.get<Blob>(
      `/workspaces/${workspaceId}/documents/${documentId}/download`,
      { responseType: 'blob' }
    );
    const blob = new Blob([response.data as unknown as BlobPart]);
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  },

  updateDocument: (
    workspaceId: string,
    documentId: string,
    data: UpdateDocumentParams
  ): Promise<DocumentDto> => {
    return apiClient.put(`/workspaces/${workspaceId}/documents/${documentId}`, data);
  },

  deleteDocument: (workspaceId: string, documentId: string): Promise<void> => {
    return apiClient.delete(`/workspaces/${workspaceId}/documents/${documentId}`);
  },

  reprocessDocument: (workspaceId: string, documentId: string): Promise<void> => {
    return apiClient.post(`/workspaces/${workspaceId}/documents/${documentId}/reprocess`);
  },

  getCategories: (workspaceId: string): Promise<string[]> => {
    return apiClient.get(`/workspaces/${workspaceId}/documents/categories`);
  },

  getFolders: (workspaceId: string): Promise<string[]> => {
    return apiClient.get(`/workspaces/${workspaceId}/documents/folders`);
  },
};
