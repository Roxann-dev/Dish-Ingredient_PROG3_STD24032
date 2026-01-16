import java.util.List;
import java.util.Objects;

public class Dish {
    private Integer id;
    private Double price;
    private String name;
    private DishTypeEnum dishType;
    private List<Ingredient> ingredients;

    public Dish() {
    }

    public Dish(Integer id, String name, DishTypeEnum dishType, List<Ingredient> ingredients) {
        this.id = id;
        this.name = name;
        this.dishType = dishType;
        this.setIngredients(ingredients);
    }

    public Double getDishCost() {
        if (this.ingredients == null || this.ingredients.isEmpty()) {
            return 0.0;
        }

        double totalCost = 0.0;

        for (Ingredient ing : ingredients) {
            Double pricePerUnit = ing.getPrice();
            Double quantityRequired = ing.getQuantity();

            if (pricePerUnit != null && quantityRequired != null) {
                totalCost += pricePerUnit * quantityRequired;
            }
        }
        return totalCost;
    }

    public Double getGrossMargin() {
        if (price == null) {
            throw new RuntimeException("Price is null");
        }
        return price - getDishCost();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DishTypeEnum getDishType() {
        return dishType;
    }

    public void setDishType(DishTypeEnum dishType) {
        this.dishType = dishType;
    }

    public List<Ingredient> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<Ingredient> ingredients) {
        if (ingredients == null) {
            this.ingredients = null;
            return;
        }
        // Logique du prof : on lie chaque ingrédient à ce plat (this)
        for (Ingredient ing : ingredients) {
            ing.setDish(this);
        }
        this.ingredients = ingredients;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Dish dish = (Dish) o;
        return Objects.equals(id, dish.id) &&
                Objects.equals(name, dish.name) &&
                dishType == dish.dishType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, dishType);
    }

    @Override
    public String toString() {
        return "Dish{" +
                "id=" + id +
                ", price=" + price +
                ", name='" + name + '\'' +
                ", dishType=" + dishType +
                ", ingredients=" + ingredients +
                '}';
    }
}