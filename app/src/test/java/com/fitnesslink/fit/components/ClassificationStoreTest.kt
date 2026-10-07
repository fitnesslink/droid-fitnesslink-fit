package com.fitnesslink.fit.components

import com.fitnesslink.fit.classification.ClassificationStore
import com.fitnesslink.fit.contributors.ContributorStore
import com.fitnesslink.fit.model.RestType
import com.fitnesslink.fit.model.api.Anatomy
import com.fitnesslink.fit.model.api.ContentStatus
import com.fitnesslink.fit.model.api.Equipment
import com.fitnesslink.fit.model.api.FLTrainingLevel
import com.fitnesslink.fit.model.api.RestTypeId
import com.fitnesslink.fit.model.api.WorkoutTaskTypeId
import com.fitnesslink.fit.network.dto.ClassificationFallbackIds
import com.fitnesslink.fit.network.dto.ContributorSummaryDto
import com.fitnesslink.fit.network.dto.RestTypeDto
import com.fitnesslink.fit.network.dto.WorkoutTaskTypeDto
import com.fitnesslink.fit.ui.components.ContentLayout
import com.fitnesslink.fit.ui.components.ContentLayoutPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ClassificationStoreTest {

    private val chestId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val legsId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val forearmId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val dumbbellId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val cableId = UUID.fromString("55555555-5555-5555-5555-555555555555")

    @Before
    fun seed() {
        ClassificationStore.seedForTesting(
            anatomy = listOf(
                Anatomy(legsId, "Legs", sortOrder = 2, isPrimary = true, category = "lower"),
                Anatomy(chestId, "Chest", sortOrder = 1, isPrimary = true, category = "upper"),
                Anatomy(forearmId, "Forearms", sortOrder = 9, isPrimary = false, category = "upper")
            ),
            equipment = listOf(
                Equipment(cableId, "Cable", sortOrder = null, isPrimary = true),
                Equipment(dumbbellId, "Dumbbell", sortOrder = 1, isPrimary = true)
            ),
            workoutTaskTypes = listOf(
                WorkoutTaskTypeDto("AAAAAAAA-0000-0000-0000-000000000001", "Movement"),
                WorkoutTaskTypeDto("AAAAAAAA-0000-0000-0000-000000000002", "Rest"),
                WorkoutTaskTypeDto("AAAAAAAA-0000-0000-0000-000000000003", "Interval")
            ),
            restTypes = listOf(
                RestTypeDto("BBBBBBBB-0000-0000-0000-000000000001", "Passive"),
                RestTypeDto("BBBBBBBB-0000-0000-0000-000000000002", "Active Recovery")
            ),
            contentStatuses = listOf(ContentStatus(UUID.fromString("cccccccc-0000-0000-0000-000000000001"), "Published")),
            trainingLevels = listOf(FLTrainingLevel(UUID.fromString("dddddddd-0000-0000-0000-000000000001"), "Beginner"))
        )
    }

    @After
    fun reset() {
        ClassificationStore.resetForTesting()
    }

    @Test
    fun primaryChipsFilterByIsPrimaryAndSortBySortOrder() {
        assertEquals(listOf("Chest", "Legs"), ClassificationStore.primaryAnatomy.map { it.name })
        // Null sortOrder sinks to the end.
        assertEquals(listOf("Dumbbell", "Cable"), ClassificationStore.primaryEquipment.map { it.name })
        assertEquals(chestId.toString(), ClassificationStore.primaryAnatomy.first().id)
    }

    @Test
    fun anatomyAndEquipmentLookupsAreCaseInsensitiveBothWays() {
        assertEquals("Chest", ClassificationStore.anatomyName(chestId.toString().uppercase()))
        assertEquals(chestId.toString(), ClassificationStore.anatomyId("chest"))
        assertEquals("Dumbbell", ClassificationStore.equipmentName(dumbbellId.toString()))
        assertEquals(dumbbellId.toString(), ClassificationStore.equipmentId(" DUMBBELL "))
        assertNull(ClassificationStore.anatomyName(UUID.randomUUID().toString()))
        assertNull(ClassificationStore.equipmentId("Barbell"))
    }

    @Test
    fun anatomyByCategoryGroupsAndSorts() {
        val grouped = ClassificationStore.anatomyByCategory
        assertEquals(listOf("lower", "upper"), grouped.map { it.first })
        assertEquals(listOf("Chest", "Forearms"), grouped.first { it.first == "upper" }.second.map { it.name })
    }

    @Test
    fun workoutTaskTypeIdPrefersCacheAndLowercasesIt() {
        assertEquals("aaaaaaaa-0000-0000-0000-000000000002", ClassificationStore.workoutTaskTypeId("rest"))
        assertEquals("aaaaaaaa-0000-0000-0000-000000000003", ClassificationStore.workoutTaskTypeId("INTERVAL"))
        // Unknown name -> fallback constant.
        assertEquals(WorkoutTaskTypeId.fallbackByName("bogus"), ClassificationStore.workoutTaskTypeId("bogus"))
    }

    @Test
    fun restTypeMapsResolveBothDirections() {
        assertEquals("bbbbbbbb-0000-0000-0000-000000000002", ClassificationStore.restTypeId("Active Recovery"))
        assertEquals("Active Recovery", ClassificationStore.restTypeName("BBBBBBBB-0000-0000-0000-000000000002"))
        // Fallback ids still resolve to names when the cache doesn't know them.
        assertEquals("Passive", ClassificationStore.restTypeName(RestTypeId.passive))
        assertNull(ClassificationStore.restTypeName(UUID.randomUUID().toString()))
    }

    @Test
    fun restTypeEnumHooksAreWiredToTheStore() {
        assertEquals(RestType.ACTIVE_RECOVERY, RestType.resolve("BBBBBBBB-0000-0000-0000-000000000002"))
        assertEquals("bbbbbbbb-0000-0000-0000-000000000001", RestType.id(RestType.PASSIVE))
    }

    @Test
    fun coldCacheFallsBackToSeededConstants() {
        ClassificationStore.resetForTesting()
        assertEquals(WorkoutTaskTypeId.movement, ClassificationStore.workoutTaskTypeId("Movement"))
        assertEquals(WorkoutTaskTypeId.rest, ClassificationStore.workoutTaskTypeId("Rest"))
        assertEquals(RestTypeId.activeRecovery, ClassificationStore.restTypeId("Active Recovery"))
        assertEquals(ClassificationFallbackIds.CONTENT_STATUS_ACTIVE, ClassificationStore.contentStatusId("Active"))
        assertTrue(ClassificationStore.primaryAnatomy.isEmpty())
        assertTrue(ClassificationStore.trainingLevels().isEmpty())
        assertEquals(RestType.PASSIVE, RestType.resolve(RestTypeId.passive))
    }

    @Test
    fun contentStatusAndTrainingLevelLookups() {
        assertEquals("cccccccc-0000-0000-0000-000000000001", ClassificationStore.contentStatusId("published"))
        assertEquals("dddddddd-0000-0000-0000-000000000001", ClassificationStore.trainingLevelId("beginner"))
        assertEquals("Beginner", ClassificationStore.trainingLevelName("DDDDDDDD-0000-0000-0000-000000000001"))
    }

    @Test
    fun contributorStoreSortsAndResolvesNames() {
        ContributorStore.seedForTesting(
            listOf(
                ContributorSummaryDto("B0000000-0000-0000-0000-000000000001", "Coach", "Zed"),
                ContributorSummaryDto("B0000000-0000-0000-0000-000000000002", "Coach", "  "),
                ContributorSummaryDto("B0000000-0000-0000-0000-000000000003", "Company", "alpha")
            )
        )
        assertEquals(listOf("alpha", "Unnamed", "Zed"), ContributorStore.contributors.value.map { it.label })
        assertEquals("Zed", ContributorStore.name("b0000000-0000-0000-0000-000000000001"))
        assertEquals("Unnamed", ContributorStore.name("B0000000-0000-0000-0000-000000000002"))
        assertNull(ContributorStore.name(null))
        assertNull(ContributorStore.name(UUID.randomUUID().toString()))
        assertTrue(ContributorStore.isHouse("DAF56C04-F9AA-41FB-BFAE-690BEA5260A2"))
        assertEquals(
            listOf("Zed"),
            ContributorStore.present(setOf("B0000000-0000-0000-0000-000000000001")).map { it.label }
        )
    }

    @Test
    fun contentLayoutParsesIosRawValuesAndToggles() {
        assertEquals(ContentLayout.CARD, ContentLayoutPrefs.parse("card"))
        assertEquals(ContentLayout.LIST, ContentLayoutPrefs.parse(" LIST "))
        assertNull(ContentLayoutPrefs.parse("grid"))
        assertEquals(ContentLayout.LIST, ContentLayout.CARD.toggled())
        assertEquals(ContentLayout.CARD, ContentLayout.LIST.toggled())
    }
}
