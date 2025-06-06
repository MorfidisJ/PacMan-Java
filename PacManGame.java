import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.awt.geom.AffineTransform;

public class PacManGame extends JFrame {

    public PacManGame() {
        initUI();
    }

    private void initUI() {
        Board board = new Board();
        add(board);

        setTitle("Pac-Man Style Game");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 750); 
        setLocationRelativeTo(null);
        setResizable(false);
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            PacManGame ex = new PacManGame();
            ex.setVisible(true);
        });
    }
}

class Board extends JPanel implements ActionListener {

    // Tile size and maze dimensions
    private final int TILE_SIZE = 20; // Size of each tile in pixels
    private final int N_ROWS = 21;    // Number of rows in the maze
    private final int N_COLS = 19;    // Number of columns in the maze

    // Screen dimensions based on tile size and maze dimensions
    private final int SCREEN_WIDTH = N_COLS * TILE_SIZE;
    private final int SCREEN_HEIGHT = N_ROWS * TILE_SIZE;

    // Game state variables
    private boolean inGame = false;
    private boolean dying = false;
    private boolean win = false;

    private int lives;
    private int score;
    private int dotsLeft;

    // Pac-Man properties
    private int pacmanX, pacmanY;       // Pac-Man's current tile coordinates
    private int pacmanDX, pacmanDY;     // Pac-Man's current direction of movement
    private int reqDX, reqDY;           // Pac-Man's requested direction of movement

    // Ghost properties
    private final int N_GHOSTS = 4;
    private int[] ghostX, ghostY;       // Ghosts' current tile coordinates
    private int[] ghostDX, ghostDY;     // Ghosts' current direction of movement
    private boolean[] ghostFrightened;  // Is the ghost currently frightened?
    private int frightenedTimer;        // How long ghosts remain frightened
    private final int FRIGHTENED_DURATION = 100; // Ticks for frightened mode

    // Maze data:
    // 0 = empty path (will be filled with dot)
    // 1 = wall
    // 2 = power pellet
    // P = Pac-Man start
    // G = Ghost start
    // E = Empty space (no dot, e.g., ghost house exit)
    private final String[] levelDataString = {
        "1111111111111111111",
        "1200000001000000021",
        "1011011101011101101",
        "1000000000000000001",
        "1011010111110101101",
        "1000010001000100001",
        "1111011101011101111",
        "111101000E000101111",
        "11110101G1G10101111",
        "00000001G1G10000000", // Tunnel
        "1111010111110101111",
        "1111010000000101111",
        "1111010111110101111",
        "100000000P000000001",
        "1011011101011101101",
        "1200010001000100021",
        "1101010111110101011",
        "1000000000000000001",
        "1011111111111111101",
        "1000000000000000001",
        "1111111111111111111"
    };
    private short[][] screenData; // Parsed maze data (0=dot, 1=wall, 2=power pellet, 16=empty)

    // Animation variables
    private int animationStep = 0;
    private final int ANIMATION_SPEED = 2; 
    private int mouthAngle = 45;
    private boolean mouthClosing = false;
    private final int MOUTH_ANGLE_CHANGE = 50; 
    
    // Colors (more authentic to original Pac-Man)
    private final Color WALL_COLOR = new Color(0, 0, 255); // Classic blue walls
    private final Color DOT_COLOR = new Color(255, 255, 255); // White dots
    private final Color POWER_PELLET_COLOR = new Color(255, 255, 255); // White power pellets
    private final Color PACMAN_COLOR = new Color(255, 255, 0); // Classic yellow
    private final Color[] GHOST_COLORS = {
        new Color(255, 0, 0),    // Red (Blinky)
        new Color(255, 184, 255), // Pink (Pinky)
        new Color(0, 255, 255),  // Cyan (Inky)
        new Color(255, 184, 82)  // Orange (Clyde)
    };
    private final Color FRIGHTENED_GHOST_COLOR = new Color(33, 33, 255); // Blue when frightened
    private final Color GHOST_EYES_COLOR = Color.WHITE;
    private final Color GHOST_PUPIL_COLOR = new Color(0, 0, 255); // Blue pupils

    private Timer timer;
    private Random random;
    private Dimension d; 

    
    private final int PADDING_X = 90;  
    private final int PADDING_Y = 110; 
    private final int PADDING_BOTTOM = 150; 
    private final int SCORE_HEIGHT = 60; 

