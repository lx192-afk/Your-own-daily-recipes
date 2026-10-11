package com.mealmenu;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class RecipeCatalog {

    private static final List<Recipe> RECIPES = List.of(
            r("pancakes", "Buttermilk Pancakes", 25, "Soft pancakes with crisp golden edges.", "Breakfast|Sweet|Mild|Hot|Comforting|Vegetarian", "Flour|Buttermilk|Egg|Butter|Maple syrup", "Whisk flour, buttermilk, egg, and melted butter until just combined.", "Cook small portions on a buttered skillet until bubbles form, then flip.", "Serve warm with maple syrup."),
            r("french-toast", "French Toast", 20, "Custardy bread cooked until golden.", "Breakfast|Sweet|Mild|Hot|Comforting|Vegetarian", "Bread|Egg|Milk|Cinnamon|Maple syrup", "Whisk egg, milk, and cinnamon in a shallow bowl.", "Dip bread briefly on both sides and cook in a buttered pan until golden.", "Serve with maple syrup and fruit."),
            r("breakfast-burrito", "Breakfast Burrito", 25, "A filling tortilla with eggs, potatoes, and cheese.", "Breakfast|Hearty|Hot|Comforting|High-Protein", "Egg|Tortilla|Potato|Cheddar|Salsa", "Cook diced potatoes in a skillet until tender and browned.", "Scramble the eggs, then add cheese until melted.", "Fill a warm tortilla with eggs, potatoes, and salsa; fold and serve."),
            r("avocado-toast", "Avocado Toast", 10, "Crisp toast topped with seasoned avocado.", "Breakfast|Lunch|Light|Mild|Hot|Fresh|Vegetarian|Dairy-Free", "Bread|Avocado|Lemon|Chili flakes|Salt", "Toast the bread until crisp.", "Mash avocado with lemon juice and salt.", "Spread over toast and finish with chili flakes."),
            r("eggs-benedict", "Eggs Benedict", 35, "Poached eggs and Canadian bacon on toasted English muffins.", "Breakfast|Hearty|Hot|Comforting|High-Protein", "English muffin|Egg|Canadian bacon|Butter|Lemon", "Toast the English muffin halves and warm the Canadian bacon.", "Poach the eggs until the whites are set and yolks remain soft.", "Top each muffin with bacon, an egg, and warm hollandaise sauce."),
            r("spinach-omelet", "Spinach and Mushroom Omelet", 15, "A quick omelet filled with vegetables and cheese.", "Breakfast|Light|Hot|Mild|Quick|Vegetarian|Gluten-Free|High-Protein|Low-Carb", "Egg|Spinach|Mushroom|Cheddar|Olive oil", "Cook sliced mushrooms and spinach in a lightly oiled skillet.", "Pour in beaten eggs and cook gently until nearly set.", "Add cheddar, fold the omelet, and cook until the cheese melts."),
            r("blt", "BLT Sandwich", 20, "Bacon, lettuce, and tomato on toasted bread.", "Lunch|Hearty|Hot|Mild|Comforting", "Bacon|Bread|Lettuce|Tomato|Mayonnaise", "Cook bacon until crisp and drain on a paper towel.", "Toast the bread and spread with mayonnaise.", "Layer bacon, lettuce, and tomato between the slices."),
            r("grilled-cheese", "Grilled Cheese Sandwich", 15, "A crisp sandwich with a melted cheese center.", "Lunch|Hearty|Hot|Mild|Comforting|Vegetarian", "Bread|Cheddar|Butter", "Butter one side of each bread slice.", "Place cheese between the unbuttered sides.", "Cook in a skillet over medium-low heat, turning once, until crisp and melted."),
            r("chicken-caesar", "Chicken Caesar Salad", 25, "Romaine, grilled chicken, croutons, and Caesar dressing.", "Lunch|Cold|Light|Mild|Fresh|High-Protein", "Chicken breast|Romaine|Croutons|Parmesan|Caesar dressing", "Season and cook the chicken until fully cooked, then let it rest.", "Toss chopped romaine with Caesar dressing and croutons.", "Slice the chicken over the salad and finish with Parmesan."),
            r("cobb-salad", "Cobb Salad", 25, "A colorful salad with chicken, egg, avocado, and bacon.", "Lunch|Cold|Light|Mild|Fresh|High-Protein|Gluten-Free", "Chicken breast|Egg|Avocado|Bacon|Romaine|Tomato", "Cook the chicken, egg, and bacon; cool them slightly.", "Arrange chopped romaine in a bowl and add rows of the toppings.", "Dress lightly with vinaigrette and serve."),
            r("turkey-club", "Turkey Club Sandwich", 20, "A layered turkey sandwich with bacon and fresh vegetables.", "Lunch|Cold|Hearty|Mild|Quick|High-Protein", "Turkey|Bread|Bacon|Lettuce|Tomato|Mayonnaise", "Toast three slices of bread and cook the bacon until crisp.", "Spread mayonnaise on the toast and layer turkey, bacon, lettuce, and tomato.", "Stack, secure with picks, and cut into quarters."),
            r("tuna-melt", "Tuna Melt", 18, "Warm tuna salad and melted cheese on toasted bread.", "Lunch|Hearty|Hot|Mild|Comforting|High-Protein", "Canned tuna|Mayonnaise|Celery|Bread|Cheddar", "Mix drained tuna with mayonnaise and chopped celery.", "Spoon onto bread and top with cheddar.", "Broil or pan-toast until the bread is crisp and cheese is melted."),
            r("tomato-soup", "Tomato Soup and Grilled Cheese", 25, "A familiar comfort-food pairing for a chilly day.", "Lunch|Dinner|Hot|Sour|Mild|Comforting|Vegetarian", "Tomato|Vegetable broth|Cream|Bread|Cheddar", "Simmer tomatoes and vegetable broth until soft, then blend carefully.", "Stir in a splash of cream and season the soup.", "Make a grilled cheese sandwich and serve alongside the soup."),
            r("peanut-butter-banana", "Peanut Butter Banana Sandwich", 7, "A quick sweet-and-savory sandwich.", "Breakfast|Lunch|Late-night snack|Cold|Sweet|Hearty|Quick|Vegetarian", "Bread|Peanut butter|Banana|Honey", "Spread peanut butter over one or two slices of bread.", "Add banana slices and a small drizzle of honey.", "Close the sandwich or serve open-faced."),
            r("mac-cheese", "Baked Mac and Cheese", 45, "Creamy macaroni with a crisp cheese topping.", "Dinner|Hearty|Hot|Mild|Comforting|Vegetarian", "Elbow macaroni|Cheddar|Milk|Butter|Breadcrumbs", "Boil macaroni until just tender and drain.", "Make a cheese sauce with butter, milk, and cheddar; stir in the pasta.", "Top with breadcrumbs and bake until bubbling and golden."),
            r("cheeseburger", "Classic Cheeseburger", 25, "A seared beef patty with cheese and crisp toppings.", "Lunch|Dinner|Hearty|Hot|Mild|Comforting|High-Protein", "Ground beef|Burger bun|Cheddar|Lettuce|Tomato|Pickle", "Shape beef into patties and season with salt and pepper.", "Sear in a hot skillet or grill until cooked to your preferred safe doneness; melt cheese on top.", "Serve on buns with lettuce, tomato, and pickles."),
            r("pulled-pork", "BBQ Pulled Pork Sandwich", 35, "A quick version of smoky pulled pork on a soft bun.", "Lunch|Dinner|Hearty|Hot|Sweet|Comforting|Dairy-Free", "Cooked pulled pork|Barbecue sauce|Bun|Coleslaw", "Warm pulled pork with barbecue sauce in a covered pan.", "Toast the buns lightly.", "Pile pork onto buns and add coleslaw."),
            r("chicken-pot-pie", "Chicken Pot Pie", 60, "Chicken and vegetables under a flaky golden crust.", "Dinner|Hearty|Hot|Mild|Comforting", "Chicken|Carrot|Peas|Broth|Pie crust", "Cook diced chicken and vegetables until nearly tender.", "Stir in a thickened broth sauce and transfer to a baking dish.", "Cover with pastry and bake until the filling bubbles and crust is golden."),
            r("beef-chili", "Beef and Bean Chili", 50, "A warming bowl of beef, beans, tomato, and chili spices.", "Lunch|Dinner|Hot|Spicy|Hearty|Cozy|High-Protein|Dairy-Free|Gluten-Free", "Ground beef|Kidney beans|Tomato|Onion|Chili powder", "Brown beef with diced onion in a heavy pot.", "Add tomatoes, beans, chili powder, and a little broth.", "Simmer until thick, then serve with your favorite toppings."),
            r("spaghetti-meatballs", "Spaghetti and Meatballs", 40, "Pasta with tender meatballs and tomato sauce.", "Dinner|Hot|Hearty|Mild|Comforting|High-Protein", "Spaghetti|Ground beef|Breadcrumbs|Egg|Tomato sauce", "Mix beef, breadcrumbs, egg, and seasoning; shape into meatballs.", "Brown the meatballs and simmer them in tomato sauce until cooked through.", "Boil spaghetti and serve with sauce and meatballs."),
            r("chicken-alfredo", "Chicken Alfredo", 35, "Chicken and fettuccine in a creamy Parmesan sauce.", "Dinner|Hot|Hearty|Mild|Comforting|High-Protein", "Chicken breast|Fettuccine|Cream|Parmesan|Garlic", "Cook fettuccine and reserve a little pasta water.", "Sear chicken until fully cooked, then slice it.", "Warm cream, garlic, and Parmesan; toss with pasta and chicken."),
            r("shrimp-tacos", "Shrimp Tacos", 25, "Seasoned shrimp with crunchy slaw in warm tortillas.", "Lunch|Dinner|Hot|Spicy|Light|Fresh|Dairy-Free|Sour", "Shrimp|Tortilla|Cabbage|Lime|Chili powder", "Season shrimp with chili powder, salt, and lime.", "Cook shrimp in a hot skillet until opaque and curled.", "Fill warm tortillas with shrimp and cabbage slaw."),
            r("baked-salmon", "Lemon Baked Salmon", 25, "A simple salmon fillet with lemon and herbs.", "Dinner|Hot|Light|Sour|Fresh|High-Protein|Low-Carb|Gluten-Free", "Salmon|Lemon|Dill|Olive oil|Green beans", "Heat the oven and place salmon on a lined baking tray.", "Season with lemon, dill, olive oil, salt, and pepper.", "Bake until the salmon flakes easily and serve with vegetables."),
            r("meatloaf", "Classic Meatloaf", 70, "A savory baked loaf with a tangy tomato glaze.", "Dinner|Hot|Hearty|Sweet|Comforting|High-Protein", "Ground beef|Breadcrumbs|Egg|Onion|Ketchup", "Mix beef with breadcrumbs, egg, and finely chopped onion.", "Shape into a loaf and brush with a ketchup-based glaze.", "Bake until cooked through, rest briefly, and slice."),
            r("steak-potatoes", "Steak and Roasted Potatoes", 45, "Pan-seared steak with crisp rosemary potatoes.", "Dinner|Hot|Hearty|Mild|Celebratory|High-Protein|Gluten-Free", "Steak|Potato|Rosemary|Garlic|Olive oil", "Cut potatoes into wedges, season, and roast until tender and browned.", "Pat steak dry, season, and sear in a hot pan to your preferred safe doneness.", "Rest the steak before slicing and serve with potatoes."),
            r("buffalo-wings", "Buffalo Chicken Wings", 45, "Crisp wings coated in a tangy, spicy sauce.", "Dinner|Late-night snack|Hot|Spicy|Hearty|Celebratory|Gluten-Free", "Chicken wings|Hot sauce|Butter|Celery|Blue cheese dressing", "Pat wings dry, season, and bake or air-fry until crisp and fully cooked.", "Warm hot sauce with butter and toss the cooked wings in the sauce.", "Serve with celery and dressing."),
            r("veggie-stir-fry", "Vegetable Stir-Fry", 20, "Colorful vegetables quickly cooked in a savory sauce.", "Lunch|Dinner|Hot|Light|Mild|Quick|Fresh|Vegetarian|Dairy-Free", "Broccoli|Bell pepper|Carrot|Soy sauce|Rice", "Prepare vegetables and mix soy sauce with a little water and ginger.", "Stir-fry vegetables over high heat until crisp-tender.", "Add the sauce, toss briefly, and serve over rice."),
            r("chicken-fried-rice", "Chicken Fried Rice", 25, "A quick skillet meal with rice, chicken, and vegetables.", "Lunch|Dinner|Hot|Hearty|Mild|Quick|High-Protein|Dairy-Free", "Cooked rice|Chicken|Egg|Peas|Soy sauce", "Cook diced chicken in a hot oiled skillet until fully cooked.", "Add peas and cold cooked rice; stir-fry until hot.", "Push rice aside, scramble the egg, then mix everything with soy sauce."),
            r("beef-tacos", "Beef Tacos", 25, "Seasoned beef in warm tortillas with fresh toppings.", "Lunch|Dinner|Hot|Spicy|Hearty|Quick|High-Protein|Gluten-Free", "Ground beef|Tortilla|Taco seasoning|Lettuce|Tomato", "Brown ground beef in a skillet and drain excess fat if needed.", "Stir in taco seasoning and water; simmer until thick.", "Serve in warm tortillas with lettuce and tomato."),
            r("sloppy-joes", "Sloppy Joes", 30, "A saucy ground-beef sandwich with a sweet tomato flavor.", "Lunch|Dinner|Hot|Sweet|Hearty|Comforting", "Ground beef|Onion|Tomato sauce|Bun|Mustard", "Brown beef with chopped onion and drain excess fat.", "Stir in tomato sauce, mustard, and seasoning; simmer until thick.", "Spoon onto toasted buns."),
            r("corn-chowder", "Corn Chowder", 35, "A creamy soup with sweet corn and potatoes.", "Lunch|Dinner|Hot|Sweet|Hearty|Cozy|Vegetarian", "Corn|Potato|Milk|Onion|Vegetable broth", "Soften chopped onion in a pot with a little butter.", "Add diced potato, corn, and broth; simmer until tender.", "Stir in milk and gently heat until creamy."),
            r("apple-crisp", "Apple Crisp", 50, "Warm cinnamon apples under a crunchy oat topping.", "Dessert|Late-night snack|Hot|Sweet|Comforting|Vegetarian", "Apple|Rolled oats|Flour|Butter|Cinnamon", "Slice apples and toss with cinnamon and a little sugar.", "Mix oats, flour, butter, and brown sugar into a crumbly topping.", "Bake until the apples are tender and topping is golden."),
            r("brownies", "Chocolate Brownies", 40, "Rich chocolate squares with a soft center.", "Dessert|Late-night snack|Hot|Sweet|Bitter|Hearty|Comforting|Vegetarian", "Chocolate|Butter|Sugar|Egg|Flour", "Melt chocolate and butter together, then let the mixture cool slightly.", "Whisk in sugar and eggs, then fold in flour.", "Bake until the edges set and the center remains slightly soft."),
            r("quesadilla", "Cheese Quesadilla", 15, "A toasted tortilla filled with melted cheese.", "Lunch|Dinner|Late-night snack|Hot|Hearty|Mild|Quick|Vegetarian", "Tortilla|Monterey Jack|Salsa|Black beans", "Add cheese and optional beans to one half of a tortilla.", "Fold and cook in a dry skillet, turning once, until crisp and melted.", "Cut into wedges and serve with salsa."),
            r("baked-potato", "Loaded Baked Potato", 65, "A fluffy potato with classic savory toppings.", "Lunch|Dinner|Hot|Hearty|Mild|Comforting|Gluten-Free", "Russet potato|Sour cream|Cheddar|Scallion|Bacon", "Scrub the potato, pierce it, and bake until soft inside.", "Split the potato and fluff the inside with a fork.", "Add cheddar, sour cream, scallion, and optional bacon."),
            r("chicken-noodle-soup", "Chicken Noodle Soup", 35, "Chicken, noodles, and vegetables in a warm broth.", "Lunch|Dinner|Hot|Mild|Light|Cozy|High-Protein|Dairy-Free", "Chicken|Egg noodles|Carrot|Celery|Chicken broth", "Simmer carrot and celery in chicken broth until nearly tender.", "Add noodles and cooked chicken; simmer until the noodles are ready.", "Season with salt, pepper, and parsley."),
            r("chicken-wrap", "Grilled Chicken Wrap", 20, "A portable wrap with chicken, greens, and a light sauce.", "Lunch|Cold|Light|Mild|Quick|Fresh|High-Protein", "Chicken breast|Tortilla|Lettuce|Tomato|Yogurt dressing", "Cook seasoned chicken until fully cooked and slice it.", "Lay lettuce and tomato over a tortilla and add the chicken.", "Add yogurt dressing, roll tightly, and slice in half."),
            r("oatmeal", "Brown Sugar Oatmeal", 12, "A warm bowl of oats with brown sugar and fruit.", "Breakfast|Hot|Sweet|Light|Cozy|Vegetarian", "Rolled oats|Milk|Brown sugar|Banana|Walnut", "Simmer oats with milk or water, stirring until creamy.", "Stir in brown sugar and a pinch of cinnamon.", "Top with banana and walnuts."),
            r("hot-dog", "Classic Hot Dog", 12, "A grilled hot dog with familiar picnic toppings.", "Lunch|Dinner|Late-night snack|Hot|Hearty|Quick|Comforting", "Hot dog|Bun|Mustard|Relish|Onion", "Warm or grill the hot dog until steaming hot.", "Toast the bun lightly.", "Serve with mustard, relish, and chopped onion."),
            r("chicken-tenders", "Baked Chicken Tenders", 30, "Crisp oven-baked chicken strips with a simple coating.", "Lunch|Dinner|Hot|Hearty|Mild|Comforting|High-Protein", "Chicken breast|Breadcrumbs|Egg|Paprika|Yogurt dip", "Cut chicken into strips and dip each piece in beaten egg.", "Coat with seasoned breadcrumbs and arrange on a baking tray.", "Bake, turning once, until crisp and fully cooked."),
            r("stuffed-peppers", "Stuffed Bell Peppers", 55, "Bell peppers filled with rice, beans, vegetables, and cheese.", "Lunch|Dinner|Hot|Hearty|Mild|Vegetarian|Gluten-Free", "Bell pepper|Rice|Black beans|Corn|Cheddar", "Halve and seed the peppers; soften them briefly in the oven.", "Mix cooked rice with black beans, corn, tomato, and seasoning.", "Fill peppers, top with cheese, and bake until tender."),
            r("turkey-meatballs", "Turkey Meatballs and Rice", 35, "Lean turkey meatballs served over fluffy rice.", "Lunch|Dinner|Hot|Hearty|Mild|High-Protein", "Ground turkey|Egg|Breadcrumbs|Rice|Tomato sauce", "Mix turkey, egg, breadcrumbs, and seasoning; shape into meatballs.", "Bake or pan-cook until fully cooked, then warm in tomato sauce.", "Serve over rice with chopped parsley.")
    );

    private RecipeCatalog() {
    }

    public static List<Recipe> all() {
        return RECIPES;
    }

    public static Optional<Recipe> byId(String id) {
        if (EatPlan.isEat(id)) {
            return EatPlan.toRecipe(id);
        }
        return RECIPES.stream().filter(recipe -> recipe.id().equals(id)).findFirst();
    }

    /** Diet choices are requirements: every selected one must apply. */
    public static final Set<String> DIET_TAGS = Set.of(
            "Vegetarian", "Gluten-Free", "Dairy-Free", "Low-Carb", "High-Protein");
    /** Meal times are requirements too; picking several means any of them. */
    public static final Set<String> MEAL_TAGS = Set.of(
            "Breakfast", "Lunch", "Dinner", "Late-night snack");

    /**
     * Picks a recipe that satisfies every selected diet tag and at least one selected
     * meal time, preferring the best match on flavor, temperature and mood.
     * Recipes in {@code seenIds} are skipped until every match has been shown.
     * Empty when nothing satisfies the requirements.
     */
    public static Optional<Recipe> recommend(Set<String> selectedTags, Set<String> seenIds) {
        List<Recipe> eligible = matching(selectedTags);
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        List<Recipe> fresh = eligible.stream().filter(recipe -> !seenIds.contains(recipe.id())).toList();
        List<Recipe> candidates = fresh.isEmpty() ? eligible : fresh;
        int best = candidates.stream().mapToInt(recipe -> preferenceScore(recipe, selectedTags))
                .max().orElse(0);
        List<Recipe> top = candidates.stream()
                .filter(recipe -> preferenceScore(recipe, selectedTags) == best).toList();
        return Optional.of(top.get(ThreadLocalRandom.current().nextInt(top.size())));
    }

    /** All recipes meeting the diet and meal-time requirements. */
    public static List<Recipe> matching(Set<String> selectedTags) {
        Set<String> meals = new HashSet<>(selectedTags);
        meals.retainAll(MEAL_TAGS);
        return RECIPES.stream()
                .filter(recipe -> selectedTags.stream().filter(DIET_TAGS::contains)
                        .allMatch(recipe.tags()::contains))
                .filter(recipe -> meals.isEmpty() || meals.stream().anyMatch(recipe.tags()::contains))
                .toList();
    }

    private static int preferenceScore(Recipe recipe, Set<String> selectedTags) {
        int matches = 0;
        for (String selectedTag : selectedTags) {
            if (!DIET_TAGS.contains(selectedTag) && !MEAL_TAGS.contains(selectedTag)
                    && recipe.tags().contains(selectedTag)) {
                matches++;
            }
        }
        return matches;
    }

    private static Recipe r(
            String id,
            String name,
            int minutes,
            String description,
            String tags,
            String ingredients,
            String... steps
    ) {
        // "Quick" follows the cooking time so the tag can't drift from the minutes shown.
        Set<String> tagSet = new HashSet<>(Set.of(tags.split("\\|")));
        if (minutes <= 20) {
            tagSet.add("Quick");
        } else {
            tagSet.remove("Quick");
        }
        return new Recipe(
                id,
                name,
                minutes,
                description,
                tagSet,
                List.of(ingredients.split("\\|")),
                List.of(steps)
        );
    }
}
