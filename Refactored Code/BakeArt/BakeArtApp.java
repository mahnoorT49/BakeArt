import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


//CONFIGURATION AND UTILITIES CLASSES
// Configuration class for entralized file path management
class Config {
    // Base directories
    public static final String BASE_DIR = "server/";
    public static final String RECIPE_PATH = BASE_DIR + "recipes/";
    public static final String USER_PATH = BASE_DIR + "users/";
    public static final String ADMIN_PATH = BASE_DIR + "admins/";
    public static final String MOD_PATH = BASE_DIR + "moderators/";
    public static final String CAT_PATH = BASE_DIR + "categories/";
    public static final String APP_PATH = BASE_DIR + "applications/";
    public static final String VERIFY_PATH = BASE_DIR + "verification/";

    // Files and keys for counters and master key for admin account creation
    public static final String COUNTER_FILE = BASE_DIR + "counter.txt";
    public static final String ADMIN_MASTER_KEY = "BakeArt-Secure-Admin-2026-X99-Z102";

    /*Funtion to create all required folders if they don't already exist 
    Ensures the entire server environment is ready before the app starts.*/
    public static void initializeFolders() {
        String[] folders = { BASE_DIR, RECIPE_PATH, USER_PATH, ADMIN_PATH, MOD_PATH, CAT_PATH, APP_PATH, VERIFY_PATH };
        for (String f : folders) {
            File dir = new File(f);
            if (!dir.exists()) dir.mkdirs();
        }
    }
}

// Class for utility methods for file handling
class FileUtils {
    // Replaces invalid filename characters with underscores
    public static String sanitize(String name) {
        if (name == null) return "unnamed";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}


/* STRUCTURAL PATTERN: Adapter interface for storing and retrieving data.
   This is the contract for how data is saved. It hides the complexity of File I/O from the rest of the application.
*/
interface IDataStore {
    void save(String path, List<String> data) throws IOException;
    List<String> load(String path) throws IOException;
    boolean delete(String path);
    boolean exists(String path);
}

/* Concrete Adapter: File-based implementation of IDataStore
   class containing the actual BufferedReader/FileWriter logic
 */
class FileStoreAdapter implements IDataStore {
    // Function to save file
    @Override public void save(String path, List<String> data) throws IOException {
        File file = new File(path);  // automatically create parent folders if they don't exist
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(path))) {
            for (String line : data) writer.println(line);
        }
    }
    // Function to load file
    @Override public List<String> load(String path) throws IOException {
        Path p = Paths.get(path);
        if (!Files.exists(p)) return new ArrayList<>();
        return Files.readAllLines(p);
    }
    // Function to delete file
    @Override public boolean delete(String path) {
        File f = new File(path);
        return f.exists() && f.delete();
    }
    // Function to check existence of file
    @Override public boolean exists(String path) {
        return new File(path).exists();
    }
}


/* BEHAVIORAL PATTERN: Observer handles notifications for likes, comments, and updates
   The observer interface represents the entities who need to be notified of the change
   The subject interface represents the entities whose change in state needs to be notified to the observer
 */
interface Observer {
    void update(String message);
}

interface Subject {
    void addObserver(Observer o);
    void removeObserver(Observer o);
    void notifyObservers(String message);
}

// Concrete Subject: A comment can receive likes and notify its owner
class Comment implements Subject {
    private String text;  // comment text
    private User commenter;  //User who wrote the comment
    private String parentRecipeName;  //Recipe name where comment was posted
    private int likes;  //Total likes on this comment
    private List<String> likedByUsers = new ArrayList<>();  // Stores usernames of people who already liked the comment
    private List<Observer> observers = new ArrayList<>();  //List of observers watching this comment

    public Comment(String text, User commenter, String parentRecipeName) {
        this.text = text;
        this.commenter = commenter;
        this.parentRecipeName = parentRecipeName;
        this.likes = 0;
        addObserver(commenter);  // Comment owner watches this comment for likes received
    }

    // Function for liking a comment
    public boolean likeComment(User liker) {
        if (likedByUsers.contains(liker.getUsername().toLowerCase())) return false;  // Prevent duplicate likes so one user can only like a comment once
        likedByUsers.add(liker.getUsername().toLowerCase());
        this.likes++;
        // Notify comment owner
        notifyObservers("User " + liker.getUsername() + " liked your comment '" +  this.text + "' on the recipe: '" + parentRecipeName + "'");
        return true;
    }
    
    // Getters and setters for comment attributes
    public int getLikesCount() { return likes; }
    public void setLikesCount(int l) { this.likes = l; } // Required for file loading
    public String getText() { return text; }
    public User getUser() { return commenter; }
    public List<String> getLikedByUsers() { return likedByUsers; }
    public void setLikedByUsers(List<String> likers) { this.likedByUsers = likers; }
    
    // Overriding the abstract fucntions form the subject interface
    @Override public void addObserver(Observer o) { if(!observers.contains(o)) observers.add(o); }
    @Override public void removeObserver(Observer o) { observers.remove(o); }
    @Override public void notifyObservers(String msg) { for (Observer o : observers) o.update(msg); }
    @Override public String toString() { return commenter.getUsername() + ": " + text + " (Likes: " + likes + ")"; }
}

// Concrete Subject: A recipe can receive likes, comments and notify its owner
class Recipe implements Subject {
    private int recipeId;              //  Unique identifier for the recipe
    private String recipeName;         //  Name of the recipe
    private String category = "Try";   //  Category/status (default value = "Try") showing how credible the recipe is
    private User owner;                //  Owner of the recipe (creator user)
    private LocalDateTime postedDate;  //  Timestamp when recipe was created
    private int likes = 0;             //  Total number of likes on this recipe
    
    // Core Data Structures representing full recipe functionality
    private List<Ingredient> ingredients = new ArrayList<>();  // List of ingredients used in the recipe
    private List<String> instructions = new ArrayList<>();     // Step-by-step cooking instructions
    private List<Comment> comments = new ArrayList<>();        // Comments made on this recipe
    private List<User> likedUsers = new ArrayList<>();         // Users who liked this recipe to prevent duplicate likes
    private List<Observer> observers = new ArrayList<>();      // Observers watching this recipe (notification recipients)

    public Recipe(String name, User owner) {
        this.recipeName = name;
        this.owner = owner;
        this.postedDate = LocalDateTime.now();  // Set creation timestamp
        addObserver(owner);  // The owner automatically observes their recipe for likes/comments
    }

    // Function to scale recipe: Scales ingredient quantities based on number of servings, Example: 2x servings allows doubling ingredient amounts
    public void scaleToServings(int servings) {
        for (Ingredient ing : ingredients) ing.scale(servings);
    }

    // Function for recipe comments: Adds a comment to the recipe and notifies the owner
    public void addComment(Comment c) {
        this.comments.add(c);
        this.owner.update("User " + c.getUser().getUsername() + " commented on your recipe: '" + this.recipeName + "'");  // Direct notification to owner and not all observers
    }

    // Function for recipe likes: Adds a like if user hasn't already liked the recipe and notifies the owner
    public void addLike(User liker) {
        if (!likedUsers.contains(liker)) {  // Prevent duplicate likes by checking if user has already liked the recipe
            this.likes++;
            this.likedUsers.add(liker);
            this.owner.update("User " + liker.getUsername() + " liked your recipe: '" + this.recipeName + "'");  // Direct notification to owner and not all observers
        }
    }

    // Overriding the abstract fucntions form the subject interface
    @Override public void addObserver(Observer o) { if(!observers.contains(o)) observers.add(o); }
    @Override public void removeObserver(Observer o) { observers.remove(o); }
    @Override public void notifyObservers(String msg) { for (Observer o : observers) o.update(msg); }

    // Getters and Setters for the recipe attributes
    public int getId() { return recipeId; }
    public void setId(int id) { this.recipeId = id; }
    public String getName() { return recipeName; }
    public void setName(String name) { this.recipeName = name; }
    public User getOwner() { return owner; }
    public String getCategory() { return category; }
    public List<Ingredient> getIngredients() { return ingredients; }
    public List<String> getInstructions() { return instructions; }
    public List<Comment> getComments() { return comments; }
    public List<User> getLikedUsers() { return likedUsers; }
    public int getLikes() { return likes; }
    public void setLikes(int l) { this.likes = l; }
    public LocalDateTime getPostedDate() { return postedDate; }
    
    // Function to update recipe category only if its category changes and notify the people who have liked that recipe
    public void setCategory(String newCategory) { 
        if (this.category != null && this.category.equals(newCategory)) return;
        this.category = newCategory; 
        notifyObservers("The status of your recipe '" + this.recipeName + "' has been updated to: " + category);
    }
}

// Concrete Observer: Users receive notifications from Recipes (likes, comments, updates) and Comments (likes)
abstract class User implements Observer {
    protected String username;                             // Username of the account
    protected String passwordHash;                         // Hashed password (SHA-256, not plain text)
    protected String role;                                 // Role of the user (user / admin / moderator)
    protected List<Recipe> myRecipes = new ArrayList<>();  // List of recipes created by this user
    protected NotificationCenter notifications;            // NotificationCenter Object to handle storing and displaying notifications

    public User(String username, String passwordHash, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.notifications = new NotificationCenter(username, role);  // Each user has their own notification manager
    }

    // Getters and setters for the User attributes
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    
    // Function to add a recipe created by this user
    public void addRecipe(Recipe r) { myRecipes.add(r); }