    // Add game progression variables
    private int currentLevel = 1;
    private int baseGhostSpeed = 150; // Base timer speed for ghosts
    private int currentGhostSpeed;    // Current ghost speed
    private boolean autoStart = true;  // Auto start flag

    public Board() {
        initBoard();
        d = getSize();  // Initialize dimension
    }

    private void initBoard() {
        addKeyListener(new TAdapter());
        setFocusable(true);
        setBackground(Color.BLACK);
       
        setPreferredSize(new Dimension(SCREEN_WIDTH + PADDING_X * 2, 
                                     SCREEN_HEIGHT + PADDING_Y + PADDING_BOTTOM + SCORE_HEIGHT));
        d = getSize();

        random = new Random();
        initGame();
    }

    private void initGame() {
        if (currentLevel == 1) {
            lives = 3;
            score = 0;
        }
        
        frightenedTimer = 0;
        dotsLeft = 0;
        dying = false;
        win = false;

        // Calculate ghost speed based on level
        currentGhostSpeed = Math.max(baseGhostSpeed - (currentLevel - 1) * 10, 50);

        screenData = new short[N_ROWS][N_COLS];
        ghostX = new int[N_GHOSTS];
        ghostY = new int[N_GHOSTS];
        ghostDX = new int[N_GHOSTS];
        ghostDY = new int[N_GHOSTS];
        ghostFrightened = new boolean[N_GHOSTS];

        parseLevelData();
        initPacManAndGhosts();

        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
        timer = new Timer(currentGhostSpeed, this);
        timer.start();
        
        inGame = true; // Auto start
    }

    private void parseLevelData() {
        dotsLeft = 0;
        for (int i = 0; i < N_ROWS; i++) {
            for (int j = 0; j < N_COLS; j++) {
                char cell = levelDataString[i].charAt(j);
                if (cell == '1') {
                    screenData[i][j] = 1; // Wall
                } else if (cell == '0') {
                    screenData[i][j] = 0; // Dot
                    dotsLeft++;
                } else if (cell == '2') {
                    screenData[i][j] = 2; // Power Pellet
                    dotsLeft++;
                } else if (cell == 'P') {
                    pacmanX = j;
                    pacmanY = i;
                    screenData[i][j] = 16; // Empty where Pac-Man starts
                } else if (cell == 'G') {
                    // Will place ghosts in initPacManAndGhosts
                    screenData[i][j] = 16; // Empty where ghosts start
                } else if (cell == 'E') {
                    screenData[i][j] = 16; // Empty space
                }
            }
        }
    }

    private void initPacManAndGhosts() {
        // Reset Pac-Man's starting position and direction
        for (int i = 0; i < N_ROWS; i++) {
            for (int j = 0; j < N_COLS; j++) {
                if (levelDataString[i].charAt(j) == 'P') {
                    pacmanX = j;
                    pacmanY = i;
                }
            }
        }
        pacmanDX = 0;
        pacmanDY = 0;
        reqDX = 0;
        reqDY = 0;

        int ghostCount = 0;
        for (int i = 0; i < N_ROWS; i++) {
            for (int j = 0; j < N_COLS; j++) {
                if (levelDataString[i].charAt(j) == 'G' && ghostCount < N_GHOSTS) {
                    ghostX[ghostCount] = j;
                    ghostY[ghostCount] = i;
                    ghostDX[ghostCount] = 0; // Initial ghost direction
                    ghostDY[ghostCount] = -1; // Start moving up from ghost house
                    ghostFrightened[ghostCount] = false;
                    ghostCount++;
                }
            }
        }
    }


    private void playGame(Graphics2D g2d) {
        if (dying) {
            death();
        } else if (win) {
            showWinScreen(g2d);
        } else {
            movePacman();
            drawPacman(g2d);
            moveGhosts(g2d);
            drawGhosts(g2d);
            checkMaze();
        }
    }

    private void death() {
        lives--;
        if (lives == 0) {
            inGame = false;
            currentLevel = 1; // Reset level on game over
        } else {
            initPacManAndGhosts();
            dying = false;
        }
    }

