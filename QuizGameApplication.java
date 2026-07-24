/* =====================================================================
 *  QUIZ GAME APPLICATION  (Programming Fundamentals - CSC103)
 *  A single-file, menu-driven, terminal based MCQ quiz system.
 *
 *  Meets the lab terminal requirements:
 *    - Menu driven with proper flow between menus
 *    - Separate login system for two roles: ADMIN and STUDENT
 *    - Four record-management methods for the Question entity:
 *          Add  /  View  /  Update  /  Search
 *    - Records stored in ARRAYS at runtime
 *    - File handling (text files) for permanent storage on disk
 *    - Exception handling everywhere so the program never crashes
 *    - Field validation (numeric ranges, non-empty, correct option, etc.)
 *    - Quiz features: 100 GRE-style MCQs, per-question TIMER,
 *      DIFFICULTY LEVELS, scoring, instant feedback, shuffled options
 *    - Each student's result is saved (serialized) to a .txt file
 *      named by the student's NAME and ID
 *
 *  Default admin login ->  username: admin   password: admin123
 *  (Students can register their own account from the main menu.)
 * ===================================================================== */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

/* ---------------------------------------------------------------------
 * Question entity. One MCQ = the prompt, four options, the index (0-3)
 * of the correct option, a difficulty band and a category label.
 * ------------------------------------------------------------------- */
class Question {
    int id;
    String difficulty;     // EASY / MEDIUM / HARD
    String category;       // Vocabulary / Quantitative / Text Completion ...
    String text;           // the question prompt
    String[] options;      // exactly 4 options
    int correctIndex;      // 0..3

    Question(int id, String difficulty, String category, String text,
             String[] options, int correctIndex) {
        this.id = id;
        this.difficulty = difficulty;
        this.category = category;
        this.text = text;
        this.options = options;
        this.correctIndex = correctIndex;
    }

    // Convert this question into one line for the questions.txt file.
    String toFileLine() {
        return id + "|" + difficulty + "|" + category + "|" + text + "|"
                + options[0] + "|" + options[1] + "|" + options[2] + "|"
                + options[3] + "|" + correctIndex;
    }
}

/* ---------------------------------------------------------------------
 * Account entity for the login system.
 * ------------------------------------------------------------------- */
class Account {
    String username;
    String password;
    String role;       // ADMIN / STUDENT
    String fullName;
    String studentId;
    int age;

    Account(String username, String password, String role,
            String fullName, String studentId, int age) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.fullName = fullName;
        this.studentId = studentId;
        this.age = age;
    }

    String toFileLine() {
        return username + "|" + password + "|" + role + "|"
                + fullName + "|" + studentId + "|" + age;
    }
}

/* =====================================================================
 *  MAIN APPLICATION CLASS
 * ===================================================================== */
public class QuizGameApplication {

    /* ---- ANSI colour codes for a nicer terminal look ---- */
    static final boolean USE_COLOR = true;          // set false on very old terminals
    static String c(String code) { return USE_COLOR ? code : ""; }
    static final String RESET  = "\u001B[0m";
    static final String BOLD   = "\u001B[1m";
    static final String RED    = "\u001B[31m";
    static final String GREEN  = "\u001B[32m";
    static final String YELLOW = "\u001B[33m";
    static final String BLUE   = "\u001B[34m";
    static final String CYAN   = "\u001B[36m";
    static final String PURPLE = "\u001B[35m";

    /* ---- file names used for permanent storage ---- */
    static final String QUESTIONS_FILE = "questions.txt";
    static final String ACCOUNTS_FILE  = "accounts.txt";
    static final String MASTER_RESULTS = "results_master.txt";

    /* ---- ARRAYS used to hold records in memory (requirement #4) ---- */
    static Question[] questions = new Question[200];
    static int questionCount = 0;

    static Account[] accounts = new Account[200];
    static int accountCount = 0;

    static Scanner sc = new Scanner(System.in);

    /* =================================================================
     *  PROGRAM ENTRY POINT
     * ================================================================= */
    public static void main(String[] args) {
        try {
            loadAccounts();      // read existing users (creates default admin if none)
            loadQuestions();     // read question bank (seeds 100 GRE MCQs first run)
            mainMenu();
        } catch (Exception e) {
            // top-level safety net: the program must never crash
            System.out.println(c(RED) + "Unexpected error: " + e.getMessage() + c(RESET));
        }
    }

    /* =================================================================
     *  MAIN MENU  (entry flow)
     * ================================================================= */
    static void mainMenu() {
        while (true) {
            clearlines();
            printBanner();
            box("MAIN MENU");
            System.out.println("  " + c(CYAN) + "1." + c(RESET) + " Login");
            System.out.println("  " + c(CYAN) + "2." + c(RESET) + " Register as Student");
            System.out.println("  " + c(CYAN) + "3." + c(RESET) + " About this program");
            System.out.println("  " + c(CYAN) + "0." + c(RESET) + " Exit");
            int choice = readInt("Choose an option: ", 0, 3);

            switch (choice) {
                case 1: login();          break;
                case 2: registerStudent();break;
                case 3: about();          break;
                case 0:
                    System.out.println(c(GREEN) + "\nGoodbye! Thanks for using the Quiz App.\n" + c(RESET));
                    return;
            }
        }
    }

