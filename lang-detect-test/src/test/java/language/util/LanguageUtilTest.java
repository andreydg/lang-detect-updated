package language.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LanguageUtil}.
 *
 * <p>Covers tokenization and n-gram extraction semantics, plus the thread-safety
 * and bounded-memory guarantees of the shared n-gram cache (the cache is reached
 * concurrently by the singleton detector serving web requests).
 */
class LanguageUtilTest {

	@BeforeEach
	void resetCache() {
		LanguageUtil.clearNgramCache();
	}

	// ---------------------------------------------------------------------
	// tokenize
	// ---------------------------------------------------------------------

	@Test
	void tokenizeSplitsLowercasesAndTrims() {
		List<String> tokens = LanguageUtil.tokenize("Hello, WORLD! foo-bar", 0);
		// '-' is a delimiter, so foo-bar splits into two tokens
		assertEquals(List.of("hello", "world", "foo", "bar"), tokens);
	}

	@Test
	void tokenizeRespectsMinLength() {
		List<String> tokens = LanguageUtil.tokenize("a bb ccc dddd", 3);
		assertEquals(List.of("ccc", "dddd"), tokens);
	}

	@Test
	void tokenizeEmptyStringYieldsNoTokens() {
		assertTrue(LanguageUtil.tokenize("", 0).isEmpty());
		assertTrue(LanguageUtil.tokenize("   \t\n  ", 0).isEmpty());
	}

	@Test
	void tokenizeDropsOverlongWords() {
		String huge = "x".repeat(250);
		List<String> tokens = LanguageUtil.tokenize(huge + " ok", 0);
		assertEquals(List.of("ok"), tokens);
	}

	// ---------------------------------------------------------------------
	// getNgrams
	// ---------------------------------------------------------------------

	@Test
	void bigramsIncludeWordBoundaryMarkers() {
		Set<String> grams = LanguageUtil.getNgrams("test", 2);
		assertEquals(Set.of("$t", "te", "es", "st", "t$"), grams);
	}

	@Test
	void unigramsKeepOnlyLetters() {
		// boundary markers and digits are not letters and must be excluded
		Set<String> grams = LanguageUtil.getNgrams("a1b", 1);
		assertEquals(Set.of("a", "b"), grams);
	}

	@Test
	void digitsAreStrippedFromMultiCharNgrams() {
		Set<String> grams = LanguageUtil.getNgrams("a1", 2);
		// "$a1$" bigrams are $a, a1, 1$ — only $a survives digit stripping
		assertTrue(grams.contains("$a"));
		assertFalse(grams.contains("a1"));
		assertFalse(grams.contains("1$"));
	}

	@Test
	void wordShorterThanNgramReturnsEmpty() {
		assertTrue(LanguageUtil.getNgrams("a", 4).isEmpty());
		assertTrue(LanguageUtil.getNgrams("", 3).isEmpty());
	}

	@Test
	void nullWordReturnsEmpty() {
		assertTrue(LanguageUtil.getNgrams(null, 3).isEmpty());
	}

	@Test
	void resultIsCachedAndReturnsSameInstance() {
		Set<String> first = LanguageUtil.getNgrams("language", 3);
		Set<String> second = LanguageUtil.getNgrams("language", 3);
		assertSame(first, second, "cache should return the identical instance");
	}

	@Test
	void sameWordDifferentSizesAreCachedSeparately() {
		Set<String> two = LanguageUtil.getNgrams("language", 2);
		Set<String> three = LanguageUtil.getNgrams("language", 3);
		assertFalse(two.equals(three));
	}

	@Test
	void returnedSetIsUnmodifiable() {
		Set<String> grams = LanguageUtil.getNgrams("test", 2);
		assertThrows(UnsupportedOperationException.class, () -> grams.add("zz"));
	}

	// ---------------------------------------------------------------------
	// getNgramsForWordCombination
	// ---------------------------------------------------------------------

	@Test
	void wordCombinationKeepsOnlyCrossBoundaryNgrams() {
		Set<String> grams = LanguageUtil.getNgramsForWordCombination("ab", "cd", 3);
		// every kept ngram must contain the boundary char, but not at either edge
		assertFalse(grams.isEmpty());
		for (String g : grams) {
			assertTrue(g.contains("$"), "expected internal boundary in " + g);
			assertFalse(g.charAt(0) == '$', "boundary should not be first char of " + g);
			assertFalse(g.charAt(g.length() - 1) == '$', "boundary should not be last char of " + g);
		}
	}

	// ---------------------------------------------------------------------
	// getJaccardCoefficient
	// ---------------------------------------------------------------------