    // Function to return all recipes created by user
    public List<Recipe> getMyRecipes() { return myRecipes; }

    // Function for SHA-256 password encryption check: Checks whether input password matches stored hash.
    public boolean checkPassword(String rawPassword) { return hashPassword(rawPassword).equals(this.passwordHash); }

    // Password setter: Converts plain password into hashed version before storing
    public void setPassword(String rawPassword) { this.passwordHash = hashPassword(rawPassword); }

    // Hashong Function: Converts password into secure hash to prevent plain-text storage
    protected String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");  // Create SHA-256 message digest instance
            byte[] hashedBytes = md.digest(password.getBytes());  // Convert password to hashed byte array
            StringBuilder sb = new StringBuilder();  // Convert bytes to hexadecimal string
            for (byte b : hashedBytes) sb.append(String.format("%02x", b));  // Convert a byte array into a hexadecimal string and always keep 2 characters with 0 padding as 0a, ff
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashing error", e);  // Fail if hashing algorithm is not available
        }
    }

    //  Overriding the abstract fucntions form the observer interface
    @Override public void update(String msg) { notifications.add(msg); }  // Store incoming notification in user's inbox
    public void viewNotifications() { notifications.show(); }  // Displays all stored notifications for the user
}

// Concrete Users
// Regular application user
class RegularUser extends User { public RegularUser(String u, String p) { super(u, p, "user"); } }
// Admin user (system-level privileges)
class Admin extends User { public Admin(String u, String p) { super(u, p, "moderator"); } }
// Moderator user (content moderation privileges)
class Moderator extends User { public Moderator(String u, String p) { super(u, p, "admin"); } }


/* CREATIONAL PATTERN: Factory class to centralize object creation logic
   Creates appropriate different User object based on role */
class UserFactory {
    public static User createUser(String username, String passwordHash, String role) {
        if (role == null) return new RegularUser(username, passwordHash);  // If role is missing, default to regular user
        
        // Decide object type based on role
        switch (role.toLowerCase()) {
            case "admin": return new Admin(username, passwordHash);
            case "moderator": return new Moderator(username, passwordHash);
            default: return new RegularUser(username, passwordHash);
        }
    }
}


/* BEHAVIORAL PATTERN: Strategy decouples the search logic from the Menu
   Allows switching between different search at runtime without changing the main program logic
 */
interface SearchStrategy {
    List<Recipe> search(List<Recipe> source, String keyword);
}

// Concrete Strategy: Search Recipes by Name
class NameSearchStrategy implements SearchStrategy {
    //  Overriding the abstract fucntions form the strategy interface
    @Override public List<Recipe> search(List<Recipe> source, String keyword) {
        List<Recipe> results = new ArrayList<>();  // List to store matching recipes
        String lowerKeyword = keyword.toLowerCase();  // Convert keyword to lowercase for case-insensitive search
        for (Recipe r : source) {  // Loop through all recipes
            if (r.getName().toLowerCase().contains(lowerKeyword)) {  // Check if recipe name contains keyword
                results.add(r);
            }
        }
        return results;
    }
}

// Concrete Strategy: Search Recipes by Ingredient
class IngredientSearchStrategy implements SearchStrategy {
    //  Overriding the abstract fucntions form the strategy interface
    @Override public List<Recipe> search(List<Recipe> source, String keyword) {
        List<Recipe> results = new ArrayList<>();  // List to store matching recipes
        String lowerKeyword = keyword.toLowerCase();  // Convert keyword to lowercase for flexible matching
        for (Recipe r : source) {  // Loop through every recipe
            for (Ingredient ing : r.getIngredients()) {  // Check each ingredient inside recipe
                if (ing.getName().toLowerCase().contains(lowerKeyword)) {  // Match ingredient name
                    results.add(r);
                    break;  // Found one matching ingredient, move to next recipe
                }
            }
        }
        return results;
    }
}


// ARCHITECTURAL PATTERN: Repository Pattern
/* User Repository Class: Acts as a bridge between File Storage (IDataStore) and In-memory cache (HashMap)
   Separates database/file logic from business logic
*/
class UserRepository {
    private IDataStore storage;                             // Abstraction over file system operations
    private Map<String, User> userCache = new HashMap<>();  // In-memory cache for fast user lookup

    public UserRepository(IDataStore storage) { this.storage = storage; }

    /* Function to save user data into appropriate folder based on role
       File Structure:
       server/users/username/user.txt
       server/admins/username/user.txt
       server/moderators/username/user.txt

       Data Stored:
       Line 1 -> username
       Line 2 -> passwordHash
    */
    public void saveUser(User user, String role) throws IOException {
        // Decide folder based on role
        String folder = role.equalsIgnoreCase("admin") ? Config.ADMIN_PATH : role.equalsIgnoreCase("moderator") ? Config.MOD_PATH : Config.USER_PATH;
         // Build directory and file paths
        String userDir = folder + user.getUsername();
        String userFile = userDir + "/user.txt";
        
        // Prepare data to store
        List<String> data = new ArrayList<>();
        data.add(user.getUsername());
        data.add(user.getPasswordHash());
        
        storage.save(userFile, data);  // Save to file system for persistence
        userCache.put(user.getUsername(), user);  // Store in memory cache for immediate use
    }

    // Function to find user by username: Fast lookup from memory cache (no file access)
    public User findByUsername(String username) { return userCache.get(username); }

    // Function to load all types of users in one unified process
    public void loadAll() {
        loadFromFolder(Config.USER_PATH, "user");
        loadFromFolder(Config.ADMIN_PATH, "admin");
        loadFromFolder(Config.MOD_PATH, "moderator");
    }

    // Function to load a specific user by reading all user directories inside a role folder and reconstructing User objects using Factory Pattern
    private void loadFromFolder(String path, String role) {
        File folder = new File(path);
        File[] subDirs = folder.listFiles(File::isDirectory);  // Get all subdirectories (each represents a user)
        if (subDirs == null) return;

        for (File dir : subDirs) {  // Loop through all subdirectories
            File uFile = new File(dir, "user.txt");
            if (uFile.exists()) {  // Ensure file exists before reading
                try {
                    List<String> lines = storage.load(uFile.getPath());
                    if (lines.size() >= 2) {  // Validate file structure
                        User u = UserFactory.createUser(lines.get(0), lines.get(1), role);  // Recreate User object using Factory Pattern
                        userCache.put(u.getUsername(), u);  // Store in cache for quick access
                    }
                } catch (IOException e) {
                    System.out.println("Error loading user: " + dir.getName());
                }
            }
        }
    }

    // Function for moderator count: Returns number of moderators currently loaded in memory
    public int getModeratorCount() {
        int count = 0;
        for (User u : userCache.values()) { if (u instanceof Moderator) count++; }
        return count;
    }

    // Function to update a user's role: Replaces an existing user object with a new role-based instance using UserFactory 
    public void refreshUserRole(String username, String newRole) {
        User oldUser = userCache.get(username);
        if (oldUser != null) {
            User newUser = UserFactory.createUser(username, oldUser.getPasswordHash(), newRole);  // Create new user object with updated role
            userCache.put(username, newUser);  // Replace the old RegularUser object in the system's memory
        }
    }
}

/* Recipe Repository Class: Acts as the "core parser" between File Storage (IDataStore) and Domain objects (Recipe, Comment, Ingredient)
   Centralized file parsing logic, Supports complex serialization/deserialization, Maintains indexing (categories, users)
*/
class RecipeRepository {
    private IDataStore storage;            // Abstraction layer for file operations
    private UserRepository userRepo;       // UserRepository class object used to resolve user references during loading
    private static int recipeCounter = 0;  // Global counter for assigning unique recipe IDs

    public RecipeRepository(IDataStore storage, UserRepository userRepo) {
        this.storage = storage;
        this.userRepo = userRepo;
        loadCounter();  // Load last used recipe ID from disk
    }

    // Function to load recipe counter for unique recipe ID
    private void loadCounter() {
        try {
            List<String> lines = storage.load(Config.COUNTER_FILE);
            if (!lines.isEmpty()) recipeCounter = Integer.parseInt(lines.get(0).trim());
        } catch (Exception e) { recipeCounter = 0; }  // If file missing or corrupted, reset counter
    }

    /* Function to save recipe in file
       File Structure:
       Line 1 -> Recipe Name
       Line 2 -> Posted Date
       Line 3 -> Owner Username
       Line 4 -> Category
       Line 5 -> Likes
       Line 6 -> "LikedBy:" List of Users who liked the recipe
       Next   -> Usernames
       Then   -> Ingredients / Instructions / Comments
    */
    public void saveRecipe(Recipe r) {
        if (r.getId() == 0) {  // Assign ID if recipe is new
            r.setId(++recipeCounter);
            // Saves  newly assigned counter value to file  
            try { storage.save(Config.COUNTER_FILE, Arrays.asList(String.valueOf(recipeCounter))); } catch (IOException e) {}  // silent fail (counter persistence is non-critical)
        }

        String safeName = FileUtils.sanitize(r.getName());  // Sanitize filename to avoid invalid characters
        String path = Config.RECIPE_PATH + r.getId() + "_" + safeName + ".txt";  // Construct the Recipe file path
        
        List<String> d = new ArrayList<>();   // List to store the file data
        d.add(r.getName());                   // Line 1
        d.add(r.getPostedDate().toString());  // Line 2
        d.add(r.getOwner().getUsername());    // Line 3
        d.add(r.getCategory());               // Line 4
        d.add(String.valueOf(r.getLikes()));  // Line 5
        d.add("LikedBy:");                    // Line 6
        for (User u : r.getLikedUsers()) d.add(u.getUsername());
        
        // List of ingredients, instructions and comments
        d.add("Ingredients:");  
        for (Ingredient i : r.getIngredients()) d.add(" - " + i.toString());
        
        d.add("Instructions:");
        for (int i=0; i < r.getInstructions().size(); i++) { d.add((i + 1) + ". " + r.getInstructions().get(i)); }
        
        d.add("Comments (" + r.getComments().size() + "):");
        for (Comment c : r.getComments()) {  // Convert list of likers of that comment into single string
            String likersList = String.join(",", c.getLikedByUsers());
            d.add(" - " + c.getUser().getUsername() + ": " + c.getText() + " [Likes: " + c.getLikesCount() + "] {" + likersList + "}");
        }

        // Write to file and update indexes
        try {
            storage.save(path, d);
            updateCategoryIndex(r, path);
            updateUserIndex(r, path);
        } catch (IOException e) { System.out.println("Critical Error: Could not save recipe file."); }
    }

