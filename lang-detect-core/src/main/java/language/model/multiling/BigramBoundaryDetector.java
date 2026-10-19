package language.model.multiling;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.Character.UnicodeScript;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

import language.model.LanguageDetector;
import language.model.NgramLanguageDetector;
import language.model.NgramLanguageDetector.ClassificationAlgorithm;
import language.util.LanguageUtil;
import language.util.Pair;

/**
 * Base bigram boundary detector which doesn't use language confidence levels to
 * determine the boundaries between languages
 * 
 * @author Andrey Gusev
 * 
 */
@ThreadSafe
public class BigramBoundaryDetector extends BaseNWordBoundaryDetector {


	private final static Lock BC = new ReentrantLock();

	@GuardedBy("BC")
	protected static volatile Map<String, Integer> BIGRAM_COUNTS;

	// raw segments are for callers that read boundaries from where segments end:
	// digits-only text isn't merged, and where the detector finds no language it
	// keeps the English default it always had
	private final boolean rawSegments;

	public BigramBoundaryDetector(ClassificationAlgorithm algorithmToUse, NgramLanguageDetector detector)
			throws IOException {
		this(algorithmToUse, detector, false);
	}

	/**
	 * @param rawSegments
	 *            - true to leave digits-and-punctuation segments unmerged and,
	 *            where the detector finds no language, labeled English as
	 *            before, for callers that read boundaries from where segments end
	 */
	protected BigramBoundaryDetector(ClassificationAlgorithm algorithmToUse, NgramLanguageDetector detector,
			boolean rawSegments) throws IOException {
		super(algorithmToUse, detector);
		this.rawSegments = rawSegments;
		lazyInitBigramCounts();

	}

	private void lazyInitBigramCounts() throws IOException {
		// initialize only once
		if (BIGRAM_COUNTS == null) {
			BC.lock();
			try {
				if (BIGRAM_COUNTS == null) {
					BIGRAM_COUNTS = Collections.unmodifiableMap(getBigramCounts());
				}
			} finally {
				BC.unlock();
			}
		}
	}

	private Map<String, Integer> getBigramCounts() throws IOException {

		Map<String, Integer> retVal = new HashMap<>();

		Path trainingDir = detector.getBasePath()
				.resolve(NgramLanguageDetector.BASE_MODEL_DIR)
				.resolve(NgramLanguageDetector.TRAINING_TEST_DIR);
		for (Locale locale : NgramLanguageDetector.getLocales()) {

			// _training
			Path file = trainingDir.resolve(locale.toString() + "_training");

			// need to read in UTF-8
			if (!Files.exists(file)) {
				continue;
			}

			String s;
			try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				while ((s = br.readLine()) != null) {
					StringTokenizer st = new StringTokenizer(s);
					String prevToken = null;
					StringBuilder bigram = new StringBuilder();
					while (st.hasMoreTokens()) {
						String currentToken = st.nextToken();
						bigram.append(currentToken.trim()).append(" ");
						if (prevToken == null) {
							prevToken = currentToken;
							continue;
						}

						incrementBigramCounts(retVal, bigram.toString().trim());
						bigram.delete(0, prevToken.length() + 1);
						prevToken = currentToken;
					}
				}
			}
		}

