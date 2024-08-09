package mealplanner

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.time.DayOfWeek

data class Meal(val id: Int? = null, val category: String, val name: String, val ingredients: Set<String>) {
    override fun toString(): String {
        return buildString {
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

// Validation Regex
val lettersOnlyRegex = "^[A-Za-z ]+$".toRegex()

//DB connection helper
val databaseUtil = DatabaseUtil.INSTANCE

fun main() {
    while (true) {
        println("What would you like to do (add, show, plan, save, exit)?")
        when (readln()) {
            "add" -> addMeal()
            "show" -> showMeal()
            "plan" -> planMeal()
            "save" -> saveShoppingList()
            "exit" -> {
                println("Bye!")
                databaseUtil.close()
                break
            }

            else -> continue
        }
    }
}

fun saveShoppingList() {
    // Check for a stored plan in db
    val ingredientList = databaseUtil.getIngredientListForPlan()
    if (ingredientList.isEmpty()) {
        println("Unable to save. Plan your meals first.")
        return
    }

    // Ask user for file name
    println("Input a filename:")
    val fileName = readln()

    // Process ingredient list to groups with count
    val shoppingList = ingredientList.groupingBy { it }.eachCount()
    //shoppingList.map { "${it.key}  ${it.value.takeIf { count -> count > 1 } ?: "" }" }.forEach(::println)
    val orderedPlan = shoppingList.map { "${it.key} ${if (it.value > 1) "x${it.value}" else ""}" }

    // Print to console
    //orderedPlan.forEach(::println)

    // Save shopping list
    saveListToFile(orderedPlan, fileName)
}

fun saveListToFile(plan: List<String>, fileName: String) {
    val file = File(fileName)
    file.printWriter().use { out ->
        plan.forEach { grocery ->
            out.println(grocery)
        }
    }
    println("Saved!")
}

fun planMeal() {
    // Init weekly plan holder
    val weeklyMealPlan = mutableMapOf<String, Map<String, Meal>>()

    DayOfWeek.values().forEach { dayOfWeek ->
        // Print Day of week
        println(dayOfWeek)

        // Init daily plan holder
        val dailyMealPlan = mutableMapOf<String, Meal>()

        MealCategory.values().forEach { mealCategory ->
            // Show list of stored meals for each category
            val storedMeals = databaseUtil.getMealInfoForCategory(mealCategory.value)
            storedMeals.sortedBy { it.name.lowercase() }.forEach { meal -> println(meal.name) }

            // Ask user to choose meal
            println("Choose the ${mealCategory.value} for $dayOfWeek from the list above:")

            // Accept user input for meal and verify it is stored in the database or not
            var mealName = readln()
            while (storedMeals.count { it.name.equals(mealName, ignoreCase = true) } == 0) {
                println("This meal doesn’t exist. Choose a meal from the list above.")
                mealName = readln()
            }

            // Temporarily hold the selected meals for each category
            val meal: Meal = storedMeals.find { it.name.equals(mealName, ignoreCase = true) }!!
            dailyMealPlan[mealCategory.value] = meal
        }

        weeklyMealPlan[dayOfWeek.name] = dailyMealPlan
        println("Yeah! We planned the meals for $dayOfWeek.")
        println()
    }

    // Print weekly meal plan
    printWeeklyPlan(weeklyMealPlan)

    // Save plan data
    databaseUtil.dropPlanTable()
    databaseUtil.createPlanTable()
    databaseUtil.insertPlan(weeklyMealPlan)
}

fun printWeeklyPlan(weeklyMealPlan: MutableMap<String, Map<String, Meal>>) {
    weeklyMealPlan.forEach { (day, category) ->
        println(day)
        category.map { "${it.key.replaceFirstChar(Char::titlecase)}: ${it.value.name}" }.forEach(::println)
        println()
    }
}

private fun showMeal() {
    println("Which category do you want to print (breakfast, lunch, dinner)?")

    // Read meal category
    val mealCategory = readMealCategory()

    // Fetch meals for category
    val dailyMeals = databaseUtil.getMealInfoForCategory(mealCategory)
    //databaseUtil.getAllMealInfo()

    with(dailyMeals) {
        if (isNotEmpty()) {
            println("Category: $mealCategory")
            println()
        }
        forEach(::println)
        ifEmpty { println("No meals found.") }
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
    val meal = Meal(category = mealCategory, name = mealName, ingredients = ingredientsList)

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
        crateMealTables()
        createPlanTable()
    }

    private fun crateMealTables() {
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

    fun getAllMealInfo(): MutableList<Meal> {
        val dailyMeals = mutableListOf<Meal>()
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
                val meal = Meal(category = mealCategory, name = mealName, ingredients = ingredientsList)
                dailyMeals.add(meal)
            }
        } catch (ex: Exception) {
            println("Error getting meal info from db: ${ex.message}")
        }

        return dailyMeals
    }

    fun getMealInfoForCategory(mealCategory: String): MutableList<Meal> {
        val dailyMeals = mutableListOf<Meal>()
        try {
            val query = """ 
            SELECT meals.meal_id, meals.category, meals.meal, GROUP_CONCAT(ingredients.ingredient, ', ') AS ingredient_list 
            FROM meals, ingredients
            WHERE meals.category = '$mealCategory' AND meals.meal_id = ingredients.meal_id 
            GROUP BY meals.meal_id
        """.trimIndent()

            val rs = statement.executeQuery(query)
            while (rs.next()) {
                val mealId = rs.getInt("meal_id")
                val category = rs.getString("category")
                val mealName = rs.getString("meal")
                val ingredientsList = rs.getString("ingredient_list").split(",").map { it.trim() }.toMutableSet()
                val meal = Meal(id = mealId, category = category, name = mealName, ingredients = ingredientsList)
                dailyMeals.add(meal)
            }
        } catch (ex: Exception) {
            println("Error getting meal info from db: ${ex.message}")
        }

        return dailyMeals
    }

    fun createPlanTable() {
        val createPlanQuery = """
            CREATE TABLE IF NOT EXISTS plan (
            day INT,
            meal TEXT NOT NULL, 
            category TEXT NOT NULL,
            meal_id INT NOT NULL,
            FOREIGN KEY(meal_id) REFERENCES meals(meal_id) 
            )
        """.trimIndent()

        try {
            statement.executeUpdate(createPlanQuery)

        } catch (ex: Exception) {
            println("Error creating plan table: ${ex.message}")
        }
    }

    fun insertPlan(plans: Map<String, Map<String, Meal>>) {
        try {
            plans.forEach { plan ->
                plan.value.forEach { (category, meal) ->
                    val query = """
                    INSERT INTO plan (day, meal, category, meal_id)
                    VALUES(${DayOfWeek.valueOf(plan.key).value},'${meal.name}', '$category', ${meal.id})
                    """.trimIndent()

                    statement.executeUpdate(query)
                }
            }
        } catch (ex: Exception) {
            println("Error inserting meal: ${ex.message}")
        }
    }

    fun dropPlanTable() {
        val dropTableQuery = """
            DROP TABLE IF EXISTS plan;
        """.trimIndent()

        try {
            statement.executeUpdate(dropTableQuery)
        } catch (ex: Exception) {
            println("Error deleting plan table: ${ex.message}")
        }
    }

    fun getIngredientListForPlan(): MutableList<String> {
        val ingredientList = mutableListOf<String>()

        try {
            val query = """ 
            SELECT plan.meal_id, GROUP_CONCAT(ingredients.ingredient, ', ') AS ingredient_list 
            FROM plan, ingredients
            WHERE plan.meal_id = ingredients.meal_id 
            GROUP BY plan.meal_id
        """.trimIndent()

            val rs = statement.executeQuery(query)
            while (rs.next()) {
                val ingList = rs.getString("ingredient_list").split(",").map { it.trim() }
                ingredientList.addAll(ingList)
            }
        } catch (ex: Exception) {
            println("Error getting meal info from db: ${ex.message}")
        }
        return ingredientList
    }

    fun close() {
        statement.close()
        connection.close()
    }
}