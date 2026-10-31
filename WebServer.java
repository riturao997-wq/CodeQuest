import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class WebServer {
    private static final String DATA_FILE = "players_database.dat";
    private static HashMap<String, Player> playerDatabase = new HashMap<>();
    private static Player currentPlayer = null;
    
    private static List<CodingQuestion> level1Questions;
    private static Random random = new Random();
    private static CodingQuestion currentQuestion;

    public static void main(String[] args) throws IOException {
        loadDatabase();
        initializeLevel1Questions();
        loadNewQuestion();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", exchange -> {
            addCorsHeaders(exchange);
            String html = new String(Files.readAllBytes(Paths.get("index.html")));
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, html.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(html.getBytes());
            os.close();
        });

        // Register API
        server.createContext("/register", exchange -> {
            addCorsHeaders(exchange);
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                String[] parts = body.split("&");
                String username = parts[0].split("=")[1];
                String password = parts[1].split("=")[1];

                if (playerDatabase.containsKey(username)) {
                    sendJson(exchange, "{\"success\":false, \"message\":\"Username already exists! Choose another or login.\"}");
                } else {
                    Player newPlayer = new Player(username, password);
                    playerDatabase.put(username, newPlayer);
                    saveDatabase();
                    currentPlayer = newPlayer;
                    sendJson(exchange, "{\"success\":true, \"message\":\"Registration successful! Game started.\"}");
                }
            }
        });

        // Login API
        server.createContext("/login", exchange -> {
            addCorsHeaders(exchange);
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                String[] parts = body.split("&");
                String username = parts[0].split("=")[1];
                String password = parts[1].split("=")[1];

                Player p = playerDatabase.get(username);
                if (p != null && p.getPassword().equals(password)) {
                    currentPlayer = p;
                    sendJson(exchange, "{\"success\":true, \"message\":\"Login successful! Welcome back " + username + "\"}");
                } else {
                    sendJson(exchange, "{\"success\":false, \"message\":\"Invalid username or password!\"}");
                }
            }
        });

        // Logout API
        server.createContext("/logout", exchange -> {
            addCorsHeaders(exchange);
            currentPlayer = null;
            sendJson(exchange, "{\"success\":true, \"message\":\"Logged out successfully.\"}");
        });

        // Reset Progress API
        server.createContext("/reset", exchange -> {
            addCorsHeaders(exchange);
            if (currentPlayer != null && "POST".equals(exchange.getRequestMethod())) {
                currentPlayer.resetProgress();
                saveDatabase();
                sendJson(exchange, "{\"success\":true, \"message\":\"Your game progress has been reset!\"}");
            } else {
                sendJson(exchange, "{\"success\":false, \"message\":\"No active player session!\"}");
            }
        });

        // Game State API
        server.createContext("/game-state", exchange -> {
            addCorsHeaders(exchange);
            if (currentPlayer == null) {
                sendJson(exchange, "{\"logged\":false}");
                return;
            }
            String houseStatus = getHouseStage(currentPlayer.getSolvedCount());
            String json = String.format(
                "{\"logged\":true, \"name\":\"%s\", \"xp\":%d, \"level\":%d, \"solvedCount\":%d, \"house\":\"%s\", \"question\":\"%s\", \"hint\":\"%s\"}",
                currentPlayer.getUsername(), currentPlayer.getXp(), currentPlayer.getCurrentLevel(), 
                currentPlayer.getSolvedCount(), houseStatus, currentQuestion.prompt, currentQuestion.hint
            );
            sendJson(exchange, json);
        });

        // Submit Code API
        server.createContext("/submit-code", exchange -> {
            addCorsHeaders(exchange);
            if (currentPlayer == null) {
                sendJson(exchange, "{\"success\":false, \"message\":\"Please login first!\"}");
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                String userCode = readBody(exchange);
                boolean isCorrect = evaluateCode(userCode, currentQuestion);

                if (isCorrect) {
                    currentPlayer.setSolvedCount(currentPlayer.getSolvedCount() + 1);
                    currentPlayer.addXp(15);
                    saveDatabase();
                    String houseStatus = getHouseStage(currentPlayer.getSolvedCount());

                    if (currentPlayer.getSolvedCount() >= 50) {
                        currentPlayer.setCurrentLevel(2);
                        saveDatabase();
                        sendJson(exchange, String.format("{\"success\":true, \"message\":\"Congratulations! Level 1 completed, Level 2 unlocked!\", \"house\":\"%s\"}", houseStatus));
                    } else {
                        loadNewQuestion();
                        sendJson(exchange, String.format("{\"success\":true, \"message\":\"Correct Answer! House upgraded.\", \"house\":\"%s\"}", houseStatus));
                    }
                } else {
                    sendJson(exchange, "{\"success\":false, \"message\":\"Incorrect logic or syntax. Try again!\"}");
                }
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("[✔] CodeQuest Server started at: http://localhost:8080");
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
        return body.toString();
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    @SuppressWarnings("unchecked")
    private static void loadDatabase() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            playerDatabase = (HashMap<String, Player>) ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveDatabase() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(playerDatabase);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadNewQuestion() {
        if (!level1Questions.isEmpty()) {
            currentQuestion = level1Questions.get(random.nextInt(level1Questions.size()));
        }
    }

    private static boolean evaluateCode(String code, CodingQuestion q) {
        for (String keyword : q.expectedKeywords) {
            if (!code.contains(keyword)) return false;
        }
        return true;
    }

    private static String getHouseStage(int count) {
        if (count <= 10) return "🪵 Foundation and initial wood gathered.";
        else if (count <= 25) return "🧱 Walls and pillars are built!";
        else if (count <= 40) return "🚪 Doors and windows are set up!";
        else if (count < 50) return "🏠 Roof is installed!";
        else return "🏰 Luxury House Built! Level 2 Unlocked!";
    }

    private static void initializeLevel1Questions() {
        level1Questions = new ArrayList<>();
        level1Questions.add(new CodingQuestion("Write code to print 'Hello World' to the console in Java.", "System.out.println", new String[]{"System.out.println", "Hello World"}));
        level1Questions.add(new CodingQuestion("Write the main method declaration line in Java.", "public static void main", new String[]{"public", "static", "void", "main"}));
        level1Questions.add(new CodingQuestion("Declare an integer variable named 'age' and assign 20 to it.", "int age = 20;", new String[]{"int", "age", "20"}));
        level1Questions.add(new CodingQuestion("Write the opening line for defining a class named 'Player' in Java.", "class Player", new String[]{"class", "Player"}));
        level1Questions.add(new CodingQuestion("Write code to print the addition of two numbers using '+' operator.", "System.out.println(a + b);", new String[]{"System.out.println", "+", ";"}));
    }
}

class CodingQuestion implements Serializable {
    private static final long serialVersionUID = 1L;
    String prompt;
    String hint;
    String[] expectedKeywords;

    public CodingQuestion(String prompt, String hint, String[] expectedKeywords) {
        this.prompt = prompt;
        this.hint = hint;
        this.expectedKeywords = expectedKeywords;
    }
}