    // Function to load a single recipe from file and reconstructs Recipe object from saved text format
    public Recipe loadRecipe(File file) {
        try {
            List<String> lines = storage.load(file.getPath());  // List to store the file data read
            // Extracting the lin by line data to resonstruct the recipe object
            String name = lines.get(0);
            LocalDateTime date = LocalDateTime.parse(lines.get(1));
            User owner = userRepo.findByUsername(lines.get(2));
            
            Recipe r = new Recipe(name, owner);
            r.setId(Integer.parseInt(file.getName().split("_")[0]));
            r.setCategory(lines.get(3));
            r.setLikes(Integer.parseInt(lines.get(4)));

            int cursor = 6; // Start at "LikedBy:"
            // Parse the list of Users who liked the recipe
            while (cursor < lines.size() && !lines.get(cursor).equals("Ingredients:")) {  // Read everything before the heading of ingredients
                User u = userRepo.findByUsername(lines.get(cursor).trim());  // Trim the line at spaces to extract username
                if (u != null) r.getLikedUsers().add(u);  // Add to list of Likers
                cursor++;
            }
            
            cursor++; // Skip "Ingredients:" heading
            // Parse Ingredients line by line
            while (cursor < lines.size() && !lines.get(cursor).equals("Instructions:")) {  // Read everything before the heading of instructions
                String line = lines.get(cursor).trim().substring(2); // Remove " - "
                String[] parts = line.split(" ");  // Split line intro seperate strings at blank spaces
                double qty = Double.parseDouble(parts[0]);                   // Frist string represents quantity
                String unit = parts[1];                                      // Second string represents the unit
                String ingName = line.substring(line.indexOf(" of ") + 4);   // Moving to index after the index of the word " of " to reach the ingredient name
                r.getIngredients().add(new Ingredient(ingName, qty, unit));  // Rebuild and add ingredient
                cursor++;
            }

            cursor++; // Skip "Instructions:" heading
            // Parse Instructions line by line
            while (cursor < lines.size() && !lines.get(cursor).startsWith("Comments")) {  // Read everything before the heading of comments
                String line = lines.get(cursor).trim();
                int dot = line.indexOf('.');
                r.getInstructions().add(dot != -1 ? line.substring(dot + 1).trim() : line);  // Move to the index after the . and record it as an instruction
                cursor++;
            }

            // Parse Comments
            if (cursor < lines.size() && lines.get(cursor).startsWith("Comments")) {  // Read everything before the end of file
                int count = Integer.parseInt(lines.get(cursor).substring(lines.get(cursor).indexOf('(')+1, lines.get(cursor).indexOf(')')));  // Extract number of comments from the file
                for (int j = 0; j < count; j++) {  // Looping over every comment line
                    cursor++;
                    String cLine = lines.get(cursor).trim().substring(2);      // Remove " - "
                    String username = cLine.substring(0, cLine.indexOf(':'));  // Split username and comment text
                    String remainder = cLine.substring(cLine.indexOf(':') + 1);
                    // Extract comment data to reconstruct comment
                    int likesStart = remainder.lastIndexOf("[Likes:");  // Find likes section [Likes:X] by looking for the index of the end of this heading
                    String text = remainder.substring(0, likesStart).trim();
                    int likesEnd = remainder.lastIndexOf(']');
                    int lCount = Integer.parseInt(remainder.substring(likesStart + 7, likesEnd).trim());
                    
                    // Convert username to User object for the name of commenter
                    User commenter = userRepo.findByUsername(username);
                    Comment c = new Comment(text, commenter != null ? commenter : new RegularUser(username, ""), name);
                    c.setLikesCount(lCount);  // Find the number of likes received by a comment

                    // Parse Comment Likers
                    int lBrace = remainder.lastIndexOf('{');
                    int rBrace = remainder.lastIndexOf('}');
                    if (lBrace != -1 && rBrace > lBrace) {
                        String likersRaw = remainder.substring(lBrace + 1, rBrace);
                        if (!likersRaw.isEmpty()) {
                            // Split the comma-separated list and set it into the comment object
                            String[] likerArray = likersRaw.split(",");
                            c.setLikedByUsers(new ArrayList<>(Arrays.asList(likerArray)));  // Set a list of comment likers
                        }
                    }
                    r.getComments().add(c);  // Add comment to recipe
                }
            }
            return r;  // Successfully reconstruct the recipe
        } catch (Exception e) {
            System.out.println("Skipping corrupted recipe: " + file.getName());  // If file is corrupted or format breaks, skip it safely
            return null;
        }
    }
    
    // Function to update category index: Keeps track of recipes grouped by category
    private void updateCategoryIndex(Recipe r, String path) throws IOException {
        String catPath = Config.CAT_PATH + r.getCategory().toLowerCase().replace(" ", "_") + ".txt";
        List<String> lines = storage.exists(catPath) ? storage.load(catPath) : new ArrayList<>();  // Save the path of the recipe's updated category in respective category file
        if (!lines.contains(path)) {
            lines.add(path);
            storage.save(catPath, lines);
        }
    }

    // Function to update user index: Keeps track of recipes belonging to each user
    private void updateUserIndex(Recipe r, String path) throws IOException {
        String uPath = Config.USER_PATH + r.getOwner().getUsername() + "/recipes.txt";
        List<String> lines = storage.exists(uPath) ? storage.load(uPath) : new ArrayList<>();  // Save the path of the recipe belonging to the user in the particular user's recipe list file
        if (!lines.contains(path)) {
            lines.add(path);
            storage.save(uPath, lines);
        }
    }

    // Access to the private storage system object inside RecipeRepository 
    public IDataStore getStorage() {
        return this.storage;
    }

    // Function to delete a recipe
    public void deleteRecipe(Recipe r) {
        String safeName = FileUtils.sanitize(r.getName());
        String path = Config.RECIPE_PATH + r.getId() + "_" + safeName + ".txt";  // Remove from Recipe file  
        storage.delete(path);
        String catPath = Config.CAT_PATH + r.getCategory().toLowerCase().replace(" ", "_") + ".txt";  // Also remove from category index
        try {
            if (storage.exists(catPath)) {
                List<String> lines = storage.load(catPath);
                lines.remove(path);  // Remove the specific file path
                storage.save(catPath, lines);
            }                   
        } catch (IOException e) {System.out.println("Warning: Could not update category index for deleted recipe.");}
    }

    // Function to delete file by its old name
    public void deleteFileBySpecificName(int id, String oldName) {
        String safeOldName = FileUtils.sanitize(oldName);
        String path = Config.RECIPE_PATH + id + "_" + safeOldName + ".txt";  // Remove its path from the list of recipes
        storage.delete(path);
    }

    // Function to load all recipes
    public List<Recipe> loadAllRecipesFromDisk() {
        List<Recipe> all = new ArrayList<>();
        File folder = new File(Config.RECIPE_PATH);  // Path to the Recipe folder
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt"));  // Get names of all the recipes from the folder
        if (files != null) {
            for (File f : files) {
                Recipe r = loadRecipe(f);
                if (r != null) all.add(r);
            }
        }
        return all;
    }
}


/* CREATIONAL PATTERN: Singleton for SessionManager ensures only ONE instance of SessionManager exists throughout
   Manages the currently logged-in user session globally
*/
class SessionManager {
    private static SessionManager instance;
    private User currentUser;

    private SessionManager() {}  // Private constructor prevents direct instantiation

    public static SessionManager getInstance() {  // Function to provide global access point to the single instance and instance is created only when first requested
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    // Getters and setters for the logged-in user
    public User getCurrentUser() { return currentUser; } 
    public void setCurrentUser(User user) { this.currentUser = user; }
    
    // Function to check login status: Returns true if a user is logged in
    public boolean isLoggedIn() { return currentUser != null; }
    
    // Function to logout user: Claers session data
    public void logout() { this.currentUser = null; }
}


// SERVICE CLASSES
/* InteractionService Class: Handles all user interactions with recipes and comments
   Acts as a service layer between UI / Menu layer, Domain layer (Recipe, Comment), Repository layer (RecipeRepository)
*/
class InteractionService {
    private RecipeRepository recipeRepo;    // RecipeRepository object for persistence (save/load recipes)
    private RankingService rankingService;  // rankingService object that handles ranking/category updates after interactions

    public InteractionService(RecipeRepository repo, RankingService ranking) {
        this.recipeRepo = repo;
        this.rankingService = ranking;
    }

