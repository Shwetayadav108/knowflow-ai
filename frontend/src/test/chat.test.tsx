import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ChatPage } from '../pages/chat/ChatPage';
import { chatApi } from '../api/chat';
import { useWorkspaceStore } from '../store/useWorkspaceStore';
import { ConversationDto, MessageDto } from '../types';

vi.mock('../api/chat', () => ({
  chatApi: {
    getConversations: vi.fn(),
    searchConversations: vi.fn(),
    createConversation: vi.fn(),
    getMessages: vi.fn(),
    sendMessage: vi.fn(),
    streamMessage: vi.fn(),
    submitFeedback: vi.fn(),
    renameConversation: vi.fn(),
    deleteConversation: vi.fn(),
  },
}));

const mockWorkspace = {
  id: 'ws-123',
  name: 'Engineering Workspace',
  slug: 'engineering-workspace',
  userRole: 'WORKSPACE_ADMIN' as const,
  memberCount: 3,
  documentCount: 5,
  createdAt: '2026-11-04T00:00:00Z',
};

const mockConversations: ConversationDto[] = [
  {
    id: 'conv-1',
    workspaceId: 'ws-123',
    title: 'Leave Policy Q&A',
    createdAt: '2026-11-04T01:00:00Z',
    updatedAt: '2026-11-04T01:05:00Z',
    messageCount: 2,
  },
];

const mockMessages: MessageDto[] = [
  {
    id: 'msg-1',
    conversationId: 'conv-1',
    sender: 'USER',
    content: 'What is the annual leave allowance?',
    retrievalInsufficient: false,
    tokensUsed: 10,
    latencyMs: 50,
    citations: [],
    createdAt: '2026-11-04T01:00:00Z',
  },
  {
    id: 'msg-2',
    conversationId: 'conv-1',
    sender: 'ASSISTANT',
    content: 'Employees receive 25 days of paid annual leave per calendar year.',
    retrievalInsufficient: false,
    tokensUsed: 65,
    latencyMs: 310,
    citations: [
      {
        id: 'cite-1',
        chunkId: 'chunk-99',
        documentId: 'doc-88',
        documentTitle: 'Employee Handbook 2026',
        pageNumber: 14,
        sectionHeading: 'Vacation & Leave Policy',
        supportingPassage:
          'Full-time enterprise personnel are granted 25 days of annual leave per annum.',
        relevanceScore: 0.94,
      },
    ],
    createdAt: '2026-11-04T01:00:01Z',
  },
];

describe('Chat Component', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
          gcTime: 0,
        },
      },
    });
    useWorkspaceStore.setState({ currentWorkspace: mockWorkspace });
    vi.mocked(chatApi.getConversations).mockResolvedValue(mockConversations);
    vi.mocked(chatApi.getMessages).mockResolvedValue(mockMessages);
  });

  it('renders chat interface with conversations list and message history', async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <ChatPage />
      </QueryClientProvider>
    );

    // Wait for the conversation to show in sidebar
    const convItem = await screen.findByText('Leave Policy Q&A');
    expect(convItem).toBeInTheDocument();

    // Click conversation to ensure activeConversationId is selected
    fireEvent.click(convItem);

    // Wait for messages to load
    await waitFor(() => {
      expect(screen.getByText('What is the annual leave allowance?')).toBeInTheDocument();
      expect(
        screen.getByText('Employees receive 25 days of paid annual leave per calendar year.')
      ).toBeInTheDocument();
    });
  });

  it('displays verifiable citations and opens citation inspector modal when clicked', async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <ChatPage />
      </QueryClientProvider>
    );

    const convItem = await screen.findByText('Leave Policy Q&A');
    fireEvent.click(convItem);

    await waitFor(() => {
      expect(screen.getByText(/Verifiable Citations/i)).toBeInTheDocument();
      expect(screen.getByText('Employee Handbook 2026')).toBeInTheDocument();
    });

    const citationBtn = screen.getByText('Employee Handbook 2026');
    fireEvent.click(citationBtn);

    await waitFor(() => {
      expect(screen.getByText('Verifiable Source Citation')).toBeInTheDocument();
      expect(
        screen.getByText(/Full-time enterprise personnel are granted 25 days/i)
      ).toBeInTheDocument();
      expect(screen.getByText('94%')).toBeInTheDocument();
    });
  });
});