    private void checkMaze() {
        if (screenData[pacmanY][pacmanX] == 0) {
            screenData[pacmanY][pacmanX] = 16;
            score += 10;
            dotsLeft--;
        } else if (screenData[pacmanY][pacmanX] == 2) {
            screenData[pacmanY][pacmanX] = 16;
            score += 50;
            dotsLeft--;
            frightenedTimer = FRIGHTENED_DURATION;
            for (int i = 0; i < N_GHOSTS; i++) {
                ghostFrightened[i] = true;
                ghostDX[i] *= -1;
                ghostDY[i] *= -1;
            }
        }

        if (dotsLeft == 0) {
            win = true;
            currentLevel++;
            // Start next level after delay
            Timer levelTimer = new Timer(2000, e -> {
                initGame();
                ((Timer)e.getSource()).stop();
            });
            levelTimer.setRepeats(false);
            levelTimer.start();
        }
    }

    private void movePacman() {
        int newPacmanX, newPacmanY;

        // Handle tunnel wrapping
        if (pacmanX == 0 && pacmanDX == -1) { // Left tunnel exit
            pacmanX = N_COLS -1;
        } else if (pacmanX == N_COLS - 1 && pacmanDX == 1) { // Right tunnel exit
            pacmanX = 0;
        }

        // Try to apply requested direction
        if (reqDX != 0 || reqDY != 0) {
            newPacmanX = pacmanX + reqDX;
            newPacmanY = pacmanY + reqDY;
            if (newPacmanX >= 0 && newPacmanX < N_COLS && newPacmanY >= 0 && newPacmanY < N_ROWS &&
                screenData[newPacmanY][newPacmanX] != 1) { // Not a wall
                pacmanDX = reqDX;
                pacmanDY = reqDY;
            }
        }

        // Move in current direction if possible
        newPacmanX = pacmanX + pacmanDX;
        newPacmanY = pacmanY + pacmanDY;

        if (newPacmanX >= 0 && newPacmanX < N_COLS && newPacmanY >= 0 && newPacmanY < N_ROWS &&
            screenData[newPacmanY][newPacmanX] != 1) { // Not a wall
            pacmanX = newPacmanX;
            pacmanY = newPacmanY;
        }
    }


