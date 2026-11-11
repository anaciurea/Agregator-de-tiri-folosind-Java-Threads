import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class Tema1 {
    // Listele cu limbi, categorii si stop words, pe care e citesc din fisiere
    static Set<String> validLanguages = new HashSet<>();
    static Set<String> validCategories = new HashSet<>();
    static Set<String> stopWords = new HashSet<>();
    
    // Folosesc ConcurrentHashMap pentru ca thread-urile sa poata scrie simultan
    static ConcurrentHashMap<String, AtomicInteger> uuidCount = new ConcurrentHashMap<>();
    static ConcurrentHashMap<String, AtomicInteger> titleCount = new ConcurrentHashMap<>();
    
    // Lista cu articolele unice
    static List<Article> articles = Collections.synchronizedList(new ArrayList<>());
    // Map-uri pentru a grupa articolele pe categorii si limbi
    static ConcurrentHashMap<String, List<String>> categories = new ConcurrentHashMap<>();
    static ConcurrentHashMap<String, List<String>> languages = new ConcurrentHashMap<>();
    // Contoare pentru statistici - cuvinte cheie, autori, categorii, limbi
    static ConcurrentHashMap<String, AtomicInteger> keywords = new ConcurrentHashMap<>();
    static ConcurrentHashMap<String, AtomicInteger> authors = new ConcurrentHashMap<>();
    static ConcurrentHashMap<String, AtomicInteger> catCount = new ConcurrentHashMap<>();
    static ConcurrentHashMap<String, AtomicInteger> langCount = new ConcurrentHashMap<>();
    
    // Contor pentru duplicate gasite
    static AtomicInteger duplicates = new AtomicInteger(0);
    // Articolul cel mai recent
    static Article latest = null;
    static final Object latestLock = new Object();
    
    // Bariera pentru a sincroniza thread-urile intre cele doua faze
    static CyclicBarrier barrier;

    public static void main(String[] args) throws Exception {
        // Verific argumentele
        if (args.length < 3) {
            System.err.println("Usage: java Tema1 <threads> <articles_file> <inputs_file>");
            return;
        }

        int numThreads = Integer.parseInt(args[0]);
        String articlesFile = args[1];
        String inputsFile = args[2];

        // Citesc fisierele de input - limbi, categorii, stop words
        Path inputsPath = Paths.get(inputsFile);
        List<String> inputLines = Files.readAllLines(inputsPath);
        Path inputsDir = inputsPath.getParent();

        readAuxFile(inputsDir.resolve(inputLines.get(1).trim()), validLanguages);
        readAuxFile(inputsDir.resolve(inputLines.get(2).trim()), validCategories);
        readAuxFile(inputsDir.resolve(inputLines.get(3).trim()), stopWords);

        // Citesc lista cu fisierele de articole
        Path articlesPath = Paths.get(articlesFile);
        List<String> articleLines = Files.readAllLines(articlesPath);
        Path baseDir = articlesPath.getParent();
        
        List<String> articleFiles = new ArrayList<>();
        for (int i = 1; i < articleLines.size(); i++) {
            articleFiles.add(baseDir.resolve(articleLines.get(i).trim()).toString());
        }

        // Creez bariera cu numarul de thread-uri
        barrier = new CyclicBarrier(numThreads);
        Thread[] threads = new Thread[numThreads];
        
        int chunkSize = (int) Math.ceil((double) articleFiles.size() / numThreads);
        for (int i = 0; i < numThreads; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, articleFiles.size());
            List<String> chunk = (start < articleFiles.size()) ? articleFiles.subList(start, end) : Collections.emptyList();
            threads[i] = new WorkerThread(chunk);
            threads[i].start();
        }

        // Astept ca toate thread-urile sa termine
        for (Thread t : threads) {
            t.join();
        }

        // Scriu rezultatele in fisiere
        writeOutputs();
    }

    // Clasa pentru thread-urile worker - fiecare proceseaza o parte din fisiere
    static class WorkerThread extends Thread {
        private final List<String> files;  // Fisierele alocate acestui thread
        private final ObjectMapper mapper = new ObjectMapper();  // Pentru parsarea JSON
        private final List<Article> myArticles = new ArrayList<>();  // Articolele citite de acest thread
        // Liste locale pentru a reduce overhead-ul de sincronizare
        private final Map<String, List<String>> localLanguages = new HashMap<>();
        private final Map<String, List<String>> localCategories = new HashMap<>();

        public WorkerThread(List<String> files) {
            this.files = files;
        }

        @Override
        public void run() {
            try {
                // Fiecare thread citeste fisierele sale si numara UUID-urile si titlurile
                for (String file : files) {
                    try {
                        // Citesc articolele din fisierul JSON
                        List<Article> articles = mapper.readValue(new File(file), new TypeReference<List<Article>>() {});
                        myArticles.addAll(articles);
                        
                        // Număr de cate ori apare fiecare UUID si titlu
                        for (Article a : articles) {
                            if (a.getUuid() != null) {
                                uuidCount.computeIfAbsent(a.getUuid(), k -> new AtomicInteger()).incrementAndGet();
                            }
                            if (a.getTitle() != null) {
                                titleCount.computeIfAbsent(a.getTitle(), k -> new AtomicInteger()).incrementAndGet();
                            }
                        }
                    } catch (IOException e) {
                        // Daca un fisier nu se poate citi, il sar
                    }
                }

                // Astept ca toate thread-urile sa termine citirea
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                    return;
                }

                // Acum ca stiu toate duplicatele, pot filtra articolele
                for (Article a : myArticles) {
                   
                    if (a.getUuid() == null || a.getTitle() == null) {
                        duplicates.incrementAndGet();
                        continue;
                    }
                    
                    // Daca UUID-ul sau titlul apare de mai mult de o data, e duplicat
                    AtomicInteger uuidCnt = uuidCount.get(a.getUuid());
                    AtomicInteger titleCnt = titleCount.get(a.getTitle());
                    if ((uuidCnt != null && uuidCnt.get() > 1) || (titleCnt != null && titleCnt.get() > 1)) {
                        duplicates.incrementAndGet();
                        continue;
                    }
                    
                    // Articolul e unic, il adaug in lista
                    articles.add(a);
                    String uuid = a.getUuid();
                    
                    // Număr autorii
                    if (a.getAuthor() != null) {
                        authors.computeIfAbsent(a.getAuthor(), k -> new AtomicInteger()).incrementAndGet();
                    }

                    // Grupez pe limbi - doar daca limba e valida
                    if (a.getLanguage() != null && validLanguages.contains(a.getLanguage())) {
                        String lang = a.getLanguage();
                        localLanguages.computeIfAbsent(lang, k -> new ArrayList<>()).add(uuid);
                        langCount.computeIfAbsent(lang, k -> new AtomicInteger()).incrementAndGet();
                    }

                    // Grupez pe categorii
                    if (a.getCategories() != null) {
                        // Pentru map-ul de categorii, folosesc un Set pentru a evita duplicatele
                        Set<String> cats = new HashSet<>(a.getCategories());
                        for (String cat : cats) {
                            if (validCategories.contains(cat)) {
                                localCategories.computeIfAbsent(cat, k -> new ArrayList<>()).add(uuid);
                                // Pentru contor, număr doar categoriile unice per articol
                                catCount.computeIfAbsent(cat, k -> new AtomicInteger()).incrementAndGet();
                            }
                        }
                    }

                    // Caut articolul cel mai recent
                
                    Article currentLatest = latest;
                    boolean shouldUpdate = false;
                    if (currentLatest == null) {
                        shouldUpdate = true;
                    } else {
                        int cmp = a.getPublished().compareTo(currentLatest.getPublished());
                        if (cmp > 0) {
                            shouldUpdate = true;
                        } else if (cmp == 0 && a.getUuid().compareTo(currentLatest.getUuid()) < 0) {
                            shouldUpdate = true;
                        }
                    }
                    
                    if (shouldUpdate) {
                        synchronized (latestLock) {
                            // Verific din nou in synchronized block
                            if (latest == null || a.getPublished().compareTo(latest.getPublished()) > 0) {
                                latest = a;
                            } else if (a.getPublished().equals(latest.getPublished()) && 
                                       a.getUuid().compareTo(latest.getUuid()) < 0) {
                                latest = a;
                            }
                        }
                    }

                    // Procesez cuvintele cheie doar pentru articolele in engleza
                    if ("english".equals(a.getLanguage()) && a.getText() != null) {
                        processKeywords(a.getText());
                    }
                }
                
                // Agregare listelor locale in structurile globale (o singura operatie sincronizata per categorie/limba)
                for (Map.Entry<String, List<String>> entry : localLanguages.entrySet()) {
                    String lang = entry.getKey();
                    List<String> uuids = entry.getValue();
                    languages.computeIfAbsent(lang, k -> Collections.synchronizedList(new ArrayList<>())).addAll(uuids);
                }
                
                for (Map.Entry<String, List<String>> entry : localCategories.entrySet()) {
                    String cat = entry.getKey();
                    List<String> uuids = entry.getValue();
                    categories.computeIfAbsent(cat, k -> Collections.synchronizedList(new ArrayList<>())).addAll(uuids);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Extrag si numar cuvintele cheie dintr-un text
        private void processKeywords(String text) {
            // Convertesc totul la lowercase
            text = text.toLowerCase();
            // Impart textul in cuvinte (separate de spatii)
            String[] wordsArray = text.split("\\s+");
            Set<String> words = new HashSet<>();  // Pentru a numara fiecare cuvant o singura data per articol
            
            for (String word : wordsArray) {
                // Curat cuvantul - pastrez doar literele mici
                // Elimin punctuatia, numerele, etc.
                if (word.isEmpty()) continue;
                
                StringBuilder clean = new StringBuilder();
                for (int i = 0; i < word.length(); i++) {
                    char c = word.charAt(i);
                    if (c >= 'a' && c <= 'z') {
                        clean.append(c);
                    }
                }
                
                if (clean.length() > 0) {
                    String cleanWord = clean.toString();
                    // Daca nu e stop word, il adaug
                    if (!stopWords.contains(cleanWord)) {
                        words.add(cleanWord);
                    }
                }
            }
            
            // Număr fiecare cuvant unic o singura data pentru acest articol
            for (String w : words) {
                keywords.computeIfAbsent(w, k -> new AtomicInteger()).incrementAndGet();
            }
        }
    }

    // Citesc un fisier auxiliar (limbi, categorii, stop words)
    // Primele linii pot avea un numar la inceput, pe care il sar
    private static void readAuxFile(Path path, Set<String> set) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) return;
        int start = 0;
        // Daca prima linie e un numar, o sar
        try {
            Integer.parseInt(lines.get(0).trim());
            start = 1;
        } catch (NumberFormatException e) {}
        // Adaug toate liniile valide in set
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) set.add(line);
        }
    }

    // Scriu toate fisierele de iesire
    private static void writeOutputs() throws IOException {
        // Sortez articolele dupa data (descrescator) si apoi dupa UUID
        List<Article> sorted = new ArrayList<>(articles);
        sorted.sort((a, b) -> {
            int cmp = b.getPublished().compareTo(a.getPublished());
            if (cmp != 0) return cmp;
            return a.getUuid().compareTo(b.getUuid());
        });

        // Scriu fisierul cu toate articolele
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter("all_articles.txt")))) {
            for (Article a : sorted) {
                pw.println(a.getUuid() + " " + a.getPublished());
            }
        }

        // Scriu fisierele pentru fiecare categorie
        for (String cat : validCategories) {
            List<String> uuids = categories.get(cat);
            if (uuids != null && !uuids.isEmpty()) {
                List<String> sortedUuids = new ArrayList<>(uuids);
                Collections.sort(sortedUuids);  // Sortez UUID-urile lexicografic
                // Normalizez numele categoriei: elimin virgule si inlocuiesc secvente de spatii cu _
                String filename = cat.replace(",", "").replaceAll("\\s+", "_") + ".txt";
                try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(filename)))) {
                    for (String u : sortedUuids) pw.println(u);
                }
            }
        }

        // Scriu fisierele pentru fiecare limba
        for (String lang : validLanguages) {
            List<String> uuids = languages.get(lang);
            if (uuids != null && !uuids.isEmpty()) {
                List<String> sortedUuids = new ArrayList<>(uuids);
                Collections.sort(sortedUuids);  // Sortez UUID-urile lexicografic
                try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(lang + ".txt")))) {
                    for (String u : sortedUuids) pw.println(u);
                }
            }
        }

        // Sortez cuvintele cheie dupa frecventa (descrescator) si apoi alfabetic
        List<Map.Entry<String, AtomicInteger>> sortedKeywords = new ArrayList<>(keywords.entrySet());
        sortedKeywords.sort((e1, e2) -> {
            int cmp = Integer.compare(e2.getValue().get(), e1.getValue().get());
            if (cmp != 0) return cmp;
            return e1.getKey().compareTo(e2.getKey());
        });
        
        // Scriu fisierul cu cuvintele cheie
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter("keywords_count.txt")))) {
            for (Map.Entry<String, AtomicInteger> e : sortedKeywords) {
                pw.println(e.getKey() + " " + e.getValue().get());
            }
        }

        // Scriu fisierul cu rapoarte/statistici
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter("reports.txt")))) {
            pw.println("duplicates_found - " + duplicates.get());
            pw.println("unique_articles - " + sorted.size());
            
            // Caut autorul cu cele mai multe articole
            String topAuthor = getTopKey(authors);
            if (topAuthor != null) {
                pw.println("best_author - " + topAuthor + " " + authors.get(topAuthor).get());
            }

            // Caut limba cu cele mai multe articole
            String topLanguage = getTopKey(langCount);
            if (topLanguage != null) {
                pw.println("top_language - " + topLanguage + " " + langCount.get(topLanguage).get());
            }

            // Caut categoria cu cele mai multe articole
            String topCategory = getTopKey(catCount);
            if (topCategory != null) {
                String catName = topCategory.replace(",", "").replaceAll("\\s+", "_");
                pw.println("top_category - " + catName + " " + catCount.get(topCategory).get());
            }

            // Articolul cel mai recent
            if (latest != null) {
                pw.println("most_recent_article - " + latest.getPublished() + " " + latest.getUrl());
            }

            // Cuvantul cheie cel mai frecvent
            if (!sortedKeywords.isEmpty()) {
                pw.println("top_keyword_en - " + sortedKeywords.get(0).getKey() + " " + sortedKeywords.get(0).getValue().get());
            }
        }
    }

    // Gaseste cheia cu valoarea maxima dintr-un map
    // Daca sunt mai multe cu aceeasi valoare, iau cea cu cheia mai mare lexicografic
    private static String getTopKey(Map<String, AtomicInteger> map) {
        return map.entrySet().stream()
            .max((e1, e2) -> {
                int cmp = Integer.compare(e1.getValue().get(), e2.getValue().get());
                if (cmp != 0) return cmp;
                // Daca valorile sunt egale, compar cheile (descrescator pentru ca vreau cea mai mare)
                return -e1.getKey().compareTo(e2.getKey());
            })
            .map(Map.Entry::getKey)
            .orElse(null);
    }
}
