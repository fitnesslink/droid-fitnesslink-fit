package com.fitnesslink.fit.model.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.fitnesslink.fit.R
import com.google.gson.annotations.SerializedName

// Onboarding PRD v1.1 — port of iOS Models/Onboarding/OnboardingModels.swift.
// The @SerializedName values are the stable wire strings stored in the
// server's onboarding answers JSON and read by its recommendation engine
// (OnboardingAnswers.cs / RecommendationEngine.cs). Never rename one.

/** One selectable answer: copy, icon and (optionally) a bundled photo. */
interface OnboardingOption {
    val title: String
    val subtitle: String? get() = null
    val icon: ImageVector
    @get:DrawableRes val photo: Int? get() = null
}

// MARK: Q1 / Q2 — fitness goals

enum class FitnessGoal(
    override val title: String,
    override val subtitle: String,
    override val icon: ImageVector,
    override val photo: Int?,
    /** Lowercase phrase for sentences ("your strength goal"). */
    val phrase: String
) : OnboardingOption {
    @SerializedName("LoseWeight") LOSE_WEIGHT("Lose Weight / Body Fat", "Reduce body fat and work toward a healthier weight and body composition.", Icons.Filled.LocalFireDepartment, R.drawable.onb_goal_lose_weight, "weight-loss"),
    @SerializedName("BuildMuscle") BUILD_MUSCLE("Build Muscle", "Increase muscle size, develop definition, and build a stronger physique.", Icons.Filled.FitnessCenter, R.drawable.onb_goal_build_muscle, "muscle-building"),
    @SerializedName("BuildStrength") BUILD_STRENGTH("Build Strength", "Increase muscular strength and improve your ability to lift heavier weights.", Icons.Filled.SportsGymnastics, R.drawable.onb_goal_build_strength, "strength"),
    @SerializedName("BodyRecomposition") BODY_RECOMPOSITION("Body Recomposition", "Build muscle while reducing body fat.", Icons.Filled.Autorenew, R.drawable.onb_goal_recomp, "body recomposition"),
    @SerializedName("Endurance") ENDURANCE("Improve Endurance & Stamina", "Improve cardiovascular fitness and sustain physical activity for longer.", Icons.Filled.DirectionsRun, R.drawable.onb_goal_endurance, "endurance"),
    @SerializedName("AthleticPerformance") ATHLETIC_PERFORMANCE("Improve Athletic Performance", "Develop speed, power, agility, and athletic ability.", Icons.Filled.Bolt, null, "athletic performance"),
    @SerializedName("GeneralFitness") GENERAL_FITNESS("Improve General Fitness & Health", "Improve overall fitness and physical well-being.", Icons.Filled.FavoriteBorder, R.drawable.onb_goal_general, "general fitness"),
    @SerializedName("Mobility") MOBILITY("Improve Mobility & Flexibility", "Improve joint mobility, flexibility, and movement quality.", Icons.Filled.SelfImprovement, R.drawable.onb_goal_mobility, "mobility"),
    @SerializedName("MaintainFitness") MAINTAIN_FITNESS("Maintain Current Fitness", "Maintain your current strength, conditioning, and fitness level.", Icons.Filled.Verified, null, "fitness maintenance");
}

// MARK: Q3 — experience

enum class TrainingExperience(override val title: String, override val subtitle: String, override val photo: Int?) : OnboardingOption {
    @SerializedName("Beginner") BEGINNER("Beginner", "I'm new to exercise or returning after a long break. I'm still learning proper form and building consistency.", R.drawable.onb_exp_beginner),
    @SerializedName("Intermediate") INTERMEDIATE("Intermediate", "I exercise regularly and understand basic movements, techniques, and workout structure.", R.drawable.onb_exp_intermediate),
    @SerializedName("Advanced") ADVANCED("Advanced", "I have several years of consistent training experience and follow structured workout programs.", R.drawable.onb_exp_advanced);

    override val icon: ImageVector get() = Icons.Filled.BarChart
}

// MARK: Q4 — training background (multi)

