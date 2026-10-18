package language.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import language.model.NgramLanguageDetector.ClassificationAlgorithm;

/**
 * Accuracy of a detector on the held-out test set
 * ({@code trainingAndTestSet/<locale>_test}, one sample per line): confusion
 * matrix, per-language accuracy and accuracy by text length.
 *
 * @author Andrey Gusev
 */
final class AccuracyReport {

	// text-length buckets in characters; upper bounds are exclusive
	private static final int[] LENGTH_LIMITS = { 15, 25, 40, 80, Integer.MAX_VALUE };
	private static final String[] LENGTH_LABELS = { "under 15", "15–24", "25–39", "40–79", "80+" };

	private final ClassificationAlgorithm algorithm;
	private final List<Locale> locales = List.of(NgramLanguageDetector.getLocales());
	// confusion[actual][detected]; the extra last column counts texts with no result
	private final int[][] confusion = new int[locales.size()][locales.size() + 1];
	private final int[] lengthTotal = new int[LENGTH_LIMITS.length];
	private final int[] lengthCorrect = new int[LENGTH_LIMITS.length];

	private AccuracyReport(ClassificationAlgorithm algorithm) {
		this.algorithm = algorithm;
	}

	/**
	 * Runs {@code detector} over every non-blank line of the test set. Languages
	 * are scored in parallel, each into its own partial report, and merged in a
	 * fixed order, so the counts don't depend on scheduling.
	 */
	static AccuracyReport evaluate(LanguageDetector detector, ClassificationAlgorithm algorithm, Path testSetDir) {
		AccuracyReport report = new AccuracyReport(algorithm);
		IntStream.range(0, report.locales.size())
				.parallel()
				.mapToObj(actual -> score(detector, algorithm, testSetDir, actual))
				.toList()
				.forEach(report::add);
		return report;
	}

	private static AccuracyReport score(LanguageDetector detector, ClassificationAlgorithm algorithm,
			Path testSetDir, int actual) {
		AccuracyReport part = new AccuracyReport(algorithm);
		Path file = testSetDir.resolve(part.locales.get(actual) + "_test");
		try {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				if (!line.isBlank()) {
					part.record(actual, detector.getMostLikelyLanguage(line, algorithm), line.trim().length());
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return part;
	}

	private void add(AccuracyReport other) {
		for (int row = 0; row < confusion.length; row++) {
			for (int col = 0; col < confusion[row].length; col++) {
				confusion[row][col] += other.confusion[row][col];
			}
		}
		for (int bucket = 0; bucket < LENGTH_LIMITS.length; bucket++) {
			lengthTotal[bucket] += other.lengthTotal[bucket];
			lengthCorrect[bucket] += other.lengthCorrect[bucket];
		}
	}

	private void record(int actual, Locale detected, int length) {
		int column = detected == null ? -1 : locales.indexOf(detected);
		if (column < 0) {
			column = locales.size();
		}
		confusion[actual][column]++;

		int bucket = 0;
		while (length >= LENGTH_LIMITS[bucket]) {
			bucket++;
		}
		lengthTotal[bucket]++;
		if (column == actual) {
			lengthCorrect[bucket]++;
		}
	}

	int lines() {
		return Arrays.stream(confusion).flatMapToInt(Arrays::stream).sum();
	}

	/** Overall accuracy, in percent. */
	double accuracy() {
		int correct = 0;
		for (int ind = 0; ind < locales.size(); ind++) {
			correct += confusion[ind][ind];
		}
		return percent(correct, lines());
	}

	/** Accuracy on texts written in {@code locale}, in percent. */
	double accuracy(Locale locale) {
		int row = locales.indexOf(locale);
		return percent(confusion[row][row], Arrays.stream(confusion[row]).sum());
	}

	/**
	 * Markdown summary. {@code baseline} maps {@code "overall"} and locale codes
	 * to the expected accuracy in percent.
	 */
	String toMarkdown(Map<String, Double> baseline) {
		StringBuilder md = new StringBuilder();
		md.append(String.format(Locale.ROOT, "## Detection accuracy (%s)\n\n", algorithm));
		md.append(String.format(Locale.ROOT, "%,d held-out lines: **%.2f%%** correct (baseline %.2f%%).\n\n",
				lines(), accuracy(), baseline.get("overall")));

		md.append("| Language | Lines | Accuracy | Baseline |\n|---|---:|---:|---:|\n");
		for (int row = 0; row < locales.size(); row++) {
			Locale locale = locales.get(row);
			md.append(String.format(Locale.ROOT, "| %s | %,d | %.2f%% | %.2f%% |\n",
					locale.getDisplayLanguage(Locale.ENGLISH), Arrays.stream(confusion[row]).sum(), accuracy(locale),
					baseline.get(locale.toString())));
		}

		md.append("\n### Confusion matrix\n\nRows are the actual language, columns the detected one.\n\n|   |");
		for (Locale locale : locales) {
			md.append(' ').append(locale).append(" |");
		}
		md.append(" none |\n|---|").append("---:|".repeat(locales.size() + 1)).append('\n');
		for (int row = 0; row < locales.size(); row++) {
			md.append("| **").append(locales.get(row)).append("** |");
			for (int col = 0; col <= locales.size(); col++) {
				String cell = String.format(Locale.ROOT, "%,d", confusion[row][col]);
				md.append(' ').append(col == row ? "**" + cell + "**" : cell).append(" |");
			}
			md.append('\n');
		}

		md.append("\n### Accuracy by text length\n\n| Characters | Lines | Accuracy |\n|---|---:|---:|\n");
		for (int bucket = 0; bucket < LENGTH_LIMITS.length; bucket++) {
			md.append(String.format(Locale.ROOT, "| %s | %,d | %.2f%% |\n", LENGTH_LABELS[bucket], lengthTotal[bucket],
					percent(lengthCorrect[bucket], lengthTotal[bucket])));
		}
		return md.toString();
	}

	private static double percent(int part, int whole) {
		return whole == 0 ? 0 : 100.0 * part / whole;
	}
}