    /* =================================================================
     *  AUTHENTICATION
     * ================================================================= */
    static void login() {
        box("LOGIN");
        String u = readNonEmpty("Username: ");
        String p = readNonEmpty("Password: ");

        Account found = null;
        for (int i = 0; i < accountCount; i++) {
            if (accounts[i].username.equals(u) && accounts[i].password.equals(p)) {
                found = accounts[i];
                break;
            }
        }

        if (found == null) {
            System.out.println(c(RED) + "Invalid username or password." + c(RESET));
            pause();
            return;
        }

        System.out.println(c(GREEN) + "\nWelcome, " + found.fullName + "! (" + found.role + ")" + c(RESET));
        pause();

        if (found.role.equals("ADMIN")) adminMenu(found);
        else studentMenu(found);
    }

    static void registerStudent() {
        box("STUDENT REGISTRATION");
        String username = readUniqueUsername();
        String password = readNonEmpty("Choose a password: ");
        String name     = readNonEmpty("Full name: ");
        String sid       = readNonEmpty("Student ID (e.g. SP26-BAI-056): ");
        int age          = readInt("Age: ", 5, 120);

        addAccount(new Account(username, password, "STUDENT", name, sid, age));
        saveAccounts();
        System.out.println(c(GREEN) + "\nAccount created! You can now log in." + c(RESET));
        pause();
    }

    /* =================================================================
     *  ADMIN AREA
     * ================================================================= */
    static void adminMenu(Account admin) {
        while (true) {
            clearlines();
            printBanner();
            box("ADMIN PANEL  -  " + admin.fullName);
            System.out.println("  " + c(CYAN) + "1." + c(RESET) + " Add a question");
            System.out.println("  " + c(CYAN) + "2." + c(RESET) + " View all questions");
            System.out.println("  " + c(CYAN) + "3." + c(RESET) + " Update a question");
            System.out.println("  " + c(CYAN) + "4." + c(RESET) + " Search questions");
            System.out.println("  " + c(CYAN) + "5." + c(RESET) + " View all student results");
            System.out.println("  " + c(CYAN) + "0." + c(RESET) + " Logout");
            int choice = readInt("Choose an option: ", 0, 5);

            switch (choice) {
                case 1: addQuestion();    break;
                case 2: viewQuestions();  break;
                case 3: updateQuestion(); break;
                case 4: searchQuestions();break;
                case 5: viewAllResults(); break;
                case 0: return;
            }
        }
    }

    // ---- ADD record ----
    static void addQuestion() {
        box("ADD QUESTION");
        String diff = readDifficulty();
        String cat  = readNonEmpty("Category (e.g. Vocabulary): ");
        String text = readNonEmpty("Question text: ");
        String[] opt = new String[4];
        for (int i = 0; i < 4; i++) {
            opt[i] = readNonEmpty("Option " + (char) ('A' + i) + ": ");
        }
        int correct = readInt("Correct option (1=A,2=B,3=C,4=D): ", 1, 4) - 1;

        int newId = nextQuestionId();
        addQuestionToArray(new Question(newId, diff, cat, text, opt, correct));
        saveQuestions();
        System.out.println(c(GREEN) + "\nQuestion added with ID " + newId + "." + c(RESET));
        pause();
    }

    // ---- VIEW record ----
    static void viewQuestions() {
        box("ALL QUESTIONS (" + questionCount + ")");
        if (questionCount == 0) {
            System.out.println("No questions in the bank.");
        }
        for (int i = 0; i < questionCount; i++) {
            printQuestionAdmin(questions[i]);
            if ((i + 1) % 5 == 0 && i + 1 < questionCount) {
                System.out.print(c(YELLOW) + "  -- press Enter for more --" + c(RESET));
                readLineBlocking();
            }
        }
        pause();
    }

    // ---- UPDATE record ----
    static void updateQuestion() {
        box("UPDATE QUESTION");
        int id = readInt("Enter the ID of the question to update: ", 1, Integer.MAX_VALUE);
        Question q = findQuestionById(id);
        if (q == null) {
            System.out.println(c(RED) + "No question found with ID " + id + "." + c(RESET));
            pause();
            return;
        }
        System.out.println("\nCurrent question:");
        printQuestionAdmin(q);
        System.out.println("Leave a field blank to keep the current value.\n");

        String t = readOptional("New question text: ");
        if (!t.isEmpty()) q.text = t;
        for (int i = 0; i < 4; i++) {
            String o = readOptional("New option " + (char) ('A' + i) + ": ");
            if (!o.isEmpty()) q.options[i] = o;
        }
        String d = readOptional("New difficulty (EASY/MEDIUM/HARD): ").toUpperCase();
        if (d.equals("EASY") || d.equals("MEDIUM") || d.equals("HARD")) q.difficulty = d;

        String cc = readOptional("New correct option (1-4): ");
        if (!cc.isEmpty()) {
            try {
                int v = Integer.parseInt(cc.trim());
                if (v >= 1 && v <= 4) q.correctIndex = v - 1;
                else System.out.println(c(YELLOW) + "Out of range - kept old answer." + c(RESET));
            } catch (NumberFormatException e) {
                System.out.println(c(YELLOW) + "Not a number - kept old answer." + c(RESET));
            }
        }
        saveQuestions();
        System.out.println(c(GREEN) + "\nQuestion updated." + c(RESET));
        pause();
    }

