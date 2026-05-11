package recipesystem;
import java.util.ArrayList;
import java.util.Scanner;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Random;



class Comment {
    private String text;
    private User commenter;
    private int likes;

    public Comment(String text, User commenter) {
        this.text = text;
        this.commenter = commenter;
        this.likes = 0;
    }
    
    public int getLikesCount() {
        return likes;
    }
    
    public void setLikesCount(int likes) {  // Add this setter!
        this.likes = likes;
    }

    public String getText() {
        return text;
    }

    public User getUser() {
        return commenter;
    }

    public void likeComment() {
        likes++;
    }

    @Override
    public String toString() {
        return commenter.getUsername() + ": " + text + " (Likes: " + likes + ")";
    }
}



class User {
    private String username;
    private String passwordHash;
    private ArrayList<Recipe> recipes;

    public User(String username, String password) {
        this.username = username;
        this.passwordHash = hashPassword(password);
        this.recipes = new ArrayList<>();
    }
    
    public User(String username, String passwordHash, boolean isHashed) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.recipes = new ArrayList<>();
    }

    public String getUsername() {
        return username;
    }

    public void addRecipe(Recipe recipe) {
        recipes.add(recipe);
    }

    public ArrayList<Recipe> getRecipes() {
        return recipes;
    }
    
    public boolean checkPassword(String rawPassword) {
        return hashPassword(rawPassword).equals(this.passwordHash);
    }

    // This method hashes and sets the password
    public void setPassword(String rawPassword) {
        this.passwordHash = hashPassword(rawPassword);
    }

    // Hash password using SHA-256
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashing algorithm not found.", e);
        }
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    @Override
    public String toString() {
        return "Username: " + username;
    }
}

class Admin extends User {
    public Admin(String username, String password) {
        super(username, password);
    }

    public Admin(String username, String passwordHash, boolean isHashed) {
        super(username, passwordHash, isHashed);
    }
}

class Moderator extends User {
    public Moderator(String username, String password) {
        super(username, password);
    }

    public Moderator(String username, String passwordHash, boolean isHashed) {
        super(username, passwordHash, isHashed);
    }
}



//Ingredient class represents an individual ingredient with its unique attributes
class Ingredient {
    private String name;
    private double quantity;
    private String unit;

    //Parameterized constructor to initialize Ingredient object
    public Ingredient(String name, double quantity, String unit) {
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
    }

    //Getter for the ingredient name
    public String getName() {
        return name;
    }

    //Method to modify ingredient details
    public void modifyIngredient(String name, double quantity, String unit) {
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
    }

    //Override toString() to display ingredient details
    @Override
    public String toString() {
        return quantity + " " + unit + " of " + name;
    }
}


//Class to represent a recipe, including its ingredients and cooking instructions
class Recipe {
    private int recipeId;
    private String recipeName;
    private ArrayList<Ingredient> ingredients;
    private ArrayList<String> cookingInstructions;
    private User owner;
    private ArrayList<Comment> comments;
    private int likes;
    private ArrayList<User> likedUsers;
    private LocalDateTime postedDate;
    private String category;

    public Recipe(String recipeName, User owner) {
        this.recipeId = recipeCounter++;
        this.recipeName = recipeName;
        this.ingredients = new ArrayList<>();
        this.cookingInstructions = new ArrayList<>();
        this.likes = 0;
        this.comments = new ArrayList<>();
        this.owner = owner;
        this.likedUsers = new ArrayList<>();
        this.postedDate = LocalDateTime.now();
    }

    private static final String COUNTER_FILE = "server/counter.txt";
    private static int recipeCounter = 0;

    public static void loadCounter() {
        try {
            if (Files.exists(Paths.get(COUNTER_FILE))) {
                String content = Files.readString(Paths.get(COUNTER_FILE)).trim();
                if (content.isEmpty()) {
                    System.out.println("Counter file is empty. Initializing counter to 0.");
                    recipeCounter = 0;
                } else {
                    recipeCounter = Integer.parseInt(content);
                    System.out.println("Loaded counter: " + recipeCounter);
                }
            } else {
                System.out.println("Counter file not found. Initializing counter to 0.");
                recipeCounter = 0;
            }
        } catch (NumberFormatException e) {
            System.out.println("Counter file contains invalid number. Resetting counter to 0.");
            recipeCounter = 0;
        } catch (Exception e) {
            System.out.println("Error loading counter: " + e.getMessage());
            recipeCounter = 0;
        }
    }
    
    public static void saveCounter() {
        try {
            Files.writeString(Paths.get(COUNTER_FILE), Integer.toString(recipeCounter));
            System.out.println("Counter saved: " + recipeCounter);
        } catch (IOException e) {
            System.out.println("Failed to save counter: " + e.getMessage());
        }
    }
    
    public void updateCategory() {
        String previousCategory = this.category;  // Track old category

        int likes = this.getLikes();
        int commentCount = this.getComments().size();

        if ((likes >= 0 && likes < 10) && (commentCount>=0 && commentCount < 5)) {
            this.category = "Try";
        } else if ((likes >= 10 && likes < 20) && (commentCount >= 5 && commentCount < 15)) {
            this.category = "Popular";
        } else {
            this.category = "Considered for Verification";
        }

        if (previousCategory == null || !previousCategory.equals(this.category)) {
            RecipeSystem.updateCategoryFiles(this, previousCategory, this.category);
            // If new category is "Considered for Verification", write the verification file
            if ("Considered for Verification".equals(this.category)) {
                this.writeVerificationFile(); // Call the method we created earlier
            }
        }
    }

    public void writeVerificationFile() {
        try {
            // Sanitize recipe name to be safe for file names
            String safeName = getRecipeName().replaceAll("[\\\\/:*?\"<>|]", "_");
            String verificationFolder = "server/verification/";
            java.io.File dir = new java.io.File(verificationFolder);
            if (!dir.exists()) {
                dir.mkdirs(); // create directory if it doesn't exist
            }

            String verificationFileName = verificationFolder + getId() + "_" + safeName + ".txt";
            java.io.File file = new java.io.File(verificationFileName);

            // Path to the recipe file
            String recipeFilePath = "server/recipes/" + getId() + "_" + safeName + ".txt";

            // Write to file
            java.io.PrintWriter writer = new java.io.PrintWriter(file);

            writer.println(recipeFilePath); // 1st line: path to recipe file
            writer.println(0);              // 2nd line: votes in favor (initially 0)
            writer.println(0);              // 3rd line: votes against (initially 0)
            writer.println("reviews:");     // 4th line: reviews heading

            writer.close();
            System.out.println("Verification file created for recipe: " + getRecipeName());
        } catch (Exception e) {
            System.out.println("Error writing verification file: " + e.getMessage());
        }
    }

    
    public String getCategory() {
        return category;
    }
    
    public void setCategory (String category){
        this.category = category;
    }


    public LocalDateTime getPostedDate() {
        return postedDate;
    }
    
    public int getLikes(){
        return likes;
    }

    
    public void setLikesCount(int likes) {
        this.likes = likes;
    }
    
    public void setLikedUsers(ArrayList<User> likedUsers) {
        this.likedUsers = likedUsers;
    }
    
    public void setIngredients(ArrayList<Ingredient> ingredients) {
        this.ingredients = ingredients;
    }

    public void setCookingInstructions(ArrayList<String> cookingInstructions) {
        this.cookingInstructions = cookingInstructions;
    }

    public void setComments(ArrayList<Comment> comments) {
        this.comments = comments;
    }

    public void setRecipeName(String newName) {
        this.recipeName = newName;
    }
    
    public void setRecipeId(int id) {
        this.recipeId = id;
    }

    public ArrayList<User> getLikedUsers() {
        return likedUsers;
    }

    public int getLikesCount() {
        return likes;
    }
    
    public int getId() {
        return recipeId;
    }
    public User getOwner() {
        return owner;
    }
    
    public ArrayList<Comment> getComments() {
        return comments;
    }
    //Getters for the recipe class attributes
    public String getRecipeName() {
        return recipeName;
    }
    public ArrayList<Ingredient> getIngredients() {
        return ingredients;
    }
    public ArrayList<String> getCookingInstructions() {
        return cookingInstructions;
    }
    
    public int getRecipeId() {
        return recipeId;
    }
    
    public boolean likeRecipe(User user) {
        if (user.equals(owner)) {
            System.out.println("You cannot like your own recipe.");
            return false;
        }

        if (likedUsers.contains(user)) {
            System.out.println("You have already liked this recipe.");
            return false;
        }

        likes++;
        likedUsers.add(user);
        updateCategory();
        System.out.println("You liked the recipe!");
        RecipeSystem.writeRecipeToFile(this);
        return true;
    }
    
    public boolean addComment(String text, User user) {
        if (user.equals(owner)) {
            System.out.println("You cannot comment on your own recipe.");
            return false;
        }

        comments.add(new Comment(text, user));
        updateCategory();
        System.out.println("Your comment was added.");
        RecipeSystem.writeRecipeToFile(this);
        return true;
    }

    //Method to add a new ingredient to the recipe
    public void addIngredient(String name, double quantity, String unit) {
        ingredients.add(new Ingredient(name, quantity, unit));
    }

