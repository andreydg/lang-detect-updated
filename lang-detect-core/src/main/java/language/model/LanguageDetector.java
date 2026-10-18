package language.model;

import java.io.IOException;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.SortedSet;

import language.model.NgramLanguageDetector.ClassificationAlgorithm;

/**
 * Generic interface for language detection
 * 
 * @author Andrey Gusev
 */
public interface LanguageDetector {

	/**
	 * Stands in for a language when text matches none of the supported ones
	 * (another script, or only digits and punctuation). {@link Locale#ROOT}
	 * has the BCP 47 tag {@code "und"} (undetermined).
	 */
	Locale UNDETERMINED = Locale.ROOT;

	/**
	 * @param text
	 *            - text for which we will detect language
	 * @return most likely language with default algorithm, or {@code null} when
	 *         the text matches none of the supported languages
	 */
	Locale getMostLikelyLanguage(String text) throws IOException;
	
	
	/**
	 * @param text
	 *            - text for which we will detect language
	 * @return most likely language with specified algorithm
	 */
	Locale getMostLikelyLanguage(String text, ClassificationAlgorithm algorithmToUse) throws IOException;

	/**
	 * returns ordered list of languages that are most similar to given text,
	 * using all nGram sizes specified for language detector
	 */
	SortedSet<Entry<Locale, Double>> detectLanguageWithLinearWeights(String text, boolean ignoreLowScores)
			throws IOException;

}
