import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        DataRetriever retriever = new DataRetriever();

        // 1. Test de récupération d'un plat
        testFindDish(retriever, 1);
        System.out.println();

        testFindDish(retriever, 999);
        System.out.println();

        // 2. Création d'ingrédients
        List<Ingredient> toCreate = List.of(
                new Ingredient(null, "Fromage", CategoryEnum.DAIRY, 1200.0),
                new Ingredient(null, "Oignon", CategoryEnum.VEGETABLE, 500.0)
        );
        toCreate.get(0).setQuantity(1.0);
        toCreate.get(1).setQuantity(2.0);

        testCreateIngredients(retriever, toCreate);
        System.out.println();

        // 3. Sauvegarde d'un nouveau plat
        Ingredient oignon = new Ingredient(null, "Oignon", CategoryEnum.VEGETABLE, 500.0);
        oignon.setQuantity(3.0);

        try {
            List<Ingredient> savedIngs = retriever.createIngredients(List.of(oignon));
            Dish nouvelleSoupe = new Dish(null, "Soupe de légumes", DishTypeEnum.START, savedIngs);
            testSaveDish(retriever, nouvelleSoupe);
        } catch (RuntimeException e) {
            System.err.println("Échec de la préparation de la soupe : " + e.getMessage());
        }

        System.out.println();

        // 4. Mise à jour d'un plat existant
        Ingredient laitue = new Ingredient(1);
        laitue.setQuantity(1.0);
        Ingredient tomate = new Ingredient(2);
        tomate.setQuantity(2.0);

        Dish saladeUpdate = new Dish(1, "Salade fraîche", DishTypeEnum.START, List.of(laitue, tomate));
        saladeUpdate.setPrice(4000.0);
        testSaveDish(retriever, saladeUpdate);
    }

    private static void testFindDish(DataRetriever retriever, int id) {
        System.out.println("--- Test Find Dish ID: " + id + " ---");
        try {
            Dish dish = retriever.findDishById(id);
            // On s'assure qu'il y a des quantités pour le calcul du coût
            dish.getIngredients().forEach(i -> {
                if(i.getQuantity() == null) i.setQuantity(1.0);
            });

            System.out.println(dish);
            System.out.println("Marge brute : " + dish.getGrossMargin() + " Ar");
        } catch (RuntimeException e) {
            System.err.println("Erreur (Plat introuvable ou SQL) : " + e.getMessage());
        }
    }

    private static void testCreateIngredients(DataRetriever retriever, List<Ingredient> ingredients) {
        System.out.println("--- Test Create Ingredients ---");
        try {
            List<Ingredient> created = retriever.createIngredients(ingredients);
            created.forEach(i -> System.out.println("Créé : " + i.getName() + " (ID: " + i.getId() + ")"));
        } catch (RuntimeException e) {
            System.err.println("Erreur lors de la création : " + e.getMessage());
        }
    }

    private static void testSaveDish(DataRetriever retriever, Dish dish) {
        System.out.println("--- Test Save Dish: " + dish.getName() + " ---");
        try {
            Dish saved = retriever.saveDish(dish);
            System.out.print("Résultat : " + saved.getName());

            try {
                System.out.print(" (Prix: " + saved.getPrice() + " Ar, Marge: " + saved.getGrossMargin() + " Ar) ");
            } catch (RuntimeException e) {
                System.out.print(" (Calcul marge impossible : " + e.getMessage() + ") ");
            }

            List<String> noms = new ArrayList<>();
            if (saved.getIngredients() != null) {
                for (Ingredient ing : saved.getIngredients()) {
                    noms.add(ing.getName() + " (qte: " + ing.getQuantity() + ")");
                }
            }
            System.out.println("\nIngrédients associés : " + noms);
        } catch (RuntimeException e) {
            System.err.println("Erreur lors de la sauvegarde du plat : " + e.getMessage());
        }
    }
}