    private void moveGhosts(Graphics2D g2d) {
        if (frightenedTimer > 0) {
            frightenedTimer--;
            if (frightenedTimer == 0) {
                for (int i = 0; i < N_GHOSTS; i++) {
                    ghostFrightened[i] = false;
                }
            }
        }

        for (int i = 0; i < N_GHOSTS; i++) {
           
            int distanceX = Math.abs(pacmanX - ghostX[i]);
            int distanceY = Math.abs(pacmanY - ghostY[i]);
            
            if (inGame) {
                if (ghostFrightened[i]) {
                   
                    if (distanceX <= 0 && distanceY <= 0) {
                        score += 200; // Score for eating a ghost
                        // Send ghost back to starting position
                        int ghostCount = 0;
                        for (int r = 0; r < N_ROWS; r++) {
                            for (int c = 0; c < N_COLS; c++) {
                                if (levelDataString[r].charAt(c) == 'G') {
                                    if(ghostCount == i) {
                                        ghostX[i] = c;
                                        ghostY[i] = r;
                                        ghostFrightened[i] = false;
                                    }
                                    ghostCount++;
                                }
                            }
                        }
                    }
                } else {
                   
                    if (distanceX <= 1 && distanceY <= 1) {
                        dying = true;
                        return; 
                    }
                }
            }

            // Ghost movement logic
            int newGhostX, newGhostY;
            int count = 0;
            int[] possibleDX = new int[4];
            int[] possibleDY = new int[4];
            int numPossibleMoves = 0;

            // Check possible moves (not into walls, not reversing unless at dead end)
            if (ghostDX[i] != 1 && canMove(ghostX[i] - 1, ghostY[i])) { // Left
                possibleDX[numPossibleMoves] = -1; possibleDY[numPossibleMoves] = 0; numPossibleMoves++;
            }
            if (ghostDX[i] != -1 && canMove(ghostX[i] + 1, ghostY[i])) { // Right
                possibleDX[numPossibleMoves] = 1; possibleDY[numPossibleMoves] = 0; numPossibleMoves++;
            }
            if (ghostDY[i] != 1 && canMove(ghostX[i], ghostY[i] - 1)) { // Up
                possibleDX[numPossibleMoves] = 0; possibleDY[numPossibleMoves] = -1; numPossibleMoves++;
            }
            if (ghostDY[i] != -1 && canMove(ghostX[i], ghostY[i] + 1)) { // Down
                possibleDX[numPossibleMoves] = 0; possibleDY[numPossibleMoves] = 1; numPossibleMoves++;
            }


            if (numPossibleMoves == 0) { // Stuck, must reverse
                 if (canMove(ghostX[i] + ghostDX[i] * -1, ghostY[i] + ghostDY[i] * -1)) {
                    ghostDX[i] *= -1;
                    ghostDY[i] *= -1;
                 } else {
                    // Truly stuck, should not happen in a well-formed Pac-Man maze
                 }
            } else if (numPossibleMoves == 1 && (ghostDX[i] != 0 || ghostDY[i] != 0) ) { // Only one way (corridor)
                 // If the only possible move is not the current direction, change to it.
                 // This handles cases where the ghost was previously stopped or needs to turn.
                if (possibleDX[0] != ghostDX[i] || possibleDY[0] != ghostDY[i]) {
                    ghostDX[i] = possibleDX[0];
                    ghostDY[i] = possibleDY[0];
                }
            }
            else { // At an intersection or needs to pick a new path
                int bestMoveIndex = -1;
                if (ghostFrightened[i]) { // Run away
                    int maxDist = -1;
                    for (int k = 0; k < numPossibleMoves; k++) {
                        int dist = Math.abs(ghostX[i] + possibleDX[k] - pacmanX) + Math.abs(ghostY[i] + possibleDY[k] - pacmanY);
                        if (dist > maxDist) {
                            maxDist = dist;
                            bestMoveIndex = k;
                        }
                    }
                } else { // Chase Pac-Man (simplified: move towards Pac-Man)
                    int minDist = Integer.MAX_VALUE;
                    for (int k = 0; k < numPossibleMoves; k++) {
                        // Basic targeting: prefer moves that reduce distance to Pac-Man
            
                        int dist = Math.abs(ghostX[i] + possibleDX[k] - pacmanX) + Math.abs(ghostY[i] + possibleDY[k] - pacmanY);

                        
                        if (dist < minDist) {
                            minDist = dist;
                            bestMoveIndex = k;
                        } else if (dist == minDist && random.nextBoolean()) {
                             bestMoveIndex = k;
                        }
                    }
                }
                 if (bestMoveIndex != -1) {
                    ghostDX[i] = possibleDX[bestMoveIndex];
                    ghostDY[i] = possibleDY[bestMoveIndex];
                } else if (numPossibleMoves > 0) { // Fallback to random if targeting fails
                    int randomIndex = random.nextInt(numPossibleMoves);
                    ghostDX[i] = possibleDX[randomIndex];
                    ghostDY[i] = possibleDY[randomIndex];
                }
            }


            // Actually move the ghost
            newGhostX = ghostX[i] + ghostDX[i];
            newGhostY = ghostY[i] + ghostDY[i];

            // Handle tunnel wrapping for ghosts
            if (newGhostX == -1 && ghostDX[i] == -1) newGhostX = N_COLS - 1;
            if (newGhostX == N_COLS && ghostDX[i] == 1) newGhostX = 0;


            if (canMove(newGhostX, newGhostY)) {
                ghostX[i] = newGhostX;
                ghostY[i] = newGhostY;
            } else {
                // If it can't move in the chosen direction (should be rare with above logic), try random available.
                // This is a fallback.
                if (numPossibleMoves > 0) {
                    int randomIndex = random.nextInt(numPossibleMoves);
                    ghostDX[i] = possibleDX[randomIndex];
                    ghostDY[i] = possibleDY[randomIndex];
                    newGhostX = ghostX[i] + ghostDX[i];
                    newGhostY = ghostY[i] + ghostDY[i];
                     if (canMove(newGhostX, newGhostY)) {
                        ghostX[i] = newGhostX;
                        ghostY[i] = newGhostY;
                    }
                }
            }
        }
    }
    
    private boolean canMove(int x, int y) {
        return x >= 0 && x < N_COLS && y >= 0 && y < N_ROWS && screenData[y][x] != 1;
    }


    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        d = getSize();

