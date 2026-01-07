import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        DataRetriever retriever = new DataRetriever();

        testFindDish(retriever, 1);
        System.out.println();
        testFindDish(retriever, 999);
        System.out.println();
        testFindIngredients(retriever, 2,2);
        System.out.println();
        testFindIngredients(retriever, 3,5);
        System.out.println();
        testFindDishsByIngredientName(retriever, "eur");
        System.out.println();
        testFindIngredientsByCriteria(retriever, null, CategoryEnum.VEGETABLE, null, 1, 10);
        System.out.println();
        testFindIngredientsByCriteria(retriever, "cho", null, "Sal", 1, 10);
        System.out.println();
        testFindIngredientsByCriteria(retriever, "cho", null, "gâteau", 1, 10);
        System.out.println();
        List<Ingredient> toCreate = List.of(
                new Ingredient(0, "Fromage", 1200.0, CategoryEnum.DAIRY, null),
                new Ingredient(0, "Oignon", 500.0, CategoryEnum.VEGETABLE, null)
        );
        testCreateIngredients(retriever, toCreate);
        System.out.println();
        List<Ingredient> toCreateFail = List.of(
                new Ingredient(0, "Carotte", 2000.0, CategoryEnum.VEGETABLE, null),
                new Ingredient(0, "Laitue", 800.0, CategoryEnum.VEGETABLE, null)
        );
        testCreateIngredients(retriever, toCreateFail);
        System.out.println();
        List<Ingredient> ingredientsSoupe = new ArrayList<>();
        ingredientsSoupe.add(new Ingredient(0, "Oignon", 500.0, CategoryEnum.VEGETABLE, null));
        Dish nouvelleSoupe = new Dish(0, "Soupe de légumes", DishTypeEnum.START);
        nouvelleSoupe.setIngredients(ingredientsSoupe);
        testSaveDish(retriever, nouvelleSoupe);

        System.out.println();

        List<Ingredient> ingredientsSalade = new ArrayList<>();
        ingredientsSalade.add(new Ingredient(0, "Oignon", 500.0, CategoryEnum.VEGETABLE, null));
        ingredientsSalade.add(new Ingredient(0, "Laitue", 800.0, CategoryEnum.VEGETABLE, null));
        ingredientsSalade.add(new Ingredient(0, "Tomate", 600.0, CategoryEnum.VEGETABLE, null));
        ingredientsSalade.add(new Ingredient(0, "Fromage", 1200.0, CategoryEnum.DAIRY, null));

        Dish saladeUpdate = new Dish(1, "Salade fraîche", DishTypeEnum.START);
        saladeUpdate.setIngredients(ingredientsSalade);
        testSaveDish(retriever, saladeUpdate);

        System.out.println();

        List<Ingredient> ingredientsSeulFromage = new ArrayList<>();
        ingredientsSeulFromage.add(new Ingredient(0, "Fromage", 1200.0, CategoryEnum.DAIRY, null));

        Dish saladeFromage = new Dish(1, "Salade de fromage", DishTypeEnum.START);
        saladeFromage.setIngredients(ingredientsSeulFromage);
        testSaveDish(retriever, saladeFromage);
    }

    private static void testFindDish(DataRetriever retriever, int id) {
        System.out.println("Plat trouvé avec l'id " + id + ": ");
        try {
            Dish dish = retriever.findDishById(id);
            System.out.println(dish);

        } catch (RuntimeException | SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void testFindIngredients(DataRetriever retriever, int page, int size) {
        System.out.println("Liste des ingredients trouvés dans la page " + page + " : " );
        try {
            List<Ingredient> ingredients = retriever.findIngredients(page, size);
                for (Ingredient ingredient : ingredients) {
                    System.out.println(ingredient);
                }
        } catch (RuntimeException | SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void testFindDishsByIngredientName(DataRetriever retriever, String search) {
        try {
            List<Dish> dishes = retriever.findDishsByIngredientName(search);
            if (dishes.isEmpty()) {
                System.out.println("Aucun plat trouvé.");
            } else {
                for (Dish dish : dishes) {
                    System.out.println("Plat trouvé avec l'ingrédient '"  + search + "' : " + dish.getName());
                }
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void testFindIngredientsByCriteria(DataRetriever retriever, String name, CategoryEnum cat, String dish, int p, int s) {
        System.out.println("Test findIngredientsByCriteria (Category=" + cat + ") :");
        try {
            List<Ingredient> ingredients = retriever.findIngredientsByCriteria(name, cat, dish, p, s);
            ingredients.forEach(System.out::println);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void testCreateIngredients(DataRetriever retriever, List<Ingredient> ingredients) {
        try {
            List<Ingredient> created = retriever.createIngredients(ingredients);
            created.forEach(i -> System.out.println("Créé : " + i.getName() + " (ID: " + i.getId() + ")"));
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void testSaveDish(DataRetriever retriever, Dish dish) {
        try {
            Dish saved = retriever.saveDish(dish);
            System.out.print(saved.getName() + " : ");

            List<String> noms = new ArrayList<>();
            for (Ingredient ing : saved.getIngredient()) {
                noms.add(ing.getName());
            }
            System.out.println("Ingrédients : [" + String.join(", ", noms) +"]");

        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

}