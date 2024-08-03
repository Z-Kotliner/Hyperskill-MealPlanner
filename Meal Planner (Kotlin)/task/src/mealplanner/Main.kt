package mealplanner

data class Meal(val category: String, val name: String, val ingredients: Set<String>)

fun main() {
    // Ask about the meal category
    println("Which meal do you want to add (breakfast, lunch, dinner)?")

    // Accept user category
    val mealCategory = readln()

    // Accept user meal name
    println("Input the meal's name:")
    val mealName = readln()

    // Accept ingredients list
    println("Input the ingredients:")
    val ingredientsList = readln().split(",").map { it.trim() }.toSet()

    // Create the meal with input info
    val meal = Meal(mealCategory, mealName, ingredientsList)

    // Output Meal info.
    println("Category: ${meal.category}")
    println("Name: ${meal.name}")
    println("Ingredients:")
    meal.ingredients.forEach { ingredient -> println(ingredient) }
    println("The meal has been added!")

}