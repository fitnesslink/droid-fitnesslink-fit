package com.fitnesslink.fit.model

// Android port of the iOS citations in `NutritionSourcesView.swift`: every
// health calculation in Nutrition has a source a user can open.
//
// The math lives in the backend's `NutritionTargetCalculator` and
// `MicroNutrients`. If a formula, cap, floor or Daily Value changes there, the
// copy here has to change with it. Each citation was checked against the source
// text; describe only what a source actually says, and label FitnessLink's own
// choices as ours rather than implying a source backs them.

/** One published source, shown inline as [label] and in full as [reference]. */
enum class NutritionCitation(val label: String, val reference: String, val url: String) {
    MIFFLIN_1990(
        "Mifflin et al., 1990",
        "Mifflin MD, St Jeor ST, Hill LA, et al. A new predictive equation for resting energy expenditure in healthy individuals. Am J Clin Nutr. 1990;51(2):241-247.",
        "https://pubmed.ncbi.nlm.nih.gov/2305711/"
    ),
    FRANKENFIELD_2005(
        "Frankenfield et al., 2005",
        "Frankenfield D, Roth-Yousey L, Compher C. Comparison of predictive equations for resting metabolic rate in healthy nonobese and obese adults: a systematic review. J Am Diet Assoc. 2005;105(5):775-789.",
        "https://pubmed.ncbi.nlm.nih.gov/15883556/"
    ),
    IOM_2005(
        "Institute of Medicine, 2005",
        "Institute of Medicine. Dietary Reference Intakes for Energy, Carbohydrate, Fiber, Fat, Fatty Acids, Cholesterol, Protein, and Amino Acids. National Academies Press; 2005.",
        "https://nap.nationalacademies.org/catalog/10490"
    ),
    CDC_LOSING_WEIGHT(
        "CDC: Steps for Losing Weight",
        "Centers for Disease Control and Prevention. Steps for Losing Weight.",
        "https://www.cdc.gov/healthy-weight-growth/losing-weight/index.html"
    ),
    JENSEN_2013(
        "AHA/ACC/TOS Guideline, 2013",
        "Jensen MD, Ryan DH, et al. 2013 AHA/ACC/TOS guideline for the management of overweight and obesity in adults. Circulation. 2014;129(25 Suppl 2):S102-S138.",
        "https://pubmed.ncbi.nlm.nih.gov/24222017/"
    ),
    ARAGON_2017(
        "Aragon et al. (ISSN), 2017",
        "Aragon AA, Schoenfeld BJ, et al. International Society of Sports Nutrition position stand: diets and body composition. J Int Soc Sports Nutr. 2017;14:16.",
        "https://pubmed.ncbi.nlm.nih.gov/28630601/"
    ),
    JAGER_2017(
        "Jäger et al. (ISSN), 2017",
        "Jäger R, Kerksick CM, Campbell BI, et al. International Society of Sports Nutrition position stand: protein and exercise. J Int Soc Sports Nutr. 2017;14:20.",
        "https://pubmed.ncbi.nlm.nih.gov/28642676/"
    ),
    WISHNOFSKY_1958(
        "Wishnofsky, 1958",
        "Wishnofsky M. Caloric equivalents of gained or lost weight. Am J Clin Nutr. 1958;6(5):542-546.",
        "https://pubmed.ncbi.nlm.nih.gov/13594881/"
    ),
    HALL_2011(
        "Hall et al., 2011",
        "Hall KD, Sacks G, Chandramohan D, et al. Quantification of the effect of energy imbalance on bodyweight. Lancet. 2011;378(9793):826-837.",
        "https://pubmed.ncbi.nlm.nih.gov/21872751/"
    ),
    NIDDK_PLANNER(
        "NIDDK Body Weight Planner",
        "National Institute of Diabetes and Digestive and Kidney Diseases. Body Weight Planner.",
        "https://www.niddk.nih.gov/bwp"
    ),
    FDA_DAILY_VALUE(
        "FDA Daily Values",
        "U.S. Food and Drug Administration. Daily Value on the Nutrition and Supplement Facts Labels.",
        "https://www.fda.gov/food/nutrition-facts-label/daily-value-nutrition-and-supplement-facts-labels"
    ),
    DIETARY_GUIDELINES(
        "Dietary Guidelines, 2025-2030",
        "U.S. Department of Agriculture and U.S. Department of Health and Human Services. Dietary Guidelines for Americans, 2025-2030.",
        "https://www.dietaryguidelines.gov/"
    ),
    IOM_WATER_2005(
        "Institute of Medicine: Water, 2005",
        "Institute of Medicine. Dietary Reference Intakes for Water, Potassium, Sodium, Chloride, and Sulfate. National Academies Press; 2005.",
        "https://nap.nationalacademies.org/catalog/10925"
    ),
    USDA_FOOD_DATA(
        "USDA FoodData Central",
        "U.S. Department of Agriculture. FoodData Central.",
        "https://fdc.nal.usda.gov/"
    ),
    OPEN_FOOD_FACTS(
        "Open Food Facts",
        "Open Food Facts. Open database of food products.",
        "https://world.openfoodfacts.org/"
    );