    // Function to like a recipe while enforcing rules: Cannot like own recipe and Cannot like twice and triggers Observer notification
    public void likeRecipe(Recipe recipe, User user) {
        if (recipe.getOwner().equals(user)) {  // Prevent self-like
            System.out.println("You cannot like your own recipe.");
            return;
        }
        if (recipe.getLikedUsers().contains(user)) {  // Prevent duplicate likes
            System.out.println("You have already liked this recipe.");
            return;
        }

        recipe.addLike(user);  // Delegate logic of liking and notification to Recipe entity
        rankingService.updateCategoryLogic(recipe, recipeRepo);  // Update ranking/category logic after interaction
        recipeRepo.saveRecipe(recipe);  // Persist updated recipe
    }

    // Function to comment on a recipe while enforcing the rule that cannot comment on own recipe
    public void addComment(Recipe recipe, User user, String text) {
        if (recipe.getOwner().equals(user)) {  // Prevent self-comment
            System.out.println("You cannot comment on your own recipe.");
            return;
        }
        
        Comment newComment = new Comment(text, user, recipe.getName());  // Create new comment object
        recipe.addComment(newComment);  // Commnet Entity handles logic and notification to observers
        rankingService.updateCategoryLogic(recipe, recipeRepo);  // Updating ranking after interaction
        recipeRepo.saveRecipe(recipe);  // Persist updated recipe
    }

    // Function to like a comment with rules: Cannot like own comment and Cannot like twice
    public void likeComment(Comment comment, User liker, Recipe recipe) {
        if (comment.getUser().getUsername().equalsIgnoreCase(liker.getUsername())) {  // Prevent liking own comment
            System.out.println("You cannot like your own comment.");
            return;
        }

        boolean success = comment.likeComment(liker);  // Delegate the liking logic to Comment entity

        if (success) {  // If true, the user hasn't liked it before
            recipeRepo.saveRecipe(recipe); // Save the new like to disk
            System.out.println("\nComment liked!");
        } else {  // If false, the user was already in the likedByUsers list
            System.out.println("\nYou have already liked this comment!");
        }
    }
}

/* RankingService Class: Handles recipe ranking/category progression based on: Number of likes and comments
   Category Flow: Try -> Popular -> Considered for Verification
        likes >= 20 AND comments >= 15 -> "Considered for Verification"
        likes >= 10 AND comments >= 5 -> "Popular"
        otherwise -> "Try"
    Verified recipes should never be downgraded if teh comments and number of likes change
*/
class RankingService {
    public void updateCategoryLogic(Recipe r, RecipeRepository repo) {
        String previousCategory = r.getCategory();  // Store old category to detect changes later
        // Current engagement statistics
        int likes = r.getLikes();
        int comments = r.getComments().size();

        if (likes >= 20 && comments >= 15) {
            r.setCategory("Considered for Verification");
        } else if (likes >= 10 && comments >= 5) {
            r.setCategory("Popular");
        } else {
            // Keep as 'Try' if it doesn't meet the AND criteria
            if (!previousCategory.equals("Verified")) { // Don't demote verified recipes
                 r.setCategory("Try");
            }
        }

        if (!previousCategory.equals(r.getCategory())) {  // If the category is different from before
            repo.saveRecipe(r);  // Save updated recipe state
            if (r.getCategory().equals("Considered for Verification")) {  // If recipe reached verification stage, create verification tracking file
                createVerificationFile(r);
            }
        }
    }

    // Function to create a verification file inside server/verification/ to track Recipe path, Votes in favor, Votes against, Moderator reviews
    private void createVerificationFile(Recipe r) {
        String fileName = Config.VERIFY_PATH + r.getId() + "_" + FileUtils.sanitize(r.getName()) + ".txt";  // Generate verification filename
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {  // Auto-close writer using try-with-resources
            writer.println(Config.RECIPE_PATH + r.getId() + "_" + FileUtils.sanitize(r.getName()) + ".txt");  // Store original recipe file path
            writer.println(0);  // Votes in favor
            writer.println(0);  // Votes against
            writer.println("reviews:");  // Reviews section header to record the reviews by moderators
            System.out.println("Status Change: Recipe '" + r.getName() + "' is now under Verification.");
        } catch (IOException e) {
            System.out.println("Failed to create verification record.");
        }
    }
}

/* BakingTimerService Class: Handles baking countdown timers for users
   Simulates a real oven timer inside the recipe application by acting as a utility/service layer
   Uses the Observer pattern to notify the user when the time is up.
*/
class BakingTimerService {
    /* get the time duration and the observer user as parameters 
       1. Display timer started message
       2. Create background thread
       3. Pause thread for specified time
       4. Notify user when timer ends
    */
    public void startBakingTimer(int minutes, User user) {  
        System.out.println("\n[TIMER STARTED]: Baking timer set for " + minutes + " minutes.");
        
        // Async thread to prevent blocking the CLI menus
        new Thread(() -> {
            try {
                Thread.sleep(minutes * 60000L);  // 60000 milli seconds = 1 minute
                user.update("DING! Your baking timer is up! Check your oven for '" + user.getUsername() + "''s recipe.");  // Notify user through Observer system inside the NOtification Tab
            } catch (InterruptedException e) {
                System.err.println("Timer interrupted.");  // Happens if thread is interrupted before timer completes
            }
        }).start();  // Start thread execution immediately
    }
}

/* Scaling Service Class: Scales the ingredients of a recipe based on a factor
   Allows users to automatically increase or decrease ingredient quantities for different serving counts
*/
class ScalingService {

    public void scale(Recipe recipe, int factor) {

        System.out.println("\n--- Scaled Ingredients for " + factor + " servings ---");
        // Loop through original ingredients
        for (Ingredient ing : recipe.getIngredients()) {
            double scaledQty = ing.getQuantity() * factor; // Calculate temporary scaled quantity
            System.out.println( " - " + scaledQty + " " + ing.getUnit() + " of " + ing.getName() );  // Display temporarily scaled ingredients
        }
    }
}


/* NotificationCenter Class: Handles notification storage, retrieval, and persistence for users 
   Each user has a notifications.txt file where notifications are saved
*/
class NotificationCenter {
    private String username;  // Username of the notification owner (notification file path determination)
    private String role;  // Role of the user (determining the folder of persistence)
    private static final int MAX_NOTIFICATIONS = 150;  // Maximum number of notifications allowed in storage, Prevents the notification file from growing infinitely

    public NotificationCenter(String username, String role) {
        this.username = username;
        this.role = role;
    }

    /* Function to determine the correct notifications.txt file path
       Example Path:
     * server/role/username/notifications.txt
    */
    private String getNotifPath() {
        String folder = role.equalsIgnoreCase("admin") ? Config.ADMIN_PATH : role.equalsIgnoreCase("moderator") ? Config.MOD_PATH : Config.USER_PATH;
        return folder + username + "/notifications.txt";
    }

    // Function to add a new notification and saves it to disk
    public void add(String msg) {
        // Prepare timestamp: [YYYY-MM-DD HH:mm]
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String newEntry = "[" + timestamp + "] " + msg;  // Combine timestamp with notification message

        try {
            File file = new File(getNotifPath());  // Open notification file
            List<String> allNotifs = new ArrayList<>();  // List to store all notifications
            // Load existing notifications if file exists
            if (file.exists()) {  allNotifs = Files.readAllLines(file.toPath()); }

            allNotifs.add(newEntry); // Add the new notification at the end

            // If notifications > 50, remove the oldest (at the top)
            while (allNotifs.size() > MAX_NOTIFICATIONS) { allNotifs.remove(0); }

            Files.write(file.toPath(), allNotifs);  // Save updated notifications back to disk

        } catch (IOException e) {
            System.err.println("Notification persistence failed for " + username);  // Error while saving notification
        }
    }
    
    // Function to display all notifications for the current user
    public void show() {  
        System.out.println("\n--- Notification Inbox (" + username + ") ---");
        try {
            File file = new File(getNotifPath());  // Open notifications file
            if (!file.exists() || file.length() == 0) {  // Check if notification file exists or is empty
                System.out.println("No notifications found.");
            } else {  // Read all notifications from file
                List<String> lines = Files.readAllLines(file.toPath());
                Collections.reverse(lines);  // Reverse order so newest notifications appear first
                for (String line : lines) { System.out.println(line); }  
            }
        } catch (IOException e) {
            System.out.println("Error reading notification history.");  // Error reading notifications
        }
        System.out.println("-------------------------------------------");
    }

    // Function to return total number of notifications stored
    public int getCount() {
        File file = new File(getNotifPath());  // Open notifications file
        if (!file.exists()) return 0;  // If file doesn't exist, notification count is 0
        try {  // Count total lines in notification file
            return (int) Files.lines(file.toPath()).count();
        } catch (IOException e) { return 0; }  // If reading fails, return 0
    }
}


// Ingredient Class: Represents a single ingredient used in a recipe, Stores the ingredient name, quantity, and measurement unit
class Ingredient {
    private String name;  // Name of the ingredient
    private double quantity;  // Numerical quantity of the ingredient
    private String unit;  // Measurement unit associated with the quantity

    public Ingredient(String name, double quantity, String unit) {
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
    }
    
    // Getters for ingredient attributes
    public String getName() { return name; }
    public double getQuantity() { return quantity; }
    public String getUnit() { return unit; }

    // Function to modify an ingredient
    public void modifyIngredient(String name, double quantity, String unit) {
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
    }

