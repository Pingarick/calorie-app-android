package com.calorie.app.data.seed

import com.calorie.app.data.local.AppDatabase
import com.calorie.app.data.local.model.Dish
import com.calorie.app.data.local.model.DishIngredient
import com.calorie.app.data.local.model.Product

/**
 * Стартовый каталог продуктов и блюд. Нужен, чтобы планировщик мог собрать неделю
 * сразу после установки, без сети и без внешних API.
 *
 * Макросы блюд считаются из ингредиентов, а не пишутся руками: так они не разъедутся
 * с составом при правках. Цены — ориентир по Москве, их можно будет менять вручную.
 */
object SeedData {

    private data class DishSeed(
        val id: String,
        val title: String,
        val slots: String,
        val cuisine: String,
        val technique: String,
        val timeMin: Int,
        val ingredients: List<Pair<String, Double>>
    )

    private val products = listOf(
        Product("chicken_breast", "Куриная грудка", 165.0, 31.0, 3.6, 0.0, "мясо", 38.0),
        Product("beef", "Говядина", 175.0, 25.0, 8.0, 0.0, "мясо", 85.0),
        Product("pork", "Свинина", 242.0, 19.0, 18.0, 0.0, "мясо", 55.0),
        Product("salmon", "Лосось", 197.0, 20.0, 12.0, 0.0, "рыба", 130.0),
        Product("cod", "Треска", 78.0, 17.0, 0.6, 0.0, "рыба", 55.0),
        Product("egg", "Яйцо", 143.0, 12.6, 9.5, 0.6, "яйца", 14.0),
        Product("milk", "Молоко 2.5%", 52.0, 2.8, 2.5, 4.7, "молочное", 7.0),
        Product("kefir", "Кефир 1%", 40.0, 3.0, 1.0, 4.0, "молочное", 8.0),
        Product("cottage", "Творог 5%", 121.0, 17.2, 5.0, 1.8, "молочное", 32.0),
        Product("yogurt", "Йогурт 2.5%", 55.0, 3.0, 2.5, 4.1, "молочное", 25.0),
        Product("cheese", "Сыр твёрдый", 356.0, 25.0, 27.0, 2.2, "молочное", 95.0),
        Product("sour_cream", "Сметана 20%", 206.0, 2.8, 20.0, 3.2, "молочное", 30.0),
        Product("butter", "Масло сливочное", 717.0, 0.8, 81.0, 0.4, "жиры", 110.0),
        Product("oil", "Масло растительное", 899.0, 0.0, 99.9, 0.0, "жиры", 13.0),
        Product("buckwheat", "Гречка отварная", 110.0, 4.2, 1.1, 21.3, "крупы", 12.0),
        Product("rice", "Рис отварной", 116.0, 2.6, 0.3, 24.9, "крупы", 13.0),
        Product("oats", "Овсяные хлопья", 352.0, 12.3, 6.1, 59.5, "крупы", 12.0),
        Product("pasta", "Паста отварная", 131.0, 5.0, 1.1, 24.9, "крупы", 13.0),
        Product("lentils", "Чечевица отварная", 116.0, 9.0, 0.4, 20.0, "бобовые", 16.0),
        Product("chickpeas", "Нут отварной", 164.0, 8.9, 2.6, 27.0, "бобовые", 18.0),
        Product("bread", "Хлеб", 265.0, 8.9, 3.3, 49.0, "хлеб", 30.0),
        Product("potato", "Картофель", 82.0, 1.9, 0.1, 16.6, "овощи", 5.0),
        Product("tomato", "Помидор", 18.0, 0.9, 0.2, 3.7, "овощи", 18.0),
        Product("cucumber", "Огурец", 15.0, 0.8, 0.1, 2.8, "овощи", 12.0),
        Product("cabbage", "Капуста", 25.0, 1.3, 0.1, 4.7, "овощи", 4.0),
        Product("carrot", "Морковь", 41.0, 0.9, 0.2, 9.6, "овощи", 5.0),
        Product("onion", "Лук", 40.0, 1.1, 0.1, 9.3, "овощи", 5.0),
        Product("pepper", "Перец болгарский", 26.0, 1.0, 0.3, 6.0, "овощи", 22.0),
        Product("broccoli", "Брокколи", 34.0, 2.8, 0.4, 6.6, "овощи", 45.0),
        Product("spinach", "Шпинат", 23.0, 2.9, 0.4, 2.0, "овощи", 70.0),
        Product("apple", "Яблоко", 47.0, 0.4, 0.4, 9.8, "фрукты", 11.0),
        Product("banana", "Банан", 89.0, 1.1, 0.3, 22.8, "фрукты", 12.0),
        Product("orange", "Апельсин", 43.0, 0.9, 0.1, 9.0, "фрукты", 13.0),
        Product("almond", "Миндаль", 579.0, 21.0, 50.0, 22.0, "орехи", 160.0),
        Product("honey", "Мёд", 304.0, 0.3, 0.0, 82.0, "сладкое", 90.0)
    )