    companion object {
        /**
         * The sources behind one "Why this number?" explanation, keyed by the
         * backend's stable `WhyKeys`. A manual override has none: the user chose
         * that number, so there is no calculation to cite.
         */
        fun forWhyKey(key: String): List<NutritionCitation> = when (key) {
            "why.bmr.mifflinStJeor" -> listOf(MIFFLIN_1990, FRANKENFIELD_2005)
            "why.maintenance.activityMultiplier", "why.target.maintenance" -> listOf(IOM_2005)
            "why.target.deficit" -> listOf(CDC_LOSING_WEIGHT, JENSEN_2013)
            "why.target.surplus" -> listOf(ARAGON_2017)
            "why.macro.protein" -> listOf(IOM_2005, JAGER_2017, DIETARY_GUIDELINES)
            "why.macro.carbs", "why.macro.fat" -> listOf(IOM_2005)
            "why.weeklyRate.projected" -> listOf(WISHNOFSKY_1958, HALL_2011, CDC_LOSING_WEIGHT)
            "why.completion.projected" -> listOf(HALL_2011, NIDDK_PLANNER)
            else -> emptyList()
        }
    }
}

/** One calculation on the Sources screen: what we do, then what it rests on. */
data class NutritionMethod(
    val title: String,
    val explanation: String,
    val citations: List<NutritionCitation>
) {
    companion object {
        val all: List<NutritionMethod> = listOf(
            NutritionMethod(
                "Resting calories (BMR)",
                "Estimated with the Mifflin-St Jeor equation: 10 × weight (kg) + 6.25 × height (cm) − 5 × age (years), then + 5 for men or − 161 for women. A systematic review found it more likely than the other equations tested to land within 10% of measured resting metabolic rate, though errors can be noteworthy for an individual.",
                listOf(NutritionCitation.MIFFLIN_1990, NutritionCitation.FRANKENFIELD_2005)
            ),
            NutritionMethod(
                "Maintenance calories",
                "Your BMR multiplied by an activity factor: 1.2 sedentary, 1.375 lightly active, 1.55 moderately active, 1.725 very active, or 1.9 extra active. These are conventional estimating factors rather than results from a single study. The Institute of Medicine report covers how physical activity affects daily energy needs.",
                listOf(NutritionCitation.IOM_2005)
            ),
            NutritionMethod(
                "Daily calorie target",
                "Weight-loss plans subtract up to 1,000 calories a day, about 2 lb per week; the CDC notes that people who lose about 1 to 2 lb a week are more likely to keep it off. Targets never go below 1,200 calories for women or 1,500 for men, the low end of the calorie ranges in the AHA/ACC/TOS guideline for adults with overweight or obesity. Weight-gain plans add at most 300 to 500 calories a day. That limit is FitnessLink's choice; the ISSN position stand notes that the size of a surplus can influence the nature of the gains.",
                listOf(NutritionCitation.CDC_LOSING_WEIGHT, NutritionCitation.JENSEN_2013, NutritionCitation.ARAGON_2017)
            ),
            NutritionMethod(
                "Weekly rate and estimated date",
                "Projected using the rule of thumb that about 7,700 calories equal 1 kg of body weight (about 3,500 per lb). Real results often differ, because the body adapts and weight changes slowly as intake changes. For a more detailed model, see the NIH's Body Weight Planner.",
                listOf(NutritionCitation.WISHNOFSKY_1958, NutritionCitation.HALL_2011, NutritionCitation.NIDDK_PLANNER)
            ),
            NutritionMethod(
                "Weight-loss goal pace",
                "When you set a weight-loss goal, FitnessLink suggests a timeframe using about 0.5% of your body weight per week, kept between 0.5 and 1.5 lb a week. It also notes when your chosen date would mean losing more than about 1% of your body weight per week. These percentages are FitnessLink's own guideline. For reference, the CDC notes that people who lose about 1 to 2 lb a week are more likely to keep it off.",
                listOf(NutritionCitation.CDC_LOSING_WEIGHT)
            ),
            NutritionMethod(
                "Protein, carbs and fat",
                "Grams come from your calorie target at 4 calories per gram of protein or carbs and 9 per gram of fat. The percentage split depends on your goal and is FitnessLink's general starting point, not a prescription. For reference, the Institute of Medicine's ranges for adults are 45-65% carbs, 20-35% fat and 10-35% protein, and some FitnessLink plans use more protein or fewer carbs than these. The Dietary Guidelines list 1.2-1.6 g of protein per kg of body weight a day, and the ISSN considers 1.4-2.0 g per kg sufficient for most people who exercise. You can set your own targets at any time.",
                listOf(NutritionCitation.IOM_2005, NutritionCitation.DIETARY_GUIDELINES, NutritionCitation.JAGER_2017)
            ),
            NutritionMethod(
                "Micronutrient targets",
                "FDA Daily Values, which are based on a 2,000-calorie diet: fiber 28 g, sodium 2,300 mg, saturated fat 20 g and cholesterol 300 mg. Sugars have no target because the FDA's 50 g Daily Value applies to added sugars, while FitnessLink tracks total sugars.",
                listOf(NutritionCitation.FDA_DAILY_VALUE)
            ),
            NutritionMethod(
                "Water goal",
                "If you haven't set a goal, FitnessLink starts you at 64 fl oz a day. That is a round starting point we chose, not a medical recommendation, and needs vary with activity, climate and health. For reference, the Institute of Medicine's adequate intake for total water, including food and all beverages, is about 3.7 L a day for men and 2.7 L for women.",
                listOf(NutritionCitation.IOM_WATER_2005)
            ),
            NutritionMethod(
                "Food data",
                "Catalog foods come from USDA FoodData Central, and scanned barcodes may come from Open Food Facts. AI estimates from a photo or description are approximate, so check them before logging.",
                listOf(NutritionCitation.USDA_FOOD_DATA, NutritionCitation.OPEN_FOOD_FACTS)
            ),
        )
    }
}