    // Function to convert the Ingredient object into a readable string format
    @Override public String toString() { return quantity + " " + unit + " of " + name; }
}





/* STRUCTURAL PATTERN: Facade provides a simplified interface to multiple subsystems (repositories, services, storage)
   Hides complexity from the client by exposing only high-level methods
   Manages authentication, recipe manipulation, interactions, scaling, timers, search, and system file I/O
*/
class BakeArtFacade {
    // Subsystem Objects : completely hidden from the client
    private RecipeRepository recipeRepo;          // Handles recipe persistence and manipulation
    private UserRepository userRepo;              // Manages user accounts and roles
    private InteractionService interactionService; // Manages likes, comments, and recipe interactions
    private RankingService rankingService;        // Provides ranking logic for recipes
    private BakingTimerService timerService;      // Provides baking timer functionality
    private ScalingService scalingService;        // Handles recipe scaling for servings
    private FileStoreAdapter storage;             // Abstracts file storage operations

    // Constructor: Initializes all subsystem objects and prepares storage folders
    public BakeArtFacade() {
        Config.initializeFolders();

        this.storage = new FileStoreAdapter();
        this.userRepo = new UserRepository(storage);
        this.userRepo.loadAll();

        this.rankingService = new RankingService();
        this.recipeRepo = new RecipeRepository(storage, userRepo);
        this.interactionService = new InteractionService(recipeRepo, rankingService);
        this.timerService = new BakingTimerService();
        this.scalingService = new ScalingService();
    }


    // AUTHENTICATION AND USER MANAGEMENT
    // Login Function to authenticate user credentials and sets active session
    public boolean login(String username, String password) {
        User user = userRepo.findByUsername(username);
        if (user != null && user.checkPassword(password)) {
            SessionManager.getInstance().setCurrentUser(user);
            return true;
        }
        return false;
    }

    // Register Function to register a new user with role assignment
    public boolean register(String username, String password, String token) {
        try {
            if (userRepo.findByUsername(username) != null) {
                return false;
            }

            String role = "user";
            if (token != null && token.equals(Config.ADMIN_MASTER_KEY)) {
                role = "admin";
            }

            User newUser = UserFactory.createUser(username, "", role);
            newUser.setPassword(password);
            userRepo.saveUser(newUser, role);

            return true;
        } catch (Exception e) { return false; }
    }

    // Function findUser encapsulates user lookup by username
    public User findUser(String username) { return userRepo.findByUsername(username); }

    // Function saveUser encapsulates saving user data with role persistence
    public void saveUser(User user, String role) throws IOException { userRepo.saveUser(user, role); }

    // Function getModeratorCount returns total number of moderators for Admin moderation logic
    public int getModeratorCount() { return userRepo.getModeratorCount(); }

    // Function promoteUserRole updates user role for Admin moderation logic */
    public void promoteUserRole(String username, String role) { userRepo.refreshUserRole(username, role); }

    
    // RECIPE MANIPULATION OPERATIONS
    // Function getAllRecipes loads all recipes from disk
    public List<Recipe> getAllRecipes() { return recipeRepo.loadAllRecipesFromDisk(); }

    // Function saveRecipe saves a recipe to disk
    public void saveRecipe(Recipe recipe) { recipeRepo.saveRecipe(recipe); }

    // Function deleteRecipe deletes a recipe from disk
    public void deleteRecipe(Recipe recipe) { recipeRepo.deleteRecipe(recipe); }

    // Function deleteOldRecipeFile deletes outdated recipe files (e.g., after renaming)
    public void deleteOldRecipeFile(int recipeId, String oldName) { recipeRepo.deleteFileBySpecificName(recipeId, oldName); }

    // Function loadRecipeFromFile loads a recipe object from a file
    public Recipe loadRecipeFromFile(File file) { return recipeRepo.loadRecipe(file); }


    // INTERACTION METHODS
    // Function likeRecipe adds a like to a recipe
    public void likeRecipe(Recipe recipe, User user) { interactionService.likeRecipe(recipe, user); }

    // Function addComment adds a comment to a recipe
    public void addComment(Recipe recipe, User user, String text) { interactionService.addComment(recipe, user, text); }

    // Function likeComment adds a like to a comment on a recipe
    public void likeComment(Comment comment, Recipe recipe, User user) { interactionService.likeComment(comment, user, recipe); }

    
    // SCAING AND TIMER METHODS
    // Function scaleRecipe adjusts recipe ingredient quantities based on servings
    public void scaleRecipe(Recipe recipe, int servings) { scalingService.scale(recipe, servings); }
    
    // Function startBakingTimer starts a baking timer for a user
    public void startBakingTimer(int minutes, User user) { timerService.startBakingTimer(minutes, user); }


    // SEARCH AND CATEGORY METHODS
    // Function searchRecipes searches recipes using a strategy and keyword
    public List<Recipe> searchRecipes(SearchStrategy strategy, String keyword) {
        List<Recipe> allRecipes = recipeRepo.loadAllRecipesFromDisk();
        return strategy.search(allRecipes, keyword);
    }

    // Function getRecipesByCategory loads recipes belonging to a specific category
    public List<Recipe> getRecipesByCategory(String category) {
        String path = Config.CAT_PATH + category + ".txt";
        List<Recipe> list = new ArrayList<>();
        try {
            List<String> recipePaths = storage.load(path);
            for (String p : recipePaths) {
                Recipe r = recipeRepo.loadRecipe(new File(p));
                if (r != null) list.add(r);
            }
        } catch (IOException e) {}
        return list;
    }

    // SYSTEM FILE I/O DELEGATION
    // Function loadSystemFile loads system file data
    public List<String> loadSystemFile(String path) throws IOException { return storage.load(path); }

    // Function saveSystemFile saves system file data (for Mod/Admin files)
    public void saveSystemFile(String path, List<String> data) throws IOException { storage.save(path, data); }
}


/* PRESENTATION LAYER: BakeArtApp acts as the CLI User Interface for the BakeArt system
   Connects user input/output with the Facade
   Provides menus for Admins, Moderators, and Users
   Delegates all business logic and persistence to BakeArtFacade
   Strict Encapsulation: No direct repository or service usage, only Facade calls
*/
class BakeArtApp {
    private Scanner scanner = new Scanner(System.in);  // Handles user input from terminal
    private BakeArtFacade facade;                      // Facade interface to subsystems
    private SearchStrategy searchStrategy;             // Strategy pattern for recipe search
    
    private List<Recipe> allLoadedRecipes = new ArrayList<>();  // All recipes loaded from disk
    private List<Recipe> displayedInFeed = new ArrayList<>();   // Recipes currently shown in feed

    // Constructor: initializes Facade and default search strategy
    public BakeArtApp() {
        this.facade = new BakeArtFacade();
        this.searchStrategy = new NameSearchStrategy();
    }

    // Main entry point: launches application loop
    public static void main(String[] args) { new BakeArtApp().start(); }


    // CORE LOOP AND SESSION MANAGEMENT
    /* Function start runs the main application loop
       If no user logged in, show login menu
       If logged in, route to Admin or User menu based on role
    */
    public void start() {
        while (true) {
            if (!SessionManager.getInstance().isLoggedIn()) {
                showLoginMenu();
            } else {
                User current = SessionManager.getInstance().getCurrentUser();
                
                if (current instanceof Admin) {
                    showAdminMenu();
                } else {
                    showUserMenu(); 
                }
            }
        }
    }


    // LOGIN AND REGISTRATION
    // Function showLoginMenu displays login/register options
    private void showLoginMenu() {
        System.out.println("\n===== Welcome to the Recipe Management System =====");
        System.out.println("1. Register\n2. Login\n3. Exit");
        System.out.print("Choose an option: ");
        String choice = scanner.nextLine();

        switch (choice) {
            case "1": handleRegister(); break;
            case "2": handleLogin(); break;
            case "3": System.exit(0);
        }
    }

    // Function to authenticate user and start session
    private void handleLogin() {
        System.out.print("Enter username: ");
        String u = scanner.nextLine();
        System.out.print("Enter password: ");
        String p = scanner.nextLine();

        if (facade.login(u, p)) System.out.println("Welcome back!");
        else System.out.println("Login failed.");
    }

    // Function handleRegister registers new user with optional admin token via Facade
    private void handleRegister() {
        String token = null;
        System.out.println("\n--- New Account Registration ---");
        System.out.print("Enter username: ");
        String u = scanner.nextLine().trim();
        if (facade.findUser(u) != null) {  // Finding Users using the facade
            System.out.println("Error: Username already taken.");
            return;
        }

        System.out.print("Enter password: ");
        String p = scanner.nextLine();

        System.out.print("Do you have a System Authorization Token? (yes/no): ");
        String hasToken = scanner.nextLine().trim().toLowerCase();
        
        String assignedRole = "user";

        if (hasToken.equals("yes")) {
            System.out.print("Enter Master System Token: ");
            token = scanner.nextLine();
            
            if (token.equals(Config.ADMIN_MASTER_KEY)) {
                System.out.println("[VERIFIED]: Admin access granted.");
                assignedRole = "admin";
            } else {
                System.out.println("[DENIED]: Invalid token. Registering as Regular User.");
            }
        }

        boolean success = facade.register(u, p, token);
        if (success) {
            System.out.println("Success! Account created as: " + assignedRole.toUpperCase());
        } else {
            System.out.println("Registration failed.");
        }
    }

    
    // PROFILE AND ACCOUNT MANAGEMENT
    // Function profileMenu allows user to change password securely via Facade
    private void profileMenu() {
        User current = SessionManager.getInstance().getCurrentUser();
        System.out.println("\n--- Profile: " + current.getUsername() + " ---");
        System.out.print("Do you want to change your password? (yes/no): ");
        
        if (scanner.nextLine().equalsIgnoreCase("yes")) {
            System.out.print("Enter current password: ");
            String oldPass = scanner.nextLine();

            if (current.checkPassword(oldPass)) {
                System.out.print("Enter new password: ");
                String newPass = scanner.nextLine();
                
                current.setPassword(newPass); 
                
                try { // Delegate saving the user safely using Facade
                    facade.saveUser(current, current.role); 
                    System.out.println("[SUCCESS]: Password changed and saved to your file.");
                } catch (IOException e) {
                    System.out.println("[ERROR]: Could not write to user file.");
                }
            } else {
                System.out.println("Incorrect current password.");
            }
        }
    }

