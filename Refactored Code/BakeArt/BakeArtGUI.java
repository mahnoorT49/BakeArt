import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Comparator;

/* BAKEART GUI (Main Application): represents the front controller of the system
   GUI navigation (CardLayout), Backend service initialization, Session-based notifications, Central coordination between modules
 */
public class BakeArtGUI extends JFrame {
    // UI NAVIGATION SYSTEM
    // CardLayout allows switching between screens (LOGIN, DASHBOARD, etc.)
    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);

    // NOTIFICATION SYSTEM
    // Background thread monitors file-based notifications
    private Thread notifThread;
    private boolean keepWatching = false;
    
    // BACKEND LAYER REFERENCES
    // These represent services and repository layer
    private RecipeRepository recipeRepo;          // Handles recipe storage/retrieval
    private UserRepository userRepo;              // Handles user persistence
    private InteractionService interactionService;// Likes, comments, engagement logic
    private RankingService rankingService;        // Ranking logic (likes/popularity)
    private ScalingService scalingService;        // Recipe scaling logic
    private BakingTimerService timerService;      // Timer feature for baking

    // GLOBAL UI THEME
    // Centralized styling for consistency across all screens
    public static final Color BAKE_BROWN = new Color(80, 50, 20);
    public static final Color BAKE_CREAM = new Color(255, 250, 240);
    public static final Color BAKE_ACCENT = new Color(139, 69, 19);
    
    public static final Font TITLE_FONT = new Font("Serif", Font.BOLD, 42);
    public static final Font LARGE_FONT = new Font("Serif", Font.BOLD, 22);
    public static final Font MEDIUM_FONT = new Font("Serif", Font.PLAIN, 18);
    public static final Font RECIPE_NAME_FONT = new Font("SansSerif", Font.BOLD, 20);

    // CONSTRUCTOR
    public BakeArtGUI() {
        initBackend();  // Initialize backend services + repositories
        setupFrame();  // Configure JFrame properties

        // Register all UI screens into CardLayout system
        mainPanel.add(new GUILogin(this), "LOGIN");
        mainPanel.add(new GUIRegister(this), "REGISTER");
        mainPanel.add(new GUIDashboard(this), "DASHBOARD");
        mainPanel.add(new GUIMyAccount(this), "ACCOUNT");
        mainPanel.add(new GUIAuthorityPanel(this), "AUTHORITY");

        add(mainPanel);  // Attach main container
        cardLayout.show(mainPanel, "LOGIN");  // Start with login screen
    }

    // BACKEND INITIALIZATION
    private void initBackend() {
        Config.initializeFolders();                          // Ensures required directories exist (data persistence setup)
        FileStoreAdapter storage = new FileStoreAdapter();   // File-based storage adapter (persistence abstraction)
        this.userRepo = new UserRepository(storage);         // Repository initialization (data access layer)
        this.userRepo.loadAll();
        this.rankingService = new RankingService();
        this.recipeRepo = new RecipeRepository(storage, userRepo);

        // Business logic services
        this.interactionService = new InteractionService(recipeRepo, rankingService);
        this.scalingService = new ScalingService();
        this.timerService = new BakingTimerService();
    }

    // FRAME CONFIGURATION
    private void setupFrame() {
        setTitle("BakeArt - Premium Digital Bakery System");
        setSize(1300, 900);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    // SCREEN NAVIGATION CONTROLLER
    public void showScreen(String name) {
        // Refresh data before switching screens
        if (name.equals("DASHBOARD")) {
            for (Component c : mainPanel.getComponents()) {
                if (c instanceof GUIDashboard) ((GUIDashboard) c).refreshFeed(null);
            }
        } else if (name.equals("ACCOUNT")) {
            for (Component c : mainPanel.getComponents()) {
                if (c instanceof GUIMyAccount) ((GUIMyAccount) c).refreshUserDetails();
            }
        } else if (name.equals("AUTHORITY")) {
            for (Component c : mainPanel.getComponents()) {
                if (c instanceof GUIAuthorityPanel) ((GUIAuthorityPanel) c).refreshLists();
            }
        }
        // Switch visible screen
        cardLayout.show(mainPanel, name);
    }

    // NOTIFICATION SYSTEM
    /* This implements REAL-TIME notification simulation using file polling.
       Instead of sockets, system checks "notifications.txt" periodically.
     */
    public void startNotificationWatcher() {
        User u = SessionManager.getInstance().getCurrentUser();
        if (u == null) return;
        String fld = u.role.equals("admin") ? Config.ADMIN_PATH : u.role.equals("moderator") ? Config.MOD_PATH : Config.USER_PATH;
        new File(fld + u.getUsername()).mkdirs();
        keepWatching = true;
        notifThread = new Thread(() -> {
            File f = new File(fld + u.getUsername() + "/notifications.txt");
            int last = 0; if (f.exists()) try { last = (int) Files.lines(f.toPath()).count(); } catch (Exception e) {}
            while (keepWatching) {
                try {
                    Thread.sleep(2500);
                    if (f.exists()) {
                        List<String> lines = Files.readAllLines(f.toPath());
                        if (lines.size() > last) {
                            String msg = lines.get(lines.size() - 1);
                            last = lines.size();
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(this, "🔔 BAKEART NOTIFICATION:\n" + msg, "New Alert", JOptionPane.INFORMATION_MESSAGE);
                            });
                        }
                    }
                } catch (Exception e) {}
            }
        });
        notifThread.setDaemon(true); notifThread.start();
    }

    // Stop notification thread safely
    public void stopWatcher() { keepWatching = false; if (notifThread != null) notifThread.interrupt(); }

    // Getter methods for backend access (Dependency exposure)
    public UserRepository getUserRepo() { return userRepo; }
    public RecipeRepository getRecipeRepo() { return recipeRepo; }
    public InteractionService getInteractionService() { return interactionService; }
    public BakingTimerService getTimerService() { return timerService; }

    // APPLICATION ENTRY POINT
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
        SwingUtilities.invokeLater(() -> new BakeArtGUI().setVisible(true));
    }
}


