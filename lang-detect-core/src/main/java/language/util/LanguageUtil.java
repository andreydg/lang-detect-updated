package language.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Set of utils for language algorithms
 * 
 * @author Andrey Gusev
 */
public class LanguageUtil {

	// all possible delimiters
	// note that there are two different spaces here
	private final static String delimeters = " \t\n\r\f.()[]{}^+=|_*&%#\"',-:;/\\?!@~\u201c\u201d\u3000»«";

	private static final int MAX_WORD_LENGTH = 200;

	private static final String WORD_BOUNDARY_CHAR = "$";

	// precompiled once instead of per ngram (getNgrams runs in a hot loop)
	private static final Pattern WHITESPACE = Pattern.compile("\\s");
	private static final Pattern DIGIT = Pattern.compile("\\d");

	/**
	 * Caches computed n-grams keyed by {@code ngramSize + '\0' + word}.
	 *
	 * <p>This cache is shared across all callers, including concurrent web
	 * requests served by the singleton {@code NgramLanguageDetector}. It must
	 * therefore be both thread-safe and bounded:
	 * <ul>
	 *   <li>thread-safe — a plain {@code HashMap} mutated from multiple threads
	 *       can lose updates or, on resize, spin a thread in an infinite loop;</li>
	 *   <li>bounded — otherwise every distinct word ever submitted is retained
	 *       forever, an unbounded heap leak for a long-running service.</li>
	 * </ul>
	 * A synchronized access-ordered {@link LinkedHashMap} gives us a simple LRU
	 * with a hard cap and no external dependency. Lookups are cheap and the
	 * critical section is tiny, so the lock is not a meaningful bottleneck next
	 * to the cosine-similarity work that dominates detection.
	 */
	private static final int MAX_CACHE_ENTRIES = 50_000;

	private static final Map<String, Set<String>> nGramCache =
			Collections.synchronizedMap(
					new LinkedHashMap<String, Set<String>>(256, 0.75f, true) {
						@Override
						protected boolean removeEldestEntry(Map.Entry<String, Set<String>> eldest) {
							return size() > MAX_CACHE_ENTRIES;
						}
					});

	/** Clears the shared n-gram cache. Intended for tests and benchmarks. */
	public static void clearNgramCache() {
		nGramCache.clear();
	}

	/** Current number of cached entries. Intended for tests and diagnostics. */
	public static int nGramCacheSize() {
		return nGramCache.size();
	}

	/**
	 * computes jaccard coefficient for two sets
	 */
	public static float getJaccardCoefficient(Set<String> set1, Set<String> set2) {
		Set<String> intersection = new HashSet<>(set1);
		intersection.retainAll(set2);
		Set<String> union = new HashSet<>(set1);
		union.addAll(set2);
		return ((float) intersection.size()) / union.size();
	}

	/**
	 * Tokenizes the string on the shared {@code delimeters} set, lower-cases and
	 * trims each token, and keeps only tokens whose length is in
	 * {@code [minLength, MAX_WORD_LENGTH)}.
	 *
	 * @param text
	 *            - text to tokenize
	 * @param minLength
	 *            - minimum token length to keep; use 0 for all tokens
	 */
	public static List<String> tokenize(String text, int minLength) {
		List<String> retVal = new ArrayList<>();
		StringTokenizer tokenizer = new StringTokenizer(text, delimeters);
		while (tokenizer.hasMoreTokens()) {
			String word = tokenizer.nextToken();
			word = word.toLowerCase().trim();
			if (word.length() >= minLength && word.length() < MAX_WORD_LENGTH) {
				retVal.add(word);
			}
		}
		return retVal;
	}

	/**
	 * Joins two strings with a single space; avoids repeated string concatenation when merging segments.
	 */
	public static String joinWithSpace(String a, String b) {
		if (a == null || a.isEmpty()) {
			return b == null ? "" : b;
		}
		if (b == null || b.isEmpty()) {
			return a;
		}
		return new StringBuilder(a.length() + b.length() + 1).append(a).append(' ').append(b).toString();
	}
	
	public static Set<String> getNgramsForWordCombination(String prevWord, String nextWord, int ngramSize) {
		Set<String> initialNgrams = getNgrams(prevWord + WORD_BOUNDARY_CHAR + nextWord, ngramSize, false);
		return initialNgrams.stream()
				.filter(
						ngram -> ngram.contains(WORD_BOUNDARY_CHAR)
								&& !WORD_BOUNDARY_CHAR.equals(String.valueOf(ngram.charAt(ngramSize - 1)))
								&& !WORD_BOUNDARY_CHAR.equals(String.valueOf(ngram.charAt(0))))
				.collect(Collectors.toCollection(() -> new HashSet<>(initialNgrams.size())));
	}

	/**
	 * computes kgrams for a given word
	 */
	public static Set<String> getNgrams(String word, int ngramSize) {
		return getNgrams(word, ngramSize, true);
	}

	/**
	 * computes kgrams for a given word
	 */
	public static Set<String> getNgrams(String word, int ngramSize, boolean addWordBoundaryMarkers) {
		// if the word is null or has fewer than k character
		// return empty set
		// add two for boundary markers
		if (word == null || (word.length() + 2) < ngramSize) {
			return Collections.emptySet();
		}

		if (addWordBoundaryMarkers) {
			word = WORD_BOUNDARY_CHAR + word + WORD_BOUNDARY_CHAR;
		}

		// '\0' can not occur in tokenized input, so it is a safe key separator
		String cacheKey = ngramSize + "\0" + word;
		Set<String> existingKGramSet = nGramCache.get(cacheKey);
		if (existingKGramSet != null) {
			return existingKGramSet;
		}

		Set<String> retSet = new HashSet<>(word.length() + 2 - ngramSize);
		StringBuilder currentKgram = new StringBuilder();
		int ind = 0;
		while (ind < word.length()) {
			Character chr = word.charAt(ind);

			switch (Character.getType(chr)) {
			case Character.END_PUNCTUATION:
			case Character.DASH_PUNCTUATION:
			case Character.START_PUNCTUATION:
			case Character.CONNECTOR_PUNCTUATION:
			case Character.OTHER_PUNCTUATION:
				// just add whitespace instead of punctuation
				chr = ' ';
				break;
			default:
			}

			currentKgram.append(chr);
			if (currentKgram.length() == ngramSize) {
				String kGram = currentKgram.toString();
				// if after trimming ngram still has the length of
				// 2 which means it didn't contain any whitespace to replace the
				// punctuation
				// we can add it
				kGram = WHITESPACE.matcher(kGram).replaceAll("");
				kGram = DIGIT.matcher(kGram).replaceAll("");
				if (kGram.length() == ngramSize) {
					kGram = kGram.toLowerCase();
					if (ngramSize == 1 && Character.isLetter(kGram.charAt(0))) {
						retSet.add(kGram);
					} else if (ngramSize > 1) {
						retSet.add(kGram);
					}
				}
				currentKgram = new StringBuilder();
				ind -= (ngramSize - 2);
			} else {
				ind++;
			}
		}
		// wrap into unmodifiable set
		Set<String> cached = Collections.unmodifiableSet(retSet);
		nGramCache.put(cacheKey, cached);
		return cached;
	}
}