    // Function applyForModerator lets user submit moderator application via Facade
    private void applyForModerator() {
        User current = SessionManager.getInstance().getCurrentUser();
        System.out.println("\n--- Moderator Application ---");
        System.out.print("Full Name: "); String name = scanner.nextLine();
        System.out.print("Baking Experience (years): "); String exp = scanner.nextLine();
        System.out.print("Specialty: "); String spec = scanner.nextLine();

        String content = "Name: " + name + "\nExp: " + exp + "\nSpecialty: " + spec;
        try {
            List<String> data = Arrays.asList(content);
            // File I/O delegation via facade
            facade.saveSystemFile(Config.APP_PATH + current.getUsername() + ".txt", data);
            System.out.println("Application submitted for Admin review!");
        } catch (IOException e) { 
            System.out.println("Failed to save application."); 
        }
    }

    /* Function handleMyAccount displays account options
       Options:
         - Profile & Password
         - My Recipes (Create/Modify/Delete)
         - Notifications
         - Moderator tasks or Apply for Moderator depending on the user role
    */
    private void handleMyAccount() {
        User current = SessionManager.getInstance().getCurrentUser();
        
        while (true) {
            if (current instanceof Admin) {
                System.out.println("\n--- Admin System Settings ---");
                System.out.println("1. Profile & Password\n2. System Admin Controls\n3. Back");
                String choice = scanner.nextLine();
                if (choice.equals("1")) profileMenu();
                else if (choice.equals("2")) handleAdminApprovals();
                else break;
                continue;
            }

            System.out.println("\n--- My Account: " + current.getUsername() + " ---");
            System.out.println("1. Profile & Password");
            System.out.println("2. My Recipes (Create/Modify/Delete)");
            System.out.println("3. Notifications Tab (" + current.notifications.getCount() + ")");

            if (current instanceof Moderator) {
                System.out.println("4. Moderator Task: Review Recipes");
            } else {
                System.out.println("4. Apply for Moderator Position");
            }

            System.out.println("5. Back to Main Menu");
            System.out.print("Choice: ");
            String choice = scanner.nextLine();

            switch (choice) {
                case "1": profileMenu(); break;
                case "2": showMyRecipesMenu(); break;
                case "3": current.viewNotifications(); break;
                case "4":
                    if (current instanceof Moderator) handleModeratorApprovals();
                    else applyForModerator();
                    break;
                case "5": return;
                default: System.out.println("Invalid choice.");
            }
        }
    }


    // FEED AND INTERACTION
    // Function showUserMenu displays bakeArt feed and navigation options
    private void showUserMenu() {
        System.out.println("\n\n=== bakeArt ===");
        refreshFeed();

        System.out.println("\nMain Menu:");
        System.out.println("1. Open a recipe\n2. Search recipes\n3. Filters\n4. My Account\n5. Log out");
        System.out.print("Choose an option: ");
        String choice = scanner.nextLine();

        switch (choice) {
            case "1": openRecipeFromFeed(); break;
            case "2": handleSearch(); break;
            case "3": handleFilters(); break;
            case "4": handleMyAccount(); break;
            case "5": SessionManager.getInstance().logout(); break;
        }
    }

    // Function showAdminMenu displays admin dashboard options
    private void showAdminMenu() {
        User current = SessionManager.getInstance().getCurrentUser();
        System.out.println("\n===== ADMIN SYSTEM DASHBOARD =====");
        System.out.println("Welcome, " + current.getUsername() + " (System Administrator)");
        System.out.println("----------------------------------");
        System.out.println("1. My Profile (Change Password)");
        System.out.println("2. Review Verification Cases (Seal of Approval)");
        System.out.println("3. Process Moderator Applications");
        System.out.println("4. Logout");
        System.out.print("Enter choice (1-4): ");

        String choice = scanner.nextLine();

        switch (choice) {
            case "1": profileMenu(); break;
            case "2": handleAdminApprovals(); break;
            case "3": reviewModeratorApplications(); break;
            case "4":
                SessionManager.getInstance().logout();
                System.out.println("Admin logged out.");
                break;
            default: System.out.println("Invalid selection.");
        }
    }

    // Function handleModeratorApprovals lets moderators vote on recipes for verification via Facade
    private void handleModeratorApprovals() {
        // FACADE FIX: Data retrieval hidden
        List<Recipe> considered = facade.getRecipesByCategory("considered_for_verification");
        if (considered.isEmpty()) {
            System.out.println("No recipes pending review.");
            return;
        }

        System.out.println("\n--- Recipes Pending Moderator Review ---");
        for (int i = 0; i < considered.size(); i++) {
            System.out.println((i + 1) + ". " + considered.get(i).getName());
        }

        System.out.print("Select recipe number to review: ");
        int idx;
        try {
            idx = Integer.parseInt(scanner.nextLine()) - 1;
        } catch (Exception e) { return; }

        if (idx < 0 || idx >= considered.size()) return;
        
        Recipe selected = considered.get(idx);

        System.out.println("\n>>> REVIEWING RECIPE DETAILS <<<");
        displayFullRecipeDetails(selected); 

        String vPath = Config.VERIFY_PATH + selected.getId() + "_" + FileUtils.sanitize(selected.getName()) + ".txt";
        try {
            // Delegated IO via facade
            List<String> lines = facade.loadSystemFile(vPath);
            int approvals = Integer.parseInt(lines.get(1));
            int disapprovals = Integer.parseInt(lines.get(2));

            System.out.println("\nCurrent Voting Status: " + approvals + " Approvals, " + disapprovals + " Disapprovals");
            System.out.println("1. Vote to APPROVE (Verified Status)");
            System.out.println("2. Vote to DISAPPROVE (Delete/Reject)");
            System.out.print("Your Vote (1-2): ");
            String vote = scanner.nextLine();

            System.out.print("Enter your professional review/critique: ");
            String review = scanner.nextLine();

            if (vote.equals("1")) {
                approvals++;
            } else {
                disapprovals++;
            }
            
            lines.set(1, String.valueOf(approvals));
            lines.set(2, String.valueOf(disapprovals));
            lines.add(SessionManager.getInstance().getCurrentUser().getUsername() + ": " + review);

            // Delegated IO via facade
            facade.saveSystemFile(vPath, lines);
            System.out.println("[SUCCESS]: Your review has been recorded. Admin will finalize the case once majority is reached.");

        } catch (IOException e) { 
            System.out.println("Error: Could not access verification file."); 
        }
    }

    // Function handleAdminApprovals allows admin to finalize recipe verification cases via Facade
    private void handleAdminApprovals() {
        File verificationFolder = new File(Config.VERIFY_PATH);
        File[] files = verificationFolder.listFiles((dir, name) -> name.endsWith(".txt"));

        if (files == null || files.length == 0) {
            System.out.println("No recipes are currently pending admin review.");
            return;
        }

        // FACADE FIX
        int moderatorCount = facade.getModeratorCount();
        int requiredMajority = (int) Math.ceil(moderatorCount * (2.0 / 3.0));

        List<File> eligibleFiles = new ArrayList<>();
        List<Recipe> eligibleRecipes = new ArrayList<>();

        System.out.println("\n--- Recipes Awaiting Final Decision (Majority: " + requiredMajority + ") ---");

        for (File vFile : files) {
            try {
                List<String> lines = facade.loadSystemFile(vFile.getPath());
                if (lines.size() < 3) continue;

                int approvals = Integer.parseInt(lines.get(1).trim());
                int disapprovals = Integer.parseInt(lines.get(2).trim());

                if (approvals >= requiredMajority || disapprovals >= requiredMajority) {
                    File recipeFile = new File(lines.get(0).trim());
                    // FACADE FIX
                    Recipe r = facade.loadRecipeFromFile(recipeFile);
                    if (r != null) {
                        eligibleFiles.add(vFile);
                        eligibleRecipes.add(r);
                    }
                }
            } catch (Exception e) {}
        }

        if (eligibleRecipes.isEmpty()) {
            System.out.println("No recipes have reached the moderator majority threshold yet.");
            return;
        }

        for (int i = 0; i < eligibleRecipes.size(); i++) {
            System.out.printf("[%d] %s (ID: %d)\n", i + 1, eligibleRecipes.get(i).getName(), eligibleRecipes.get(i).getId());
        }

        System.out.print("\nEnter recipe number to review (or 0 to cancel): ");
        int choice = Integer.parseInt(scanner.nextLine());
        if (choice <= 0 || choice > eligibleRecipes.size()) return;

        Recipe selectedRecipe = eligibleRecipes.get(choice - 1);
        File selectedVFile = eligibleFiles.get(choice - 1);

        System.out.println("\n>>> REVIEWING RECIPE CONTENT <<<");
        displayFullRecipeDetails(selectedRecipe); 

        try {
            // FACADE FIX
            List<String> lines = facade.loadSystemFile(selectedVFile.getPath());
            System.out.println("\n--- MODERATOR FEEDBACK ---");
            for (int i = 3; i < lines.size(); i++) {
                System.out.println(lines.get(i));
            }

            int approvals = Integer.parseInt(lines.get(1).trim());
            
            if (approvals >= requiredMajority) {
                System.out.print("\nModerator consensus is POSITIVE. Verify this recipe? (yes/no): ");
                if (scanner.nextLine().equalsIgnoreCase("yes")) {
                    selectedRecipe.setCategory("Verified");
                    facade.saveRecipe(selectedRecipe);
                    System.out.println("SUCCESS: Recipe verified.");
                }
            } else {
                System.out.print("\nModerator consensus is NEGATIVE. Delete this recipe? (yes/no): ");
                if (scanner.nextLine().equalsIgnoreCase("yes")) {
                    facade.deleteRecipe(selectedRecipe);
                    System.out.println("RECIPE DELETED.");
                }
            }

            selectedVFile.delete();
            System.out.println("Verification case closed.");

        } catch (IOException e) {
            System.out.println("Error processing the decision.");
        }
    }

