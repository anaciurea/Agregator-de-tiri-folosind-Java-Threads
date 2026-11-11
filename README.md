# Agregator de știri folosind Java Threads

Tema 1 – Algoritmi Paraleli și Distribuiți (APD)  
Autor: **Ciurea Ana-Sorina**

Agregator paralel de articole de știri (JSON) implementat în Java cu thread-uri. Programul citește articolele, elimină duplicatele, le grupează pe categorii și limbi și generează statistici.

## Structură

| Fișier | Descriere |
|---|---|
| `Tema1.java` | Programul principal și thread-urile worker |
| `Article.java` | Modelul unui articol (mapare JSON cu Jackson) |
| `Makefile` | Compilare / rulare / curățare |
| `libs/` | Bibliotecile Jackson 2.17.0 (core, annotations, databind) |
| `README.pdf` | Documentația detaliată a temei |

## Compilare și rulare

```bash
make build
make run ARGS="<num_threads> <articles_file> <inputs_file>"
make clean
```

- `articles_file` – prima linie e numărul de fișiere, apoi căile către fișierele JSON cu articole.
- `inputs_file` – conține căile către fișierele cu limbi, categorii și stop words.

## Implementare

Fișierele de articole sunt împărțite în bucăți egale între `N` thread-uri. Procesarea are două faze, separate de un `CyclicBarrier`:

1. **Citire** – fiecare thread parsează fișierele sale și numără aparițiile fiecărui `uuid` și `title` în `ConcurrentHashMap<String, AtomicInteger>`.
2. **Procesare** – după barieră, toate duplicatele sunt cunoscute. Un articol e considerat duplicat dacă `uuid`-ul sau titlul apare de mai multe ori. Pentru articolele unice:
   - se numără autorii, limbile și categoriile (valide);
   - se grupează UUID-urile pe limbă/categorie în map-uri locale, unite la final în cele globale (reduce contenția);
   - se determină cel mai recent articol (verificare dublă într-un bloc `synchronized`);
   - pentru articolele în engleză se numără cuvintele cheie (fără stop words, o dată per articol).

Sincronizare: `ConcurrentHashMap`, `AtomicInteger`, `Collections.synchronizedList`, `CyclicBarrier` și un lock dedicat pentru cel mai recent articol.

## Fișiere de ieșire

- `all_articles.txt` – articolele unice, sortate descrescător după dată, apoi după UUID
- `<categorie>.txt` – UUID-urile din fiecare categorie (virgulele eliminate, spațiile înlocuite cu `_`)
- `<limba>.txt` – UUID-urile pentru fiecare limbă
- `keywords_count.txt` – cuvintele cheie din articolele în engleză, după frecvență
- `reports.txt` – `duplicates_found`, `unique_articles`, `best_author`, `top_language`, `top_category`, `most_recent_article`, `top_keyword_en`
