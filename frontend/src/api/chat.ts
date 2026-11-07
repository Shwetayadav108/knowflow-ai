import { apiClient } from './client';
import { ConversationDto, MessageDto, CitationDto, DocumentComparisonRequest } from '../types';
import { useAuthStore } from '../store/useAuthStore';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export interface SendMessageParams {
  content: string;
  documentFilterId?: string;
}

export interface FeedbackParams {
  rating: number; // 1 or -1
  comment?: string;
}

export interface StreamCallbacks {
  onToken: (token: string) => void;
  onCitations?: (citations: CitationDto[]) => void;
  onDone?: (finalMessage: MessageDto) => void;
  onError?: (error: Error) => void;
}

export const chatApi = {
  getConversations: (workspaceId: string): Promise<ConversationDto[]> => {
    return apiClient.get(`/workspaces/${workspaceId}/chat/conversations`);
  },

  searchConversations: (workspaceId: string, query: string): Promise<ConversationDto[]> => {
    return apiClient.get(`/workspaces/${workspaceId}/chat/conversations/search`, {
      params: { q: query },
    });
  },

  createConversation: (
    workspaceId: string,
    data: { title?: string; documentFilterId?: string }
  ): Promise<ConversationDto> => {
    return apiClient.post(`/workspaces/${workspaceId}/chat/conversations`, data);
  },

  getConversation: (workspaceId: string, conversationId: string): Promise<ConversationDto> => {
    return apiClient.get(`/workspaces/${workspaceId}/chat/conversations/${conversationId}`);
  },

  renameConversation: (
    workspaceId: string,
    conversationId: string,
    title: string
  ): Promise<ConversationDto> => {
    return apiClient.patch(`/workspaces/${workspaceId}/chat/conversations/${conversationId}`, {
      title,
    });
  },

  deleteConversation: (workspaceId: string, conversationId: string): Promise<void> => {
    return apiClient.delete(`/workspaces/${workspaceId}/chat/conversations/${conversationId}`);
  },

  getMessages: (workspaceId: string, conversationId: string): Promise<MessageDto[]> => {
    return apiClient.get(`/workspaces/${workspaceId}/chat/conversations/${conversationId}/messages`);
  },

  sendMessage: (
    workspaceId: string,
    conversationId: string,
    data: SendMessageParams
  ): Promise<MessageDto> => {
    return apiClient.post(
      `/workspaces/${workspaceId}/chat/conversations/${conversationId}/messages`,
      data
    );
  },

  streamMessage: async (
    workspaceId: string,
    conversationId: string,
    data: SendMessageParams,
    callbacks: StreamCallbacks,
    signal?: AbortSignal
  ): Promise<void> => {
    const token = useAuthStore.getState().token;
    const url = `${BASE_URL}/workspaces/${workspaceId}/chat/conversations/${conversationId}/messages/stream`;

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
          'X-Workspace-Id': workspaceId,
        },
        body: JSON.stringify(data),
        signal,
      });

      if (!response.ok) {
        throw new Error(`Streaming failed: HTTP ${response.status}`);
      }

      if (!response.body) {
        throw new Error('ReadableStream not supported by browser.');
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        let currentEvent = 'message';

        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed) continue;

          if (trimmed.startsWith('event:')) {
            currentEvent = trimmed.replace('event:', '').trim();
          } else if (trimmed.startsWith('data:')) {
            const rawData = trimmed.replace('data:', '').trim();
            if (currentEvent === 'token') {
              callbacks.onToken(rawData);
            } else if (currentEvent === 'citations' && callbacks.onCitations) {
              try {
                const citations = JSON.parse(rawData);
                callbacks.onCitations(citations);
              } catch {
                // ignore json error
              }
            } else if (currentEvent === 'done' && callbacks.onDone) {
              try {
                const finalMsg = JSON.parse(rawData);
                callbacks.onDone(finalMsg);
              } catch {
                // ignore json error
              }
            } else if (currentEvent === 'error') {
              throw new Error(rawData);
            }
          }
        }
      }
    } catch (err) {
      if ((err as Error).name === 'AbortError') {
        return; // User cancelled
      }
      if (callbacks.onError) {
        callbacks.onError(err as Error);
      } else {
        throw err;
      }
    }
  },

  submitFeedback: (
    workspaceId: string,
    messageId: string,
    data: FeedbackParams
  ): Promise<void> => {
    return apiClient.post(`/workspaces/${workspaceId}/chat/messages/${messageId}/feedback`, data);
  },

  compareDocuments: (
    workspaceId: string,
    data: DocumentComparisonRequest
  ): Promise<MessageDto> => {
    return apiClient.post(`/workspaces/${workspaceId}/chat/compare`, data);
  },
};