    // Function reviewModeratorApplications allows admin to review the user applications to becoem moderators via facade
    private void reviewModeratorApplications() {
        File appDir = new File(Config.APP_PATH);
        File[] apps = appDir.listFiles((dir, name) -> name.endsWith(".txt") && !name.endsWith("_votes.txt"));

        if (apps == null || apps.length == 0) {
            System.out.println("No pending applications.");
            return;
        }

        for (int i = 0; i < apps.length; i++) {
            System.out.println((i + 1) + ". " + apps[i].getName().replace(".txt", ""));
        }

        System.out.print("Select application to review: ");
        int choice = Integer.parseInt(scanner.nextLine()) - 1;
        File selectedApp = apps[choice];
        String targetUser = selectedApp.getName().replace(".txt", "");

        try {
            List<String> lines = facade.loadSystemFile(selectedApp.getPath());
            System.out.println("\n--- APPLICATION CONTENT ---");
            for (String line : lines) System.out.println(line);
            System.out.println("---------------------------");
            
            System.out.print("Approve this user as Moderator? (yes/no): ");
            if (scanner.nextLine().equalsIgnoreCase("yes")) {
                promoteUserToModerator(targetUser);
                System.out.println("Application Approved.");
            } else {
                System.out.println("Application Rejected.");
            }

            if (selectedApp.delete()) {
                System.out.println("Application file cleared from system.");
            }

        } catch (IOException e) { 
            System.out.println("Error processing application file.");
        }
    }

    // Function promoteUserToModerator promotes a user to moderator
    private void promoteUserToModerator(String username) {
        try {
            Path source = Paths.get(Config.USER_PATH + username);
            Path target = Paths.get(Config.MOD_PATH + username);
            
            Files.walk(source).forEach(p -> {
                try {
                    Path dest = target.resolve(source.relativize(p));
                    if (Files.isDirectory(p)) Files.createDirectories(dest);
                    else Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) { throw new RuntimeException(e); }
            });
            
            Files.walk(source).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            
            // FACADE FIX: Delegation to memory array
            facade.promoteUserRole(username, "moderator");
            System.out.println("User promoted and folders moved successfully.");
        } catch (IOException e) { System.out.println("Promotion failed."); }
    }

    // Function refreshFeed loads recipes from Facade and organizes them by category
    private void refreshFeed() {
        this.allLoadedRecipes = facade.getAllRecipes();
        displayedInFeed.clear();

        if (allLoadedRecipes.isEmpty()) {
            System.out.println("\n[NOTICE]: The bakery is currently empty. Be the first to post a recipe!");
            return;
        }

        System.out.println("\n--- Suggested for You ---");

        String[] categories = {"Verified", "Popular", "Considered for Verification", "Try"};

        for (String cat : categories) {
            List<Recipe> categoryList = new ArrayList<>();
            for (Recipe r : allLoadedRecipes) {
                if (r.getCategory().equalsIgnoreCase(cat)) {
                    categoryList.add(r);
                }
            }

            if (!categoryList.isEmpty()) {
                System.out.println("\n>> " + cat.toUpperCase() + " <<");
                
                Collections.shuffle(categoryList);
                int limit = Math.min(5, categoryList.size());
                
                for (int i = 0; i < limit; i++) {
                    Recipe r = categoryList.get(i);
                    displayedInFeed.add(r);
                    
                    System.out.printf("[%d] %s (by %s)\n", 
                        displayedInFeed.size(), 
                        r.getName(), 
                        r.getOwner().getUsername()
                    );
                }
            }
        }
        System.out.println("\n--------------------------");
    }

