package language.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Test;

/**
 * Unit tests for {@link NgramModel} cosine-similarity scoring and its
 * add-after-seal invariants. These exercise the core math directly, without
 * needing the on-disk language models.
 */
public class NgramModelTest {

	private static final int SIZE = 2;

	/** A normalized (unit-length) language model with the given ngram weights. */
	private static NgramModel languageModel(Locale locale, String[] ngrams, double[] weights) {
		NgramModel m = new NgramModel(locale, SIZE);
		for (int i = 0; i < ngrams.length; i++) {
			m.addNormalizedNgram(ngrams[i], weights[i]);
		}
		return m;
	}

	/** A raw text model with the given ngram counts. */
	private static NgramModel textModel(String[] ngrams, double[] values) {
		NgramModel m = new NgramModel(SIZE);
		for (int i = 0; i < ngrams.length; i++) {
			m.addNgram(ngrams[i], values[i]);
		}
		return m;
	}

	@Test
	public void identicalDirectionGivesCosineOne() {
		// unit vector (0.6, 0.8); text with the same proportions
		NgramModel lang = languageModel(Locale.ENGLISH,
				new String[] {"ab", "bc"}, new double[] {0.6, 0.8});
		NgramModel text = textModel(new String[] {"ab", "bc"}, new double[] {0.6, 0.8});

		assertEquals(1.0, lang.calculateCosineSimilarity(text), 1e-9);
	}

	@Test
	public void orthogonalVectorsGiveZero() {
		NgramModel lang = languageModel(Locale.ENGLISH,
				new String[] {"ab"}, new double[] {1.0});
		NgramModel text = textModel(new String[] {"xy"}, new double[] {1.0});

		assertEquals(0.0, lang.calculateCosineSimilarity(text), 1e-9);
	}

	@Test
	public void partialOverlapMatchesManualComputation() {
		// lang unit vector (0.6, 0.8) over {ab, bc}; text counts {ab:1, bc:1}
		// dot = 0.6*1 + 0.8*1 = 1.4 ; ||text|| = sqrt(2) ; ||lang|| = 1
		NgramModel lang = languageModel(Locale.ENGLISH,
				new String[] {"ab", "bc"}, new double[] {0.6, 0.8});
		NgramModel text = textModel(new String[] {"ab", "bc"}, new double[] {1.0, 1.0});

		double expected = 1.4 / Math.sqrt(2.0);
		assertEquals(expected, lang.calculateCosineSimilarity(text), 1e-9);
	}

	@Test
	public void emptyTextModelGivesZero() {
		NgramModel lang = languageModel(Locale.ENGLISH,
				new String[] {"ab"}, new double[] {1.0});
		NgramModel emptyText = new NgramModel(SIZE);

		assertEquals(0.0, lang.calculateCosineSimilarity(emptyText), 1e-9);
	}

	@Test
	public void cosineRequiresAtLeastOneLanguageModel() {
		NgramModel a = textModel(new String[] {"ab"}, new double[] {1.0});
		NgramModel b = textModel(new String[] {"ab"}, new double[] {1.0});

		assertThrows(IllegalArgumentException.class, () -> a.calculateCosineSimilarity(b));
	}

	@Test
	public void addNormalizedNgramRejectsNonLanguageModel() {
		NgramModel text = new NgramModel(SIZE);
		assertThrows(RuntimeException.class, () -> text.addNormalizedNgram("ab", 0.5));
	}

	@Test
	public void addNgramAfterSealingThrows() {
		NgramModel text = textModel(new String[] {"ab"}, new double[] {1.0});
		// toString seals the model by computing the length norm + sorted ngrams
		text.toString();
		assertThrows(RuntimeException.class, () -> text.addNgram("cd", 1.0));
	}

	@Test
	public void repeatedNgramCountsAccumulate() {
		NgramModel lang = languageModel(Locale.ENGLISH,
				new String[] {"ab"}, new double[] {1.0});
		// adding "ab" twice -> raw count 2 ; ||text|| = 2 ; dot = 1*2 ; cosine = 1
		NgramModel text = new NgramModel(SIZE);
		text.addNgram("ab", 1.0);
		text.addNgram("ab", 1.0);

		assertEquals(1.0, lang.calculateCosineSimilarity(text), 1e-9);
	}

	@Test
	public void languageModelToStringIsParseableRoundTrip() {
		NgramModel lang = languageModel(Locale.ENGLISH,
				new String[] {"ab", "bc"}, new double[] {0.6, 0.8});
		String dump = lang.toString();
		assertTrue(dump.contains(NgramModel.NGRAM_SEPARTOR));
		// each non-empty line must split into exactly ngram:score (score
		// formatting is locale-dependent, so we only assert the structure)
		int lines = 0;
		for (String line : dump.split("\n")) {
			if (line.isEmpty()) {
				continue;
			}
			String[] parts = line.split(NgramModel.NGRAM_SEPARTOR);
			assertEquals("malformed dump line: " + line, 2, parts.length);
			lines++;
		}
		assertEquals("expected one line per ngram", 2, lines);
	}
}