enum class TrainingBackground(override val title: String, override val icon: ImageVector) : OnboardingOption {
    @SerializedName("StrengthTraining") STRENGTH_TRAINING("Strength Training / Weightlifting", Icons.Filled.FitnessCenter),
    @SerializedName("Running") RUNNING("Running", Icons.Filled.DirectionsRun),
    @SerializedName("CardioEndurance") CARDIO_ENDURANCE("Cardio & Endurance", Icons.Filled.MonitorHeart),
    @SerializedName("FunctionalTraining") FUNCTIONAL_TRAINING("Functional Training", Icons.Filled.SportsMartialArts),
    @SerializedName("HIIT") HIIT("HIIT & Circuit Training", Icons.Filled.Timer),
    @SerializedName("SportsPerformance") SPORTS_PERFORMANCE("Sports Performance", Icons.Filled.SportsBasketball),
    @SerializedName("YogaPilates") YOGA_PILATES("Yoga & Pilates", Icons.Filled.SelfImprovement),
    @SerializedName("MobilityFlexibility") MOBILITY_FLEXIBILITY("Mobility & Flexibility", Icons.Filled.Accessibility),
    @SerializedName("Boxing") BOXING("Boxing & Combat Training", Icons.Filled.SportsMma),
    @SerializedName("NewToExercise") NEW_TO_EXERCISE("I'm New to Exercise", Icons.Filled.AutoAwesome);
}

// MARK: Q5 — locations (multi). Wire values match the server's WorkoutLocation names.

enum class WorkoutLocationChoice(override val title: String, override val icon: ImageVector, override val photo: Int?) : OnboardingOption {
    @SerializedName("Gym") GYM("Gym / Fitness Center", Icons.Filled.Apartment, R.drawable.onb_loc_gym),
    @SerializedName("Home") HOME("Home", Icons.Filled.Home, R.drawable.onb_loc_home),
    @SerializedName("Outdoors") OUTDOORS("Outdoors", Icons.Filled.Park, R.drawable.onb_loc_outdoors),
    @SerializedName("Studio") STUDIO("Fitness Studio", Icons.Filled.SelfImprovement, R.drawable.onb_loc_studio);
}

// MARK: Q6 — equipment

enum class EquipmentAccess(override val title: String, override val subtitle: String, override val icon: ImageVector, override val photo: Int?) : OnboardingOption {
    @SerializedName("FullGym") FULL_GYM("Full Gym Access", "Access to a fully equipped gym with machines, free weights, and cardio equipment.", Icons.Filled.Apartment, R.drawable.onb_equip_machines),
    @SerializedName("HomeGym") HOME_GYM("Home Gym / Minimal Equipment", "Access to basic fitness equipment at home.", Icons.Filled.FitnessCenter, R.drawable.onb_equip_dumbbells),
    @SerializedName("Bodyweight") BODYWEIGHT("Bodyweight Only", "Prefer workouts requiring little to no equipment.", Icons.Filled.AccessibilityNew, R.drawable.onb_equip_bodyweight);
}

enum class HomeEquipment(override val title: String, override val icon: ImageVector) : OnboardingOption {
    @SerializedName("Dumbbells") DUMBBELLS("Dumbbells", Icons.Filled.FitnessCenter),
    @SerializedName("Kettlebells") KETTLEBELLS("Kettlebells", Icons.Filled.Scale),
    @SerializedName("ResistanceBands") RESISTANCE_BANDS("Resistance Bands", Icons.Filled.Gesture),
    @SerializedName("Bench") BENCH("Weight Bench", Icons.Filled.Weekend),
    @SerializedName("Barbell") BARBELL("Barbell & Weight Plates", Icons.Filled.SportsGymnastics),
    @SerializedName("SquatRack") SQUAT_RACK("Squat Rack", Icons.Filled.GridOn),
    @SerializedName("CableMachine") CABLE_MACHINE("Cable Machine", Icons.Filled.Cable),
    @SerializedName("CardioEquipment") CARDIO_EQUIPMENT("Cardio Equipment", Icons.Filled.DirectionsBike),
    @SerializedName("Other") OTHER("Other", Icons.Filled.MoreHoriz);
}

// MARK: Q7 / Q8 — schedule