    //Method to add a new cooking instruction to the recipe
    public void addCookingInstruction(String instruction) {
        cookingInstructions.add(instruction);
    }

    //Method to modify an ingredient in the recipe
    public void modifyIngredient(String name, double quantity, String unit) {
        for (Ingredient ingredient : ingredients) {
            if (ingredient.getName().equalsIgnoreCase(name)) {
                System.out.print("Ingredient to be modified: ");
                System.out.println(ingredient);
                ingredient.modifyIngredient(name, quantity, unit);
                return;
            }
        }
    }

    //Method to modify a cooking instruction at a specified index in recipe
    public void modifyInstruction(int index) {
        Scanner scanner = new Scanner (System.in);
        if (index >= 0 && index < cookingInstructions.size()) {
            System.out.print("Cooking Instruction to be modified: ");
            System.out.println(cookingInstructions.get(index));
            System.out.print("New instruction: ");
            String newInstruction = scanner.nextLine();
            cookingInstructions.set(index, newInstruction);
            System.out.println("Cooking instruction modified successfully.");
        }
        else System.out.println("Instruction index out of bounds.");
            
    }
    
    public int checkIngredient(String name) {
        for (int i = 0; i < ingredients.size(); i++) {
            if (ingredients.get(i).getName().equalsIgnoreCase(name)) {
                return 1;
            }
        }
        return 0;
    }
    //Method to delete an ingredient from the recipe by its name
    public boolean deleteIngredient(String name) {
        for (int i = 0; i < ingredients.size(); i++) {
            if (ingredients.get(i).getName().equalsIgnoreCase(name)) {
                System.out.print("Ingredient to delete: ");
                System.out.println(ingredients.get(i));
                ingredients.remove(i);
                System.out.println("Ingredient removed successfully.");
                return true;  // success
            }
        }
        System.out.println("Ingredient not found in the recipe.");
        return false;  // failure
    }


     //Method to delete a cooking instruction by its index
    public void deleteInstruction(int index) {
        if (index >= 0 && index < cookingInstructions.size()) {
            System.out.print("Cooking Instruction to delete: ");
            System.out.println(cookingInstructions.get(index));
            cookingInstructions.remove(index);
            System.out.println("Cooking instruction removed successfully.");
            return;
        }
        System.out.println("Instruction " + (index+1) +" not found in the recipe.");
    }

     //Method to display the details of the recipe
    public void displayRecipeDetails() {
        System.out.println("\nRecipe Id: " + recipeId + "\nRecipe Name: " + recipeName);
        System.out.println("Posted by: " + owner.getUsername());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        System.out.println("Posted on: " + postedDate.format(formatter));
        System.out.println("Category" + category);
        System.out.println("Likes: " + likes);

        System.out.println("\nIngredients:");
        for (Ingredient ingredient : ingredients) {
            System.out.println(" - " + ingredient);
        }

        System.out.println("\nCooking Instructions:");
        for (int i = 0; i < cookingInstructions.size(); i++) {
            System.out.println((i + 1) + ". " + cookingInstructions.get(i));
        }

        System.out.println("\nComments:");
        if (comments.isEmpty()) {
            System.out.println("No comments yet.");
        } else {
            for (Comment comment : comments) {
                System.out.println(" - " + comment);
            }
        }
    }

}

//Main class that controls the Recipe Management System
public class RecipeSystem {
    private static ArrayList<Recipe> recipes = new ArrayList<>();
    private static Scanner scanner = new Scanner(System.in);
    private static ArrayList<User> users = new ArrayList<>();
    private static ArrayList<Recipe> tryRecipes = new ArrayList<>();
    private static ArrayList<Recipe> popularRecipes = new ArrayList<>();
    private static ArrayList<Recipe> consideredRecipes = new ArrayList<>();
    private static ArrayList<Recipe> verifiedRecipes = new ArrayList<>();
    private static ArrayList<Recipe> displayedRecipes = new ArrayList<>();
    private static ArrayList<Admin> admins = new ArrayList<>();
    private static ArrayList<Moderator> moderators = new ArrayList<>();
    private static User currentUser = null;


