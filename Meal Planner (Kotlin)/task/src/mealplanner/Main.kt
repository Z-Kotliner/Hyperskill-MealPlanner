package mealplanner

import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

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

//DB connection helper
val databaseUtil = DatabaseUtil.INSTANCE

fun main() {
    while (true) {
        println("What would you like to do (add, show, exit)?")
        when (readln()) {
            "add" -> addMeal()
            "show" -> showMeal()
            "exit" -> {
                println("Bye!")
                databaseUtil.close()
                break
            }

            else -> continue
        }
    }
}

private fun showMeal() {
    databaseUtil.getAllMealInfo()
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

    //Add meal to Meals db
    val mealId = databaseUtil.insertMeal(meal)
    if (mealId != null) {
        databaseUtil.insertIngredient(ingredientsList, mealId)
        println("The meal has been added!")
    }
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

enum class DatabaseUtil {
    INSTANCE;

    private var connection: Connection = DriverManager.getConnection("jdbc:sqlite:meals.db")
    private val statement: Statement = connection.createStatement()

    init {
        crateTables()
    }

    private fun crateTables() {
        val createMealQuery = """
            CREATE TABLE IF NOT EXISTS meals (
            meal_id INTEGER PRIMARY KEY AUTOINCREMENT,
            meal TEXT NOT NULL, 
            category TEXT NOT NULL
            )
        """.trimIndent()

        val createIngredientQuery = """
            CREATE TABLE IF NOT EXISTS ingredients (
            ingredient_id INTEGER PRIMARY KEY AUTOINCREMENT,
            ingredient TEXT NOT NULL,
            meal_id  INTEGER,
            FOREIGN KEY(meal_id) REFERENCES meals(meal_id) 
            )
        """.trimIndent()

        try {
            statement.executeUpdate(createMealQuery)
            statement.executeUpdate(createIngredientQuery)
        } catch (ex: Exception) {
            println("Error creating tables: ${ex.message}")
        }
    }

    fun insertMeal(meal: Meal): Int? {
        return try {
            val query = """
            INSERT INTO meals (meal, category)
            VALUES('${meal.name}','${meal.category}')
        """.trimIndent()
            statement.executeUpdate(query)

            //val insertedMealIdQuery = "SELECT last_insert_rowid()"
            val insertedMealIdQuery = "SELECT meal_id FROM meals ORDER BY meal_id DESC limit 1"
            val rs = statement.executeQuery(insertedMealIdQuery)
            rs.getInt("meal_id")
        } catch (ex: Exception) {
            println("Error inserting meal: ${ex.message}")
            null
        }
    }

    fun insertIngredient(ingredientList: Set<String>, mealId: Int) {
        try {
            ingredientList.forEach { ingredient ->
                val query = """
            INSERT INTO ingredients (ingredient, meal_id)
            VALUES('$ingredient', $mealId)
        """.trimIndent()

                statement.executeUpdate(query)
            }
        } catch (ex: Exception) {
            println("Error inserting meal: ${ex.message}")
        }
    }

    fun getAllMealInfo() {
        try {
            val query = """ 
            SELECT meals.category, meals.meal, GROUP_CONCAT(ingredients.ingredient, ', ') AS ingredient_list 
            FROM meals, ingredients
            WHERE meals.meal_id = ingredients.meal_id
            GROUP BY meals.meal_id
        """.trimIndent()

            val rs = statement.executeQuery(query)
            while (rs.next()) {
                val mealCategory = rs.getString("category")
                val mealName = rs.getString("meal")
                val ingredientsList = rs.getString("ingredient_list").split(",").map { it.trim() }.toMutableSet()
                val meal = Meal(mealCategory, mealName, ingredientsList)
                dailyMeals.add(meal)
            }
        } catch (ex: Exception) {
            println("Error getting meal info from db: ${ex.message}")
        }
    }

    fun close() {
        statement.close()
        connection.close()
    }
}