enum class WeeklyFrequency(override val title: String, override val icon: ImageVector, val nutritionBand: String) : OnboardingOption {
    @SerializedName("OneToTwo") ONE_TO_TWO("1–2 Days", Icons.Filled.CalendarToday, "OneToTwo"),
    @SerializedName("ThreeToFour") THREE_TO_FOUR("3–4 Days", Icons.Filled.CalendarMonth, "ThreeToFour"),
    @SerializedName("FiveToSix") FIVE_TO_SIX("5–6 Days", Icons.Filled.EventRepeat, "FiveToSix"),
    @SerializedName("EveryDay") EVERY_DAY("Every Day", Icons.Filled.EventAvailable, "SevenPlus");
}

enum class SessionDuration(override val title: String, override val subtitle: String, override val icon: ImageVector) : OnboardingOption {
    @SerializedName("Min15To30") MIN_15_TO_30("15–30 Minutes", "Short workouts for busy schedules.", Icons.Filled.Schedule),
    @SerializedName("Min30To45") MIN_30_TO_45("30–45 Minutes", "Focused workouts with moderate training time.", Icons.Filled.Schedule),
    @SerializedName("Min45To60") MIN_45_TO_60("45–60 Minutes", "Full training sessions with balanced exercise volume.", Icons.Filled.Timer),
    @SerializedName("Min60To90") MIN_60_TO_90("60–90 Minutes", "Longer sessions for additional exercises and conditioning.", Icons.Filled.Timer),
    @SerializedName("Min90Plus") MIN_90_PLUS("90+ Minutes", "Extended training sessions.", Icons.Filled.HourglassBottom);
}

// MARK: Q9 — interests (multi, grouped)

enum class InterestCategory(val title: String, val icon: ImageVector) {
    STRENGTH("Strength & Muscle Building", Icons.Filled.FitnessCenter),
    CARDIO("Cardio & Endurance", Icons.Filled.DirectionsRun),
    FUNCTIONAL("Functional & Athletic Training", Icons.Filled.SportsMartialArts),
    MIND_BODY("Mind-Body & Recovery", Icons.Filled.SelfImprovement),
    COMBAT("Combat & Sport", Icons.Filled.SportsMma),
    SPECIALIZED("Specialized Training", Icons.Filled.Star);

    val interests: List<TrainingInterest> get() = TrainingInterest.entries.filter { it.category == this }
}

enum class TrainingInterest(val title: String, val category: InterestCategory) {
    @SerializedName("Weightlifting") WEIGHTLIFTING("Weightlifting", InterestCategory.STRENGTH),
    @SerializedName("Bodybuilding") BODYBUILDING("Bodybuilding", InterestCategory.STRENGTH),
    @SerializedName("Powerlifting") POWERLIFTING("Powerlifting", InterestCategory.STRENGTH),
    @SerializedName("Running") RUNNING("Running", InterestCategory.CARDIO),
    @SerializedName("Cycling") CYCLING("Cycling", InterestCategory.CARDIO),
    @SerializedName("Swimming") SWIMMING("Swimming", InterestCategory.CARDIO),
    @SerializedName("Rowing") ROWING("Rowing", InterestCategory.CARDIO),
    @SerializedName("FunctionalTraining") FUNCTIONAL_TRAINING("Functional Training", InterestCategory.FUNCTIONAL),
    @SerializedName("HIIT") HIIT("HIIT", InterestCategory.FUNCTIONAL),
    @SerializedName("CircuitTraining") CIRCUIT_TRAINING("Circuit Training", InterestCategory.FUNCTIONAL),
    @SerializedName("SportsPerformance") SPORTS_PERFORMANCE("Sports Performance", InterestCategory.FUNCTIONAL),
    @SerializedName("Yoga") YOGA("Yoga", InterestCategory.MIND_BODY),
    @SerializedName("Pilates") PILATES("Pilates", InterestCategory.MIND_BODY),
    @SerializedName("Mobility") MOBILITY("Mobility", InterestCategory.MIND_BODY),
    @SerializedName("Stretching") STRETCHING("Stretching", InterestCategory.MIND_BODY),
    @SerializedName("Boxing") BOXING("Boxing", InterestCategory.COMBAT),
    @SerializedName("MartialArts") MARTIAL_ARTS("Martial Arts", InterestCategory.COMBAT),
    @SerializedName("Wrestling") WRESTLING("Wrestling", InterestCategory.COMBAT),
    @SerializedName("AdaptiveTraining") ADAPTIVE_TRAINING("Adaptive Training", InterestCategory.SPECIALIZED),
    @SerializedName("SportSpecific") SPORT_SPECIFIC("Sport-Specific Training", InterestCategory.SPECIALIZED),
    @SerializedName("PrenatalPostnatal") PRENATAL_POSTNATAL("Prenatal / Postnatal Fitness", InterestCategory.SPECIALIZED);
}

