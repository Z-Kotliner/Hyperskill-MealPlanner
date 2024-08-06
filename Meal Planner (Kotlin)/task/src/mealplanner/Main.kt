package mealplanner

data class Meal(val category: String, val name: String, val ingredients: Set<String>) {
    override fun toString(): String {
        return buildString {
            appendLine("Category: $category")
            appendLine("Name: $name")
            appendLine("Ingredients:")
            ingredients.forEach(::appendLine)
        }
    }
}

enum class MealCategory(val value: String) {
    BREAKFAST("breakfast"),
    LUNCH("lunch"),
    DINNER("dinner")
}

private val dailyMeals = mutableListOf<Meal>()

// Validation Regex
val lettersOnlyRegex = "^[A-Za-z ]+$".toRegex()

fun main() {
    while (true) {
        println("What would you like to do (add, show, exit)?")
        when (readln()) {
            "add" -> addMeal()
            "show" -> showMeal()
            "exit" -> {
                println("Bye!")
                break
            }

            else -> continue
        }
    }
}

private fun showMeal() {
    println()
    with(dailyMeals) {
        forEach(::println)
        ifEmpty { println("No meals saved. Add a meal first.") }
    }
}

private fun addMeal() {
    // Ask about the meal category
    println("Which meal do you want to add (breakfast, lunch, dinner)?")

    // Accept user meal category
    val mealCategory = readMealCategory()

    // Accept user meal name + validate
    println("Input the meal's name:")
    val mealName = readMealName()

    // Accept ingredients list
    println("Input the ingredients:")
    val ingredientsList: MutableSet<String> = readIngredientList()

    // Create the meal with input info
    val meal = Meal(mealCategory, mealName, ingredientsList)

    //Add meal to Meal-list
    dailyMeals.add(meal)

    println("The meal has been added!")
}

private fun readMealCategory(): String {
    val mealCategory = readln()
    while (MealCategory.values().none { it.value == mealCategory }) {
        println("Wrong meal category! Choose from: breakfast, lunch, dinner.")
        return readMealCategory()
    }
    return mealCategory
}

private fun readMealName(): String {
    val mealName = readln()
    while (!mealName.matches(lettersOnlyRegex)) {
        println("Wrong format. Use letters only!")
        return readMealName()
    }
    return mealName
}

private fun readIngredientList(): MutableSet<String> {
    val ingredientsList: MutableSet<String> = readln().split(",").map { it.trim() }.toMutableSet()
    while (true) {
        if (ingredientsList.all { it.matches(lettersOnlyRegex) }) break
        println("Wrong format. Use letters only!")
        return readIngredientList()
    }
    return ingredientsList
}