    // ---- SEARCH record ----
    static void searchQuestions() {
        box("SEARCH QUESTIONS");
        String key = readNonEmpty("Enter a keyword (searches text & category): ").toLowerCase();
        int hits = 0;
        for (int i = 0; i < questionCount; i++) {
            Question q = questions[i];
            if (q.text.toLowerCase().contains(key) || q.category.toLowerCase().contains(key)) {
                printQuestionAdmin(q);
                hits++;
            }
        }
        System.out.println(c(YELLOW) + "\n" + hits + " match(es) found." + c(RESET));
        pause();
    }

    static void viewAllResults() {
        box("ALL STUDENT RESULTS");
        File f = new File(MASTER_RESULTS);
        if (!f.exists()) {
            System.out.println("No results recorded yet.");
            pause();
            return;
        }
        try (Scanner fileSc = new Scanner(f)) {
            while (fileSc.hasNextLine()) System.out.println("  " + fileSc.nextLine());
        } catch (IOException e) {
            System.out.println(c(RED) + "Could not read results file." + c(RESET));
        }
        pause();
    }

    /* =================================================================
     *  STUDENT AREA
     * ================================================================= */
    static void studentMenu(Account student) {
        while (true) {
            clearlines();
            printBanner();
            box("STUDENT MENU  -  " + student.fullName);
            System.out.println("  " + c(CYAN) + "1." + c(RESET) + " Start a quiz");
            System.out.println("  " + c(CYAN) + "2." + c(RESET) + " View my past results");
            System.out.println("  " + c(CYAN) + "3." + c(RESET) + " Question bank statistics");
            System.out.println("  " + c(CYAN) + "0." + c(RESET) + " Logout");
            int choice = readInt("Choose an option: ", 0, 3);

            switch (choice) {
                case 1: startQuiz(student);  break;
                case 2: viewMyResults(student); break;
                case 3: showStats();         break;
                case 0: return;
            }
        }
    }

    /* ---------- the quiz engine ---------- */
    static void startQuiz(Account student) {
        box("CHOOSE DIFFICULTY");
        System.out.println("  " + c(GREEN)  + "1. EASY"   + c(RESET) + "   (30 seconds per question)");
        System.out.println("  " + c(YELLOW) + "2. MEDIUM" + c(RESET) + " (25 seconds per question)");
        System.out.println("  " + c(RED)    + "3. HARD"   + c(RESET) + "   (20 seconds per question)");
        System.out.println("  " + c(PURPLE) + "4. MIXED"  + c(RESET) + "  (25 seconds per question)");
        int d = readInt("Select level: ", 1, 4);

        String level;
        int timeLimit;
        switch (d) {
            case 1: level = "EASY";   timeLimit = 30; break;
            case 2: level = "MEDIUM"; timeLimit = 25; break;
            case 3: level = "HARD";   timeLimit = 20; break;
            default: level = "MIXED"; timeLimit = 25; break;
        }

        // Build a working array of questions that match the chosen level.
        Question[] pool = new Question[questionCount];
        int poolCount = 0;
        for (int i = 0; i < questionCount; i++) {
            if (level.equals("MIXED") || questions[i].difficulty.equals(level)) {
                pool[poolCount++] = questions[i];
            }
        }
        if (poolCount == 0) {
            System.out.println(c(RED) + "No questions available for that level." + c(RESET));
            pause();
            return;
        }

        // Shuffle the pool (simple Fisher-Yates) and take up to 10 questions.
        shuffle(pool, poolCount);
        int total = Math.min(10, poolCount);

        int howMany = readInt("How many questions (1-" + total + ")? ", 1, total);
        total = howMany;

        System.out.println(c(CYAN) + "\nStarting a " + level + " quiz of " + total
                + " questions. You have " + timeLimit + "s each. Good luck!\n" + c(RESET));
        pause();

        int score = 0;
        StringBuilder review = new StringBuilder();

        for (int i = 0; i < total; i++) {
            Question q = pool[i];
            clearlines();
            System.out.println(c(BOLD) + c(BLUE) + "Question " + (i + 1) + " of " + total
                    + "   [" + q.difficulty + " | " + q.category + "]" + c(RESET));
            System.out.println(c(YELLOW) + "Score so far: " + score + c(RESET));
            line();
            System.out.println(c(BOLD) + q.text + c(RESET) + "\n");

            // Shuffle the option order so the answer position varies.
            int[] order = {0, 1, 2, 3};
            shuffleInts(order);
            int displayedCorrect = -1;
            for (int j = 0; j < 4; j++) {
                System.out.println("   " + (char) ('A' + j) + ") " + q.options[order[j]]);
                if (order[j] == q.correctIndex) displayedCorrect = j;
            }

            System.out.print(c(PURPLE) + "\nYour answer (A/B/C/D) - " + timeLimit + "s: " + c(RESET));
            String ans = readLineTimed(timeLimit);

            String correctLetter = String.valueOf((char) ('A' + displayedCorrect));

            if (ans == null) {
                System.out.println(c(RED) + "\n[Time's up!] The correct answer was "
                        + correctLetter + "." + c(RESET));
                review.append("Q").append(i + 1).append(": TIMED OUT (correct ")
                      .append(correctLetter).append(")\n");
            } else {
                ans = ans.trim().toUpperCase();
                if (ans.equals(correctLetter)) {
                    System.out.println(c(GREEN) + "Correct!" + c(RESET));
                    score++;
                    review.append("Q").append(i + 1).append(": CORRECT\n");
                } else {
                    String shown = ans.isEmpty() ? "(blank)" : ans;
                    System.out.println(c(RED) + "Wrong. You chose " + shown
                            + ", correct was " + correctLetter + "." + c(RESET));
                    review.append("Q").append(i + 1).append(": WRONG (you ")
                          .append(shown).append(", correct ").append(correctLetter).append(")\n");
                }
            }
            pause();
        }

        // ---- final scoring & feedback ----
        clearlines();
        box("QUIZ COMPLETE");
        double pct = total == 0 ? 0 : (score * 100.0 / total);
        System.out.println("  Student : " + student.fullName + " (" + student.studentId + ")");
        System.out.println("  Level   : " + level);
        System.out.println("  Score   : " + score + " / " + total);
        System.out.printf ("  Percent : %.1f%%%n", pct);
        System.out.println("  Grade   : " + feedback(pct));

        saveResult(student, level, score, total, pct, review.toString());
        System.out.println(c(GREEN) + "\nYour result has been saved to a file." + c(RESET));
        pause();
    }

