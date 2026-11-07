import { apiClient } from './client';
import { RetrievedPassage } from '../types';

export interface SearchParams {
  query: string;
  searchType?: 'SEMANTIC' | 'KEYWORD' | 'HYBRID';
  category?: string;
  documentId?: string;
  topK?: number;
  similarityThreshold?: number;
}

export const searchApi = {
  search: (workspaceId: string, params: SearchParams): Promise<RetrievedPassage[]> => {
    return apiClient.post(`/workspaces/${workspaceId}/search`, params);
  },
};
