# News Aggregator using Java Threads

Assignment 1 – Parallel and Distributed Algorithms (APD)

A parallel news article aggregator (JSON) implemented in Java using threads. The program reads the articles, removes duplicates, groups them by category and language, and generates statistics.

## Structure

| File | Description |
|---|---|
| `Tema1.java` | Main program and worker threads |
| `Article.java` | Article model (JSON mapping with Jackson) |
| `Makefile` | Build / run / clean |
| `libs/` | Jackson 2.17.0 libraries (core, annotations, databind) |
| `README.pdf` | Detailed assignment documentation |

## Build and run

```bash
make build
make run ARGS="<num_threads> <articles_file> <inputs_file>"
make clean
```

- `articles_file` – the first line is the number of files, followed by the paths to the JSON article files.
- `inputs_file` – contains the paths to the languages, categories and stop words files.

## Implementation

The article files are split into equal chunks among `N` threads. Processing has two phases, separated by a `CyclicBarrier`:

1. **Reading** – each thread parses its files and counts the occurrences of each `uuid` and `title` in a `ConcurrentHashMap<String, AtomicInteger>`.
2. **Processing** – after the barrier, all duplicates are known. An article is considered a duplicate if its `uuid` or title appears more than once. For unique articles:
   - authors, languages and (valid) categories are counted;
   - UUIDs are grouped by language/category in local maps, merged into the global ones at the end (reduces contention);
   - the most recent article is determined (double-checked inside a `synchronized` block);
   - for English articles, keywords are counted (excluding stop words, once per article).

Synchronization: `ConcurrentHashMap`, `AtomicInteger`, `Collections.synchronizedList`, `CyclicBarrier` and a dedicated lock for the most recent article.

## Output files

- `all_articles.txt` – unique articles, sorted by date (descending), then by UUID
- `<category>.txt` – UUIDs for each category (commas removed, whitespace replaced with `_`)
- `<language>.txt` – UUIDs for each language
- `keywords_count.txt` – keywords from English articles, sorted by frequency
- `reports.txt` – `duplicates_found`, `unique_articles`, `best_author`, `top_language`, `top_category`, `most_recent_article`, `top_keyword_en`