// LOGIN AND REGISTRATION MODULE: represents the LOGIN SCREEN of the BakeArt system
class GUILogin extends JPanel {
    // Constructor receives reference to main controller (BakeArtGUI)
    public GUILogin(BakeArtGUI gui) {
        // UI LAYOUT CONFIGURATION
        setLayout(new GridBagLayout());                    // GridBagLayout allows flexible positioning of components
        setBackground(BakeArtGUI.BAKE_CREAM);              // Background theme color (global BakeArt design system)
        GridBagConstraints gbc = new GridBagConstraints(); // Layout constraints object used for positioning UI elements
        gbc.insets = new Insets(15, 15, 15, 15);           // spacing between components
        gbc.fill = GridBagConstraints.HORIZONTAL;          // stretch horizontally

        // UI COMPONENTS: TEXT ELEMENTS
        // Title
        JLabel title = new JLabel("BakeArt Login", SwingConstants.CENTER);
        title.setFont(BakeArtGUI.TITLE_FONT);
        title.setForeground(BakeArtGUI.BAKE_BROWN);

        // Labels with Large Font
        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(BakeArtGUI.LARGE_FONT);
        userLabel.setForeground(BakeArtGUI.BAKE_BROWN);

        // Password label
        JLabel passLabel = new JLabel("Password:");
        passLabel.setFont(BakeArtGUI.LARGE_FONT);
        passLabel.setForeground(BakeArtGUI.BAKE_BROWN);

        // UI COMPONENTS: INPUT FIELDS
        // Username input field
        JTextField uF = new JTextField(20);
        uF.setFont(BakeArtGUI.MEDIUM_FONT);

        // Password input field (masked input)
        JPasswordField pF = new JPasswordField(20);
        pF.setFont(BakeArtGUI.MEDIUM_FONT);
        
        // UI COMPONENTS: BUTTONS
        // Login button (primary action)
        JButton lBtn = new JButton("Login to Bakery");
        lBtn.setFont(BakeArtGUI.LARGE_FONT);

        // Highlighted styling for primary action
        lBtn.setBackground(BakeArtGUI.BAKE_ACCENT);
        lBtn.setForeground(Color.WHITE);
        lBtn.setPreferredSize(new Dimension(250, 50));

        // Registration navigation button
        JButton rBtn = new JButton("Create New Account");
        rBtn.setFont(BakeArtGUI.MEDIUM_FONT);
        rBtn.setForeground(BakeArtGUI.BAKE_BROWN);

        // LAYOUT ASSEMBLY (GRID POSITIONING)
        // Title at top spanning 2 columns
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        add(title, gbc);

        // Username row
        gbc.gridwidth = 1; gbc.gridy = 1;
        add(userLabel, gbc);
        gbc.gridx = 1;
        add(uF, gbc);

        // Password row
        gbc.gridx = 0; gbc.gridy = 2;
        add(passLabel, gbc);
        gbc.gridx = 1;
        add(pF, gbc);
        
        // Login button row (full width)
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        add(lBtn, gbc);

        // Register button row
        gbc.gridy = 4;
        add(rBtn, gbc);

        // EVENT HANDLING (CONTROLLER INTERACTION)
        // LOGIN LOGIC
        lBtn.addActionListener(e -> {
            // Fetch user from repository (data layer)
            User u = gui.getUserRepo().findByUsername(uF.getText().trim());
            // Validate credentials
            if (u != null && u.checkPassword(new String(pF.getPassword()))) {
                SessionManager.getInstance().setCurrentUser(u);  // Set global session user
                gui.startNotificationWatcher();  // Start real-time notification watcher (background thread)
                gui.showScreen("DASHBOARD");  // Navigate to dashboard screen
            } else {  // Authentication failure feedback
                JOptionPane.showMessageDialog(this, "Authentication Failed. Please check your credentials.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // NAVIGATE TO REGISTRATION SCREEN
        rBtn.addActionListener(e -> gui.showScreen("REGISTER"));
    }
}

// REGISTRATION SCREEN: provides the graphical interface for new user registration
class GUIRegister extends JPanel {
    // Constructor receives reference to main application controller
    public GUIRegister(BakeArtGUI gui) {
        // PANEL LAYOUT CONFIGURATION
        setLayout(new GridBagLayout());                     // GridBagLayout allows structured flexible UI placement
        setBackground(BakeArtGUI.BAKE_CREAM);               // Apply global BakeArt theme color   
        GridBagConstraints gbc = new GridBagConstraints();  // Constraint object used for positioning UI components
        gbc.insets = new Insets(12, 12, 12, 12);            // Add spacing between components
        gbc.fill = GridBagConstraints.HORIZONTAL;           // Stretch components horizontally

        // TITLE COMPONENT
        JLabel title = new JLabel("Join the Community", SwingConstants.CENTER);
        title.setFont(new Font("Serif", Font.BOLD, 38));
        title.setForeground(BakeArtGUI.BAKE_BROWN);

        // LABEL COMPONENTS
        JLabel uLbl = new JLabel("Username:"); uLbl.setFont(BakeArtGUI.LARGE_FONT);
        JLabel pLbl = new JLabel("Password:"); pLbl.setFont(BakeArtGUI.LARGE_FONT);
        JLabel tLbl = new JLabel("Admin Key:"); tLbl.setFont(BakeArtGUI.LARGE_FONT);

        // INPUT FIELDS
        JTextField uF = new JTextField(20); uF.setFont(BakeArtGUI.MEDIUM_FONT);
        JPasswordField pF = new JPasswordField(20); pF.setFont(BakeArtGUI.MEDIUM_FONT);
        JTextField tF = new JTextField(20); tF.setFont(BakeArtGUI.MEDIUM_FONT);

       // ACTION BUTTONS
        JButton sBtn = new JButton("Complete Registration");
        sBtn.setFont(BakeArtGUI.LARGE_FONT);
        sBtn.setBackground(BakeArtGUI.BAKE_ACCENT);
        sBtn.setForeground(Color.WHITE);
        sBtn.setPreferredSize(new Dimension(300, 50));
        
        // INPUT FIELDS
        // Username input field
        JButton bBtn = new JButton("Return to Login");
        bBtn.setFont(BakeArtGUI.MEDIUM_FONT);
        bBtn.setForeground(BakeArtGUI.BAKE_BROWN);

        // LAYOUT ASSEMBLY
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        add(title, gbc);

        gbc.gridwidth = 1; gbc.gridy = 1;
        add(uLbl, gbc);
        gbc.gridx = 1;
        add(uF, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        add(pLbl, gbc);
        gbc.gridx = 1;
        add(pF, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        add(tLbl, gbc);
        gbc.gridx = 1;
        add(tF, gbc);
        
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        add(sBtn, gbc);

        gbc.gridy = 5;
        add(bBtn, gbc);

        // EVENT HANDLING
        sBtn.addActionListener(e -> {
            try {  // ROLE DETERMINATION
                String role = tF.getText().equals(Config.ADMIN_MASTER_KEY) ? "admin" : "user";
                User u = UserFactory.createUser(uF.getText().trim(), "", role);
                u.setPassword(new String(pF.getPassword()));
                gui.getUserRepo().saveUser(u, role);
                JOptionPane.showMessageDialog(this, "Success! You can now log in.", "Account Created", JOptionPane.INFORMATION_MESSAGE);
                gui.showScreen("LOGIN");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "System Error: Could not save profile.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Return to login screen without registration
        bBtn.addActionListener(e -> gui.showScreen("LOGIN"));
    }
}

// DASHBOARD SCREEN
class GUIDashboard extends JPanel {
    private JPanel listPanel = new JPanel();   // Panel that dynamically displays recipe cards
    private BakeArtGUI gui;  // Reference to main application controller
    private JTextField searchField = new JTextField(25);  // Search components
    private JComboBox<String> sType = new JComboBox<>(new String[]{"Search by Name", "Search by Ingredient"});   // Strategy Pattern used for selecting search behavior
    private JComboBox<String> catDropdown = new JComboBox<>(new String[]{"All Recipes", "Try", "Popular", "Considered for Verification", "Verified"});  // Recipe category filter dropdown
    private JButton authBtn = new JButton("Authority Tools");

    // Admin/Moderator tools button
    public GUIDashboard(BakeArtGUI gui) {
        this.gui = gui;
        setLayout(new BorderLayout());

        // TOP BAR: Centered Search
        JPanel topBar = new JPanel(new BorderLayout(15, 10));
        topBar.setBackground(BakeArtGUI.BAKE_BROWN);
        topBar.setBorder(new EmptyBorder(10, 20, 10, 20));

        JLabel logo = new JLabel(" bakeArt ");
        logo.setForeground(Color.WHITE);
        logo.setFont(new Font("Serif", Font.BOLD, 28));
        topBar.add(logo, BorderLayout.WEST);

        // Search Panel (Middle)
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        searchPanel.setOpaque(false);
        searchField.setFont(BakeArtGUI.MEDIUM_FONT);
        sType.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JButton searchBtn = new JButton("🔍 Search");
        searchBtn.setPreferredSize(new Dimension(110, 35));
        
        searchPanel.add(searchField);
        searchPanel.add(sType);
        searchPanel.add(searchBtn);
        topBar.add(searchPanel, BorderLayout.CENTER);

        JButton logoutBtn = new JButton("Logout");
        topBar.add(logoutBtn, BorderLayout.EAST);

        // SIDEBAR: Dropdown Filter 
        JPanel side = new JPanel(new GridLayout(12, 1, 5, 10));
        side.setPreferredSize(new Dimension(250, 0));
        side.setBorder(new EmptyBorder(20, 15, 15, 15));
        
        JButton accBtn = new JButton("My Account / Profile");
        side.add(accBtn);
        side.add(authBtn); 
        side.add(new JSeparator());
        
        JLabel filterLabel = new JLabel("FILTER BY CATEGORY:");
        filterLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        side.add(filterLabel);
        catDropdown.setFont(new Font("SansSerif", Font.PLAIN, 14));
        side.add(catDropdown);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        add(topBar, BorderLayout.NORTH);
        add(side, BorderLayout.WEST);
        add(new JScrollPane(listPanel), BorderLayout.CENTER);

        // LISTENERS 
        authBtn.addActionListener(e -> gui.showScreen("AUTHORITY"));
        accBtn.addActionListener(e -> gui.showScreen("ACCOUNT"));
        logoutBtn.addActionListener(e -> { gui.stopWatcher(); SessionManager.getInstance().setCurrentUser(null); gui.showScreen("LOGIN"); });
        
        catDropdown.addActionListener(e -> {
            String selected = (String) catDropdown.getSelectedItem();
            if (selected.equals("All Recipes")) refreshFeed(null);
            else filterByCategory(selected);
        });

        searchBtn.addActionListener(e -> {
            SearchStrategy strategy = sType.getSelectedIndex() == 0 ? new NameSearchStrategy() : new IngredientSearchStrategy();
            updateList(strategy.search(gui.getRecipeRepo().loadAllRecipesFromDisk(), searchField.getText()));
        });
    }

    // Reloads recipes and updates authority button visibility
    public void refreshFeed(List<Recipe> s) {
        User u = SessionManager.getInstance().getCurrentUser();
        if (u != null) {
            authBtn.setText(u instanceof Admin ? "Admin Tools" : "Moderator Tools");
            authBtn.setVisible(!(u instanceof RegularUser));
        }
        updateList(s != null ? s : gui.getRecipeRepo().loadAllRecipesFromDisk());
    }

    // CATEGORY FILTERING
    private void filterByCategory(String cat) {
        updateList(gui.getRecipeRepo().loadAllRecipesFromDisk().stream()
                .filter(r -> r.getCategory().equalsIgnoreCase(cat)).collect(Collectors.toList()));
    }

    // UPDATE RECIPE DISPLAY LIST
    private void updateList(List<Recipe> recipes) {
        listPanel.removeAll();
        for (Recipe r : recipes) {
            JPanel card = new JPanel(new BorderLayout());
            card.setBorder(new TitledBorder(r.getCategory()));
            card.setMaximumSize(new Dimension(1000, 85)); // Taller cards

            JLabel nameLabel = new JLabel("  " + r.getName() + " (by " + r.getOwner().getUsername() + ")");
            nameLabel.setFont(BakeArtGUI.RECIPE_NAME_FONT); // Large Bold Font
            
            JLabel likesLabel = new JLabel("[♥ " + r.getLikes() + "]  ");
            likesLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
            likesLabel.setForeground(BakeArtGUI.BAKE_ACCENT);

            card.add(nameLabel, BorderLayout.CENTER);
            card.add(likesLabel, BorderLayout.WEST);
            
            JButton vB = new JButton("View Recipe");
            vB.addActionListener(e -> showDetail(r, 1.0));
            card.add(vB, BorderLayout.EAST);
            
            listPanel.add(card);
            listPanel.add(Box.createVerticalStrut(8));
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    // RECIPE DETAIL WINDOW
    private void showDetail(Recipe r, double scale) {
        JDialog d = new JDialog(gui, r.getName(), true); 
        d.setSize(700, 800);
        JTabbedPane tabs = new JTabbedPane();
        
        // Recipe Tab
        JTextArea area = new JTextArea(); 
        area.setEditable(false); 
        area.setFont(new Font("Monospaced", Font.PLAIN, 15));
        area.append("RECIPE: " + r.getName().toUpperCase() + " (Scale: " + scale + "x)\n");
        area.append("==================================================\n");
        area.append("INGREDIENTS:\n");
        for(Ingredient i : r.getIngredients()) 
            area.append(String.format(" - %.2f %s of %s\n", i.getQuantity() * scale, i.getUnit(), i.getName()));
        area.append("\nINSTRUCTIONS:\n");
        for(String s : r.getInstructions()) area.append(" > " + s + "\n");
        
        // COMMUNITY TAB
        JPanel comm = new JPanel(new BorderLayout()); 
        JTextArea cArea = new JTextArea(); 
        cArea.setEditable(false);
        cArea.append("--- COMMUNITY STATS ---\n");
        for(User u : r.getLikedUsers()) cArea.append(" ♥ Liked by: " + u.getUsername() + "\n");
        cArea.append("\n--- COMMENTS ---\n");
        for(Comment c : r.getComments()) 
            cArea.append(c.getUser().getUsername() + ": " + c.getText() + " (Likes: " + c.getLikesCount() + ")\n");
        comm.add(new JScrollPane(cArea));

        tabs.addTab("Instructions", new JScrollPane(area)); 
        tabs.addTab("Community", comm);
        
        // INTERACTION BUTTONS
        JPanel bp = new JPanel(); 
        JButton lB = new JButton("Like ♥"); 
        JButton cB = new JButton("Comment"); 
        JButton lcB = new JButton("Like a Comment"); 
        JButton sB = new JButton("Scale Servings"); 
        JButton tB = new JButton("Oven Timer");
        
        lB.addActionListener(e -> { gui.getInteractionService().likeRecipe(r, SessionManager.getInstance().getCurrentUser()); d.dispose(); showDetail(r, scale); });
        cB.addActionListener(e -> { String t = JOptionPane.showInputDialog(d, "Enter Comment:"); if(t!=null) { gui.getInteractionService().addComment(r, SessionManager.getInstance().getCurrentUser(), t); d.dispose(); showDetail(r, scale); }});
        lcB.addActionListener(e -> {
            if(r.getComments().isEmpty()) return;
            String[] list = r.getComments().stream().map(c -> c.getUser().getUsername() + ": " + c.getText()).toArray(String[]::new);
            String sel = (String) JOptionPane.showInputDialog(d, "Select a comment to like:", "Community", 0, null, list, list[0]);
            if(sel != null) { for(Comment c : r.getComments()) if((c.getUser().getUsername() + ": " + c.getText()).equals(sel)) gui.getInteractionService().likeComment(c, SessionManager.getInstance().getCurrentUser(), r); d.dispose(); showDetail(r, scale); }
        });
        sB.addActionListener(e -> { String f = JOptionPane.showInputDialog(d, "Enter scaling factor (e.g. 2):"); try { if(f!=null) { d.dispose(); showDetail(r, Double.parseDouble(f)); } } catch(Exception ex){} });
        tB.addActionListener(e -> { String m = JOptionPane.showInputDialog(d, "Minutes:"); if(m!=null) gui.getTimerService().startBakingTimer(Integer.parseInt(m), SessionManager.getInstance().getCurrentUser()); });
        
        bp.add(lB); bp.add(cB); bp.add(lcB); bp.add(sB); bp.add(tB);
        d.add(tabs); d.add(bp, BorderLayout.SOUTH); d.setLocationRelativeTo(gui); d.setVisible(true);
    }
}

// MY ACCOUNT SCREEN
class GUIMyAccount extends JPanel {
    private BakeArtGUI gui;  // Reference to main application controller
    private JTextArea personalDetails = new JTextArea(5, 30);  // Displays user profile information
    private JTextArea myRecipesSummary = new JTextArea(15, 30);  // Displays recipe statistics summary

    public GUIMyAccount(BakeArtGUI gui) {
        this.gui = gui; setLayout(new BorderLayout());  // Main layout
        JButton bk = new JButton("← Dashboard"); bk.addActionListener(e -> gui.showScreen("DASHBOARD"));  // Navigation button back to dashboard
        add(bk, BorderLayout.NORTH);

        JPanel mainContent = new JPanel(new BorderLayout(20, 20));
        mainContent.setBorder(new EmptyBorder(20, 20, 20, 20));

        // PROFILE SECTION
        personalDetails.setEditable(false);
        personalDetails.setFont(new Font("SansSerif", Font.BOLD, 16));
        personalDetails.setBorder(new TitledBorder("Personal Profile"));
        mainContent.add(personalDetails, BorderLayout.NORTH);

        // PROFILE ACTIONS
        JPanel center = new JPanel(new GridLayout(1, 2, 20, 20));
        JPanel pSide = new JPanel(); pSide.setLayout(new BoxLayout(pSide, BoxLayout.Y_AXIS)); 
        pSide.setBorder(new TitledBorder("Profile Actions"));
        JButton passB = new JButton("Change Password"); JButton notiB = new JButton("View Notification History"); JButton modB = new JButton("Apply for Moderator");
        pSide.add(passB); pSide.add(notiB); pSide.add(modB);
        center.add(pSide);

        myRecipesSummary.setEditable(false);
        myRecipesSummary.setFont(new Font("Monospaced", Font.PLAIN, 14));
        center.add(new JScrollPane(myRecipesSummary));
        myRecipesSummary.setBorder(new TitledBorder("My Posted Recipes Stats"));
        mainContent.add(center, BorderLayout.CENTER);
        
        // RECIPE MANAGEMENT
        JPanel mSide = new JPanel(); mSide.setBorder(new TitledBorder("Recipe Management"));
        JButton cB = new JButton("1. Create New"); JButton mB = new JButton("2. Deep Modify"); JButton dB = new JButton("3. Delete");
        mSide.add(cB); mSide.add(mB); mSide.add(dB);
        mainContent.add(mSide, BorderLayout.SOUTH);
        add(mainContent, BorderLayout.CENTER);

        // LISTENERS
        passB.addActionListener(e -> {
            User u = SessionManager.getInstance().getCurrentUser(); String old = JOptionPane.showInputDialog("Verify OLD Password:");
            if(old != null && u.checkPassword(old)) {
                String n = JOptionPane.showInputDialog("Enter NEW Password:");
                if(n != null && !n.isEmpty()) { u.setPassword(n); try { gui.getUserRepo().saveUser(u, u.role); } catch(Exception ex){} JOptionPane.showMessageDialog(this, "Updated!"); }
            } else if(old != null) JOptionPane.showMessageDialog(this, "Verification Denied.");
        });

         // NOTIFICATION HISTORY
        notiB.addActionListener(e -> {
            User u = SessionManager.getInstance().getCurrentUser(); String fld = u.role.equals("admin") ? Config.ADMIN_PATH : u.role.equals("moderator") ? Config.MOD_PATH : Config.USER_PATH;
            File f = new File(fld + u.getUsername() + "/notifications.txt"); JTextArea a = new JTextArea();
            try { if(f.exists()) for(String s : Files.readAllLines(f.toPath())) a.append(s + "\n---\n"); } catch(Exception ex){}
            JOptionPane.showMessageDialog(this, new JScrollPane(a));
        });

        // MODERATOR APPLICATION
        modB.addActionListener(e -> {
            User cur = SessionManager.getInstance().getCurrentUser();
            JPanel f = new JPanel(new GridLayout(3, 2)); JTextField nf=new JTextField(), ef=new JTextField(), sf=new JTextField();
            f.add(new JLabel("Name:")); f.add(nf); f.add(new JLabel("Exp:")); f.add(ef); f.add(new JLabel("Spec:")); f.add(sf);
            if(JOptionPane.showConfirmDialog(this, f, "Apply", JOptionPane.OK_CANCEL_OPTION)==0) {
                try { Files.write(new File(Config.APP_PATH + cur.getUsername() + ".txt").toPath(), ("Name: "+nf.getText()+"\nExp: "+ef.getText()+"\nSpec: "+sf.getText()).getBytes()); } catch(Exception ex){}
            }
        });

        // CREATE NEW RECIPE
        cB.addActionListener(e -> {
            String n = JOptionPane.showInputDialog("Recipe Name:"); if(n == null) return;
            Recipe r = new Recipe(n, SessionManager.getInstance().getCurrentUser());
            while(true) {
                String in = JOptionPane.showInputDialog("Ingredient (or 'done'):"); if(in == null || in.equalsIgnoreCase("done")) break;
                try { r.getIngredients().add(new Ingredient(in, Double.parseDouble(JOptionPane.showInputDialog("Qty:")), JOptionPane.showInputDialog("Unit:"))); } catch(Exception ex){}
            }
            while(true) { String st = JOptionPane.showInputDialog("Step (or 'done'):"); if(st == null || st.equalsIgnoreCase("done")) break; r.getInstructions().add(st); }
            gui.getRecipeRepo().saveRecipe(r); refreshUserDetails();
        });
        mB.addActionListener(e -> handleDeepModify());

        // DELETE RECIPE
        dB.addActionListener(e -> {
            List<Recipe> mine = gui.getRecipeRepo().loadAllRecipesFromDisk().stream().filter(r -> r.getOwner().getUsername().equals(SessionManager.getInstance().getCurrentUser().getUsername())).collect(Collectors.toList());
            if(mine.isEmpty()) return; String[] ns = mine.stream().map(Recipe::getName).toArray(String[]::new);
            String s = (String)JOptionPane.showInputDialog(this, "Delete?", "Delete", 0, null, ns, ns[0]);
            if(s!=null) { gui.getRecipeRepo().deleteRecipe(mine.stream().filter(x -> x.getName().equals(s)).findFirst().get()); refreshUserDetails(); }
        });

        // Dynamically adjusts buttons depending on user role
        Timer roleTimer = new Timer(500, al -> {
            User u = SessionManager.getInstance().getCurrentUser();
            if (u != null) {
                notiB.setVisible(!(u instanceof Admin)); 
                modB.setVisible(u instanceof RegularUser); 
            }
        });
        roleTimer.start();
    }

    // REFRESH USER PROFILE DETAILS
    public void refreshUserDetails() {
        User u = SessionManager.getInstance().getCurrentUser();
        if (u == null) return;
        personalDetails.setText("Username: " + u.getUsername() + "\nRole: " + u.role.toUpperCase());
        List<Recipe> mine = gui.getRecipeRepo().loadAllRecipesFromDisk().stream().filter(r -> r.getOwner().getUsername().equalsIgnoreCase(u.getUsername())).collect(Collectors.toList());
        myRecipesSummary.setText("");
        if (mine.isEmpty()) myRecipesSummary.append("No recipes posted yet.");
        else for (Recipe r : mine) myRecipesSummary.append(String.format("• %-18s | Likes: %-3d | Comms: %-3d\n", r.getName(), r.getLikes(), r.getComments().size()));
    }

    // DEEP MODIFY SYSTEM
    private void handleDeepModify() {
        User u = SessionManager.getInstance().getCurrentUser();
        List<Recipe> mine = gui.getRecipeRepo().loadAllRecipesFromDisk().stream().filter(r -> r.getOwner().getUsername().equals(u.getUsername())).collect(Collectors.toList());
        if(mine.isEmpty()) return; String[] ns = mine.stream().map(Recipe::getName).toArray(String[]::new);
        String sel = (String)JOptionPane.showInputDialog(this, "Select Recipe:", "Modify", 0, null, ns, ns[0]);
        if(sel!=null) {
            Recipe r = mine.stream().filter(x -> x.getName().equals(sel)).findFirst().get(); String oldN = r.getName();
            boolean done = false;
            while(!done) {
                String[] opts = {"Change Name", "Add Ingredient", "Edit Existing Ingredient", "Edit Step", "Finish"};
                int c = JOptionPane.showOptionDialog(this, "Editing: "+r.getName(), "Deep Edit", 0, 3, null, opts, opts[0]);
                if(c==0) r.setName(JOptionPane.showInputDialog("New Name:", r.getName()));
                else if(c==1) r.getIngredients().add(new Ingredient(JOptionPane.showInputDialog("Name:"), Double.parseDouble(JOptionPane.showInputDialog("Qty:")), JOptionPane.showInputDialog("Unit:")));
                else if(c==2) {
                    if(r.getIngredients().isEmpty()) continue;
                    String[] iNames = r.getIngredients().stream().map(Ingredient::toString).toArray(String[]::new);
                    String iSel = (String) JOptionPane.showInputDialog(this, "Choose ingredient:", "Edit", 0, null, iNames, iNames[0]);
                    if(iSel != null) for(Ingredient ing : r.getIngredients()) if(ing.toString().equals(iSel)) {
                        ing.modifyIngredient(JOptionPane.showInputDialog("Name:", ing.getName()), Double.parseDouble(JOptionPane.showInputDialog("Qty:", ing.getQuantity())), JOptionPane.showInputDialog("Unit:", ing.getUnit())); break;
                    }
                } else if(c==3) {
                    if(r.getInstructions().isEmpty()) continue;
                    String[] steps = new String[r.getInstructions().size()]; for(int i=0; i<steps.length; i++) steps[i] = "Step "+(i+1);
                    String sSel = (String) JOptionPane.showInputDialog(this, "Step?", "Edit", 0, null, steps, steps[0]);
                    if(sSel != null) { int idx=Integer.parseInt(sSel.split(" ")[1])-1; r.getInstructions().set(idx, JOptionPane.showInputDialog("Prev Text: "+r.getInstructions().get(idx)+"\nNew Text:", r.getInstructions().get(idx))); }
                } else done = true;
            }
            gui.getRecipeRepo().saveRecipe(r); if(!oldN.equals(r.getName())) gui.getRecipeRepo().deleteFileBySpecificName(r.getId(), oldN);
            refreshUserDetails();
        }
    }
}

// AUTHORITY PANEL
class GUIAuthorityPanel extends JPanel {
    private BakeArtGUI gui; private JPanel listPanel = new JPanel(); private JLabel roleLabel = new JLabel();

    public GUIAuthorityPanel(BakeArtGUI gui) {
        this.gui = gui; setLayout(new BorderLayout());
        JButton bk = new JButton("← Dashboard"); bk.addActionListener(e -> gui.showScreen("DASHBOARD"));
        JPanel top = new JPanel(new BorderLayout()); top.setBackground(new Color(70, 130, 180));
        top.add(bk, BorderLayout.WEST); roleLabel.setForeground(Color.WHITE); top.add(roleLabel, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH); listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS)); add(new JScrollPane(listPanel), BorderLayout.CENTER);
    }

    // REFRESH AUTHORITY TASKS
    public void refreshLists() {
        listPanel.removeAll(); User cur = SessionManager.getInstance().getCurrentUser(); if(cur == null) return;
        roleLabel.setText("  " + cur.role.toUpperCase() + " TASK CENTER");
        if (cur instanceof Moderator) {  // MODERATOR TASKS
            listPanel.add(new JLabel("--- VERIFICATION VOTES ---"));
            for (Recipe r : gui.getRecipeRepo().loadAllRecipesFromDisk().stream().filter(rec -> rec.getCategory().equalsIgnoreCase("Considered for Verification")).collect(Collectors.toList())) {
                JPanel card = new JPanel(new BorderLayout()); card.setBorder(new LineBorder(Color.GRAY));
                card.add(new JLabel(" Vote for: " + r.getName()), BorderLayout.CENTER);
                JButton b = new JButton("Cast Vote"); b.addActionListener(e -> handleVote(r));
                card.add(b, BorderLayout.EAST); listPanel.add(card);
            }
        } else if (cur instanceof Admin) {  // ADMIN TASKS
            File[] fs = new File(Config.VERIFY_PATH).listFiles(); int maj = (int) Math.ceil(gui.getUserRepo().getModeratorCount() * 0.66);
            if (fs != null) for (File f : fs) try {
                List<String> ls = Files.readAllLines(f.toPath()); int a = Integer.parseInt(ls.get(1)), d = Integer.parseInt(ls.get(2));
                if (a >= maj || d >= maj) {
                    JButton b = new JButton("Finalize: " + f.getName()); b.addActionListener(e -> finalizeDec(f, ls, a >= maj)); listPanel.add(b);
                }
            } catch (Exception e) {}
            for (File app : new File(Config.APP_PATH).listFiles()) {
                JButton b = new JButton("Promote Applicant: " + app.getName()); b.addActionListener(e -> promote(app)); listPanel.add(b);
            }
        }
        listPanel.revalidate(); listPanel.repaint();
    }

    // MODERATOR VOTING SYSTEM
    private void handleVote(Recipe r) {
        String p = Config.VERIFY_PATH + r.getId() + "_" + r.getName().replace(" ","_") + ".txt";
        try {
            List<String> ls = Files.readAllLines(Paths.get(p));
            JTextArea det = new JTextArea(10, 40); det.append("RECIPE:\n"); for(Ingredient i : r.getIngredients()) det.append("- "+i+"\n");
            JPanel pan = new JPanel(new BorderLayout()); pan.add(new JScrollPane(det), BorderLayout.CENTER);
            JPanel sub = new JPanel(new GridLayout(2,1)); JRadioButton app=new JRadioButton("Approve"), dis=new JRadioButton("Reject");
            ButtonGroup bg=new ButtonGroup(); bg.add(app); bg.add(dis); sub.add(app); sub.add(dis); pan.add(sub, BorderLayout.SOUTH);
            if(JOptionPane.showConfirmDialog(this, pan, "Vote", 0)==0) {
                if(app.isSelected()) ls.set(1, String.valueOf(Integer.parseInt(ls.get(1))+1));
                else ls.set(2, String.valueOf(Integer.parseInt(ls.get(2))+1));
                ls.add(SessionManager.getInstance().getCurrentUser().getUsername()+": Done");
                Files.write(Paths.get(p), ls); refreshLists();
            }
        } catch (Exception e) {}
    }
    
    // Admin either verifies or deletes recipe
    private void finalizeDec(File f, List<String> ls, boolean ok) {
        try { Recipe r = gui.getRecipeRepo().loadRecipe(new File(ls.get(0).trim()));
            if(ok) r.setCategory("Verified"); else gui.getRecipeRepo().deleteRecipe(r);
            gui.getRecipeRepo().saveRecipe(r); f.delete(); refreshLists();
        } catch (Exception e) {}
    }
    
    // PROMOTE MODERATOR APPLICANT
    private void promote(File app) {
        String u = app.getName().replace(".txt","");
        if(JOptionPane.showConfirmDialog(this, "Promote "+u+"?") == 0) {
            try {
                Path s = Paths.get(Config.USER_PATH + u), d = Paths.get(Config.MOD_PATH + u);
                Files.walk(s).forEach(p -> { try { Path targ = d.resolve(s.relativize(p)); if (Files.isDirectory(p)) Files.createDirectories(targ); else Files.copy(p, targ, StandardCopyOption.REPLACE_EXISTING); } catch (Exception e) {} });
                Files.walk(s).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                gui.getUserRepo().refreshUserRole(u, "moderator"); app.delete(); refreshLists();
            } catch (Exception e) {}
        }
    }
}