	@Test
	void jaccardOfIdenticalSetsIsOne() {
		Set<String> a = Set.of("x", "y", "z");
		assertEquals(1.0f, LanguageUtil.getJaccardCoefficient(a, a), 1e-6);
	}

	@Test
	void jaccardOfDisjointSetsIsZero() {
		assertEquals(0.0f, LanguageUtil.getJaccardCoefficient(Set.of("a"), Set.of("b")), 1e-6);
	}

	@Test
	void jaccardOfPartialOverlap() {
		// intersection {b} = 1, union {a,b,c} = 3
		float j = LanguageUtil.getJaccardCoefficient(Set.of("a", "b"), Set.of("b", "c"));
		assertEquals(1.0f / 3.0f, j, 1e-6);
	}

	// ---------------------------------------------------------------------
	// joinWithSpace
	// ---------------------------------------------------------------------

	@Test
	void joinWithSpaceHandlesEmptyAndNull() {
		assertEquals("a b", LanguageUtil.joinWithSpace("a", "b"));
		assertEquals("b", LanguageUtil.joinWithSpace("", "b"));
		assertEquals("a", LanguageUtil.joinWithSpace("a", ""));
		assertEquals("a", LanguageUtil.joinWithSpace("a", null));
		assertEquals("b", LanguageUtil.joinWithSpace(null, "b"));
		assertEquals("", LanguageUtil.joinWithSpace(null, null));
	}

	// ---------------------------------------------------------------------
	// cache: thread-safety and bounded memory (the bug we fixed)
	// ---------------------------------------------------------------------

	/**
	 * Hammers {@link LanguageUtil#getNgrams} from many threads with a mix of
	 * shared and unique words. With the old unsynchronized {@code HashMap} this
	 * could corrupt the map, lose updates, or spin forever on resize. We assert
	 * that every thread sees results equal to a single-threaded reference and
	 * that no exception escapes.
	 */
	@Test
	void getNgramsIsThreadSafeUnderConcurrency() throws Exception {
		final int sharedWords = 200;
		final int threads = 16;
		final int iterations = 400;

		// single-threaded reference for the shared vocabulary
		ConcurrentHashMap<String, Set<String>> reference = new ConcurrentHashMap<>();
		for (int i = 0; i < sharedWords; i++) {
			String w = "shared" + i;
			reference.put(w, LanguageUtil.getNgrams(w, 3));
		}

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();

		List<Future<?>> futures = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			final int threadId = t;
			futures.add(pool.submit(() -> {
				try {
					start.await();
					for (int it = 0; it < iterations; it++) {
						// shared words must match the reference exactly
						int idx = it % sharedWords;
						String shared = "shared" + idx;
						Set<String> got = LanguageUtil.getNgrams(shared, 3);
						if (!got.equals(reference.get(shared))) {
							throw new AssertionError("mismatch for " + shared);
						}
						// unique words exercise concurrent inserts / eviction
						LanguageUtil.getNgrams("t" + threadId + "_" + it, 3);
					}
				} catch (Throwable ex) {
					failure.compareAndSet(null, ex);
				}
			}));
		}

		start.countDown();
		pool.shutdown();
		assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "threads did not finish in time");
		for (Future<?> f : futures) {
			f.get(); // surface any execution exception
		}
		if (failure.get() != null) {
			throw new AssertionError("concurrent failure", failure.get());
		}
	}

	/**
	 * Feeds far more distinct words than the cache cap and asserts the cache
	 * stays bounded — the old static map grew without limit (a heap leak for a
	 * long-running service).
	 */
	@Test
	void cacheIsBounded() {
		LanguageUtil.clearNgramCache();
		final int distinct = 60_000; // exceeds MAX_CACHE_ENTRIES (50_000)
		for (int i = 0; i < distinct; i++) {
			Set<String> grams = LanguageUtil.getNgrams("uniqueword" + i, 3);
			assertNotNull(grams);
		}
		int size = LanguageUtil.nGramCacheSize();
		assertTrue(size <= 50_000, "cache must stay bounded but was " + size);
		assertTrue(size > 0, "cache should retain recent entries");
	}

	@Test
	void clearAndSizeReflectState() {
		LanguageUtil.clearNgramCache();
		assertEquals(0, LanguageUtil.nGramCacheSize());
		LanguageUtil.getNgrams("hello", 3);
		assertTrue(LanguageUtil.nGramCacheSize() >= 1);
		LanguageUtil.clearNgramCache();
		assertEquals(0, LanguageUtil.nGramCacheSize());
	}
}