    static String feedback(double pct) {
        if (pct >= 90) return c(GREEN)  + "Outstanding! GRE-ready." + c(RESET);
        if (pct >= 70) return c(GREEN)  + "Great work." + c(RESET);
        if (pct >= 50) return c(YELLOW) + "Decent - keep practising." + c(RESET);
        return c(RED) + "Needs more study. Don't give up!" + c(RESET);
    }

    /* ---- save the result, serialized by student NAME + ID ---- */
    static void saveResult(Account student, String level, int score,
                           int total, double pct, String review) {
        String safe = (student.studentId + "_" + student.fullName).replaceAll("[^a-zA-Z0-9]", "_");
        String fileName = "Result_" + safe + ".txt";
        String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date());

        // per-student file (append so history is kept)
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)))) {
            pw.println("=========================================");
            pw.println("Name      : " + student.fullName);
            pw.println("ID        : " + student.studentId);
            pw.println("Date      : " + stamp);
            pw.println("Level     : " + level);
            pw.println("Score     : " + score + " / " + total);
            pw.printf ("Percentage: %.1f%%%n", pct);
            pw.println("Breakdown :");
            pw.print(review);
            pw.println("=========================================");
        } catch (IOException e) {
            System.out.println(c(RED) + "Could not save personal result file." + c(RESET));
        }

        // master log for the admin
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(MASTER_RESULTS, true)))) {
            pw.printf("%s | %s (%s) | %s | %d/%d | %.1f%%%n",
                    stamp, student.fullName, student.studentId, level, score, total, pct);
        } catch (IOException e) {
            System.out.println(c(RED) + "Could not update master results file." + c(RESET));
        }
    }

    static void viewMyResults(Account student) {
        box("MY RESULTS");
        String safe = (student.studentId + "_" + student.fullName).replaceAll("[^a-zA-Z0-9]", "_");
        File f = new File("Result_" + safe + ".txt");
        if (!f.exists()) {
            System.out.println("You have no saved results yet.");
            pause();
            return;
        }
        try (Scanner fileSc = new Scanner(f)) {
            while (fileSc.hasNextLine()) System.out.println("  " + fileSc.nextLine());
        } catch (IOException e) {
            System.out.println(c(RED) + "Could not read your results." + c(RESET));
        }
        pause();
    }

    static void showStats() {
        int e = 0, m = 0, h = 0;
        for (int i = 0; i < questionCount; i++) {
            if (questions[i].difficulty.equals("EASY")) e++;
            else if (questions[i].difficulty.equals("MEDIUM")) m++;
            else h++;
        }
        box("QUESTION BANK STATISTICS");
        System.out.println("  Total questions : " + questionCount);
        System.out.println("  Easy            : " + e);
        System.out.println("  Medium          : " + m);
        System.out.println("  Hard            : " + h);
        pause();
    }

    /* =================================================================
     *  FILE  +  ARRAY  HELPERS
     * ================================================================= */
    static void loadAccounts() {
        accountCount = 0;
        File f = new File(ACCOUNTS_FILE);
        if (!f.exists()) {
            // create the default admin account on first run
            addAccount(new Account("admin", "admin123", "ADMIN", "System Administrator", "ADMIN-000", 30));
            saveAccounts();
            return;
        }
        try (Scanner fileSc = new Scanner(f)) {
            while (fileSc.hasNextLine()) {
                String[] p = fileSc.nextLine().split("\\|");
                if (p.length == 6) {
                    int age;
                    try { age = Integer.parseInt(p[5].trim()); } catch (Exception ex) { age = 0; }
                    addAccount(new Account(p[0], p[1], p[2], p[3], p[4], age));
                }
            }
        } catch (IOException e) {
            System.out.println(c(RED) + "Could not read accounts file." + c(RESET));
        }
        // safety: guarantee at least one admin exists
        boolean hasAdmin = false;
        for (int i = 0; i < accountCount; i++) if (accounts[i].role.equals("ADMIN")) hasAdmin = true;
        if (!hasAdmin) {
            addAccount(new Account("admin", "admin123", "ADMIN", "System Administrator", "ADMIN-000", 30));
            saveAccounts();
        }
    }

    static void saveAccounts() {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(ACCOUNTS_FILE)))) {
            for (int i = 0; i < accountCount; i++) pw.println(accounts[i].toFileLine());
        } catch (IOException e) {
            System.out.println(c(RED) + "Could not save accounts." + c(RESET));
        }
    }

    static void addAccount(Account a) {
        if (accountCount == accounts.length) {
            Account[] bigger = new Account[accounts.length * 2];
            System.arraycopy(accounts, 0, bigger, 0, accounts.length);
            accounts = bigger;
        }
        accounts[accountCount++] = a;
    }

    static void loadQuestions() {
        questionCount = 0;
        File f = new File(QUESTIONS_FILE);
        if (!f.exists()) {
            seedQuestions();   // first run: create 100 GRE MCQs and write the file
            saveQuestions();
            return;
        }
        try (Scanner fileSc = new Scanner(f)) {
            while (fileSc.hasNextLine()) {
                String row = fileSc.nextLine();
                if (row.trim().isEmpty()) continue;
                String[] p = row.split("\\|");
                if (p.length == 9) {
                    try {
                        int id = Integer.parseInt(p[0].trim());
                        String[] opt = { p[4], p[5], p[6], p[7] };
                        int correct = Integer.parseInt(p[8].trim());
                        addQuestionToArray(new Question(id, p[1], p[2], p[3], opt, correct));
                    } catch (NumberFormatException ex) {
                        // skip a malformed line rather than crashing
                    }
                }
            }
        } catch (IOException e) {
            System.out.println(c(RED) + "Could not read questions file." + c(RESET));
        }
        if (questionCount == 0) { seedQuestions(); saveQuestions(); }
    }

    static void saveQuestions() {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(QUESTIONS_FILE)))) {
            for (int i = 0; i < questionCount; i++) pw.println(questions[i].toFileLine());
        } catch (IOException e) {
            System.out.println(c(RED) + "Could not save questions." + c(RESET));
        }
    }

    static void addQuestionToArray(Question q) {
        if (questionCount == questions.length) {
            Question[] bigger = new Question[questions.length * 2];
            System.arraycopy(questions, 0, bigger, 0, questions.length);
            questions = bigger;
        }
        questions[questionCount++] = q;
    }

    static Question findQuestionById(int id) {
        for (int i = 0; i < questionCount; i++) if (questions[i].id == id) return questions[i];
        return null;
    }

    static int nextQuestionId() {
        int max = 0;
        for (int i = 0; i < questionCount; i++) if (questions[i].id > max) max = questions[i].id;
        return max + 1;
    }

    /* =================================================================
     *  INPUT  +  VALIDATION  HELPERS  (so the program never crashes)
     * ================================================================= */
    static int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(c(CYAN) + prompt + c(RESET));
            String in = readLineBlocking().trim();
            try {
                int v = Integer.parseInt(in);
                if (v < min || v > max) {
                    System.out.println(c(RED) + "Please enter a number between "
                            + min + " and " + max + "." + c(RESET));
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println(c(RED) + "That is not a valid whole number." + c(RESET));
            }
        }
    }

    static String readNonEmpty(String prompt) {
        while (true) {
            System.out.print(c(CYAN) + prompt + c(RESET));
            String in = readLineBlocking().trim();
            if (!in.isEmpty()) return in;
            System.out.println(c(RED) + "This field cannot be empty." + c(RESET));
        }
    }

    static String readOptional(String prompt) {
        System.out.print(c(CYAN) + prompt + c(RESET));
        return readLineBlocking().trim();
    }

    static String readUniqueUsername() {
        while (true) {
            String u = readNonEmpty("Choose a username: ");
            boolean taken = false;
            for (int i = 0; i < accountCount; i++) if (accounts[i].username.equals(u)) taken = true;
            if (taken) System.out.println(c(RED) + "That username is taken, try another." + c(RESET));
            else return u;
        }
    }

    static String readDifficulty() {
        while (true) {
            String d = readNonEmpty("Difficulty (EASY/MEDIUM/HARD): ").toUpperCase();
            if (d.equals("EASY") || d.equals("MEDIUM") || d.equals("HARD")) return d;
            System.out.println(c(RED) + "Type EASY, MEDIUM or HARD." + c(RESET));
        }
    }

    // Blocking line read straight from System.in (kept consistent with timed read).
    static String readLineBlocking() {
        StringBuilder sb = new StringBuilder();
        try {
            int ch = System.in.read();
            if (ch == -1) {              // end of input (Ctrl-D or closed stream)
                System.out.println();
                System.exit(0);          // exit cleanly instead of looping forever
            }
            while (ch != -1) {
                if (ch == '\n') break;
                if (ch != '\r') sb.append((char) ch);
                ch = System.in.read();
            }
        } catch (IOException e) { /* ignore */ }
        return sb.toString();
    }

    // Timed line read used by the quiz. Returns null if time runs out.
    static String readLineTimed(int seconds) {
        long end = System.currentTimeMillis() + seconds * 1000L;
        StringBuilder sb = new StringBuilder();
        try {
            while (System.currentTimeMillis() < end) {
                if (System.in.available() > 0) {
                    int ch = System.in.read();
                    if (ch == '\n') return sb.toString();
                    if (ch != '\r') sb.append((char) ch);
                } else {
                    Thread.sleep(40);
                }
            }
        } catch (Exception e) { /* ignore -> treated as timeout */ }
        return null;
    }

    /* =================================================================
     *  SHUFFLING (simple Fisher-Yates, no external libraries)
     * ================================================================= */
    static java.util.Random rng = new java.util.Random();

    static void shuffle(Question[] arr, int n) {
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Question t = arr[i]; arr[i] = arr[j]; arr[j] = t;
        }
    }

    static void shuffleInts(int[] arr) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = arr[i]; arr[i] = arr[j]; arr[j] = t;
        }
    }

    /* =================================================================
     *  SMALL UI HELPERS
     * ================================================================= */
    static void printBanner() {
        System.out.println(c(BOLD) + c(PURPLE) +
            "  ___  _   _ ___ ____    ____    _    __  __ _____ \n" +
            " / _ \\| | | |_ _|__  /  / ___|  / \\  |  \\/  | ____|\n" +
            "| | | | | | || |  / /  | |  _  / _ \\ | |\\/| |  _|  \n" +
            "| |_| | |_| || | / /_  | |_| |/ ___ \\| |  | | |___ \n" +
            " \\__\\_\\\\___/|___/____|  \\____/_/   \\_\\_|  |_|_____|" + c(RESET));
        System.out.println(c(CYAN) + "        GRE Practice  -  100 toughest MCQs\n" + c(RESET));
    }

    static void box(String title) {
        line();
        System.out.println(c(BOLD) + c(YELLOW) + "  " + title + c(RESET));
        line();
    }

    static void line() {
        System.out.println(c(BLUE) + "==================================================" + c(RESET));
    }

    static void pause() {
        System.out.print(c(YELLOW) + "\n[ Press Enter to continue ]" + c(RESET));
        readLineBlocking();
    }

    // print a little spacing to "refresh" the screen between menus
    static void clearlines() {
        System.out.print("\n\n");
    }

    static void printQuestionAdmin(Question q) {
        System.out.println(c(BOLD) + "  #" + q.id + "  [" + q.difficulty + " | " + q.category + "]" + c(RESET));
        System.out.println("    " + q.text);
        for (int i = 0; i < 4; i++) {
            String mark = (i == q.correctIndex) ? c(GREEN) + "  <-- correct" + c(RESET) : "";
            System.out.println("      " + (char) ('A' + i) + ") " + q.options[i] + mark);
        }
        System.out.println();
    }

    static void about() {
        box("ABOUT");
        System.out.println("  Quiz Game Application (CSC103 - Programming Fundamentals)");
        System.out.println("  A terminal MCQ system with login, an admin-managed");
        System.out.println("  question bank, timed quizzes, difficulty levels and");
        System.out.println("  per-student result files saved to disk.");
        pause();
    }

    /* =================================================================
     *  SEED DATA: 100 GRE-style MCQs (used only on first run)
     *  Format per entry: difficulty | category | text | A | B | C | D | correctIndex
     * ================================================================= */
    static void seedQuestions() {
        String[] seed = {
            // ---------- Vocabulary (synonyms / meaning) ----------
            "MEDIUM|Vocabulary|Choose the word closest in meaning to ABATE.|Intensify|Lessen|Confuse|Gather|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to ABERRATION.|Normality|Deviation|Agreement|Clarity|1",
            "HARD|Vocabulary|Choose the word closest in meaning to ABSCOND.|Reveal|Flee secretly|Confront|Remain|1",
            "HARD|Vocabulary|Choose the word closest in meaning to ABSTRUSE.|Obvious|Hard to understand|Pleasant|Brief|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to ACERBIC.|Sweet|Sharp or bitter|Gentle|Wide|1",
            "EASY|Vocabulary|Choose the word closest in meaning to ADMONISH.|Praise|Warn or reprimand|Ignore|Reward|1",
            "HARD|Vocabulary|Choose the word closest in meaning to ALACRITY.|Reluctance|Brisk eagerness|Sorrow|Confusion|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to AMELIORATE.|Worsen|Improve|Decorate|Confuse|1",
            "HARD|Vocabulary|Choose the word closest in meaning to ANATHEMA.|A blessing|Something detested|A remedy|A tradition|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to ANOMALY.|Standard|Irregularity|Routine|Symmetry|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to ANTIPATHY.|Deep affection|Strong dislike|Indifference|Curiosity|1",
            "EASY|Vocabulary|Choose the word closest in meaning to APATHY.|Enthusiasm|Lack of interest|Anger|Sympathy|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to ASSUAGE.|Aggravate|Ease or soothe|Question|Abandon|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to AUSTERE.|Lavish|Severe or plain|Cheerful|Flexible|1",
            "HARD|Vocabulary|Choose the word closest in meaning to BELIE.|Confirm|Contradict or misrepresent|Announce|Support|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to CACOPHONY.|Harmony|Harsh discordant sound|Silence|Melody|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to CAPRICIOUS.|Steady|Impulsive and unpredictable|Careful|Honest|1",
            "HARD|Vocabulary|Choose the word closest in meaning to CASTIGATE.|Praise|Criticize harshly|Ignore|Reward|1",
            "HARD|Vocabulary|Choose the word closest in meaning to CHURLISH.|Polite|Rude or surly|Generous|Cheerful|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to COGENT.|Weak|Convincing|Confusing|Lengthy|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to CONFLAGRATION.|A small spark|A large destructive fire|A flood|A celebration|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to CONTRITE.|Unrepentant|Deeply remorseful|Arrogant|Joyful|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to CREDULOUS.|Skeptical|Gullible|Dishonest|Intelligent|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to CURSORY.|Thorough|Hasty and superficial|Careful|Rude|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to DEARTH.|Abundance|Scarcity|Wealth|Growth|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to DERIDE.|Admire|Ridicule|Explain|Follow|1",
            "HARD|Vocabulary|Choose the word closest in meaning to DESICCATE.|To moisten|To dry out completely|To decorate|To confuse|1",
            "HARD|Vocabulary|Choose the word closest in meaning to DIDACTIC.|Purely entertaining|Intended to instruct|Random|Secretive|1",
            "HARD|Vocabulary|Choose the word closest in meaning to DILATORY.|Prompt|Tending to delay|Generous|Honest|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to DISPARAGE.|To praise|To belittle|To gather|To repair|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to DOGMATIC.|Open-minded|Asserting opinions arrogantly|Doubtful|Shy|1",
            "HARD|Vocabulary|Choose the word closest in meaning to EBULLIENT.|Gloomy|Enthusiastic and lively|Calm|Tired|1",
            "HARD|Vocabulary|Choose the word closest in meaning to EFFRONTERY.|Shyness|Shameless boldness|Honesty|Fear|1",
            "HARD|Vocabulary|Choose the word closest in meaning to ENERVATE.|To energize|To weaken|To anger|To inspire|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to EPHEMERAL.|Permanent|Short-lived|Heavy|Ancient|1",
            "HARD|Vocabulary|Choose the word closest in meaning to EQUIVOCATE.|To speak clearly|To use ambiguous language|To shout|To agree|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to ERUDITE.|Ignorant|Scholarly|Rude|Lazy|1",
            "HARD|Vocabulary|Choose the word closest in meaning to ESCHEW.|To embrace|To deliberately avoid|To eat|To find|1",
            "HARD|Vocabulary|Choose the word closest in meaning to FATUOUS.|Wise|Silly or foolish|Thin|Angry|1",
            "MEDIUM|Vocabulary|Choose the word closest in meaning to GARRULOUS.|Silent|Excessively talkative|Honest|Shy|1",

            // ---------- Text / Sentence completion ----------
            "MEDIUM|Text Completion|Despite the critic's _____ review, the play attracted huge audiences.|Glowing|Scathing|Lengthy|Accurate|1",
            "EASY|Text Completion|Her _____ nature made her a beloved teacher, always patient with struggling students.|Irascible|Benevolent|Aloof|Deceitful|1",
            "MEDIUM|Text Completion|The hypothesis was so _____ that even experts struggled to follow it.|Lucid|Abstruse|Trivial|Popular|1",
            "HARD|Text Completion|Although usually _____, he became surprisingly animated when discussing music.|Gregarious|Taciturn|Jovial|Loud|1",
            "MEDIUM|Text Completion|The dictator ruled with an iron fist, brooking no _____.|Loyalty|Dissent|Wealth|Order|1",
            "HARD|Text Completion|The evidence was _____; it pointed unmistakably to a single conclusion.|Ambiguous|Incontrovertible|Scarce|Misleading|1",
            "EASY|Text Completion|A good editor must be _____, catching even the smallest of errors.|Careless|Meticulous|Hasty|Generous|1",
            "HARD|Text Completion|His argument, though initially persuasive, ultimately proved _____.|Sound|Specious|Honest|Clear|1",
            "MEDIUM|Text Completion|The once-_____ kingdom fell into ruin after years of mismanagement.|Prosperous|Impoverished|Tiny|Hidden|0",
            "MEDIUM|Text Completion|She remained _____ in the face of criticism, refusing to change her stance.|Wavering|Resolute|Timid|Confused|1",
            "HARD|Text Completion|The professor's lectures were so _____ that students often dozed off.|Riveting|Soporific|Brief|Hilarious|1",
            "HARD|Text Completion|The diplomat's _____ remarks were designed to avoid offending any party.|Inflammatory|Anodyne|Honest|Detailed|1",
            "HARD|Text Completion|Far from being _____, the new policy created even more confusion.|Ameliorative|Harmful|Costly|Popular|0",
            "MEDIUM|Text Completion|The detective's _____ for detail allowed her to solve the case.|Disregard|Penchant|Fear|Hatred|1",
            "MEDIUM|Text Completion|His strict _____ to the truth made him a trusted witness.|Aversion|Adherence|Indifference|Opposition|1",

            // ---------- Quantitative ----------
            "EASY|Quantitative|If 3x + 7 = 22, what is x?|3|5|7|15|1",
            "EASY|Quantitative|What is 15% of 200?|15|20|30|45|2",
            "EASY|Quantitative|A rectangle has length 8 and width 5. What is its area?|13|26|40|80|2",
            "EASY|Quantitative|What is the average of 4, 8 and 12?|6|8|10|12|1",
            "MEDIUM|Quantitative|If 2^x = 32, what is x?|4|5|6|16|1",
            "EASY|Quantitative|A train travels 180 km in 3 hours. What is its average speed in km/h?|50|60|90|540|1",
            "MEDIUM|Quantitative|What is the value of 7! / 5!?|2|12|42|5040|2",
            "MEDIUM|Quantitative|If the ratio of boys to girls is 3:2 and there are 30 students, how many girls are there?|10|12|15|18|1",
            "MEDIUM|Quantitative|Solve x^2 - 9 = 0 for the positive value of x.|3|9|-3|81|0",
            "EASY|Quantitative|What is the square root of 144?|11|12|13|14|1",
            "HARD|Quantitative|A shirt costs $40 after a 20% discount. What was its original price?|$48|$50|$60|$80|1",
            "MEDIUM|Quantitative|The sum of three consecutive integers is 72. What is the largest?|23|24|25|26|2",
            "MEDIUM|Quantitative|A circle has radius 7. Using pi about 22/7, what is its area?|44|154|22|49|1",
            "MEDIUM|Quantitative|If x + y = 10 and x - y = 4, what is x?|3|5|7|14|2",
            "EASY|Quantitative|25% of a number is 50. What is the number?|100|150|200|12.5|2",
            "MEDIUM|Quantitative|What is the next number: 2, 6, 12, 20, __ ?|24|28|30|32|2",
            "MEDIUM|Quantitative|If 5 workers build a wall in 8 days, how many days for 10 workers at the same rate?|2|4|8|16|1",
            "MEDIUM|Quantitative|A bag has 4 red and 6 blue balls. What is the probability of drawing a red ball?|0.4|0.6|0.5|0.25|0",
            "MEDIUM|Quantitative|The perimeter of a square is 36. What is its area?|36|49|81|144|2",
            "EASY|Quantitative|Simplify 3/4 + 1/8.|4/12|7/8|1/2|5/8|1",
            "HARD|Quantitative|If f(x) = 2x^2 - 3x + 1, what is f(2)?|1|3|7|11|1",
            "HARD|Quantitative|A number increased by 30% becomes 65. What was the number?|45.5|50|55|35|1",
            "MEDIUM|Quantitative|The angles of a triangle are in the ratio 1:2:3. What is the largest angle?|30|60|90|120|2",
            "EASY|Quantitative|Express 0.25 as a fraction in lowest terms.|1/2|1/4|1/5|2/5|1",
            "MEDIUM|Quantitative|If 12 is 40% of x, what is x?|4.8|24|30|48|2",

            // ---------- More advanced vocabulary ----------
            "HARD|Vocabulary|LACONIC most nearly means:|Talkative|Using few words|Lazy|Cheerful|1",
            "MEDIUM|Vocabulary|MOLLIFY most nearly means:|To anger|To soothe|To harden|To confuse|1",
            "MEDIUM|Vocabulary|NEFARIOUS most nearly means:|Virtuous|Extremely wicked|Careless|Generous|1",
            "HARD|Vocabulary|OBDURATE most nearly means:|Flexible|Stubborn|Kind|Brief|1",
            "HARD|Vocabulary|OBSEQUIOUS most nearly means:|Rebellious|Excessively fawning|Honest|Lazy|1",
            "MEDIUM|Vocabulary|PARSIMONIOUS most nearly means:|Generous|Stingy|Wealthy|Careless|1",
            "HARD|Vocabulary|PELLUCID most nearly means:|Murky|Transparently clear|Loud|Hidden|1",
            "HARD|Vocabulary|PERFIDIOUS most nearly means:|Loyal|Treacherous|Honest|Timid|1",
            "HARD|Vocabulary|PHLEGMATIC most nearly means:|Excitable|Calm and unemotional|Angry|Sickly|1",
            "MEDIUM|Vocabulary|PRODIGAL most nearly means:|Thrifty|Wastefully extravagant|Honest|Shy|1",
            "MEDIUM|Vocabulary|PROSAIC most nearly means:|Poetic|Dull or ordinary|Brief|Loud|1",
            "MEDIUM|Vocabulary|PUGNACIOUS most nearly means:|Peaceful|Eager to fight|Lazy|Honest|1",
            "HARD|Vocabulary|QUIESCENT most nearly means:|Active|In a state of rest|Noisy|Angry|1",
            "HARD|Vocabulary|RECALCITRANT most nearly means:|Obedient|Stubbornly defiant|Cheerful|Honest|1",
            "MEDIUM|Vocabulary|SAGACIOUS most nearly means:|Foolish|Wise and shrewd|Lazy|Loud|1",
            "MEDIUM|Vocabulary|SPURIOUS most nearly means:|Genuine|False or fake|Generous|Brief|1",
            "MEDIUM|Vocabulary|TACITURN most nearly means:|Talkative|Reserved, saying little|Cheerful|Honest|1",
            "HARD|Vocabulary|TRENCHANT most nearly means:|Vague|Sharp and incisive|Gentle|Dull|1",
            "MEDIUM|Vocabulary|UBIQUITOUS most nearly means:|Rare|Present everywhere|Hidden|Ancient|1",
            "MEDIUM|Vocabulary|VERACITY most nearly means:|Falsehood|Truthfulness|Speed|Anger|1"
        };

        for (int i = 0; i < seed.length; i++) {
            String[] p = seed[i].split("\\|");
            String[] opt = { p[2 + 1], p[2 + 2], p[2 + 3], p[2 + 4] };
            int correct = Integer.parseInt(p[7].trim());
            addQuestionToArray(new Question(i + 1, p[0], p[1], p[2], opt, correct));
        }
    }
}
