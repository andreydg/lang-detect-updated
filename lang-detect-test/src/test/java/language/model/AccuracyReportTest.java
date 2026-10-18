package language.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import language.model.NgramLanguageDetector.ClassificationAlgorithm;

/**
 * {@link AccuracyReport} on small synthetic test sets, with a detector that
 * returns fixed answers
 *
 * @author Andrey Gusev
 */
class AccuracyReportTest {

	@TempDir
	Path testSetDir;

	/** Writes the six {@code <locale>_test} files; locales not given get an empty file. */
	private void writeTestSet(Map<Locale, List<String>> linesByLocale) throws Exception {
		for (Locale locale : NgramLanguageDetector.getLocales()) {
			Files.write(testSetDir.resolve(locale + "_test"), linesByLocale.getOrDefault(locale, List.of()),
					StandardCharsets.UTF_8);
		}
	}

	private static LanguageDetector answering(Map<String, Locale> answers) {
		return new LanguageDetector() {
			@Override
			public Locale getMostLikelyLanguage(String text) {
				return answers.get(text);
			}

			@Override
			public Locale getMostLikelyLanguage(String text, ClassificationAlgorithm algorithmToUse) {
				return answers.get(text);
			}

			@Override
			public SortedSet<Entry<Locale, Double>> detectLanguageWithLinearWeights(String text,
					boolean ignoreLowScores) {
				throw new UnsupportedOperationException();
			}
		};
	}

	// wrong answers land in the detected language's column; no result and
	// unsupported results (such as "und") land in the "none" column
	@Test
	void testConfusionCounts() throws Exception {
		writeTestSet(Map.of(
				Locale.ENGLISH, List.of("en right", "en as fr", "en no result", "en und", "   "),
				Locale.FRENCH, List.of("fr right")));
		Map<String, Locale> answers = new HashMap<>();
		answers.put("en right", Locale.ENGLISH);
		answers.put("en as fr", Locale.FRENCH);
		answers.put("en und", Locale.ROOT);
		answers.put("fr right", Locale.FRENCH);

		AccuracyReport report = AccuracyReport.evaluate(answering(answers), ClassificationAlgorithm.LINEAR_WEIGHTS,
				testSetDir);

		assertEquals(5, report.lines(), "Blank lines should be skipped");
		assertEquals(1, report.count(Locale.ENGLISH, Locale.ENGLISH), "en -> en");
		assertEquals(1, report.count(Locale.ENGLISH, Locale.FRENCH), "en -> fr");
		assertEquals(2, report.count(Locale.ENGLISH, null), "en -> none (null and und)");
		assertEquals(1, report.count(Locale.FRENCH, Locale.FRENCH), "fr -> fr");
		assertEquals(40.0, report.accuracy(), 1e-9, "Overall accuracy");
		assertEquals(25.0, report.accuracy(Locale.ENGLISH), 1e-9, "English accuracy");
		assertEquals(100.0, report.accuracy(Locale.FRENCH), 1e-9, "French accuracy");
	}

	// buckets are under 15, 15-24, 25-39, 40-79 and 80+ characters, measured on the trimmed line
	@Test
	void testLengthBucketBoundaries() throws Exception {
		List<String> lines = List.of("a".repeat(14), "  " + "b".repeat(14) + "  ", "c".repeat(15), "d".repeat(24),
				"e".repeat(25), "f".repeat(39), "g".repeat(40), "h".repeat(79), "i".repeat(80));
		writeTestSet(Map.of(Locale.ENGLISH, lines));
		Map<String, Locale> answers = new HashMap<>();
		for (String line : lines) {
			answers.put(line, Locale.ENGLISH);
		}
		answers.put("i".repeat(80), Locale.GERMAN);

		AccuracyReport report = AccuracyReport.evaluate(answering(answers), ClassificationAlgorithm.LINEAR_WEIGHTS,
				testSetDir);

		int[] expectedLines = { 2, 2, 2, 2, 1 };
		int[] expectedCorrect = { 2, 2, 2, 2, 0 };
		for (int bucket = 0; bucket < expectedLines.length; bucket++) {
			assertEquals(expectedLines[bucket], report.lengthBucketLines(bucket), "Lines in bucket " + bucket);
			assertEquals(expectedCorrect[bucket], report.lengthBucketCorrect(bucket), "Correct in bucket " + bucket);
		}
	}

	// a drop of exactly the tolerance passes, despite floating-point rounding;
	// anything more fails, and rises are never regressions
	@Test
	void testToleranceEdges() {
		Map<String, Double> baseline = Map.of("overall", 93.42, "es", 88.53);
		double tolerance = 0.10;

		assertEquals(List.of(), AccuracyReport.regressions(Map.of("overall", 93.32, "es", 88.43), baseline, tolerance),
				"A drop of exactly the tolerance should pass");
		assertEquals(List.of("overall 93.31% < 93.42%"),
				AccuracyReport.regressions(Map.of("overall", 93.31, "es", 88.53), baseline, tolerance),
				"A drop of 0.11 points should fail");
		assertEquals(List.of(), AccuracyReport.regressions(Map.of("overall", 99.0, "es", 99.0), baseline, tolerance),
				"Rises are not regressions");

		assertEquals(List.of(), AccuracyReport.improvements(Map.of("overall", 93.52, "es", 88.53), baseline, tolerance),
				"A rise of exactly the tolerance is not reported");
		assertEquals(List.of("es 88.64% > 88.53%"),
				AccuracyReport.improvements(Map.of("overall", 93.42, "es", 88.64), baseline, tolerance),
				"A rise of 0.11 points is reported");
	}
}