// MARK: Q10 — about you

enum class MetabolicSexInput(override val title: String, val nutritionSex: String?) : OnboardingOption {
    @SerializedName("Female") FEMALE("Female", "Female"),
    @SerializedName("Male") MALE("Male", "Male"),
    @SerializedName("PreferNotToSay") PREFER_NOT_TO_SAY("Prefer Not to Say", null);

    override val icon: ImageVector get() = Icons.Filled.Person
}

/**
 * PRD Q10 Part B — everyday movement OUTSIDE planned workouts, so planned
 * workout frequency is never double-counted into TDEE. Wire values are the
 * nutrition profile's ActivityLevel names.
 */
enum class DailyActivityLevel(override val title: String, override val subtitle: String, override val icon: ImageVector) : OnboardingOption {
    @SerializedName("Sedentary") SEDENTARY("Sedentary", "I spend most of my day sitting with very little movement.", Icons.Filled.Chair),
    @SerializedName("LightlyActive") LIGHTLY_ACTIVE("Lightly Active", "I spend some time standing or walking, but much of my day is relatively sedentary.", Icons.Filled.DirectionsWalk),
    @SerializedName("ModeratelyActive") MODERATELY_ACTIVE("Moderately Active", "I'm regularly on my feet, walking, or moving throughout the day.", Icons.Filled.Hiking),
    @SerializedName("VeryActive") VERY_ACTIVE("Very Active", "I spend most of my day moving or performing physically demanding activities.", Icons.Filled.DirectionsRun),
    @SerializedName("ExtraActive") EXTRA_ACTIVE("Extremely Active", "I have a physically demanding job or spend much of my day doing strenuous physical activity.", Icons.Filled.Terrain);

    val wire: String get() = when (this) {
        SEDENTARY -> "Sedentary"; LIGHTLY_ACTIVE -> "LightlyActive"; MODERATELY_ACTIVE -> "ModeratelyActive"
        VERY_ACTIVE -> "VeryActive"; EXTRA_ACTIVE -> "ExtraActive"
    }
}

// MARK: Q11 — nutrition goal + conditional branches

enum class NutritionGoalChoice(override val title: String, override val subtitle: String, override val icon: ImageVector) : OnboardingOption {
    @SerializedName("LoseWeight") LOSE_WEIGHT("Lose Weight", "Reduce your body weight while supporting fat loss and maintaining muscle.", Icons.Filled.TrendingDown),
    @SerializedName("BuildMuscleGainWeight") BUILD_MUSCLE_GAIN_WEIGHT("Build Muscle & Gain Weight", "Build lean muscle, increase strength, and support healthy weight gain.", Icons.Filled.TrendingUp),
    @SerializedName("BodyRecomposition") BODY_RECOMPOSITION("Body Recomposition", "Build muscle while reducing body fat, without necessarily changing your overall weight.", Icons.Filled.Autorenew),
    @SerializedName("MaintainWeight") MAINTAIN_WEIGHT("Maintain My Weight", "Maintain your current weight while supporting energy, fitness, and recovery.", Icons.Filled.DragHandle),
    @SerializedName("ImproveHealth") IMPROVE_HEALTH("Improve Overall Health", "Focus on balanced nutrition, daily energy, and long-term wellness.", Icons.Filled.FavoriteBorder);

    /** Goals that change body weight need an adult (PRD §18.9). */
    val changesWeight: Boolean get() = this == LOSE_WEIGHT || this == BUILD_MUSCLE_GAIN_WEIGHT || this == BODY_RECOMPOSITION
}

