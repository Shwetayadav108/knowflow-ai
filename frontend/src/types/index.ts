export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  error?: string;
  timestamp: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export type GlobalRole = 'SUPER_ADMIN' | 'USER';
export type UserStatus = 'ACTIVE' | 'PENDING_VERIFICATION' | 'SUSPENDED';

export interface UserDto {
  id: string;
  email: string;
  fullName: string;
  avatarUrl?: string;
  globalRole: GlobalRole;
  status: UserStatus;
  emailVerified: boolean;
  onboardingCompleted?: boolean;
}

export interface AuthResponse {
  token: string;
  refreshToken: string;
  tokenType: string;
  user: UserDto;
}

export type WorkspaceRole = 'WORKSPACE_ADMIN' | 'EDITOR' | 'VIEWER';

export interface WorkspaceDto {
  id: string;
  name: string;
  slug: string;
  description?: string;
  userRole: WorkspaceRole;
  memberCount: number;
  documentCount: number;
  createdAt: string;
}

export interface WorkspaceMemberDto {
  userId: string;
  email: string;
  fullName: string;
  role: WorkspaceRole;
  joinedAt: string;
}

export type FileType = 'PDF' | 'DOCX' | 'TXT' | 'CSV' | 'PPTX';
export type DocumentStatus =
  | 'UPLOADED'
  | 'PROCESSING'
  | 'EXTRACTING'
  | 'CHUNKING'
  | 'EMBEDDING'
  | 'INDEXING'
  | 'READY'
  | 'COMPLETED'
  | 'FAILED';

export type DocumentAccessLevel = 'WORKSPACE_SHARED' | 'RESTRICTED_ROLES' | 'PRIVATE';

export interface DocumentDto {
  id: string;
  workspaceId: string;
  title: string;
  originalFilename: string;
  fileType: FileType;
  mimeType?: string;
  fileSizeBytes: number;
  status: DocumentStatus;
  version: number;
  description?: string;
  folderPath: string;
  category: string;
  tags?: string;
  accessLevel: DocumentAccessLevel;
  allowedRoles?: string;
  errorMessage?: string;
  totalPages: number;
  totalChunks: number;
  uploadedBy?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CitationDto {
  id?: string;
  chunkId?: string;
  documentId: string;
  documentTitle: string;
  pageNumber: number;
  sectionHeading?: string;
  supportingPassage: string;
  relevanceScore: number;
}

export type MessageSender = 'USER' | 'ASSISTANT' | 'SYSTEM';

export interface MessageDto {
  id: string;
  conversationId: string;
  sender: MessageSender;
  content: string;
  retrievalInsufficient: boolean;
  tokensUsed: number;
  latencyMs: number;
  citations: CitationDto[];
  createdAt: string;
}

export interface ConversationDto {
  id: string;
  workspaceId: string;
  title: string;
  documentFilterId?: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
}

export interface RetrievedPassage {
  chunkId: string;
  documentId: string;
  documentTitle: string;
  pageNumber: number;
  sectionHeading?: string;
  content: string;
  relevanceScore: number;
}

export interface TimeSeriesPoint {
  date: string;
  count: number;
}

export interface AuditLogDto {
  id: string;
  workspaceId?: string;
  userId?: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  details?: string;
  ipAddress?: string;
  status: string;
  createdAt: string;
}

export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED';

export interface WorkspaceInvitationDto {
  id: string;
  workspaceId: string;
  workspaceName?: string;
  email: string;
  role: WorkspaceRole;
  status: InvitationStatus;
  token?: string;
  invitedByName?: string;
  expiresAt: string;
  createdAt: string;
}

export interface ChunkPreviewDto {
  id: string;
  chunkIndex: number;
  content: string;
  pageNumber: number;
  sectionHeading?: string;
  tokenCount: number;
}

export interface DocumentPreviewDto {
  document: DocumentDto;
  extractedTextPreview: string;
  chunks: ChunkPreviewDto[];
}

export interface DocumentComparisonRequest {
  docId1: string;
  docId2: string;
  instruction?: string;
}

export interface DashboardMetricsDto {
  totalDocuments: number;
  processedDocuments: number;
  failedDocuments: number;
  totalChunks: number;
  totalWorkspaces: number;
  totalMembers: number;
  totalQuestions: number;
  totalTokensConsumed: number;
  successRatePercent: number;
  averageLatencyMs: number;
  adminCount?: number;
  editorCount?: number;
  viewerCount?: number;
  pendingInvitationsCount?: number;
  aiRequestVolume?: number;
  recentDocuments: DocumentDto[];
  recentActivity: AuditLogDto[];
  documentsOverTime: TimeSeriesPoint[];
  questionsOverTime: TimeSeriesPoint[];
}

export interface AdminSystemStatsDto {
  totalUsers: number;
  activeUsers?: number;
  suspendedUsers?: number;
  totalWorkspaces: number;
  totalDocuments: number;
  indexedDocuments?: number;
  failedDocuments?: number;
  totalChunks: number;
  totalGlobalTokens: number;
  aiRequestVolume?: number;
  estimatedCostUsd?: number;
  systemHealth: string;
  activeAiModel: string;
  storageEngine: string;
  recentActivity?: AuditLogDto[];
}