    private val dishes = listOf(
        DishSeed(
            "oat_banana", "Овсянка с бананом и миндалём", "BREAKFAST", "домашняя", "плита", 10,
            listOf("oats" to 60.0, "milk" to 200.0, "banana" to 100.0, "almond" to 10.0)
        ),
        DishSeed(
            "omelette", "Омлет с овощами", "BREAKFAST,LUNCH", "домашняя", "плита", 15,
            listOf("egg" to 150.0, "milk" to 30.0, "tomato" to 80.0, "pepper" to 60.0, "oil" to 5.0)
        ),
        DishSeed(
            "cottage_apple", "Творог с яблоком и мёдом", "BREAKFAST,SNACK", "домашняя", "без готовки", 5,
            listOf("cottage" to 200.0, "apple" to 150.0, "honey" to 15.0)
        ),
        DishSeed(
            "buck_milk", "Гречка с молоком", "BREAKFAST", "домашняя", "плита", 15,
            listOf("buckwheat" to 200.0, "milk" to 150.0, "butter" to 5.0)
        ),
        DishSeed(
            "chicken_buck", "Курица с гречкой и овощами", "LUNCH,DINNER", "домашняя", "плита", 30,
            listOf("chicken_breast" to 150.0, "buckwheat" to 200.0, "carrot" to 80.0, "onion" to 40.0, "oil" to 8.0)
        ),
        DishSeed(
            "rice_veg_egg", "Рис с овощами и яйцом", "LUNCH", "домашняя", "плита", 25,
            listOf("rice" to 200.0, "egg" to 100.0, "pepper" to 100.0, "onion" to 50.0, "oil" to 8.0)
        ),
        DishSeed(
            "pasta_chicken", "Паста с курицей и томатами", "LUNCH,DINNER", "итальянская", "плита", 25,
            listOf("pasta" to 180.0, "chicken_breast" to 120.0, "tomato" to 150.0, "oil" to 8.0)
        ),
        DishSeed(
            "lentil_soup", "Чечевичный суп", "LUNCH", "домашняя", "плита", 35,
            listOf("lentils" to 250.0, "potato" to 150.0, "carrot" to 60.0, "onion" to 50.0, "oil" to 5.0)
        ),
        DishSeed(
            "cod_potato", "Треска с картофелем", "LUNCH", "домашняя", "плита", 30,
            listOf("cod" to 180.0, "potato" to 250.0, "oil" to 8.0, "cucumber" to 80.0)
        ),
        DishSeed(
            "beef_stew", "Говядина тушёная с овощами", "LUNCH,DINNER", "домашняя", "плита", 45,
            listOf("beef" to 150.0, "carrot" to 100.0, "onion" to 80.0, "tomato" to 100.0, "oil" to 8.0)
        ),
        DishSeed(
            "salmon_broccoli", "Лосось с брокколи", "DINNER", "домашняя", "духовка", 25,
            listOf("salmon" to 150.0, "broccoli" to 200.0, "oil" to 5.0)
        ),
        DishSeed(
            "chickpea_stew", "Рагу с нутом и овощами", "DINNER", "домашняя", "плита", 30,
            listOf("chickpeas" to 150.0, "pepper" to 100.0, "tomato" to 150.0, "onion" to 50.0, "oil" to 8.0)
        ),
        DishSeed(
            "chicken_rice", "Курица с рисом и шпинатом", "DINNER", "домашняя", "плита", 25,
            listOf("chicken_breast" to 150.0, "rice" to 180.0, "spinach" to 50.0, "oil" to 8.0)
        ),
        DishSeed(
            "cod_veg", "Треска с овощами", "DINNER", "домашняя", "плита", 25,
            listOf("cod" to 200.0, "broccoli" to 150.0, "carrot" to 80.0, "oil" to 8.0)
        ),
        DishSeed(
            "beef_buck", "Говядина с гречкой", "DINNER", "домашняя", "плита", 40,
            listOf("beef" to 130.0, "buckwheat" to 180.0, "onion" to 50.0, "oil" to 8.0)
        ),
        DishSeed(
            "pasta_veg_cheese", "Паста с овощами и сыром", "DINNER", "итальянская", "плита", 20,
            listOf("pasta" to 160.0, "tomato" to 150.0, "pepper" to 100.0, "cheese" to 30.0, "oil" to 5.0)
        ),
        DishSeed(
            "yogurt_apple", "Йогурт с яблоком", "SNACK", "домашняя", "без готовки", 0,
            listOf("yogurt" to 200.0, "apple" to 100.0)
        ),
        DishSeed(
            "cottage_banana", "Творог с бананом", "SNACK", "домашняя", "без готовки", 0,
            listOf("cottage" to 150.0, "banana" to 100.0)
        ),
        DishSeed(
            "nuts_orange", "Миндаль с апельсином", "SNACK", "домашняя", "без готовки", 0,
            listOf("almond" to 20.0, "orange" to 150.0)
        ),
        DishSeed(
            "kefir_bread", "Кефир с хлебом", "SNACK", "домашняя", "без готовки", 0,
            listOf("kefir" to 250.0, "bread" to 30.0)
        )
    )