enum class MuscleFocus(override val title: String, override val subtitle: String, override val icon: ImageVector) : OnboardingOption {
    @SerializedName("GainWeight") GAIN_WEIGHT("Gain Weight & Build Muscle", "Set a target weight and a steady rate of gain.", Icons.Filled.Scale),
    @SerializedName("NoWeightGoal") NO_WEIGHT_GOAL("Build Muscle Without a Weight Goal", "No target weight or date — calories and protein to support muscle growth.", Icons.Filled.FitnessCenter);
}

enum class RecompFocus(override val title: String, override val subtitle: String, override val icon: ImageVector, val wire: String) : OnboardingOption {
    @SerializedName("MaintainWeight") MAINTAIN_WEIGHT("Maintain Weight While Building Muscle", "Eat near maintenance and let training reshape your body.", Icons.Filled.DragHandle, "MaintainWeight"),
    @SerializedName("ReduceFat") REDUCE_FAT("Reduce Body Fat While Building Muscle", "A modest calorie deficit with plenty of protein.", Icons.Filled.SouthEast, "ReduceFat"),
    @SerializedName("TargetWeight") TARGET_WEIGHT("Set a Specific Weight Goal", "Work toward a target weight at a steady pace.", Icons.Filled.TrackChanges, "TargetWeight");
}

// MARK: Q12 — dietary

enum class DietaryPreferenceChoice(override val title: String, override val subtitle: String, override val icon: ImageVector, override val photo: Int?) : OnboardingOption {
    @SerializedName("NoPreference") NO_PREFERENCE("No Preference", "I eat a variety of foods", Icons.Filled.Restaurant, R.drawable.onb_diet_none),
    @SerializedName("HighProtein") HIGH_PROTEIN("High Protein", "Prioritize higher protein intake", Icons.Filled.Bolt, R.drawable.onb_diet_high_protein),
    @SerializedName("Mediterranean") MEDITERRANEAN("Mediterranean", "Whole foods, lean proteins, healthy fats", Icons.Filled.Eco, R.drawable.onb_diet_mediterranean),
    @SerializedName("LowCarb") LOW_CARB("Low Carb", "Lower in carbs, higher in protein and fats", Icons.Filled.TrendingDown, R.drawable.onb_diet_low_carb),
    @SerializedName("Keto") KETO("Keto", "Very low carb, higher in healthy fats", Icons.Filled.WaterDrop, R.drawable.onb_diet_keto),
    @SerializedName("Vegetarian") VEGETARIAN("Vegetarian", "Plant-based, no meat", Icons.Filled.Spa, R.drawable.onb_diet_vegetarian),
    @SerializedName("Vegan") VEGAN("Vegan", "Plant-based, no animal products", Icons.Filled.Grass, R.drawable.onb_diet_vegan),
    @SerializedName("Pescatarian") PESCATARIAN("Pescatarian", "Plant-based with fish and seafood", Icons.Filled.SetMeal, R.drawable.onb_diet_pescatarian),
    @SerializedName("Paleo") PALEO("Paleo", "Whole, minimally processed foods", Icons.Filled.Landscape, R.drawable.onb_diet_paleo);
}

val AllergyChoices = listOf("Peanuts", "Tree Nuts", "Dairy", "Eggs", "Soy", "Wheat / Gluten", "Fish", "Shellfish", "Sesame")

// MARK: - The answers document

/**
 * Everything onboarding collects. Persisted locally (resume) and to
 * PUT /onboarding/me; keys are the server contract. Every constructor
 * parameter has a default so Gson uses the no-arg constructor (defaults
 * survive a sparse document). Unknown enum strings decode as null list
 * entries — read lists through [normalized].
 */