    public static void main(String[] args) {
        users.clear();
        tryRecipes.clear();
        popularRecipes.clear();
        consideredRecipes.clear();
        verifiedRecipes.clear();
        displayedRecipes.clear();
        
        loadRegisteredUsers();
        loadRegisteredAdmins();
        loadRegisteredModerators();
        initializeCategories();
        
        User currentUser = loginMenu();  // get logged in user here
        loadUserRecipes(currentUser);    // load their recipes
        Recipe.loadCounter();
        boolean exit = false;
        //Main loop for the program, displaying the menu and taking user input
        
        while (true) {
            if (currentUser == null){
                currentUser = loginMenu(); 
                recipes.clear(); 
                loadUserRecipes(currentUser);    // load their recipes
                Recipe.loadCounter();
            }
            
            if (currentUser instanceof Admin) {
                exit=false;
                while (!exit) {
                    System.out.println("\n--- Admin Menu ---");
                    System.out.println("1. Review Verification Cases");
                    System.out.println("2. Process Moderator Applications");
                    System.out.println("3. Logout");
                    System.out.print("Choose an option: ");
                    String option = scanner.nextLine();

                    switch (option) {
                        case "1":
                            handleAdminApprovals();  // This is the function we created
                            break;
                        case "2":
                            reviewModeratorApplications();  // This is the function we created
                            break;
                        case "3":{
                            currentUser = null;
                            System.out.println("\nYou have been logged out.");
                            exit=true;
                            break;}
                        default:
                            System.out.println("Invalid option.");
                    }
                }
            } 
            
            else if (currentUser instanceof Moderator) {
                exit=false;
                while (!exit) {
                    System.out.println("\n--- Moderator Menu ---");
                    displayedRecipes.clear();
                    dispalyRandomRecipesFromList(verifiedRecipes, 5);
                    dispalyRandomRecipesFromList(consideredRecipes, 5);
                    dispalyRandomRecipesFromList(popularRecipes, 5);
                    dispalyRandomRecipesFromList(tryRecipes, 5);

                    System.out.println("\nMain Menu:");
                    System.out.println("1. Open a recipe");
                    System.out.println("2. Search recipes");
                    System.out.println("3. Filters");
                    System.out.println("4. My Account");
                    System.out.println("5. Log out");
                    System.out.print("Choose an option: ");

                    String choice = scanner.nextLine();

                    switch (choice) {
                        case "1":
                            openRecipeFromDisplayed();
                            break;
                        case "2":
                            searchRecipes();
                            break;
                        case "3":
                            applyFilters();
                            break;
                        case "4":
                            myAccountMenuforModerator();
                            break;
                        case "5":{currentUser = null;
                            System.out.println("\nYou have been logged out.");
                            exit=true;
                            break;}
                        default:
                            System.out.println("Invalid choice. Please try again.");
                    }
                }
            } 
            
            
            else if (currentUser instanceof User){
                System.out.println("\n\n=== bakeArt ===");
                displayedRecipes.clear();
                dispalyRandomRecipesFromList(verifiedRecipes, 5);
                dispalyRandomRecipesFromList(consideredRecipes, 5);
                dispalyRandomRecipesFromList(popularRecipes, 5);
                dispalyRandomRecipesFromList(tryRecipes, 5);

                System.out.println("\nMain Menu:");
                System.out.println("1. Open a recipe");
                System.out.println("2. Search recipes");
                System.out.println("3. Filters");
                System.out.println("4. My Account");
                System.out.println("5. Log out");
                System.out.print("Choose an option: ");

                String choice = scanner.nextLine();

                switch (choice) {
                    case "1":
                        openRecipeFromDisplayed();
                        break;
                    case "2":
                        searchRecipes();
                        break;
                    case "3":
                        applyFilters();
                        break;
                    case "4":
                        myAccountMenu();
                        break;
                    case "5":{currentUser = null;
                        System.out.println("\nYou have been logged out.");
                        //loginMenu(); // Re-run login menu
                        break;}
                    default:
                        System.out.println("Invalid choice. Please try again.");
                }
            }
        }
    }
    

    
    private static void handleModeratorApprovals() {
        if (consideredRecipes.isEmpty()) {
            System.out.println("No recipes are currently under consideration for verification.");
            return;
        }

        System.out.println("\n=== Recipes Considered for Verification ===");
        for (int i = 0; i < consideredRecipes.size(); i++) {
            System.out.println((i + 1) + ". " + consideredRecipes.get(i).getRecipeName());
        }

        System.out.print("Select a recipe to review (enter number): ");
        int choice;
        try {
            choice = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a number.");
            return;
        }

        if (choice < 1 || choice > consideredRecipes.size()) {
            System.out.println("Invalid choice.");
            return;
        }

        Recipe selectedRecipe = consideredRecipes.get(choice - 1);
        selectedRecipe.displayRecipeDetails();

        // Construct verification file path
        String fileName = selectedRecipe.getId() + "_" +
                selectedRecipe.getRecipeName().replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
        File verificationFile = new File("server/verification/" + fileName);

        ArrayList<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(verificationFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            System.out.println("Error reading verification file.");
            return;
        }

        if (lines.size() < 3) {
            System.out.println("Invalid verification file format.");
            return;
        }

        // Extract current vote counts
        int approvals = Integer.parseInt(lines.get(1).trim());
        int disapprovals = Integer.parseInt(lines.get(2).trim());

        // Check if this moderator has already voted
        int existingVoteIndex = -1;
        for (int i = 3; i < lines.size(); i++) {
            if (lines.get(i).startsWith(currentUser.getUsername() + ":")) {
                existingVoteIndex = i;
                break;
            }
        }

        boolean updating = existingVoteIndex != -1;
        if (updating) {
            System.out.println("\nYou've already voted on this recipe.");
            System.out.println("Your previous vote: " + lines.get(existingVoteIndex));
            System.out.print("Do you want to edit your vote? (yes/no): ");
            String edit = scanner.nextLine().trim().toLowerCase();
            if (!edit.equals("yes")) {
                System.out.println("No changes made.");
                return;
            }

            // Reverse old vote
            String oldVoteLine = lines.get(existingVoteIndex);
            if (oldVoteLine.contains("[APPROVE]")) {
                approvals--;
            } else if (oldVoteLine.contains("[DISAPPROVE]")) {
                disapprovals--;
            }
        }

        // Now take new vote
        System.out.println("\nDo you approve this recipe?");
        System.out.println("1. Approve");
        System.out.println("2. Disapprove");
        System.out.print("Enter choice: ");
        int voteChoice;
        try {
            voteChoice = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Invalid vote input.");
            return;
        }

        if (voteChoice != 1 && voteChoice != 2) {
            System.out.println("Invalid vote selection.");
            return;
        }

        boolean approved = (voteChoice == 1);

        System.out.print("Enter your updated review: ");
        String review = scanner.nextLine();

        // Update vote count
        if (approved) {
            approvals++;
        } else {
            disapprovals++;
        }

        // Update vote line
        String updatedLine = currentUser.getUsername() + ": " +
                (approved ? "[APPROVE]" : "[DISAPPROVE]") +
                " - " + review;

        if (updating) {
            lines.set(existingVoteIndex, updatedLine); // Replace old vote
        } else {
            lines.add(updatedLine); // Append new vote
        }

        // Update counts in file
        lines.set(1, String.valueOf(approvals));
        lines.set(2, String.valueOf(disapprovals));

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(verificationFile))) {
            for (String l : lines) {
                writer.write(l);
                writer.newLine();
            }
            System.out.println("Vote and review " + (updating ? "updated" : "recorded") + " successfully.");
        } catch (IOException e) {
            System.out.println("Error writing to verification file.");
        }
    }

    
    
    
    private static void handleAdminApprovals() {
        File verificationFolder = new File("server/verification");
        File[] files = verificationFolder.listFiles((dir, name) -> name.endsWith(".txt"));

        if (files == null || files.length == 0) {
            System.out.println("No recipes pending admin approval.");
            return;
        }

        int moderatorCount = getModeratorCount();
        int requiredMajority = (int) Math.ceil(moderatorCount * (2.0 / 3));

        List<File> eligibleFiles = new ArrayList<>();
        List<Recipe> eligibleRecipes = new ArrayList<>();

        // Filter eligible verification files
        for (File verificationFile : files) {
            try (BufferedReader reader = new BufferedReader(new FileReader(verificationFile))) {
                String recipePath = reader.readLine();
                int approvals = Integer.parseInt(reader.readLine().trim());
                int disapprovals = Integer.parseInt(reader.readLine().trim());

                if (approvals >= requiredMajority || disapprovals >= requiredMajority) {
                    File recipeFile = new File(recipePath);
                    Recipe recipe = loadRecipeFromFile(recipeFile, null);
                    if (recipe != null) {
                        eligibleFiles.add(verificationFile);
                        eligibleRecipes.add(recipe);
                    }
                }
            } catch (IOException | NumberFormatException e) {
                System.out.println("Error reading verification file: " + verificationFile.getName());
            }
        }

        if (eligibleRecipes.isEmpty()) {
            System.out.println("No recipes have reached the required majority.");
            return;
        }

        // Display eligible recipes
        System.out.println("\n=== Recipes Eligible for Admin Approval ===");
        for (int i = 0; i < eligibleRecipes.size(); i++) {
            Recipe r = eligibleRecipes.get(i);
            System.out.printf("[%d] %s (ID: %d)\n", i + 1, r.getRecipeName(), r.getId());
        }

        System.out.print("\nEnter the number of the recipe to process (or 0 to cancel): ");
        int choice;
        try {
            choice = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            return;
        }

        if (choice <= 0 || choice > eligibleRecipes.size()) {
            System.out.println("Cancelled or invalid choice.");
            return;
        }

        // Process the selected recipe
        Recipe recipe = eligibleRecipes.get(choice - 1);
        File verificationFile = eligibleFiles.get(choice - 1);

        // Load vote counts and comments again
        int approvals = 0;
        int disapprovals = 0;
        List<String> reviews = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(verificationFile))) {
            reader.readLine(); // skip recipePath
            approvals = Integer.parseInt(reader.readLine().trim());
            disapprovals = Integer.parseInt(reader.readLine().trim());
            String line;
            while ((line = reader.readLine()) != null) {
                reviews.add(line);
            }
        } catch (IOException e) {
            System.out.println("Error reading verification file: " + verificationFile.getName());
            return;
        }

        System.out.println("\n=== Selected Recipe: " + recipe.getRecipeName() + " ===");
        recipe.displayRecipeDetails();
        System.out.println("\nVotes: Approvals = " + approvals + ", Disapprovals = " + disapprovals);

        System.out.println("\nReviews:");
        for (String rev : reviews) {
            System.out.println(" - " + rev);
        }

        // Build file name the way you want
        String recipeFileName = recipe.getId() + "_" + recipe.getRecipeName().replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
        String recipeFullPath = "server/recipes/" + recipeFileName;

        if (approvals >= requiredMajority) {
            System.out.print("\nApprove and verify this recipe? (yes/no): ");
            String decision = scanner.nextLine().trim().toLowerCase();
            if (decision.equals("yes")) {
                String oldCategory = recipe.getCategory();
                recipe.setCategory("Verified");
                writeRecipeToFile(recipe);
                addPathToCategoryFile("verified", recipeFullPath);
                removePathFromCategoryFile(oldCategory, recipeFullPath);
                consideredRecipes.removeIf(r -> r.getId() == recipe.getId());

                if (verificationFile.delete()) {
                    System.out.println("Verification file deleted.");
                } else {
                    System.out.println("Failed to delete verification file.");
                }

                System.out.println("Recipe marked as verified.");
            }

        } else if (disapprovals >= requiredMajority) {
            System.out.print("\nDelete this recipe based on disapproval majority? (yes/no): ");
            String decision = scanner.nextLine().trim().toLowerCase();
            if (decision.equals("yes")) {
                deleteRecipeFile(recipe);
                removeRecipePathFromCategoryFile(recipe);
                consideredRecipes.removeIf(r -> r.getId() == recipe.getId());
                recipes.remove(recipe);

                if (verificationFile.delete()) {
                    System.out.println("Verification file deleted.");
                } else {
                    System.out.println("Failed to delete verification file.");
                }

                System.out.println("Recipe deleted.");
            }
        } else {
            System.out.println("Recipe does not have majority for any action.");
        }
    }




    private static int getModeratorCount() {
        int count = 0;
        for (User user : moderators) {
            count++;
        }
        return count;
    }
    
    private static void searchRecipes() {
        System.out.print("Enter keyword to search for recipes: ");
        String keyword = scanner.nextLine().trim().toLowerCase();

        if (keyword.isEmpty()) {
            System.out.println("Keyword cannot be empty.");
            return;
        }

        List<Recipe> allRecipes = new ArrayList<>();
        allRecipes.addAll(tryRecipes);
        allRecipes.addAll(popularRecipes);
        allRecipes.addAll(consideredRecipes);
        allRecipes.addAll(verifiedRecipes);

        List<Recipe> matchingRecipes = new ArrayList<>();

        for (Recipe recipe : allRecipes) {
            if (recipe.getRecipeName().toLowerCase().contains(keyword)) {
                matchingRecipes.add(recipe);
            }
        }

        if (matchingRecipes.isEmpty()) {
            System.out.println("No recipes found with that keyword.");
            return;
        }

        System.out.println("\n=== Matching Recipes ===");
        for (int i = 0; i < matchingRecipes.size(); i++) {
            Recipe r = matchingRecipes.get(i);
            System.out.printf("[%d] %s (Category: %s)\n", i + 1, r.getRecipeName(), r.getCategory());
        }

        System.out.print("\nEnter the number of the recipe you want to view (or 0 to cancel): ");
        try {
           int choice = Integer.parseInt(scanner.nextLine().trim());
           if (choice <= 0 || choice > matchingRecipes.size()) {
                System.out.println("Cancelled or invalid selection.");
                return;
           }

            Recipe selected = matchingRecipes.get(choice - 1);
            interactWithRecipe(selected); 
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a number.");
        }
    }

    private static void applyFilters() {
        System.out.println("\nFilter recipes by category:");
        System.out.println("1. Try Recipes");
        System.out.println("2. Popular Recipes");
        System.out.println("3. Considered Recipes");
        System.out.println("4. Verified Recipes");
        System.out.print("Choose a category (1-4) or 0 to cancel: ");

        String input = scanner.nextLine().trim();
        List<Recipe> selectedCategoryRecipes;

        switch (input) {
            case "1":
                selectedCategoryRecipes = tryRecipes;
                break;
            case "2":
                selectedCategoryRecipes = popularRecipes;
                break;
            case "3":
                selectedCategoryRecipes = consideredRecipes;
                break;
            case "4":
                selectedCategoryRecipes = verifiedRecipes;
                break;
            case "0":
                System.out.println("Cancelled.");
                return;
            default:
                System.out.println("Invalid choice.");
                return;
        }

        if (selectedCategoryRecipes.isEmpty()) {
            System.out.println("No recipes found in this category.");
            return;
        }

        System.out.println("\nRecipes in selected category:");
        for (int i = 0; i < selectedCategoryRecipes.size(); i++) {
            Recipe r = selectedCategoryRecipes.get(i);
            System.out.printf("[%d] %s (by %s)\n", i + 1, r.getRecipeName(), r.getOwner().getUsername());
        }

        System.out.print("\nEnter the number of the recipe you want to view (or 0 to cancel): ");
        try {
            int choice = Integer.parseInt(scanner.nextLine().trim());
            if (choice <= 0 || choice > selectedCategoryRecipes.size()) {
                System.out.println("Cancelled or invalid selection.");
                return;
            }
            interactWithRecipe(selectedCategoryRecipes.get(choice - 1));
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a number.");
        }
    }
    
    private static void dispalyRandomRecipesFromList(ArrayList<Recipe> sourceList, int maxCount) {
        Random rand = new Random();
        int added = 0;
        ArrayList<Integer> usedIndices = new ArrayList<>();

        int size = sourceList.size();
        if (size == 0) return;

        while (added < maxCount && added < size) {
            int randomIndex = rand.nextInt(size);
            if (!usedIndices.contains(randomIndex)) {
                usedIndices.add(randomIndex);
                Recipe r = sourceList.get(randomIndex);
                displayedRecipes.add(r);
                System.out.printf("%d. %s\n", displayedRecipes.size(), r.getRecipeName());
                added++;
            }
        }
    }
    
    private static void myAccountMenu() {
        while (true) {
            System.out.println("\nMy Account Menu:");
            System.out.println("1. Profile");
            System.out.println("2. My Recipes");
            System.out.println("3. Apply for Moderator");
            System.out.println("4. Back to Main Menu");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    profileMenu();
                    break;
                case "2":
                    displayUserRecipes();
                    break;
                case "3":
                    applyForModerator();
                    break;
                case "4":
                    return; // Back to main menu
                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }
    }
    
    private static void myAccountMenuforModerator() {
        while (true) {
            System.out.println("\nMy Account Menu:");
            System.out.println("1. Profile");
            System.out.println("2. My Recipes");
            System.out.println("3. Vote on Recipes for Verification");
            System.out.println("4. Back to Main Menu");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    profileMenu();
                    break;
                case "2":
                    displayUserRecipes();
                    break;
                case "3":
                    handleModeratorApprovals();
                    break;
                case "4":
                    return; // Back to main menu
                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }
    }

    public static User findUserByUsername(String username) {
        for (User user : users) {
            if (user.getUsername().equalsIgnoreCase(username)) {
                return user;
            }
        }
        // User not found
        return null;
    }
    
    private static void profileMenu() {
        System.out.println("\nUsername: " + currentUser.getUsername());
        System.out.print("Do you want to change your password? (yes/no): ");
        String ans = scanner.nextLine();
        if (ans.equalsIgnoreCase("yes")) {
            System.out.print("Enter current password: ");
            String currentPass = scanner.nextLine();

            if (currentUser.checkPassword(currentPass)) {
                System.out.print("Enter new password: ");
                String newPass = scanner.nextLine();
                currentUser.setPassword(newPass);  // This will hash the new password
                System.out.println("Password changed successfully.");
            } else {
                System.out.println("Incorrect current password.");
            }
        }
    }

    private static void displayUserRecipes() {
        System.out.println("\nYour Recipes:");

        if (recipes.isEmpty()) {
            System.out.println("You have no recipes.");
        } else {
            for (int i = 0; i < recipes.size(); i++) {
                System.out.printf("%d. %s\n", i + 1, recipes.get(i).getRecipeName());
            }
        }
        
        while (true){
            System.out.println("\n ]C hoose what to do with your recipes:");
            System.out.println("1. Create a new recipe\n2. Add ingredients\n3. Add instructions\n4. Modify Recipe Component\n5. Delete Recipe Component\n6. Display a recipe\n7. Exit");
            System.out.print("Choose an option (1-8): ");

            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1: recipes.add(createRecipe()); break;  //Create a new recipe
                case 2: addIngredients(); break;  //Add ingredients to a recipe with no prior ingredients added
                case 3: addInstructions(); break;  //Add instructions to a recipe with no prior instructions added
                case 4: modifyRecipe(); break;  //Modify a existing recipe component (name, ingredient, or instruction)
                case 5: deleteRecipe(); break;  //Delete a recipe component (recipe, ingredient, instruction)
                case 6: displayRecipe(); break;  //Display a recipe
                case 7: {
                    return;
                }
                default: System.out.println("Invalid choice. Please try again.");  //Invalid option handler
            }
        }
    }
    
    private static void openRecipeFromDisplayed() {
        if (displayedRecipes.isEmpty()) {
            System.out.println("No recipes displayed currently. Please refresh the main menu.");
            return;
        }

        System.out.print("Enter the number of the recipe to open (1–" + displayedRecipes.size() + "): ");
        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume leftover newline

        if (choice < 1 || choice > displayedRecipes.size()) {
            System.out.println("Invalid recipe number.");
            return;
        }

        Recipe selectedRecipe = displayedRecipes.get(choice - 1);
        selectedRecipe.displayRecipeDetails();

                if (!selectedRecipe.getOwner().equals(currentUser)) {
                    while (true) {
                        System.out.println("\nWhat would you like to do?");
                        System.out.println("1. Like the recipe");
                        System.out.println("2. Comment on the recipe");
                        System.out.println("3. Like a comment");
                        System.out.println("4. Exit interaction");
                        System.out.print("Choose an option (1-4): ");
                        String action = scanner.nextLine();

                        switch (action) {
                            case "1":
                                selectedRecipe.likeRecipe(currentUser);
                                break;
                            case "2":
                                System.out.print("Enter your comment: ");
                                String commentText = scanner.nextLine();
                                selectedRecipe.addComment(commentText, currentUser);
                                break;
                            case "3":
                                ArrayList<Comment> comments = selectedRecipe.getComments();
                                if (comments.isEmpty()) {
                                    System.out.println("There are no comments to like.");
                                } else {
                                    System.out.println("\nComments:");
                                    for (int i = 0; i < comments.size(); i++) {
                                        System.out.println((i + 1) + ". " + comments.get(i));
                                    }
                                    System.out.print("Select comment number to like: ");
                                    try {
                                        int commentIndex = Integer.parseInt(scanner.nextLine());
                                        if (commentIndex >= 1 && commentIndex <= comments.size()) {
                                            comments.get(commentIndex - 1).likeComment();
                                            System.out.println("Comment liked!");
                                        } else {
                                            System.out.println("Invalid comment number.");
                                        }
                                    } catch (NumberFormatException e) {
                                        System.out.println("Please enter a valid number.");
                                    }
                                }
                                break;
                            case "4":
                                System.out.println("Exiting interaction menu.");
                                return;
                            default:
                                System.out.println("Invalid option. Try again.");
                        }
                    }
                } else {
                    System.out.println("You are the author. You cannot like or comment on your own recipe.");
                }


                return;
    }

    private static void applyForModerator() {
        System.out.println("Moderator Application Form:");
        System.out.print("Enter your full name: ");
        String name = scanner.nextLine().trim();

        System.out.print("Enter your age: ");
        String age = scanner.nextLine().trim();

        System.out.print("Enter your highest qualification: ");
        String qualification = scanner.nextLine().trim();

        System.out.println("Enter your baking knowledge (type each item and press enter; type '/s' to stop):");
        List<String> bakingKnowledge = new ArrayList<>();
        while (true) {
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("/s")) break;
            if (!input.isEmpty()) bakingKnowledge.add(input);
        }

        System.out.println("Enter your baking experience workplaces (type each item and press enter; type '/s' to stop):");
        List<String> bakingExperience = new ArrayList<>();
        while (true) {
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("/s")) break;
            if (!input.isEmpty()) bakingExperience.add(input);
        }

        // Compose application content
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(name).append("\n");
        sb.append("Age: ").append(age).append("\n");
        sb.append("Qualification: ").append(qualification).append("\n");
        sb.append("Baking Knowledge:\n");
        for (String knowledge : bakingKnowledge) {
            sb.append(" - ").append(knowledge).append("\n");
        }
        sb.append("Baking Experience:\n");
        for (String exp : bakingExperience) {
            sb.append(" - ").append(exp).append("\n");
        }

        // Save to file (e.g., use username for filename)
        String applicationFolder = "server/applications/";
        new File(applicationFolder).mkdirs();
        String filename = applicationFolder + currentUser.getUsername() + ".txt";

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println(sb.toString());
            System.out.println("Your application has been submitted for admin approval.");
        } catch (IOException e) {
            System.out.println("Failed to save application: " + e.getMessage());
        }
    }
    
    private static void reviewModeratorApplications() {
        File appDir = new File("server/applications");
        File[] apps = appDir.listFiles((dir, name) -> name.endsWith(".txt") && !name.endsWith("_votes.txt"));


        if (apps == null || apps.length == 0) {
            System.out.println("No moderator applications pending.");
            return;
        }

        List<File> applications = Arrays.asList(apps);

        System.out.println("\nPending Moderator Applications:");
        for (int i = 0; i < applications.size(); i++) {
            String username = applications.get(i).getName().replace(".txt", "");
            System.out.println((i + 1) + ". " + username);
        }

        System.out.print("Select application to review (number), or 0 to cancel: ");
        int choice = -1;
        try {
            choice = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            return;
        }

        if (choice == 0) {
            System.out.println("Review cancelled.");
            return;
        }

        if (choice < 1 || choice > applications.size()) {
            System.out.println("Invalid selection.");
            return;
        }

        File selectedApp = applications.get(choice - 1);
        String username = selectedApp.getName().replace(".txt", "");
        int totalAdmins = getModeratorCount();
        int requiredApprovals = (int) Math.ceil(totalAdmins * 2.0 / 3);

        try {
            List<String> lines = Files.readAllLines(selectedApp.toPath());
            System.out.println("\n--- Application details ---");
            for (String line : lines) {
                System.out.println(line);
            }

            File voteFile = new File("server/applications/" + username + "_votes.txt");
            List<String> votes = voteFile.exists() ? Files.readAllLines(voteFile.toPath()) : new ArrayList<>();

            long approvals = votes.stream().filter(v -> v.equalsIgnoreCase("approve")).count();
            long disapprovals = votes.stream().filter(v -> v.equalsIgnoreCase("disapprove")).count();

            System.out.println("Current votes: Approvals = " + approvals + ", Disapprovals = " + disapprovals);
            System.out.print("Enter your vote (approve/disapprove): ");
            String vote = scanner.nextLine().trim().toLowerCase();

            if (vote.equals("approve") || vote.equals("disapprove")) {
                votes.add(vote);
                Files.write(voteFile.toPath(), votes);
                System.out.println("Vote recorded.");
            } else {
                System.out.println("Invalid Input");
            }

            // Recalculate after voting
            approvals = votes.stream().filter(v -> v.equalsIgnoreCase("approve")).count();
            disapprovals = votes.stream().filter(v -> v.equalsIgnoreCase("disapprove")).count();

            if (approvals >= requiredApprovals) {
                System.out.println("Application approved! Promoting user to moderator...");
                promoteUserToModerator(username);
                selectedApp.delete();
                if (voteFile.exists()) voteFile.delete();
            } else if (disapprovals >= requiredApprovals) {
                System.out.println("Application rejected.");
                selectedApp.delete();
                if (voteFile.exists()) voteFile.delete();
            }

        } catch (IOException e) {
            System.out.println("Error reading application: " + e.getMessage());
        }
    }
    
    private static void promoteUserToModerator(String username) {
        // Paths
        File userFolder = new File("server/users/" + username);
        File modFolder = new File("server/moderators/" + username);

        if (!userFolder.exists()) {
            System.out.println("User folder does not exist, cannot promote.");
            return;
        }

        // Copy user folder to moderators folder
        try {
            copyFolder(userFolder.toPath(), modFolder.toPath());

            // Delete original user folder
            deleteFolder(userFolder);

            // Remove user from users list (assuming users is your ArrayList<User>)
            users.removeIf(u -> u.getUsername().equals(username));

            System.out.println("User " + username + " promoted to moderator successfully.");
        } catch (IOException e) {
            System.out.println("Error promoting user to moderator: " + e.getMessage());
        }
    }

    // Helper to copy folder recursively
    private static void copyFolder(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path relative = source.relativize(path);
                Path targetPath = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    if (!Files.exists(targetPath)) {
                        Files.createDirectories(targetPath);
                    }
                } else {
                    Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // Helper to delete folder recursively
    private static void deleteFolder(File folder) throws IOException {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteFolder(f);
                }
            }
        }
        if (!folder.delete()) {
            throw new IOException("Failed to delete " + folder.getAbsolutePath());
        }
    }



    private static void verificationFeedback() {
        System.out.println("\nVerification feedback feature coming soon...");
    }
    

    private static void loadRecipesFromCategory(String categoryFileName, ArrayList<Recipe> recipeList) {
        String categoryFilePath = "server/categories/" + categoryFileName.toLowerCase().replace(" ", "_") + ".txt";
        File categoryFile = new File(categoryFilePath);

        if (!categoryFile.exists()) {
            System.out.println("Category file not found: " + categoryFilePath);
            return;
        }

        try {
            List<String> recipePaths = Files.readAllLines(categoryFile.toPath());
            for (String path : recipePaths) {
                File recipeFile = new File(path.trim());
                if (recipeFile.exists()) {
                    Recipe recipe = loadRecipeFromFile(recipeFile, null); // Pass the appropriate 'User' object
                    if (recipe != null) {
                        recipeList.add(recipe);
                    }
                } else {
                    //System.out.println("Recipe file not found: " + path);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading category file: " + e.getMessage());
        }
    }
    
    
    
    
    
    
    
    
    
    public static void initializeCategories() {
        loadRecipesFromCategory("try", tryRecipes);
        loadRecipesFromCategory("popular", popularRecipes);
        loadRecipesFromCategory("considered_for_verification", consideredRecipes);
        loadRecipesFromCategory("verified", verifiedRecipes);
    }


    
    
    public static void updateCategoryFiles(Recipe recipe, String oldCategory, String newCategory) {
        String recipePath = getRecipeFilePath(recipe);

        if (oldCategory != null) {
            removePathFromCategoryFile(oldCategory, recipePath);
        }
        addPathToCategoryFile(newCategory, recipePath);
    }
    
    private static String getRecipeFilePath(Recipe recipe) {
        String safeRecipeName = recipe.getRecipeName().replaceAll("[\\\\/:*?\"<>|]", "_");
        return "server/recipes/" + recipe.getId() + "_" + safeRecipeName + ".txt";
    }
    
    private static void addPathToCategoryFile(String category, String path) {
        String categoryFilePath = "server/categories/" + category.toLowerCase().replace(" ", "_") + ".txt";
        File file = new File(categoryFilePath);
        try {
            file.getParentFile().mkdirs(); // Ensure directory exists
            List<String> lines = file.exists() ? Files.readAllLines(file.toPath()) : new ArrayList<>();
            if (!lines.contains(path)) {
                try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
                    writer.println(path);
                }
            }
        } catch (IOException e) {
            System.out.println("Error writing to category file: " + e.getMessage());
        }
    }

    private static void removePathFromCategoryFile(String category, String path) {
        String categoryFilePath = "server/categories/" + category.toLowerCase().replace(" ", "_") + ".txt";
        File file = new File(categoryFilePath);
        if (!file.exists()) return;

        try {
            List<String> lines = Files.readAllLines(file.toPath());
            lines.removeIf(line -> line.trim().equals(path));
            Files.write(file.toPath(), lines);
        } catch (IOException e) {
            System.out.println("Error removing path from category file: " + e.getMessage());
        }
    }
    
    private static void removeRecipePathFromCategoryFile(Recipe recipe) {
        String category = recipe.getCategory();  // e.g., "try", "popular"

        // Build the correct file name: ID + "_" + safe recipe name
        String recipeFileName = recipe.getId() + "_" + recipe.getRecipeName().replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";

        // Match exactly how the path is saved in category file
        String path = "server/recipes/" + recipeFileName;

        removePathFromCategoryFile(category, path);
    }

                                                    
    public static void loadRegisteredUsers() {
        File usersDir = new File("server/users");
        if (!usersDir.exists() || !usersDir.isDirectory()) {
            System.out.println("No users found yet.");
            return;
        }

        File[] userDirs = usersDir.listFiles(File::isDirectory);
        if (userDirs == null) return;

        for (File userDir : userDirs) {
            File userFile = new File(userDir, "user.txt");
            if (userFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
                    String username = reader.readLine();
                    String passwordHash = reader.readLine();

                    if (username != null && passwordHash != null) {
                        User user = new User(username, passwordHash, true);
                        users.add(user);
                    }

                } catch (IOException e) {
                    System.out.println("Failed to load user from " + userFile.getName());
                }
            }
        }
    }
    
    public static void loadRegisteredAdmins() {
        File adminsDir = new File("server/admins");
        if (!adminsDir.exists() || !adminsDir.isDirectory()) {
            System.out.println("No admins found.");
            return;
        }

        File[] adminDirs = adminsDir.listFiles(File::isDirectory);
        if (adminDirs == null) return;

        for (File adminDir : adminDirs) {
            File userFile = new File(adminDir, "user.txt");
            if (userFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
                    String username = reader.readLine();
                    String passwordHash = reader.readLine();

                    if (username != null && passwordHash != null) {
                        Admin admin = new Admin(username, passwordHash, true); // assuming hashed password
                        admins.add(admin);
                    }
                } catch (IOException e) {
                    System.out.println("Failed to load admin from " + userFile.getPath());
                }
            } else {
                System.out.println("Missing user.txt in " + adminDir.getName());
            }
        }
    }

    public static void loadRegisteredModerators() {
        File modsDir = new File("server/moderators");
        if (!modsDir.exists() || !modsDir.isDirectory()) {
            System.out.println("No moderators found yet.");
            return;
        }

        File[] modDirs = modsDir.listFiles(File::isDirectory);
        if (modDirs == null) return;

        for (File modDir : modDirs) {
            File userFile = new File(modDir, "user.txt");
            if (userFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
                    String username = reader.readLine();
                    String passwordHash = reader.readLine();

                    if (username != null && passwordHash != null) {
                        Moderator mod = new Moderator(username, passwordHash, true);
                        moderators.add(mod);
                    }

                } catch (IOException e) {
                    System.out.println("Failed to load moderator from " + userFile.getName());
                }
            }
        }
    }

    
    private static User loginMenu() {
        while (true) {
            System.out.println("\n===== Welcome to the Recipe Management System =====");
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("Choose an option (1-3): ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    register();
                    break;
                case "2":
                    User loggedInUser = login();
                    if (loggedInUser != null) {
                        return loggedInUser;  // Return user on successful login
                    } else {
                        System.out.println("Login failed. Please try again.");
                    }
                    break;
                case "3":
                    System.out.println("Exiting program. Goodbye!");
                    System.exit(0);
                    break;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }

    
    private static void register() {
        String username;

        while (true) {
            System.out.print("Enter a new username: ");
            username = scanner.nextLine().trim();

            if (username.isEmpty()) {
                System.out.println("Username cannot be empty.");
                continue;
            }

            boolean exists = false;
            for (User user : users) {
                if (user.getUsername().equalsIgnoreCase(username)) {
                    System.out.println("Username already exists. Please choose a different username.");
                    exists = true;
                    break;
                }
            }

            if (!exists) break;
        }

        // Now ask for password (could add password confirmation too)
        String password;
        while (true) {
            System.out.print("Enter a password: ");
            password = scanner.nextLine();

            if (password.isEmpty()) {
                System.out.println("Password cannot be empty.");
            } else {
                break;
            }
        }

        User newUser = new User(username, password);
        users.add(newUser);
        try {
        createUserFolderAndFiles(newUser, password);  // <-- call here
            System.out.println("Registration successful. You can now log in.");
        } catch (IOException e) {
            System.out.println("Failed to create user data folder: " + e.getMessage());
            users.remove(newUser);  // Remove user if creation failed
        }
    }
    
    public static void createUserFolderAndFiles(User user, String password) throws IOException {
        String userFolderPath = "server/users/" + user.getUsername();

        File userFolder = new File(userFolderPath);
        if (!userFolder.exists()) {
            userFolder.mkdirs();
        }

        File userFile = new File(userFolderPath, "user.txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(userFile))) {
            writer.println( user.getUsername());
            writer.println( user.getPasswordHash());
        }

        File recipesFile = new File(userFolderPath, "recipes.txt");
        if (!recipesFile.exists()) {
            recipesFile.createNewFile();
        }
    }


    
    private static User login() {
        System.out.print("Enter your username: ");
        String username = scanner.nextLine().trim();

        System.out.print("Enter your password: ");
        String password = scanner.nextLine();

        // Check Admins
        for (Admin admin : admins) {
            if (admin.getUsername().equalsIgnoreCase(username)) {
                if (admin.checkPassword(password)) {
                    currentUser = admin;
                    System.out.println("Welcome back, Admin " + currentUser.getUsername() + "!");
                    return currentUser;
                } else {
                    System.out.println("Incorrect password.");
                    return null;
                }
            }
        }

        // Check Moderators
        for (Moderator mod : moderators) {
            if (mod.getUsername().equalsIgnoreCase(username)) {
                if (mod.checkPassword(password)) {
                    currentUser = mod;
                    System.out.println("Welcome back, Moderator " + currentUser.getUsername() + "!");
                    return currentUser;
                } else {
                    System.out.println("Incorrect password.");
                    return null;
                }
            }
        }

        // Check Regular Users
        for (User user : users) {
            if (user.getUsername().equalsIgnoreCase(username)) {
                if (user.checkPassword(password)) {
                    currentUser = user;
                    System.out.println("Welcome back, " + currentUser.getUsername() + "!");
                    return currentUser;
                } else {
                    System.out.println("Incorrect password.");
                    return null;
                }
            }
        }

        System.out.println("User not found. Please register first.");
        return null;
    }


    
    
        

    
    
    public static void writeRecipeToFile(Recipe recipe) {
        // Create the recipes folder inside server folder
        String recipesDir = "server/recipes/";
        File recipesFolder = new File(recipesDir);
        if (!recipesFolder.exists()) {
            recipesFolder.mkdirs();
        }

        // Sanitize recipe name to remove invalid filename characters
        String safeRecipeName = recipe.getRecipeName().replaceAll("[\\\\/:*?\"<>|]", "_");

        // Compose filename as recipeID_recipename.txt
        String safeFileName = recipesDir + recipe.getId() + "_" + safeRecipeName + ".txt";

        try (PrintWriter writer = new PrintWriter(new FileWriter(safeFileName))) {
            writer.println(recipe.getRecipeName());
            writer.println(recipe.getPostedDate());
            writer.println(recipe.getOwner().getUsername());
            writer.println(recipe.getCategory());
            writer.println(recipe.getLikesCount());
            writer.println("LikedBy:");
            for (User u : recipe.getLikedUsers()) {
                writer.println(u.getUsername());
            }
            
            
            writer.println("Ingredients:");
            for (Ingredient ingredient : recipe.getIngredients()) {
                writer.println(" - " + ingredient);
            }
            writer.println("Instructions:");
            int i = 1;
            for (String instruction : recipe.getCookingInstructions()) {
                writer.println(i++ + ". " + instruction);
            }
            writer.println("Comments (" + recipe.getComments().size() + "):");
            for (Comment comment : recipe.getComments()) {
                writer.println(" - " + comment.getUser().getUsername() + ": " + comment.getText() + " [Likes: " + comment.getLikesCount() + "]");
            }
        } catch (IOException e) {
            System.out.println("Error writing recipe file: " + e.getMessage());
            return;
        }

        // Append the path of this recipe file to the user's recipes.txt
        try {
            String userFolderPath = "server/users/" + recipe.getOwner().getUsername();
            File userFolder = new File(userFolderPath);
            if (!userFolder.exists()) {
                userFolder.mkdirs();
            }

            File recipesListFile = new File(userFolderPath, "recipes.txt");
            if (!recipesListFile.exists()) {
                recipesListFile.createNewFile();
            }

            List<String> existingPaths = Files.readAllLines(recipesListFile.toPath());
            if (!existingPaths.contains(safeFileName)) {
                try (PrintWriter writer = new PrintWriter(new FileWriter(recipesListFile, true))) {
                    writer.println(safeFileName);
                }
            }
        } catch (IOException e) {
            System.out.println("Error updating user's recipes.txt: " + e.getMessage());
        }

        System.out.println("Recipe written to file: " + safeFileName);
    }
    
    public static Recipe loadRecipeFromFile(File recipeFile, User owner) {
        // Extract recipeId from filename: e.g., "12_jello.txt" -> id=12
        String filename = recipeFile.getName();
        int underscoreIndex = filename.indexOf('_');
        int recipeId = -1;
        if (underscoreIndex > 0) {
            try {
                recipeId = Integer.parseInt(filename.substring(0, underscoreIndex));
            } catch (NumberFormatException e) {
                System.out.println("Invalid recipe id in filename: " + filename);
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(recipeFile))) {
            String recipeName = reader.readLine();   // First line: recipe name
            String postedLine = reader.readLine();  // e.g., "2025-06-02T18:30"
            LocalDateTime postedDate = LocalDateTime.parse(postedLine);  // parse ISO string
            String author = reader.readLine();
            User recipeOwner = findUserByUsername(author);
            String categoryLine = reader.readLine();
            
            
            
            int likes = Integer.parseInt(reader.readLine());  // Second line: likes count
            String line = reader.readLine();
            ArrayList<User> likedUsers = new ArrayList<>();

            if (line != null && line.equals("LikedBy:")) {
                // Read usernames until you reach "Ingredients:" line
                while ((line = reader.readLine()) != null && !line.equals("Ingredients:")) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        User likedUser = searchUserByUsername(line);
                        if (likedUser != null) {
                            likedUsers.add(likedUser);
                        } else {
                            likedUsers.add(new User(line, ""));
                        }
                    }
                }
            } else {
                throw new IOException("Expected 'LikedBy:' line but found: " + line);
            }

            // At this point, 'line' is "Ingredients:" or null
            if (line == null || !line.equals("Ingredients:")) {
                throw new IOException("Expected 'Ingredients:' line");
            }
            
            ArrayList<Ingredient> ingredients = new ArrayList<>();
            //line = reader.readLine();
            while ((line = reader.readLine()) != null && !line.equals("Instructions:")) {
                line = line.trim();
                if (line.startsWith("- ")) {
                    String ingredientLine = line.substring(2).trim();  // remove "- "
                    // parse quantity, unit and name
                    // example: "1.0 unit of jello"
                    try {
                        // Split on space first
                        String[] parts = ingredientLine.split(" ");
                        double quantity = Double.parseDouble(parts[0]);
                        String unit = parts[1];
                        // The rest after "of" is the name
                        int ofIndex = ingredientLine.indexOf(" of ");
                        String name;
                        if (ofIndex != -1) {
                            name = ingredientLine.substring(ofIndex + 4).trim(); // skip " of "
                        } else {
                            // fallback if "of" is missing
                            name = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
                        }

                        Ingredient ingredient = new Ingredient(name, quantity, unit);
                        ingredients.add(ingredient);
                    } catch (Exception e) {
                        System.out.println("Failed to parse ingredient line: " + ingredientLine);
                    }
                }
            }

            if (line == null || !line.equals("Instructions:")) {
                throw new IOException("Expected 'Instructions:' line");
            }

            ArrayList<String> instructions = new ArrayList<>();
            while ((line = reader.readLine()) != null && !line.startsWith("Comments")) {
                line = line.trim();
                if (!line.isEmpty()) {
                    int dotIndex = line.indexOf('.');
                    if (dotIndex > 0) {
                        instructions.add(line.substring(dotIndex + 1).trim());
                    } else {
                        instructions.add(line);
                    }
                }
            }

            if (line == null || !line.startsWith("Comments")) {
                throw new IOException("Expected 'Comments' line");
            }

            int commentCount = 0;
            int start = line.indexOf('(');
            int end = line.indexOf(')');
            if (start != -1 && end != -1 && end > start) {
                String numberStr = line.substring(start + 1, end);
                try {
                    commentCount = Integer.parseInt(numberStr);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid comment count: " + numberStr);
                }
            }


            ArrayList<Comment> comments = new ArrayList<>();
            for (int i = 0; i < commentCount; i++) {
                line = reader.readLine();
                if (line != null) {
                    line = line.trim();
                    if (line.startsWith(" - ")) {
                        line = line.substring(3);  // remove " - "
                    }

                    int colonIndex = line.indexOf(':');
                    if (colonIndex > 0) {
                        String username = line.substring(0, colonIndex).trim();
                        String remainder = line.substring(colonIndex + 1).trim();

                        int likesStart = remainder.lastIndexOf("[Likes:");
                        int likesEnd = remainder.lastIndexOf(']');
                        int likesCount = 0;

                        if (likesStart != -1 && likesEnd != -1 && likesEnd > likesStart) {
                            String likesString = remainder.substring(likesStart + 7, likesEnd).trim();
                            likesCount = Integer.parseInt(likesString);
                        }

                        String commentText = remainder.substring(0, likesStart).trim();

                        // Now create User and Comment objects
                        User commenter = findUserByUsername(username);
                        if (commenter == null) {
                            // User not found, create a new user or handle error
                            commenter = new User(username, ""); // or handle appropriately
                        }
                        Comment comment = new Comment(commentText, commenter);
                        comment.setLikesCount(likesCount);

                        // Add comment to your list or recipe
                        comments.add(comment);
                    }
                }
            }

            Recipe recipe = new Recipe(recipeName, recipeOwner);
            if (recipeId != -1) {
                recipe.setRecipeId(recipeId);
            }
            recipe.setLikesCount(likes);
            recipe.setLikedUsers(likedUsers);
            recipe.setIngredients(ingredients);
            recipe.setCookingInstructions(instructions);
            recipe.setComments(comments);
            recipe.setCategory(categoryLine);
            return recipe;

        } catch (IOException | NumberFormatException e) {
            System.out.println("Failed to load recipe from file " + recipeFile.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    public static User searchUserByUsername(String username) {
    for (User u : users) {
        if (u.getUsername().equalsIgnoreCase(username)) {
            return u;
        }
    }
    return null;
}

    public static void loadUserRecipes(User user) {
        recipes.clear();
        String userFolderPath = "server/users/" + user.getUsername();
        File recipesListFile = new File(userFolderPath, "recipes.txt");

        if (!recipesListFile.exists()) {
            System.out.println("No recipes file found for user " + user.getUsername());
            return;
        }

        try {
            List<String> recipeFilePaths = Files.readAllLines(recipesListFile.toPath());

            for (String recipeFilePath : recipeFilePaths) {
                File recipeFile = new File(recipeFilePath.trim());
                Recipe recipe = loadRecipeFromFile(recipeFile, user);
                if (recipe != null) {
                    user.addRecipe(recipe);
                    recipes.add(recipe);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading recipes list for user " + user.getUsername() + ": " + e.getMessage());
        }
    }

    
   





    //Method to create a new recipe and add it to the list
    private static Recipe createRecipe() {
        System.out.print("\n\nEnter recipe name: ");
        String name = scanner.nextLine();
    
        Recipe newRecipe = new Recipe(name, currentUser);
        newRecipe.updateCategory();
        currentUser.addRecipe(newRecipe);
        System.out.println("Recipe successfully created.");
        writeRecipeToFile(newRecipe); // Initially ingredients and instructions will be empty
        Recipe.saveCounter();
    	return newRecipe;
    }
    
    //Method to add ingredients to an existing recipe
    private static void addIngredients() {
        System.out.println("\n\nSelect a recipe to add ingredients:");
        Recipe recipe = selectUserRecipe("add ingredients");
        if (recipe == null) return;

        if (recipe.getIngredients().isEmpty()) {
            System.out.println("Add ingredients (type '/s' to save the ingredients):\n");
            while (true) {
                System.out.print("\nIngredient name: ");
                String ingredientName = scanner.nextLine();
                if (ingredientName.equalsIgnoreCase("/s")) break;

                double quantity = -1;
                while (quantity <= 0) {
                    System.out.print("Quantity: ");
                    if (scanner.hasNextDouble()) {
                        quantity = scanner.nextDouble();
                        scanner.nextLine(); // consume newline
                        if (quantity <= 0) {
                            System.out.println("Quantity must be positive.");
                        }
                    } else {
                        System.out.println("Please enter a valid number.");
                        scanner.nextLine(); // consume invalid input
                    }
                }

                System.out.print("Unit: ");
                String unit = scanner.nextLine();

                recipe.addIngredient(ingredientName, quantity, unit);
            }

            System.out.println("Ingredients successfully added.");
            writeRecipeToFile(recipe);
        } else {
            System.out.println("Ingredients have already been added in this recipe. You can only append to the existing ingredients.");
        }
    }

    //Method to add cooking instructions to an existing recipe
    private static void addInstructions() {
        System.out.println("\n\nSelect a recipe to add instructions:");
        Recipe recipe = selectUserRecipe("add instructions");
        if (recipe == null) return;

        int i = recipe.getCookingInstructions().size() + 1; // Continue from existing count if needed

        if (recipe.getCookingInstructions().isEmpty()) {
            System.out.println("Add cooking instructions (type '/s' to save the instructions):");
            while (true) {
                System.out.print("Instruction " + i + ": ");
                String instruction = scanner.nextLine();
                if (instruction.equalsIgnoreCase("/s")) break;
                recipe.addCookingInstruction(instruction);
                i++;
            }
            System.out.println("Recipe instructions created successfully.");
            writeRecipeToFile(recipe);
        } else {
            System.out.println("Instructions have already been added in this recipe. You can only append to the existing instructions.");
        }
    }


    //Method to modify an existing recipe
    private static void modifyRecipe() {
        System.out.println("\n\nSelect a recipe to modify:");
        Recipe recipe = selectUserRecipe("modify");
        if (recipe == null) return;

        System.out.println("1. Modify name\n2. Modify ingredient\n3. Modify instruction\n4. Append ingredient\n5. Append instruction");
        System.out.print("Choose an Option (1-5): ");
        int choice = scanner.nextInt();
        scanner.nextLine();

        switch (choice) {
            case 1: {
                System.out.print("\n\nEnter new name: ");
                String newName = scanner.nextLine();

                for (Recipe r : recipes) {
                    if (r != recipe && r.getRecipeName().equalsIgnoreCase(newName)) {
                        System.out.println("A recipe with this name already exists. Please choose a different name.");
                        return;
                    }
                }

                // Delete old file before renaming
                String oldFileName = "server/recipes/" + recipe.getId() + "_" + recipe.getRecipeName().replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
                java.io.File oldFile = new java.io.File(oldFileName);
                if (oldFile.exists()) {
                    if (oldFile.delete()) {
                        System.out.println("Old recipe file deleted: " + oldFileName);
                    } else {
                        System.out.println("Failed to delete old recipe file: " + oldFileName);
                    }
                }


                // Rename and save
                recipe.setRecipeName(newName);
                System.out.println("Recipe's name successfully changed.");
                writeRecipeToFile(recipe);
                break;
            }

            case 2: {
                if (!recipe.getIngredients().isEmpty()) {
                    System.out.print("\n\nEnter ingredient name to modify: ");
                    String ingredientName = scanner.nextLine();

                    if (recipe.checkIngredient(ingredientName) != 1) {
                        System.out.println("Ingredient not found in the recipe.");
                        return;
                    }

                    System.out.print("New name: ");
                    String newName = scanner.nextLine();

                    System.out.print("New quantity: ");
                    double quantity = scanner.nextDouble();
                    scanner.nextLine();

                    while (quantity <= 0) {
                        System.out.print("The quantity of the ingredient must be non-negative. Enter again: ");
                        quantity = scanner.nextDouble();
                        scanner.nextLine();
                    }

                    System.out.print("New unit: ");
                    String unit = scanner.nextLine();

                    recipe.modifyIngredient(newName, quantity, unit);
                    System.out.println("Ingredient successfully modified.");
                    writeRecipeToFile(recipe);
                } else {
                    System.out.println("No ingredients found. Please add ingredients before modifying.");
                }
                break;
            }

            case 3: {
                if (!recipe.getCookingInstructions().isEmpty()) {
                    System.out.print("\n\nEnter instruction number: ");
                    int index = scanner.nextInt();
                    scanner.nextLine();

                    recipe.modifyInstruction(index - 1);
                    writeRecipeToFile(recipe);
                } else {
                    System.out.println("No instructions found. Please add instructions before modifying.");
                }
                break;
            }

            case 4: {
                if (!recipe.getIngredients().isEmpty()) {
                    System.out.println("\n\nAppend ingredients (type '/s' to save ingredients):");
                    while (true) {
                        System.out.print("\nIngredient name: ");
                        String ingredientName = scanner.nextLine();
                        if (ingredientName.equalsIgnoreCase("/s")) break;

                        System.out.print("Quantity: ");
                        double quantity = scanner.nextDouble();
                        scanner.nextLine();

                        while (quantity <= 0) {
                            System.out.print("The quantity of the ingredient must be non-negative. Enter again: ");
                            quantity = scanner.nextDouble();
                            scanner.nextLine();
                        }

                        System.out.print("Unit: ");
                        String unit = scanner.nextLine();

                        recipe.addIngredient(ingredientName, quantity, unit);
                    }
                    System.out.println("Ingredients appended successfully.");
                    writeRecipeToFile(recipe);
                } else {
                    System.out.println("No ingredients found. Please add ingredients before appending.");
                }
                break;
            }

            case 5: {
                if (!recipe.getCookingInstructions().isEmpty()) {
                    int i = recipe.getCookingInstructions().size() + 1;
                    System.out.println("\n\nAppend cooking instructions (type '/s' to save):");
                    while (true) {
                        System.out.print("Instruction " + i + ": ");
                        String instruction = scanner.nextLine();
                        if (instruction.equalsIgnoreCase("/s")) break;
                        recipe.addCookingInstruction(instruction);
                        i++;
                    }
                    System.out.println("Instructions appended successfully.");
                    writeRecipeToFile(recipe);
                } else {
                    System.out.println("No instructions found. Please add instructions before appending.");
                }
                break;
            }

            default:
                System.out.println("\n\nInvalid choice. Please try again.");
                break;
        }
    }

    
    //Method to delete a recipe or its components
    private static void deleteRecipe() {
        System.out.println("\n\nSelect a Recipe Item to Delete: ");
        System.out.println("1. Delete a recipe");
        System.out.println("2. Delete an ingredient from a recipe");
        System.out.println("3. Delete an instruction from a recipe");
        System.out.print("Choose an Option (1-3): ");

        int choice = scanner.nextInt();
        scanner.nextLine();  //Consume the newline character

        switch (choice) {
            case 1:
                deleteEntireRecipe();
                break;

            case 2: {
                // Select the recipe first (only user's recipes)
                Recipe recipe = selectUserRecipe("delete ingredient from");
                if (recipe == null) return;

                System.out.print("Enter ingredient name to delete: ");
                String ingredientName = scanner.nextLine();
                if (recipe.deleteIngredient(ingredientName)) {
                    writeRecipeToFile(recipe);
                    System.out.println("Ingredient deleted successfully.");
                } else {
                    System.out.println("Ingredient not found.");
                }
                break;
            }

            case 3: {
                // Select the recipe first (only user's recipes)
                Recipe recipe = selectUserRecipe("delete instruction from");
                if (recipe == null) return;

                System.out.print("Enter instruction index to delete (starting from 1): ");
                int instructionIndex = scanner.nextInt();
                scanner.nextLine();  //Consume the newline character

                if (instructionIndex < 1 || instructionIndex > recipe.getCookingInstructions().size()) {
                    System.out.println("Invalid instruction index.");
                    return;
                }

                recipe.deleteInstruction(instructionIndex - 1);
                writeRecipeToFile(recipe);
                System.out.println("Instruction deleted successfully.");
                break;
            }

            default:
                System.out.println("\n\nInvalid choice. Please try again.");
                break;
        }
    }


    
    private static void deleteRecipeFile(Recipe recipe) {
        String safeFileName = recipe.getId() + "_" + recipe.getRecipeName().replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
        java.io.File file = new java.io.File("server/recipes/" + safeFileName); // add your folder path if needed

        if (file.exists()) {
            if (file.delete()) {
                System.out.println("Deleted recipe file: " + safeFileName);
            } else {
                System.out.println("Failed to delete the file: " + safeFileName);
            }
        } else {
            System.out.println("No file found for recipe: " + safeFileName);
        }
    }


    
    //Method to delete an entire recipe from the array list
    private static void deleteEntireRecipe() {
        System.out.println("\n\nSelect a recipe to delete:");
        Recipe recipe = selectUserRecipe("delete");
        if (recipe == null) return;
        removeRecipePathFromCategoryFile(recipe);
        deleteRecipeFile(recipe);
        recipes.remove(recipe);
        System.out.println("Recipe has been successfully deleted");
    }

    //Method to display a recipe's details by its name
    private static void interactWithRecipe(Recipe recipe) {
        recipe.displayRecipeDetails();

        if (!recipe.getOwner().equals(currentUser)) {
            while (true) {
                System.out.println("\nWhat would you like to do?");
                System.out.println("1. Like the recipe");
                System.out.println("2. Comment on the recipe");
                System.out.println("3. Like a comment");
                System.out.println("4. Exit interaction");
                System.out.print("Choose an option (1-4): ");
                String action = scanner.nextLine();

                switch (action) {
                    case "1":
                        recipe.likeRecipe(currentUser);
                        break;
                    case "2":
                        System.out.print("Enter your comment: ");
                        String commentText = scanner.nextLine();
                        recipe.addComment(commentText, currentUser);
                        break;
                    case "3":
                        ArrayList<Comment> comments = recipe.getComments();
                        if (comments.isEmpty()) {
                            System.out.println("There are no comments to like.");
                        } else {
                            System.out.println("\nComments:");
                            for (int i = 0; i < comments.size(); i++) {
                                System.out.println((i + 1) + ". " + comments.get(i));
                            }
                            System.out.print("Select comment number to like: ");
                            try {
                                int commentIndex = Integer.parseInt(scanner.nextLine());
                                if (commentIndex >= 1 && commentIndex <= comments.size()) {
                                    comments.get(commentIndex - 1).likeComment();
                                    System.out.println("Comment liked!");
                                } else {
                                    System.out.println("Invalid comment number.");
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Please enter a valid number.");
                            }
                        }
                        break;
                    case "4":
                        System.out.println("Exiting interaction menu.");
                        return;
                    default:
                        System.out.println("Invalid option. Try again.");
                }
            }
        } else {
            System.out.println("You are the author. You cannot like or comment on your own recipe.");
        }
    }

    
    private static void displayRecipe() {
        if (recipes.isEmpty()) {
            System.out.println("You have not added any recipes yet.");
            return;
        }

        System.out.println("\nYour Recipes:");
        for (int i = 0; i < recipes.size(); i++) {
            System.out.printf("[%d] %s (Category: %s)\n", i + 1, recipes.get(i).getRecipeName(), recipes.get(i).getCategory());
        }

        System.out.print("\nEnter the number of the recipe you want to view (or 0 to cancel): ");
        int choice;

        try {
            choice = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a number.");
            return;
        }

        if (choice <= 0 || choice > recipes.size()) {
            System.out.println("Cancelled or invalid selection.");
            return;
        }

        Recipe selectedRecipe = recipes.get(choice - 1);
        System.out.println("\n=== Recipe Details ===");
        selectedRecipe.displayRecipeDetails();

        System.out.println("\nThis is your recipe. You cannot like or comment on it.");
    }

    
    
    
    private static Recipe selectUserRecipe(String action) {
        ArrayList<Recipe> userRecipes = new ArrayList<>();
        int index = 1;

        for (Recipe recipe : recipes) {
            if (recipe.getOwner().equals(currentUser)) {
                System.out.println(index + ". " + recipe.getRecipeName());
                userRecipes.add(recipe);
                index++;
            }
        }

        if (userRecipes.isEmpty()) {
            System.out.println("You have no recipes to " + action + ".");
            return null;
        }

        System.out.print("Enter the number of the recipe to " + action + ": ");
        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        if (choice < 1 || choice > userRecipes.size()) {
            System.out.println("Invalid choice.");
            return null;
        }

        return userRecipes.get(choice - 1);
    }

}