        // Fill entire background
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, d.width, d.height);

        // Draw game area with padding
        g2d.translate(PADDING_X, PADDING_Y);
        
        drawMaze(g2d);
        drawScore(g2d);

        if (inGame) {
            playGame(g2d);
        } else if (win) {
            showWinScreen(g2d);
        } else {
            showIntroScreen(g2d);
        }

        // Reset translation
        g2d.translate(-PADDING_X, -PADDING_Y);

        Toolkit.getDefaultToolkit().sync();
    }

    private void drawMaze(Graphics2D g2d) {
        // Draw background
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        for (int r = 0; r < N_ROWS; r++) {
            for (int c = 0; c < N_COLS; c++) {
                int x = c * TILE_SIZE;
                int y = r * TILE_SIZE;

                if (screenData[r][c] == 1) { // Wall
                    // Draw wall with rounded corners
                    g2d.setColor(WALL_COLOR);
                    g2d.fillRoundRect(x, y, TILE_SIZE, TILE_SIZE, 8, 8);
                    
                    // Add wall highlight
                    g2d.setColor(new Color(0, 0, 200));
                    g2d.drawRoundRect(x, y, TILE_SIZE, TILE_SIZE, 8, 8);
                } else if (screenData[r][c] == 0) { // Dot
                    g2d.setColor(DOT_COLOR);
                    g2d.fillOval(x + TILE_SIZE/2 - 2, y + TILE_SIZE/2 - 2, 4, 4);
                } else if (screenData[r][c] == 2) { // Power Pellet
                    // Animated power pellet
                    int pulseSize = 6 + (int)(Math.sin(animationStep * 0.5) * 2);
                    g2d.setColor(POWER_PELLET_COLOR);
                    g2d.fillOval(x + TILE_SIZE/2 - pulseSize/2, 
                               y + TILE_SIZE/2 - pulseSize/2, 
                               pulseSize, pulseSize);
                }
            }
        }
    }

    private void drawPacman(Graphics2D g2d) {
        int centerX = pacmanX * TILE_SIZE + TILE_SIZE / 2;
        int centerY = pacmanY * TILE_SIZE + TILE_SIZE / 2;
        int radius = TILE_SIZE / 2 - 2;

        
        animationStep = (animationStep + 1) % ANIMATION_SPEED;
        if (animationStep == 0) {
            if (mouthClosing) {
                mouthAngle -= MOUTH_ANGLE_CHANGE;
                if (mouthAngle <= 0) {
                    mouthAngle = 0;
                    mouthClosing = false;
                }
            } else {
                mouthAngle += MOUTH_ANGLE_CHANGE;
                if (mouthAngle >= 45) {
                    mouthAngle = 45;
                    mouthClosing = true;
                }
            }
        }

        // Draw Pac-Man body
        g2d.setColor(PACMAN_COLOR);
        
        // Calculate mouth angles based on direction
        int startAngle = 0;
        int arcAngle = 360 - (mouthAngle * 2);
        
        
        if (pacmanDX == 1) { // Right
            startAngle = mouthAngle;
        } else if (pacmanDX == -1) { // Left
            startAngle = 180 + mouthAngle;
        } else if (pacmanDY == -1) { // Up
            startAngle = 90 + mouthAngle; 
        } else if (pacmanDY == 1) { // Down
            startAngle = 270 + mouthAngle; 
        }
        
        g2d.fillArc(centerX - radius, centerY - radius, 
                   radius * 2, radius * 2, 
                   startAngle, arcAngle);
    }

    private void drawGhosts(Graphics2D g2d) {
        for (int i = 0; i < N_GHOSTS; i++) {
            int centerX = ghostX[i] * TILE_SIZE + TILE_SIZE / 2;
            int centerY = ghostY[i] * TILE_SIZE + TILE_SIZE / 2;
            int radius = TILE_SIZE / 2 - 2;

            // Draw ghost body
            if (ghostFrightened[i]) {
                if (frightenedTimer < FRIGHTENED_DURATION / 2 && frightenedTimer % 10 < 5) {
                    g2d.setColor(Color.WHITE);
                } else {
                    g2d.setColor(FRIGHTENED_GHOST_COLOR);
                }
            } else {
                g2d.setColor(GHOST_COLORS[i]);
            }

           
            int bodyHeight = radius * 2;
            int bodyWidth = radius * 2;
            
            
            g2d.fillRoundRect(centerX - radius, centerY - radius, 
                            bodyWidth, bodyHeight - 4, 
                            radius, radius);

            
            int waveHeight = 6;
            int waveOffset = (int)(Math.sin(animationStep * 0.5) * 2);
            int bottomY = centerY + radius - 4;
            
            
            for (int j = 0; j < 3; j++) {
                int waveX = centerX - radius + j * (radius * 2 / 3);
                int waveWidth = radius * 2 / 3;
               
                g2d.fillArc(waveX, bottomY - waveHeight/2 + waveOffset, 
                           waveWidth, waveHeight, 
                           0, 180);
            }

            
            if (!ghostFrightened[i]) {
                for (int j = 0; j < 4; j++) {
                    int skirtY = bottomY + j;
                    int skirtWidth = bodyWidth - j * 3;
                    float alpha = 0.8f - (j * 0.2f);
                    
                   
                    Color skirtColor = new Color(
                        GHOST_COLORS[i].getRed(),
                        GHOST_COLORS[i].getGreen(),
                        GHOST_COLORS[i].getBlue(),
                        (int)(alpha * 255)
                    );
                    g2d.setColor(skirtColor);
                    
                   
                    g2d.fillRoundRect(centerX - skirtWidth/2, skirtY, 
                                    skirtWidth, 2, 
                                    4, 4);
                }
            }

           
            if (!ghostFrightened[i]) {
               
                g2d.setColor(GHOST_EYES_COLOR);
                int eyeSize = radius / 2;
                int eyeOffset = radius / 3;
                
                
                g2d.fillOval(centerX - eyeOffset - eyeSize/2, 
                            centerY - eyeSize/3, 
                            eyeSize, eyeSize);
                
                g2d.fillOval(centerX + eyeOffset - eyeSize/2, 
                            centerY - eyeSize/3, 
                            eyeSize, eyeSize);

               
                g2d.setColor(GHOST_PUPIL_COLOR);
                int pupilSize = eyeSize / 2;
                int pupilOffsetX = 0;
                int pupilOffsetY = 0;
                
               
                if (ghostDX[i] == 1) {
                    pupilOffsetX = 2;
                    pupilOffsetY = 0;
                } else if (ghostDX[i] == -1) {
                    pupilOffsetX = -2;
                    pupilOffsetY = 0;
                } else if (ghostDY[i] == 1) {
                    pupilOffsetX = 0;
                    pupilOffsetY = 2;
                } else if (ghostDY[i] == -1) {
                    pupilOffsetX = 0;
                    pupilOffsetY = -2;
                }

               
                g2d.fillOval(centerX - eyeOffset - pupilSize/2 + pupilOffsetX, 
                            centerY - pupilSize/3 + pupilOffsetY, 
                            pupilSize, pupilSize);
                
                g2d.fillOval(centerX + eyeOffset - pupilSize/2 + pupilOffsetX, 
                            centerY - pupilSize/3 + pupilOffsetY, 
                            pupilSize, pupilSize);
            } else {
                
                g2d.setColor(Color.WHITE);
                int eyeSize = radius / 3;
                
              
                g2d.fillOval(centerX - eyeSize - 2, centerY - eyeSize/3, eyeSize, eyeSize);
                g2d.fillOval(centerX + 2, centerY - eyeSize/3, eyeSize, eyeSize);
                
               
                g2d.setColor(Color.WHITE);
                int mouthWidth = radius;
                int mouthHeight = 3;
                g2d.fillRoundRect(centerX - mouthWidth/2, centerY + radius/3, 
                                mouthWidth, mouthHeight, 
                                2, 2);
            }
        }
    }
    private void drawScore(Graphics2D g) {
        
        g.setFont(new Font("Arial", Font.BOLD, 24)); 
        g.setColor(Color.WHITE);
        String s = "SCORE: " + score;
        g.drawString(s, PADDING_X + 10, PADDING_Y + SCREEN_HEIGHT + 40);

     
        for (int i = 0; i < lives; i++) {
            g.setColor(PACMAN_COLOR);
            int x = PADDING_X + SCREEN_WIDTH - (i + 1) * (TILE_SIZE + 15);
            int y = PADDING_Y + SCREEN_HEIGHT + 25; 
            g.fillArc(x, y, TILE_SIZE - 4, TILE_SIZE - 4, 45, 270);
        }
    }

    private void showIntroScreen(Graphics2D g2d) {
       
        int gameWidth = SCREEN_WIDTH;
        int gameHeight = SCREEN_HEIGHT;
        
      
        GradientPaint gradient = new GradientPaint(0, 0, new Color(0, 0, 40), 
                                                  gameWidth, gameHeight, new Color(0, 0, 80));
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, gameWidth, gameHeight);
    
       
        g2d.setColor(new Color(255, 255, 0, 100));
        g2d.setStroke(new BasicStroke(4));
        g2d.drawRoundRect(10, 10, gameWidth - 20, gameHeight - 20, 20, 20);
    
        
        String title = "PAC-MAN";
        Font titleFont = new Font("Arial", Font.BOLD, Math.min(48, gameWidth / 10));
        FontMetrics titleMetrics = getFontMetrics(titleFont);
        g2d.setFont(titleFont);
        
        int titleY = gameHeight / 4;
        
        // Draw shadow
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.drawString(title, gameWidth/2 - titleMetrics.stringWidth(title)/2 + 4, titleY + 4);
        
        // Draw main text
        g2d.setColor(Color.YELLOW);
        g2d.drawString(title, gameWidth/2 - titleMetrics.stringWidth(title)/2, titleY);
    
        // Draw status message
        String statusMsg;
        if (win) {
            statusMsg = "Level " + currentLevel + " Complete!";
        } else if (!inGame && lives > 0) {
            statusMsg = "Starting Level " + currentLevel;
        } else {
            statusMsg = "Game Over";
        }
    
        Font statusFont = new Font("Arial", Font.BOLD, Math.min(32, gameWidth / 15));
        FontMetrics statusMetrics = getFontMetrics(statusFont);
        g2d.setFont(statusFont);
        g2d.setColor(Color.WHITE);
        g2d.drawString(statusMsg, gameWidth/2 - statusMetrics.stringWidth(statusMsg)/2, gameHeight/2);
    
        // Draw score
        Font scoreFont = new Font("Arial", Font.BOLD, Math.min(24, gameWidth / 20));
        FontMetrics scoreMetrics = getFontMetrics(scoreFont);
        g2d.setFont(scoreFont);
        String scoreMsg = "Score: " + score;
        g2d.drawString(scoreMsg, gameWidth/2 - scoreMetrics.stringWidth(scoreMsg)/2, gameHeight/2 + 50);
    
        // Draw lives with animated Pac-Man icons
        int pacmanSize = Math.min(30, gameWidth / 20);
        int spacing = pacmanSize + 20;
        int pacmanTotalWidth = lives * spacing;
        int startPacmanX = gameWidth/2 - pacmanTotalWidth/2;
        
        for (int i = 0; i < lives; i++) {
            int x = startPacmanX + i * spacing;
            int y = gameHeight/2 + 100;
            
            // Animate Pac-Man mouth
            int mouthAngle = (int)(Math.sin(System.currentTimeMillis() / 200.0) * 30 + 30);
            g2d.setColor(Color.YELLOW);
            g2d.fillArc(x, y, pacmanSize, pacmanSize, mouthAngle, 360 - (mouthAngle * 2));
        }
    
       \
        if (!inGame && !win) {
            Font smallFont = new Font("Arial", Font.BOLD, Math.min(20, gameWidth / 25));
            FontMetrics smallMetrics = getFontMetrics(smallFont);
            g2d.setFont(smallFont);
            
            String restartMsg;
            if (lives == 0) {
                restartMsg = "Game Over - Press S to Start New Game";
                currentLevel = 1;
            } else {
                restartMsg = "Press S to Play";
            }
            
            // Blinking effect
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                g2d.setColor(Color.WHITE);
                g2d.drawString(restartMsg, 
                             gameWidth/2 - smallMetrics.stringWidth(restartMsg)/2, 
                             gameHeight - 50);
            }
        }
    
        // Draw decorative dots
        drawDecorativeDots(g2d, 0, 0, gameWidth, gameHeight);
    }
    
    private void showWinScreen(Graphics2D g2d) {
        // Use the available game area dimensions instead of window dimensions
        int gameWidth = SCREEN_WIDTH;
        int gameHeight = SCREEN_HEIGHT;
        
        // Create a gradient background for game area
        GradientPaint gradient = new GradientPaint(0, 0, new Color(0, 40, 0), 
                                                  gameWidth, gameHeight, new Color(0, 80, 0));
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, gameWidth, gameHeight);
    
        // Draw a decorative border
        g2d.setColor(new Color(0, 255, 0, 100));
        g2d.setStroke(new BasicStroke(4));
        g2d.drawRoundRect(10, 10, gameWidth - 20, gameHeight - 20, 20, 20);
    
        // Draw victory message with shadow
        String victoryMsg = "LEVEL " + currentLevel + " COMPLETE!";
        Font victoryFont = new Font("Arial", Font.BOLD, Math.min(40, gameWidth / 12));
        FontMetrics victoryMetrics = getFontMetrics(victoryFont);
        g2d.setFont(victoryFont);
        
        int victoryY = gameHeight / 3;
        
        // Draw shadow
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.drawString(victoryMsg, gameWidth/2 - victoryMetrics.stringWidth(victoryMsg)/2 + 4, victoryY + 4);
        
        // Draw main text
        g2d.setColor(new Color(0, 255, 0));
        g2d.drawString(victoryMsg, gameWidth/2 - victoryMetrics.stringWidth(victoryMsg)/2, victoryY);
    
        // Draw score with animation
        Font scoreFont = new Font("Arial", Font.BOLD, Math.min(32, gameWidth / 15));
        FontMetrics scoreMetrics = getFontMetrics(scoreFont);
        g2d.setFont(scoreFont);
        String scoreMsg = "Score: " + score;
        
        // Animate score text
        double scale = 1.0 + Math.sin(System.currentTimeMillis() / 200.0) * 0.1;
        AffineTransform originalTransform = g2d.getTransform();
        g2d.translate(gameWidth/2, gameHeight/2);
        g2d.scale(scale, scale);
        g2d.setColor(Color.WHITE);
        g2d.drawString(scoreMsg, -scoreMetrics.stringWidth(scoreMsg)/2, 0);
        g2d.setTransform(originalTransform);
    
        // Draw next level info
        Font infoFont = new Font("Arial", Font.BOLD, Math.min(24, gameWidth / 20));
        FontMetrics infoMetrics = getFontMetrics(infoFont);
        g2d.setFont(infoFont);
        g2d.setColor(Color.WHITE);
        
        String nextLevelMsg = "Next Level: " + (currentLevel + 1);
        String speedMsg = "Ghosts will move faster!";
        
        g2d.drawString(nextLevelMsg, gameWidth/2 - infoMetrics.stringWidth(nextLevelMsg)/2, gameHeight/2 + 50);
        g2d.drawString(speedMsg, gameWidth/2 - infoMetrics.stringWidth(speedMsg)/2, gameHeight/2 + 90);
    
        // Draw decorative elements
        drawVictoryDecorations(g2d, 0, 0, gameWidth, gameHeight);
    }

    private void drawDecorativeDots(Graphics2D g2d, int startX, int startY, int width, int height) {
        // Draw small dots around the screen within the usable area
        g2d.setColor(new Color(255, 255, 255, 100));
        Random rand = new Random(123); \
        for (int i = 0; i < 50; i++) {
            int x = startX + rand.nextInt(width);
            int y = startY + rand.nextInt(height);
            int size = rand.nextInt(4) + 2;
            g2d.fillOval(x, y, size, size);
        }
    }

    private void drawVictoryDecorations(Graphics2D g2d, int startX, int startY, int width, int height) {
        // Draw animated victory stars within the usable area
        long time = System.currentTimeMillis();
        Random rand = new Random(456); 
        
        for (int i = 0; i < 20; i++) {
            int x = startX + rand.nextInt(width);
            int y = startY + rand.nextInt(height);
            double scale = 0.5 + Math.sin(time/200.0 + i) * 0.3;
            
            AffineTransform originalTransform = g2d.getTransform();
            g2d.translate(x, y);
            g2d.scale(scale, scale);
            
            // Draw star
            g2d.setColor(new Color(255, 255, 0, 150));
            int[] xPoints = {0, 5, 10, 5, 8, 0, -8, -5, -10, -5};
            int[] yPoints = {-10, -5, 0, 5, 10, 7, 10, 5, 0, -5};
            g2d.fillPolygon(xPoints, yPoints, 10);
            
            g2d.setTransform(originalTransform);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        repaint(); // This will call paintComponent
    }

    class TAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int key = e.getKeyCode();

            if (key == KeyEvent.VK_S) {
                if (!inGame && !win) {
                    currentLevel = 1; // Reset level when starting new game
                    initGame();
                }
            } else if (inGame) {
                if (key == KeyEvent.VK_LEFT) {
                    reqDX = -1;
                    reqDY = 0;
                } else if (key == KeyEvent.VK_RIGHT) {
                    reqDX = 1;
                    reqDY = 0;
                } else if (key == KeyEvent.VK_UP) {
                    reqDX = 0;
                    reqDY = -1;
                } else if (key == KeyEvent.VK_DOWN) {
                    reqDX = 0;
                    reqDY = 1;
                } else if (key == KeyEvent.VK_ESCAPE && timer.isRunning()) {
                    inGame = false;
                }
            }
        }
    }
}

