package language.model.multiling;

import java.io.BufferedReader;
import java.io.IOException;
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

	public BigramBoundaryDetector(ClassificationAlgorithm algorithmToUse, NgramLanguageDetector detector)
			throws IOException {
		super(algorithmToUse, detector);
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

	public List<Pair<String, Locale>> tagStringWithLanguages(String s) throws IOException {

		List<Pair<String, Locale>> retVal = new ArrayList<>();
		StringTokenizer st = new StringTokenizer(s);
		StringBuilder currentString = new StringBuilder();
		String prevToken = null;
		StringBuilder slidingWindow = new StringBuilder();
		Pair<String, Locale> previousPair = null;
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
				String stringToAdd = currentString.toString().trim();
				Pair<String, Locale> thisPair = new Pair<>(stringToAdd, this.getLanguageWithDefault(stringToAdd));
				// if the languages are the same we can collapse it into one
				// language
				if (previousPair != null && previousPair.second().equals(thisPair.second())) {
					retVal.remove(retVal.size() - 1);
					previousPair = new Pair<>(LanguageUtil.joinWithSpace(previousPair.first(), stringToAdd), previousPair.second());
					retVal.add(previousPair);

				} else {
					retVal.add(thisPair);
					previousPair = thisPair;
				}
				currentString.delete(0, currentString.length());
			}
			// remove prevToken
			slidingWindow.delete(0, prevToken.length() + 1);
			prevToken = currentToken;

		}

		if (currentString.length() > 0 || slidingWindow.length() > 0) {
			String stringToAdd = currentString.toString().trim() + " " + slidingWindow.toString().trim();
			stringToAdd = stringToAdd.trim();
			Pair<String, Locale> thisPair = new Pair<>(stringToAdd, this.getLanguageWithDefault(stringToAdd));
			// if the languages are the same we can collapse it into one
			// language
			if (previousPair.second().equals(thisPair.second())) {
				retVal.remove(retVal.size() - 1);
				retVal.add(new Pair<>(LanguageUtil.joinWithSpace(previousPair.first(), stringToAdd), previousPair.second()));
			} else {
				retVal.add(thisPair);
			}
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

	protected Locale getLanguageWithDefault(String s) throws IOException {
		Locale retVal = detector.getMostLikelyLanguage(s, algorithmToUse);
		if (retVal == null) {
			return Locale.ENGLISH;
		} else {
			return retVal;
		}
	}

	private void incrementBigramCounts(Map<String, Integer> map, String s) {
		map.merge(s, 1, Integer::sum);
	}

}
