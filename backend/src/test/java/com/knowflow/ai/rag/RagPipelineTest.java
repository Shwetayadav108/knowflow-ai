package com.knowflow.ai.rag;

import com.knowflow.ai.analytics.AiUsageRecordRepository;
import com.knowflow.ai.document.Document;
import com.knowflow.ai.document.FileType;
import com.knowflow.ai.retrieval.RetrievalFilter;
import com.knowflow.ai.retrieval.RetrievalService;
import com.knowflow.ai.retrieval.RetrievedPassage;
import com.knowflow.ai.workspace.WorkspaceRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RagPipelineTest {

    @Mock
    private RetrievalService retrievalService;

    private PromptTemplateService promptTemplateService;
    private HallucinationDetector hallucinationDetector;
    private LocalFallbackLlmClient localFallbackLlmClient;

    @Mock
    private AiUsageRecordRepository aiUsageRecordRepository;

    @Mock
    private com.knowflow.ai.document.DocumentRepository documentRepository;

    @Mock
    private com.knowflow.ai.security.DocumentAuthorizationService documentAuthService;

    private RagOrchestrator ragOrchestrator;

    @BeforeEach
    public void setup() {
        promptTemplateService = new PromptTemplateService();
        hallucinationDetector = new HallucinationDetector();
        localFallbackLlmClient = new LocalFallbackLlmClient();

        ragOrchestrator = new RagOrchestrator(
                retrievalService,
                promptTemplateService,
                localFallbackLlmClient,
                hallucinationDetector,
                aiUsageRecordRepository,
                documentRepository,
                documentAuthService
        );
        ReflectionTestUtils.setField(ragOrchestrator, "modelName", "local-grounded");
    }

    @Test
    public void testInsufficientInformationWhenNoContextFound() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(retrievalService.retrieveAuthorizedContext(eq("What is the extraterrestrial holiday policy?"), any(RetrievalFilter.class)))
                .thenReturn(Collections.emptyList());

        RagResponse response = ragOrchestrator.generateAnswer(
                "What is the extraterrestrial holiday policy?",
                wsId,
                userId,
                WorkspaceRole.VIEWER,
                null,
                Collections.emptyList()
        );

        assertNotNull(response);
        assertTrue(response.isRetrievalInsufficient());
        assertEquals("I couldn't find sufficient information in your authorized documents to answer this question reliably.",
                response.getAnswer());
        assertTrue(response.getCitations().isEmpty());
    }

    @Test
    public void testGroundingAndCitationGenerationWhenContextAvailable() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        RetrievedPassage passage = new RetrievedPassage(
                chunkId,
                docId,
                "Employee Handbook 2026.pdf",
                14,
                "Annual Leave",
                "Employees accrue 2.08 days of paid leave per month, totaling 25 days annually.",
                0.88
        );

        when(retrievalService.retrieveAuthorizedContext(eq("What is annual leave?"), any(RetrievalFilter.class)))
                .thenReturn(List.of(passage));

        RagResponse response = ragOrchestrator.generateAnswer(
                "What is annual leave?",
                wsId,
                userId,
                WorkspaceRole.VIEWER,
                null,
                Collections.emptyList()
        );

        assertNotNull(response);
        assertFalse(response.isRetrievalInsufficient());
        assertFalse(response.getCitations().isEmpty());
        assertEquals("Employee Handbook 2026.pdf", response.getCitations().get(0).getDocumentTitle());
        assertEquals(14, response.getCitations().get(0).getPageNumber());
        assertTrue(response.getAnswer().contains("25 days"));
    }

    private RetrievedPassage createPassage(UUID docId, String title, int page, String heading, String content, double score) {
        return new RetrievedPassage(
                UUID.randomUUID(),
                docId,
                title,
                page,
                heading,
                content,
                score
        );
    }

    private RagOrchestrator createOrchestratorWithLlm(LlmClient customLlm) {
        RagOrchestrator orch = new RagOrchestrator(
                retrievalService,
                promptTemplateService,
                customLlm,
                hallucinationDetector,
                aiUsageRecordRepository,
                documentRepository,
                documentAuthService
        );
        ReflectionTestUtils.setField(orch, "modelName", "test-model");
        return orch;
    }

    @Test
    @DisplayName("Single valid source among several retrieved passages is extracted and renumbered")
    public void testSingleValidSourceAmongSeveralRetrievedPassages() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        RetrievedPassage p1 = createPassage(docId, "Travel Policy.pdf", 3, "Per Diem", "Domestic per diem is $60.", 0.65);
        RetrievedPassage p2 = createPassage(docId, "Handbook.pdf", 12, "Bereavement", "Full-time employees receive 3 days paid bereavement leave.", 0.85);
        RetrievedPassage p3 = createPassage(docId, "IT Security.pdf", 7, "VPN", "Hardware token required.", 0.58);

        when(retrievalService.retrieveAuthorizedContext(eq("What is bereavement leave?"), any(RetrievalFilter.class)))
                .thenReturn(List.of(p1, p2, p3));

        LlmClient llm = (prompt, history, params) -> new LlmClient.LlmResult(
                "Full-time employees receive 3 days of paid bereavement leave [S2].",
                50, 20, 70
        );
        RagOrchestrator orchestrator = createOrchestratorWithLlm(llm);

        RagResponse response = orchestrator.generateAnswer(
                "What is bereavement leave?", wsId, userId, WorkspaceRole.VIEWER, null, Collections.emptyList()
        );

        assertNotNull(response);
        assertEquals(1, response.getCitations().size());
        assertEquals("Handbook.pdf", response.getCitations().get(0).getDocumentTitle());
        assertEquals(12, response.getCitations().get(0).getPageNumber());
        assertEquals("Full-time employees receive 3 days of paid bereavement leave [1].", response.getAnswer());
    }

    @Test
    @DisplayName("Multiple valid sources preserve first-appearance order and renumber accurately")
    public void testMultipleValidSourcesAndOrderPreservation() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        RetrievedPassage p1 = createPassage(docId, "Handbook.pdf", 14, "Vacation", "Annual leave is 25 days.", 0.80);
        RetrievedPassage p2 = createPassage(docId, "Handbook.pdf", 16, "Sick", "Sick leave requires note.", 0.75);
        RetrievedPassage p3 = createPassage(docId, "Parental.pdf", 4, "Maternity", "Maternity leave is 16 weeks.", 0.88);
        RetrievedPassage p4 = createPassage(docId, "Parental.pdf", 8, "Paternity", "Paternity leave is 4 weeks.", 0.82);

        when(retrievalService.retrieveAuthorizedContext(eq("Parental and vacation policies?"), any(RetrievalFilter.class)))
                .thenReturn(List.of(p1, p2, p3, p4));

        LlmClient llm = (prompt, history, params) -> new LlmClient.LlmResult(
                "Maternity leave is 16 weeks [S3]. Paternity leave is 4 weeks [S4]. Annual vacation is 25 days [S1].",
                100, 30, 130
        );
        RagOrchestrator orchestrator = createOrchestratorWithLlm(llm);

        RagResponse response = orchestrator.generateAnswer(
                "Parental and vacation policies?", wsId, userId, WorkspaceRole.VIEWER, null, Collections.emptyList()
        );

        assertNotNull(response);
        assertEquals(3, response.getCitations().size());
        // Order of first appearance: S3, S4, S1
        assertEquals("Parental.pdf", response.getCitations().get(0).getDocumentTitle());
        assertEquals(4, response.getCitations().get(0).getPageNumber());
        assertEquals("Parental.pdf", response.getCitations().get(1).getDocumentTitle());
        assertEquals(8, response.getCitations().get(1).getPageNumber());
        assertEquals("Handbook.pdf", response.getCitations().get(2).getDocumentTitle());
        assertEquals(14, response.getCitations().get(2).getPageNumber());

        assertEquals("Maternity leave is 16 weeks [1]. Paternity leave is 4 weeks [2]. Annual vacation is 25 days [3].",
                response.getAnswer());
    }

    @Test
    @DisplayName("Duplicate citation markers are deduplicated and all instances renumber to the same index")
    public void testDuplicateIdentifiersDeduplicated() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        RetrievedPassage p1 = createPassage(docId, "Security.pdf", 2, "Passwords", "Passwords expire in 90 days.", 0.70);
        RetrievedPassage p2 = createPassage(docId, "Security.pdf", 5, "2FA", "2FA is mandatory for all employees.", 0.90);

        when(retrievalService.retrieveAuthorizedContext(eq("Security requirements?"), any(RetrievalFilter.class)))
                .thenReturn(List.of(p1, p2));

        LlmClient llm = (prompt, history, params) -> new LlmClient.LlmResult(
                "Two-factor auth is mandatory [S2]. All staff must enroll [S2].",
                60, 25, 85
        );
        RagOrchestrator orchestrator = createOrchestratorWithLlm(llm);

        RagResponse response = orchestrator.generateAnswer(
                "Security requirements?", wsId, userId, WorkspaceRole.VIEWER, null, Collections.emptyList()
        );

        assertNotNull(response);
        assertEquals(1, response.getCitations().size());
        assertEquals(5, response.getCitations().get(0).getPageNumber());
        assertEquals("Two-factor auth is mandatory [1]. All staff must enroll [1].", response.getAnswer());
    }

    @Test
    @DisplayName("Out-of-range, zero, and fabricated citation markers are stripped from text and discarded from citations")
    public void testInvalidAndOutOfRangeIdentifiersStripped() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        RetrievedPassage p1 = createPassage(docId, "Policy.pdf", 1, "Scope", "All staff are covered.", 0.85);
        RetrievedPassage p2 = createPassage(docId, "Policy.pdf", 2, "Exceptions", "Contractors excluded.", 0.75);

        when(retrievalService.retrieveAuthorizedContext(eq("Who is covered?"), any(RetrievalFilter.class)))
                .thenReturn(List.of(p1, p2));

        LlmClient llm = (prompt, history, params) -> new LlmClient.LlmResult(
                "All staff are covered [S1]. Fabricated claim [S99]. Zero claim [S0].",
                80, 25, 105
        );
        RagOrchestrator orchestrator = createOrchestratorWithLlm(llm);

        RagResponse response = orchestrator.generateAnswer(
                "Who is covered?", wsId, userId, WorkspaceRole.VIEWER, null, Collections.emptyList()
        );

        assertNotNull(response);
        assertEquals(1, response.getCitations().size());
        assertEquals(1, response.getCitations().get(0).getPageNumber());
        assertFalse(response.getAnswer().contains("[S99]"));
        assertFalse(response.getAnswer().contains("[S0]"));
        assertFalse(response.getAnswer().contains("[99]"));
        assertTrue(response.getAnswer().contains("All staff are covered [1]."));
    }

    @Test
    @DisplayName("Mixed valid and invalid markers correctly retain only valid sources")
    public void testMixedValidAndInvalidIdentifiers() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        RetrievedPassage p1 = createPassage(docId, "Benefits.pdf", 10, "Health", "Health insurance starts day 1.", 0.88);
        RetrievedPassage p2 = createPassage(docId, "Benefits.pdf", 15, "Dental", "Dental covers $1500 yearly.", 0.82);

        when(retrievalService.retrieveAuthorizedContext(eq("Benefits?"), any(RetrievalFilter.class)))
                .thenReturn(List.of(p1, p2));

        LlmClient llm = (prompt, history, params) -> new LlmClient.LlmResult(
                "Health plan starts day 1 [S1][S45]. Dental is $1500 [S2].",
                70, 25, 95
        );
        RagOrchestrator orchestrator = createOrchestratorWithLlm(llm);

        RagResponse response = orchestrator.generateAnswer(
                "Benefits?", wsId, userId, WorkspaceRole.VIEWER, null, Collections.emptyList()
        );

        assertNotNull(response);
        assertEquals(2, response.getCitations().size());
        assertEquals("Health plan starts day 1 [1]. Dental is $1500 [2].", response.getAnswer());
    }

    @Test
    @DisplayName("Answer without citation markers returns empty citations list")
    public void testNoCitationMarkers_returnsEmptyCitations() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        RetrievedPassage p1 = createPassage(docId, "Guide.pdf", 1, "Intro", "Company overview.", 0.80);

        when(retrievalService.retrieveAuthorizedContext(eq("Hi"), any(RetrievalFilter.class)))
                .thenReturn(List.of(p1));

        LlmClient llm = (prompt, history, params) -> new LlmClient.LlmResult(
                "Hello! How can I assist you with company documents today?",
                40, 15, 55
        );
        RagOrchestrator orchestrator = createOrchestratorWithLlm(llm);

        RagResponse response = orchestrator.generateAnswer(
                "Hi", wsId, userId, WorkspaceRole.VIEWER, null, Collections.emptyList()
        );

        assertNotNull(response);
        assertTrue(response.getCitations().isEmpty());
        assertEquals("Hello! How can I assist you with company documents today?", response.getAnswer());
    }

    @Test
    @DisplayName("Insufficient answer triggers empty citations even if candidate passages exist")
    public void testInsufficientAnswer_returnsEmptyCitations() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        RetrievedPassage p1 = createPassage(docId, "Handbook.pdf", 1, "Overview", "General handbook overview.", 0.70);

        when(retrievalService.retrieveAuthorizedContext(eq("Mars colony policy?"), any(RetrievalFilter.class)))
                .thenReturn(List.of(p1));

        LlmClient llm = (prompt, history, params) -> new LlmClient.LlmResult(
                "I couldn't find sufficient information in your authorized documents to answer this question reliably.",
                60, 20, 80
        );
        RagOrchestrator orchestrator = createOrchestratorWithLlm(llm);

        RagResponse response = orchestrator.generateAnswer(
                "Mars colony policy?", wsId, userId, WorkspaceRole.VIEWER, null, Collections.emptyList()
        );

        assertNotNull(response);
        assertTrue(response.isRetrievalInsufficient());
        assertTrue(response.getCitations().isEmpty());
    }

    @Test
    @DisplayName("LocalFallbackLlmClient generates valid source tags and cites only passages it actually uses")
    public void testLocalFallbackCitesOnlyPassagesActuallyUsed() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        RetrievedPassage p1 = createPassage(docId, "Doc1.pdf", 1, "Sec1", "Content 1", 0.90);
        RetrievedPassage p2 = createPassage(docId, "Doc2.pdf", 2, "Sec2", "Content 2", 0.85);
        RetrievedPassage p3 = createPassage(docId, "Doc3.pdf", 3, "Sec3", "Content 3", 0.80);
        RetrievedPassage p4 = createPassage(docId, "Doc4.pdf", 4, "Sec4", "Content 4", 0.75);
        RetrievedPassage p5 = createPassage(docId, "Doc5.pdf", 5, "Sec5", "Content 5", 0.70);

        when(retrievalService.retrieveAuthorizedContext(eq("Summarize all"), any(RetrievalFilter.class)))
                .thenReturn(List.of(p1, p2, p3, p4, p5));

        // Using default ragOrchestrator from setup(), which uses localFallbackLlmClient
        RagResponse response = ragOrchestrator.generateAnswer(
                "Summarize all", wsId, userId, WorkspaceRole.VIEWER, null, Collections.emptyList()
        );

        assertNotNull(response);
        // Fallback caps snippets at 3, so only 3 passages should be cited
        assertEquals(3, response.getCitations().size());
        assertEquals("Doc1.pdf", response.getCitations().get(0).getDocumentTitle());
        assertEquals("Doc2.pdf", response.getCitations().get(1).getDocumentTitle());
        assertEquals("Doc3.pdf", response.getCitations().get(2).getDocumentTitle());
        assertTrue(response.getAnswer().contains("[1]"));
        assertTrue(response.getAnswer().contains("[2]"));
        assertTrue(response.getAnswer().contains("[3]"));
        assertFalse(response.getAnswer().contains("[4]"));
        assertFalse(response.getAnswer().contains("[5]"));
    }

    @Test
    @DisplayName("Document comparison attributes and renumbers citations across both documents accurately")
    public void testDocumentComparisonWithSourcesFromBothDocuments() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();

        Document doc1 = new Document(wsId, "Handbook 2026.pdf", "hb26.pdf", FileType.PDF, 1024L, "/path1", "application/pdf", userId);
        doc1.setId(docId1);
        Document doc2 = new Document(wsId, "Handbook 2027.pdf", "hb27.pdf", FileType.PDF, 2048L, "/path2", "application/pdf", userId);
        doc2.setId(docId2);

        when(documentAuthService.canViewDocument(docId1, userId)).thenReturn(true);
        when(documentAuthService.canViewDocument(docId2, userId)).thenReturn(true);
        when(documentRepository.findByIdAndDeletedAtIsNull(docId1)).thenReturn(Optional.of(doc1));
        when(documentRepository.findByIdAndDeletedAtIsNull(docId2)).thenReturn(Optional.of(doc2));

        RetrievedPassage p1 = createPassage(docId1, "Handbook 2026.pdf", 2, "Remote", "Allows 2 remote days.", 0.88);
        RetrievedPassage p2 = createPassage(docId1, "Handbook 2026.pdf", 4, "Hours", "Core hours 9-5.", 0.70);

        RetrievedPassage p3 = createPassage(docId2, "Handbook 2027.pdf", 2, "Remote", "Allows 4 remote days.", 0.91);
        RetrievedPassage p4 = createPassage(docId2, "Handbook 2027.pdf", 5, "Hours", "Core hours 10-4.", 0.72);

        when(retrievalService.retrieveAuthorizedContext(anyString(), any(RetrievalFilter.class)))
                .thenAnswer(inv -> {
                    RetrievalFilter rf = inv.getArgument(1);
                    if (docId1.equals(rf.getDocumentFilterId())) return List.of(p1, p2);
                    if (docId2.equals(rf.getDocumentFilterId())) return List.of(p3, p4);
                    return Collections.emptyList();
                });

        // Unified sequence: S1(p1), S2(p2), S3(p3), S4(p4).
        // LLM cites S1 (from doc1) and S3 (from doc2).
        LlmClient compareLlm = (prompt, history, params) -> new LlmClient.LlmResult(
                "Handbook 2026 allows 2 remote days [S1], whereas Handbook 2027 expands this to 4 remote days [S3].",
                120, 40, 160
        );
        RagOrchestrator orchestrator = createOrchestratorWithLlm(compareLlm);

        RagResponse response = orchestrator.compareDocuments(
                wsId, userId, WorkspaceRole.VIEWER, docId1, docId2, "Compare remote work"
        );

        assertNotNull(response);
        assertEquals(2, response.getCitations().size());
        assertEquals("Handbook 2026.pdf", response.getCitations().get(0).getDocumentTitle());
        assertEquals("Handbook 2027.pdf", response.getCitations().get(1).getDocumentTitle());
        assertEquals("Handbook 2026 allows 2 remote days [1], whereas Handbook 2027 expands this to 4 remote days [2].",
                response.getAnswer());
    }

    @Test
    @DisplayName("LocalFallbackLlmClient end-to-end comparison processes <document_comparison_context> across both documents")
    public void testLocalFallbackWithDocumentComparisonAcrossBothDocuments() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();

        Document doc1 = new Document(wsId, "Policy 2026.pdf", "p26.pdf", FileType.PDF, 1024L, "/path1", "application/pdf", userId);
        doc1.setId(docId1);
        Document doc2 = new Document(wsId, "Policy 2027.pdf", "p27.pdf", FileType.PDF, 2048L, "/path2", "application/pdf", userId);
        doc2.setId(docId2);

        when(documentAuthService.canViewDocument(docId1, userId)).thenReturn(true);
        when(documentAuthService.canViewDocument(docId2, userId)).thenReturn(true);
        when(documentRepository.findByIdAndDeletedAtIsNull(docId1)).thenReturn(Optional.of(doc1));
        when(documentRepository.findByIdAndDeletedAtIsNull(docId2)).thenReturn(Optional.of(doc2));

        RetrievedPassage p1 = createPassage(docId1, "Policy 2026.pdf", 1, "Remote", "2026 allows 2 days remote.", 0.88);
        RetrievedPassage p2 = createPassage(docId1, "Policy 2026.pdf", 2, "Hours", "2026 core hours 9-5.", 0.75);

        RetrievedPassage p3 = createPassage(docId2, "Policy 2027.pdf", 1, "Remote", "2027 allows 4 days remote.", 0.92);
        RetrievedPassage p4 = createPassage(docId2, "Policy 2027.pdf", 2, "Hours", "2027 core hours 10-4.", 0.78);

        when(retrievalService.retrieveAuthorizedContext(anyString(), any(RetrievalFilter.class)))
                .thenAnswer(inv -> {
                    RetrievalFilter rf = inv.getArgument(1);
                    if (docId1.equals(rf.getDocumentFilterId())) return List.of(p1, p2);
                    if (docId2.equals(rf.getDocumentFilterId())) return List.of(p3, p4);
                    return Collections.emptyList();
                });

        // Using default ragOrchestrator from setup(), which uses LocalFallbackLlmClient
        RagResponse response = ragOrchestrator.compareDocuments(
                wsId, userId, WorkspaceRole.VIEWER, docId1, docId2, "Compare policies"
        );

        assertNotNull(response);
        assertFalse(response.isRetrievalInsufficient());
        // LocalFallback caps at 3 snippets: S1(p1), S2(p2) from Doc1, and S3(p3) from Doc2
        assertEquals(3, response.getCitations().size());
        assertEquals("Policy 2026.pdf", response.getCitations().get(0).getDocumentTitle());
        assertEquals("Policy 2026.pdf", response.getCitations().get(1).getDocumentTitle());
        assertEquals("Policy 2027.pdf", response.getCitations().get(2).getDocumentTitle());
        assertTrue(response.getAnswer().contains("[1]"));
        assertTrue(response.getAnswer().contains("[2]"));
        assertTrue(response.getAnswer().contains("[3]"));
        assertFalse(response.getAnswer().contains("[4]"));
    }

    @Test
    @DisplayName("PromptTemplateService sanitizes case and whitespace variants of closing tags and document markers")
    public void testSanitizeUntrustedContentClosingBoundaryAndMarkers() {
        // Closing context variants
        assertEquals("Before [TAG_FILTERED] After",
                promptTemplateService.sanitizeUntrustedContent("Before </document_context> After"));
        assertEquals("Before [TAG_FILTERED] After",
                promptTemplateService.sanitizeUntrustedContent("Before </DOCUMENT_CONTEXT> After"));
        assertEquals("Before [TAG_FILTERED] After",
                promptTemplateService.sanitizeUntrustedContent("Before </document_context > After"));
        assertEquals("Before [TAG_FILTERED] After",
                promptTemplateService.sanitizeUntrustedContent("Before </ document_context> After"));
        assertEquals("Before [TAG_FILTERED] After",
                promptTemplateService.sanitizeUntrustedContent("Before </DOCUMENT_COMPARISON_CONTEXT> After"));
        assertEquals("Before [TAG_FILTERED] After",
                promptTemplateService.sanitizeUntrustedContent("Before </ document_comparison_context > After"));

        // Document-supplied citation markers neutralization (case-insensitive)
        assertEquals("Ref [DOC_REF_S1] here",
                promptTemplateService.sanitizeUntrustedContent("Ref [S1] here"));
        assertEquals("Ref [DOC_REF_S1] here",
                promptTemplateService.sanitizeUntrustedContent("Ref [s1] here"));
        assertEquals("Ref [DOC_SOURCE_S2: Page 4",
                promptTemplateService.sanitizeUntrustedContent("Ref [Source S2: Page 4"));
        assertEquals("Ref [DOC_SOURCE_S2: Page 4",
                promptTemplateService.sanitizeUntrustedContent("Ref [source s2: Page 4"));

        // Ordinary text preserved
        assertEquals("Ordinary text with [Section 4] and [x] is untouched.",
                promptTemplateService.sanitizeUntrustedContent("Ordinary text with [Section 4] and [x] is untouched."));
    }

    @Test
    @DisplayName("RagOrchestrator parses lowercase [s1] and strips malformed [Sx], [S-1], and oversized markers")
    public void testCaseInsensitiveAndMalformedCitationMarkerParsing() {
        UUID wsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        RetrievedPassage p1 = createPassage(docId, "Policy.pdf", 1, "Sec1", "Content 1", 0.90);
        RetrievedPassage p2 = createPassage(docId, "Policy.pdf", 2, "Sec2", "Content 2", 0.80);

        when(retrievalService.retrieveAuthorizedContext(eq("Test malformed"), any(RetrievalFilter.class)))
                .thenReturn(List.of(p1, p2));

        LlmClient llm = (prompt, history, params) -> new LlmClient.LlmResult(
                "Fact one [s1]. Fact two [Sx]. Negative [S-1]. Huge [S999999999999999999999]. Fact three [s2]. Note [Section 4] applies.",
                80, 25, 105
        );
        RagOrchestrator orchestrator = createOrchestratorWithLlm(llm);

        RagResponse response = orchestrator.generateAnswer(
                "Test malformed", wsId, userId, WorkspaceRole.VIEWER, null, Collections.emptyList()
        );

        assertNotNull(response);
        // Valid citations: p1 (via [s1]) and p2 (via [s2])
        assertEquals(2, response.getCitations().size());
        assertEquals(1, response.getCitations().get(0).getPageNumber());
        assertEquals(2, response.getCitations().get(1).getPageNumber());

        // Malformed [Sx], [S-1], [S999999999999999999999] stripped
        assertFalse(response.getAnswer().contains("[Sx]"));
        assertFalse(response.getAnswer().contains("[S-1]"));
        assertFalse(response.getAnswer().contains("999999999999999999999"));

        // Valid citations renumbered
        assertTrue(response.getAnswer().contains("Fact one [1]."));
        assertTrue(response.getAnswer().contains("Fact three [2]."));

        // Ordinary prose bracket preserved
        assertTrue(response.getAnswer().contains("Note [Section 4] applies."));
    }
}