data class OnboardingAnswers(
    val primaryGoal: FitnessGoal? = null,
    val secondaryGoal: FitnessGoal? = null,
    val noSecondaryGoal: Boolean? = null,
    val experience: TrainingExperience? = null,
    val trainingBackground: List<TrainingBackground> = emptyList(),
    val locations: List<WorkoutLocationChoice> = emptyList(),
    val equipmentAccess: EquipmentAccess? = null,
    val equipment: List<HomeEquipment> = emptyList(),
    val weeklyFrequency: WeeklyFrequency? = null,
    val sessionDuration: SessionDuration? = null,
    val interests: List<TrainingInterest> = emptyList(),
    val sex: MetabolicSexInput? = null,
    val age: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    /** "imperial" | "metric" */
    val heightUnit: String? = null,
    /** "lb" | "kg" */
    val weightUnit: String? = null,
    val activityLevel: DailyActivityLevel? = null,
    val nutritionGoal: NutritionGoalChoice? = null,
    val muscleFocus: MuscleFocus? = null,
    val recompFocus: RecompFocus? = null,
    val targetWeightKg: Double? = null,
    val skipTargetWeight: Boolean? = null,
    val weeklyRateKg: Double? = null,
    /** yyyy-MM-dd when the user asked for a target date. */
    val targetDate: String? = null,
    val dietaryPreference: DietaryPreferenceChoice? = null,
    val allergies: List<String> = emptyList(),
    val otherRestrictions: String? = null
) {
    val usesMetricHeight: Boolean get() = heightUnit == "metric"
    val usesKilograms: Boolean get() = weightUnit == "kg"

    /** Strips null list entries (unknown enum values from a newer client) and null lists. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun normalized(): OnboardingAnswers = copy(
        trainingBackground = (trainingBackground ?: emptyList()).filterNotNull(),
        locations = (locations ?: emptyList()).filterNotNull(),
        equipment = (equipment ?: emptyList()).filterNotNull(),
        interests = (interests ?: emptyList()).filterNotNull(),
        allergies = (allergies ?: emptyList()).filterNotNull()
    )
}

// MARK: - Server documents

data class OnboardingProfileResponse(
    val answers: OnboardingAnswers? = null,
    val currentStep: String? = null,
    val nutritionDecision: String? = null,
    val homeExperienceState: String? = null
)

data class SaveOnboardingRequest(val answers: OnboardingAnswers, val currentStep: String?)
data class CompleteOnboardingRequest(val nutritionDecision: String?)
data class HomeExperienceRequest(val state: String)
data class DismissRecommendationRequest(val contentType: String, val contentId: String, val reason: String?)

/** One recommended catalog item (GET /recommendations/me). */
data class RecommendationItem(
    val id: String = "",
    val contentType: String = "",
    val name: String = "",
    val description: String? = null,
    val imageUrl: String? = null,
    val imageId: String? = null,
    val estimatedMinutes: Int? = null,
    val weeks: Int? = null,
    val daysPerWeek: Int? = null,
    val levels: List<String> = emptyList(),
    val location: String? = null,
    val equipment: List<String> = emptyList(),
    val category: String? = null,
    /** "Strong" | "Partial" */
    val matchType: String = "",
    val reasonCodes: List<String> = emptyList(),
    val explanation: String = ""
) {
    val isProgram: Boolean get() = contentType == "Program"
    val isStrongMatch: Boolean get() = matchType == "Strong"
    val levelText: String? get() = levels.firstOrNull()
}

data class RecommendationsResponse(
    /** "Recommendations" | "Standard" */
    val homeState: String = "Standard",
    val onboardingCompleted: Boolean = false,
    /** "Strong" | "Partial" | "None" */
    val matchSummary: String = "None",
    val programs: List<RecommendationItem> = emptyList(),
    val workouts: List<RecommendationItem> = emptyList(),
    val dismissedCount: Int = 0
) {
    val showsFirstRunSection: Boolean get() = homeState == "Recommendations"
}

enum class RecommendationDismissReason(val wire: String, val title: String) {
    NOT_MY_GOAL("NotMyGoal", "Not My Goal"),
    WRONG_EQUIPMENT("WrongEquipment", "Wrong Equipment"),
    TOO_DIFFICULT("TooDifficult", "Too Difficult"),
    TOO_EASY("TooEasy", "Too Easy"),
    TOO_MANY_DAYS("TooManyDays", "Too Many Training Days"),
    TOO_LONG("TooLong", "Workouts Are Too Long"),
    NOT_MY_STYLE("NotMyStyle", "Not My Training Style"),
    OTHER("Other", "Other");
}