    /* Function openRecipeFromFeed: Lets user interact with a recipe
       Options:
         - Like recipe
         - Comment
         - Scale ingredients
         - Start baking timer
         - Like a comment
    */
    private void openRecipeFromFeed() {
        if (displayedInFeed.isEmpty()) {
            System.out.println("No recipes displayed. Please search or filter first.");
            return;
        }

        System.out.print("\nEnter the number of the recipe to open (1-" + displayedInFeed.size() + "): ");
        int choice;
        try {
            choice = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            return;
        }

        if (choice < 1 || choice > displayedInFeed.size()) {
            System.out.println("Selection out of bounds.");
            return;
        }

        Recipe selected = displayedInFeed.get(choice - 1);
        displayFullRecipeDetails(selected);

        // Extract current user for stateless passing via facade
        User current = SessionManager.getInstance().getCurrentUser();

        boolean interacting = true;
        while (interacting) {
            System.out.println("\n--- What would you like to do with this recipe? ---");
            System.out.println("1. Like this recipe");
            System.out.println("2. Comment on this recipe");
            System.out.println("3. Scale Ingredients (Change Servings)");
            System.out.println("4. Start Baking Timer (Oven Notification)");
            System.out.println("5. Like a Comment");
            System.out.println("6. Back to Main Menu");
            System.out.print("Choose an option (1-6): ");
            
            String action = scanner.nextLine();

            switch (action) {
                case "1":
                    facade.likeRecipe(selected, current);
                    break;
                case "2":
                    System.out.print("Enter your comment: ");
                    String text = scanner.nextLine();
                    facade.addComment(selected, current, text);
                    break;
                case "3":
                    handleScaling(selected);
                    break;
                case "4":
                    handleTimer(current);
                    break;
                case "5":
                    handleLikeComment(selected, current);
                    break;
                case "6":
                    interacting = false;
                    break;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }

    // Function handleLikeComment allows user to like a specific comment on a recipe
    private void handleLikeComment(Recipe r, User current) {
        if (r.getComments().isEmpty()) {
            System.out.println("This recipe has no comments to like.");
            return;
        }

        System.out.println("\n--- Select a Comment to Like ---");
        for (int i = 0; i < r.getComments().size(); i++) {
            Comment c = r.getComments().get(i);
            System.out.printf("[%d] %s: %s (Likes: %d)\n", i + 1, c.getUser().getUsername(), c.getText(), c.getLikesCount());
        }

        System.out.print("Enter choice: ");
        try {
            int idx = Integer.parseInt(scanner.nextLine()) - 1;
            if (idx >= 0 && idx < r.getComments().size()) {
                facade.likeComment(r.getComments().get(idx), r, current);
            } else {
                System.out.println("Invalid selection.");
            }
        } catch (Exception e) {
            System.out.println("Invalid input.");
        }
    }

    // Function handleScaling scales recipe ingredients via Facade
    private void handleScaling(Recipe r) {
        System.out.print("This recipe is for 1 serving. How many servings do you need? ");
        try {
            int servings = Integer.parseInt(scanner.nextLine());
            if (servings <= 0) throw new NumberFormatException();
            
            facade.scaleRecipe(r, servings); 
            
        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid positive number.");
        }
    }

    // Function handleTimer starts baking timer via Facade
    private void handleTimer(User current) {
        System.out.print("Enter the required baking time in minutes: ");
        try {
            int mins = Integer.parseInt(scanner.nextLine());
            if (mins <= 0) throw new NumberFormatException();

            facade.startBakingTimer(mins, current);

            System.out.println("Timer started! You can continue using the app. We will notify you when it's done.");
        } catch (NumberFormatException e) {
            System.out.println("Invalid time entered.");
        }
    }


    // RECIPE MANAGEMENT
    // Function showMyRecipesMenu displays recipe management options
    private void showMyRecipesMenu() {
        System.out.println("\n1. Create Recipe\n2. Modify Recipe\n3. Delete Recipe\n4. Exit");
        String choice = scanner.nextLine();
        switch (choice) {
            case "1": createNewRecipe(); break;
            case "2": modifyRecipe(); break;
            case "3": deleteRecipe(); break; 
            case "4": return;
        }
    }

     // Function selectMyRecipe lets user choose one of their own recipes for actions
    private Recipe selectMyRecipe(String action) {
        User current = SessionManager.getInstance().getCurrentUser();
        List<Recipe> myRecipes = new ArrayList<>();

        for (Recipe r : allLoadedRecipes) {
            if (r.getOwner().getUsername().equalsIgnoreCase(current.getUsername())) {
                myRecipes.add(r);
            }
        }

        if (myRecipes.isEmpty()) {
            System.out.println("\n[ERROR]: You have no recipes to " + action + ".");
            return null;
        }

        System.out.println("\n--- Select a Recipe to " + action.toUpperCase() + " ---");
        for (int i = 0; i < myRecipes.size(); i++) {
            System.out.printf("[%d] %s (ID: %d)\n", i + 1, myRecipes.get(i).getName(), myRecipes.get(i).getId());
        }
        System.out.println("[0] Cancel");
        System.out.print("Enter choice (1-" + myRecipes.size() + "): ");

        try {
            int choice = Integer.parseInt(scanner.nextLine());
            if (choice == 0) return null;
            if (choice < 1 || choice > myRecipes.size()) {
                System.out.println("Invalid selection.");
                return null;
            }
            return myRecipes.get(choice - 1);
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a number.");
            return null;
        }
    }
    
    // Function createNewRecipe: Builds a new recipe with ingredients and instructions via Facade
    private void createNewRecipe() {
        System.out.print("Enter recipe name: ");
        String name = scanner.nextLine();
        Recipe r = new Recipe(name, SessionManager.getInstance().getCurrentUser());
        
        System.out.println("Add ingredients (type '/s' to save):");
        while (true) {
            System.out.print("Ingredient Name: ");
            String iName = scanner.nextLine();
            if (iName.equalsIgnoreCase("/s")) break;
            System.out.print("Quantity: ");
            double qty = Double.parseDouble(scanner.nextLine());
            System.out.print("Unit: ");
            String unit = scanner.nextLine();
            r.getIngredients().add(new Ingredient(iName, qty, unit));
        }

        System.out.println("Add instructions (type '/s' to save):");
        int step = 1;
        while (true) {
            System.out.print("Step " + step + ": ");
            String inst = scanner.nextLine();
            if (inst.equalsIgnoreCase("/s")) break;
            r.getInstructions().add(inst);
            step++;
        }

        facade.saveRecipe(r);
        System.out.println("Recipe created and saved.");
    }

    // Function modifyRecipe edits recipe name, ingredients, or instructions via Facade
    private void modifyRecipe() {
        Recipe selected = selectMyRecipe("modify");
        if (selected == null) return;

        String oldNameBeforeEdits = selected.getName();
        boolean nameChanged = false;

        System.out.println("\n--- Modifying Recipe: " + oldNameBeforeEdits + " ---");
        System.out.println("1. Edit Recipe Name");
        System.out.println("2. Edit Ingredient (Change Name, Quantity, and Unit)");
        System.out.println("3. Edit Instruction (Modify specific step)");
        System.out.print("Choose an option (1-3): ");
        String choice = scanner.nextLine();

        try {
            switch (choice) {
                case "1":
                    System.out.print("Enter New Recipe Name (was '" + oldNameBeforeEdits + "'): ");
                    String newName = scanner.nextLine();
                    if (!newName.trim().isEmpty()) {
                        selected.setName(newName);
                        nameChanged = true;
                    }
                    break;

                case "2":
                    if (selected.getIngredients().isEmpty()) {
                        System.out.println("This recipe has no ingredients to edit.");
                        return;
                    }
                    System.out.println("\nCurrent Ingredients:");
                    for (int i = 0; i < selected.getIngredients().size(); i++) {
                        System.out.println((i + 1) + ". " + selected.getIngredients().get(i));
                    }
                    System.out.print("Select ingredient number to modify: ");
                    int ingIdx = Integer.parseInt(scanner.nextLine()) - 1;
                    
                    if (ingIdx >= 0 && ingIdx < selected.getIngredients().size()) {
                        Ingredient ing = selected.getIngredients().get(ingIdx);
                        System.out.print("New Ingredient Name (was " + ing.getName() + "): ");
                        String n = scanner.nextLine();
                        System.out.print("New Quantity: ");
                        double q = Double.parseDouble(scanner.nextLine());
                        System.out.print("New Unit (e.g., cups, grams): ");
                        String u = scanner.nextLine();
                        
                        ing.modifyIngredient(n, q, u);
                    } else {
                        System.out.println("Invalid selection.");
                        return;
                    }
                    break;

                case "3":
                    if (selected.getInstructions().isEmpty()) {
                        System.out.println("This recipe has no instructions to edit.");
                        return;
                    }
                    System.out.println("\nCurrent Instructions:");
                    for (int i = 0; i < selected.getInstructions().size(); i++) {
                        System.out.println((i + 1) + ". " + selected.getInstructions().get(i));
                    }
                    System.out.print("Select instruction number to change: ");
                    int instIdx = Integer.parseInt(scanner.nextLine()) - 1;

                    if (instIdx >= 0 && instIdx < selected.getInstructions().size()) {
                        System.out.print("Enter new text for step " + (instIdx + 1) + ": ");
                        selected.getInstructions().set(instIdx, scanner.nextLine());
                    } else {
                        System.out.println("Invalid selection.");
                        return;
                    }
                    break;

                default:
                    System.out.println("Invalid choice.");
                    return;
            }

            // Delegate saving and deleting files via facade
            facade.saveRecipe(selected);

            if (nameChanged) {
                facade.deleteOldRecipeFile(selected.getId(), oldNameBeforeEdits);
                System.out.println("\n[SUCCESS]: Recipe renamed and old file removed.");
            } else {
                System.out.println("\n[SUCCESS]: Changes saved to existing file.");
            }

        } catch (Exception e) {
            System.out.println("\n[ERROR]: Could not update recipe. Please check your inputs.");
        }
    }
    
    // Function deleteRecipe deletes a recipe via Facade
    private void deleteRecipe() {
        Recipe selected = selectMyRecipe("delete"); 
        if (selected == null) return;

        System.out.print("Are you sure you want to delete '" + selected.getName() + "'? (yes/no): ");
        if (scanner.nextLine().equalsIgnoreCase("yes")) {
            facade.deleteRecipe(selected);
            allLoadedRecipes.remove(selected);
            displayedInFeed.remove(selected);
            System.out.println("Recipe deleted successfully.");
        }
    }

    /* Function displayFullRecipeDetails: Prints recipe details
       Shows name, owner, category, likes, ingredients, instructions, comments
    */
    private void displayFullRecipeDetails(Recipe r) {
        System.out.println("\n==================================");
        System.out.println("RECIPE: " + r.getName());
        System.out.println("Owner: " + r.getOwner().getUsername());
        System.out.println("Category: " + r.getCategory());
        System.out.println("Likes: " + r.getLikes());
        System.out.println("----------------------------------");
        System.out.println("INGREDIENTS:");
        for (Ingredient i : r.getIngredients()) System.out.println(" - " + i);
        System.out.println("\nINSTRUCTIONS:");
        for (int i=0; i<r.getInstructions().size(); i++) {
            System.out.println((i+1) + ". " + r.getInstructions().get(i));
        }
        System.out.println("\nCOMMENTS:");
        if (r.getComments().isEmpty()) {
            System.out.println("No comments yet.");
        } else {
            for (Comment c : r.getComments()) {
                System.out.println(" > " + c.getUser().getUsername() + ": " + c.getText() + " (Likes: " + c.getLikesCount() + ")");
            }
        }
        System.out.println("==================================");
    }


    // SEARCH MECHANISM
    // Function handleSearch uses the Strategy pattern to dynamically change the serach criteria
    private void handleSearch() {
        System.out.println("\n--- Search Recipes ---");
        System.out.println("1. Search by Name\n2. Search by Ingredient");
        System.out.print("Choice: ");
        String type = scanner.nextLine();
        
        if (type.equals("2")) searchStrategy = new IngredientSearchStrategy();
        else searchStrategy = new NameSearchStrategy();

        System.out.print("Enter keyword: ");
        String key = scanner.nextLine();
        
        // FACADE FIX: Completely self-contained search execution
        List<Recipe> results = facade.searchRecipes(searchStrategy, key);

        if (results.isEmpty()) {
            System.out.println("No matching recipes found.");
        } else {
            this.displayedInFeed = results;
            System.out.println("\nSearch Results:");
            for (int i = 0; i < displayedInFeed.size(); i++) {
                System.out.printf("[%d] %s (by %s)\n", i + 1, displayedInFeed.get(i).getName(), displayedInFeed.get(i).getOwner().getUsername());
            }
            openRecipeFromFeed();
        }
    }

    // Function handleFilters searches recipes by category vis facade implementation
    private void handleFilters() {
        System.out.println("\nFilter by Category:\n1. Try\n2. Popular\n3. Considered\n4. Verified");
        System.out.print("Choice: ");
        String choice = scanner.nextLine();
        String cat = "";
        switch(choice) {
            case "1": cat = "Try"; break;
            case "2": cat = "Popular"; break;
            case "3": cat = "Considered for Verification"; break;
            case "4": cat = "Verified"; break;
        }

        List<Recipe> filtered = new ArrayList<>();
        for (Recipe r : allLoadedRecipes) {
            if (r.getCategory().equalsIgnoreCase(cat)) filtered.add(r);
        }

        if (filtered.isEmpty()) {
            System.out.println("No recipes found in this category.");
        } else {
            this.displayedInFeed = filtered;
            System.out.println("\n--- Recipes in " + cat + " ---");
            for (int i = 0; i < displayedInFeed.size(); i++) {
                System.out.printf("[%d] %s (by %s)\n", i + 1, displayedInFeed.get(i).getName(), displayedInFeed.get(i).getOwner().getUsername());
            }
            openRecipeFromFeed();
        }
    }
}