    /** Идемпотентно: блюда не дублируются, продукты обновляются всегда (цены могли измениться). */
    suspend fun ensure(db: AppDatabase) {
        db.productDao().insertAll(products)
        if (db.dishDao().count() > 0) return

        val byId = products.associateBy { it.id }
        val dishesToInsert = ArrayList<Dish>(dishes.size)
        val ingredientsToInsert = ArrayList<DishIngredient>()
        for (seed in dishes) {
            var grams = 0.0
            var kcal = 0.0
            var protein = 0.0
            var fat = 0.0
            var carbs = 0.0
            for ((productId, amount) in seed.ingredients) {
                val p = byId[productId] ?: continue
                val f = amount / 100.0
                grams += amount
                kcal += p.kcal100 * f
                protein += p.protein100 * f
                fat += p.fat100 * f
                carbs += p.carbs100 * f
                ingredientsToInsert += DishIngredient(seed.id, productId, amount)
            }
            dishesToInsert += Dish(
                id = seed.id,
                title = seed.title,
                slots = seed.slots,
                cuisine = seed.cuisine,
                technique = seed.technique,
                timeMin = seed.timeMin,
                gramsPerServing = grams,
                kcalPerServing = kcal,
                proteinPerServing = protein,
                fatPerServing = fat,
                carbsPerServing = carbs
            )
        }
        db.dishDao().insertAll(dishesToInsert)
        db.dishDao().insertIngredients(ingredientsToInsert)
    }
}
