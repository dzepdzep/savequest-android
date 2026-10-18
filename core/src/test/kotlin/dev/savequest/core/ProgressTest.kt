package dev.savequest.core
import org.junit.Assert.*
import org.junit.Test
class ProgressTest {
    private val unknown = Evidence(EvidenceKind.UNKNOWN, "Unknown")
    @Test fun manualSurvivesSaveWithNoEvidence() { assertEquals(EvidenceKind.MANUAL, Progress.resolve(unknown, false, true).kind) }
    @Test fun historicalEvidenceIsNotCurrentBag() { assertEquals(EvidenceKind.HISTORICAL, Progress.resolve(unknown, true, false).kind) }
    @Test fun clearingManualDoesNotEraseObservedEvidence() { val bag = Evidence(EvidenceKind.BAG, "Bag"); assertEquals(bag, Progress.resolve(bag, true, false)) }
    @Test fun absenceRemainsUnknown() { assertEquals(unknown, Progress.resolve(unknown, false, false)) }
}