		return retVal;
	}

	/**
	 * Splits {@code s} into segments tagged with their languages. Text in a
	 * script other than Latin, the script of all supported languages, is split
	 * off first, at the word where the script changes, and becomes an
	 * {@link LanguageDetector#UNDETERMINED} segment of its own: there is no
	 * training data for it, so the detector can't place that boundary itself.
	 * The Latin text around it goes to {@link #tagLatinText}. Words without
	 * letters of a particular script (digits, punctuation) stay with the text
	 * before them, or with the text after them at the start, as in
	 * {@link #appendSegment(List, String)}.
	 */
	@Override
	public final List<Pair<String, Locale>> tagStringWithLanguages(String s) throws IOException {
		List<String> tokens = new ArrayList<>();
		List<TokenScript> scripts = new ArrayList<>();
		StringTokenizer st = new StringTokenizer(s);
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			tokens.add(token);
			scripts.add(scriptOf(token));
		}
		if (!scripts.contains(TokenScript.OTHER)) {
			return tagLatinText(s);
		}

		List<Pair<String, Locale>> retVal = new ArrayList<>();
		int runStart = 0;
		TokenScript runScript = TokenScript.NONE;
		for (int ind = 0; ind < tokens.size(); ind++) {
			TokenScript script = scripts.get(ind);
			if (script == TokenScript.NONE) {
				continue;
			}
			if (runScript != TokenScript.NONE && script != runScript) {
				appendRun(retVal, tokens.subList(runStart, ind), runScript);
				runStart = ind;
			}
			runScript = script;
		}
		appendRun(retVal, tokens.subList(runStart, tokens.size()), runScript);
		return retVal;
	}

	/** Adds the segments of a run of words in one script. */
	private void appendRun(List<Pair<String, Locale>> segments, List<String> tokens, TokenScript script)
			throws IOException {
		String text = String.join(" ", tokens);
		if (script == TokenScript.OTHER) {
			appendSegment(segments, text, LanguageDetector.UNDETERMINED);
			return;
		}
		for (Pair<String, Locale> segment : tagLatinText(text)) {
			appendSegment(segments, segment.first(), segment.second());
		}
	}

	/** Script of a word's letters, for splitting text where the script changes. */
	private enum TokenScript {
		/** Has a Latin letter, even next to letters of another script. */
		LATIN,
		/** Has letters, none of them Latin: Cyrillic, Greek, Han and so on. */
		OTHER,
		/** Has no letters of a particular script: digits, punctuation, symbols. */
		NONE
	}

	private static TokenScript scriptOf(String token) {
		TokenScript retVal = TokenScript.NONE;
		int ind = 0;
		while (ind < token.length()) {
			int codePoint = token.codePointAt(ind);
			ind += Character.charCount(codePoint);
			if (!Character.isLetter(codePoint)) {
				continue;
			}
			UnicodeScript script = UnicodeScript.of(codePoint);
			if (script == UnicodeScript.LATIN) {
				return TokenScript.LATIN;
			}
			// common and inherited letters, like the micro sign, belong to no script
			if (script != UnicodeScript.COMMON && script != UnicodeScript.INHERITED) {
				retVal = TokenScript.OTHER;
			}
		}
		return retVal;
	}

	/**
	 * Splits Latin text, digits and punctuation included, into segments tagged
	 * with their languages. {@link #tagStringWithLanguages} calls it for each
	 * run of Latin text.
	 */
	protected List<Pair<String, Locale>> tagLatinText(String s) throws IOException {

		List<Pair<String, Locale>> retVal = new ArrayList<>();
		StringTokenizer st = new StringTokenizer(s);
		StringBuilder currentString = new StringBuilder();
		String prevToken = null;
		StringBuilder slidingWindow = new StringBuilder();
		while (st.hasMoreTokens()) {
			String currentToken = st.nextToken();
			slidingWindow.append(currentToken).append(" ");
			if (prevToken != null) {
				currentString.append(prevToken).append(" ");
			} else {
				// we need two words to do detection
				prevToken = currentToken;
				continue;
			}

			int count = getBigramCount(slidingWindow.toString().trim());
			if (count == 0) {
				appendSegment(retVal, currentString.toString().trim());
				currentString.delete(0, currentString.length());
			}
			// remove prevToken
			slidingWindow.delete(0, prevToken.length() + 1);
			prevToken = currentToken;

		}

		if (currentString.length() > 0 || slidingWindow.length() > 0) {
			String stringToAdd = currentString.toString().trim() + " " + slidingWindow.toString().trim();
			appendSegment(retVal, stringToAdd.trim());
		}

		return retVal;
	}

	protected int getBigramCount(String s) {
		Integer count = BIGRAM_COUNTS.get(s);
		if (count == null) {
			return 0;
		} else {
			return count;
		}
	}

	/**
	 * Language of a sliding window, for deciding where language boundaries are.
	 * Letters no supported language recognizes make the window
	 * {@link LanguageDetector#UNDETERMINED}, so that text gets its own segment;
	 * words in another script never get here, {@link #tagStringWithLanguages}
	 * splits them off first. A window of only digits and punctuation carries no
	 * language evidence and keeps the English default it always had, so
	 * boundary decisions for it are unchanged; {@link #appendSegment} then
	 * merges any digits-only fragment back into the text next to it.
	 */
	protected Locale getWindowLanguage(String window) throws IOException {
		Locale detected = detector.getMostLikelyLanguage(window, algorithmToUse);
		if (detected != null) {
			return detected;
		}
		return isLanguageNeutral(window) ? Locale.ENGLISH : LanguageDetector.UNDETERMINED;
	}

	/**
	 * Adds {@code text} as the next segment, merging it into the previous one
	 * when both have the same language, or when either is only digits and
	 * punctuation: those have no language of their own and join the text next
	 * to them. Input that is nothing but digits and punctuation stays
	 * {@link LanguageDetector#UNDETERMINED}.
	 *
	 * @return the last segment
	 */
	protected Pair<String, Locale> appendSegment(List<Pair<String, Locale>> segments, String text)
			throws IOException {
		Locale detected = getLanguageWithDefault(text);
		if (rawSegments && isLanguageNeutral(text) && LanguageDetector.UNDETERMINED.equals(detected)) {
			detected = Locale.ENGLISH;
		}
		return appendSegment(segments, text, detected);
	}

	/** {@link #appendSegment(List, String)} for text whose language is already known. */
	private Pair<String, Locale> appendSegment(List<Pair<String, Locale>> segments, String text, Locale locale) {
		Pair<String, Locale> segment = new Pair<>(text, locale);
		if (!segments.isEmpty()) {
			Pair<String, Locale> previous = segments.get(segments.size() - 1);
			boolean previousNeutral = !rawSegments && isLanguageNeutral(previous.first());
			boolean neutral = !rawSegments && isLanguageNeutral(text);
			if (previousNeutral || neutral || previous.second().equals(segment.second())) {
				Locale language = previousNeutral ? segment.second() : previous.second();
				segment = new Pair<>(LanguageUtil.joinWithSpace(previous.first(), text), language);
				segments.remove(segments.size() - 1);
			}
		}
		segments.add(segment);
		return segment;
	}

	/** True for text with no letters at all: only digits, punctuation and symbols. */
	private static boolean isLanguageNeutral(String text) {
		return text.codePoints().noneMatch(Character::isLetter);
	}

	/**
	 * Detected language of {@code s}, or {@link LanguageDetector#UNDETERMINED}
	 * when it matches none of the supported languages, so unsupported text gets
	 * its own segment instead of being labeled with a guess.
	 */
	protected Locale getLanguageWithDefault(String s) throws IOException {
		Locale retVal = detector.getMostLikelyLanguage(s, algorithmToUse);
		if (retVal == null) {
			return LanguageDetector.UNDETERMINED;
		} else {
			return retVal;
		}
	}

	private void incrementBigramCounts(Map<String, Integer> map, String s) {
		map.merge(s, 1, Integer::sum);
	}

}
