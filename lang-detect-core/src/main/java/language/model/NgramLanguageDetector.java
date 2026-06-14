package language.model;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.jcip.annotations.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import language.classifier.BaggedDecisionTreeClassifier;
import language.classifier.Classifier;
import language.classifier.LogisticRegressionClassifier;
import language.util.LanguageUtil;
import language.util.Pair;

/**
 * Uses ngram model in Eucledian ngram vector space to detect language of the
 * string at this point only supports one language in string and will likely
 * detect the language that is dominant in a string. The correctness of
 * detection increases with length of a string.
 * 
 * @author Andrey Gusev
 */
public class NgramLanguageDetector implements LanguageDetector {

	private static final Logger log = LoggerFactory.getLogger(NgramLanguageDetector.class);
	private static final Random rnd = new Random(1);

	// path constants
	public static final String BASE_MODEL_DIR = "languagemodels";
	public static final String NGRAM_MODEL_DIR = "ngramModel";
	public static final String TRAINING_TEST_DIR = "trainingAndTestSet";
	public static final String LOGISTIC_CLASSFIER_DIR = "logisticClassifier";

	// classifier constants
	private static final Double MIN_SCORE = 0.05;
	private static final int WORD_LENGTH_BOUNDARY = 1;
	private static final int DEFAULT_DECISION_TREE_BAGS = 10;
	private static final ClassificationAlgorithm DEFAULT_CLASSIFIER = ClassificationAlgorithm.LINEAR_WEIGHTS;

	// locales
	protected static final Locale[] LOCALES;
	protected static final Map<String, Locale> LOCALE_MAP;

	// format (thread-safe for concurrent detectors / reports)
	private static final int scale = 3;
	private static final ThreadLocal<DecimalFormat> DECIMAL_FORMAT =
			ThreadLocal.withInitial(
					() -> {
						DecimalFormat df = new DecimalFormat();
						df.setMinimumFractionDigits(scale);
						df.setMaximumFractionDigits(scale);
						return df;
					});

	/** Formats doubles for logging and text reports; safe under concurrency. */
	protected static String formatScaled(double value) {
		return DECIMAL_FORMAT.get().format(value);
	}

	private final static Lock DF = new ReentrantLock();
	private final static Lock LC = new ReentrantLock();
	private final static Lock DS = new ReentrantLock();

	// dataset cache
	@GuardedBy("DS")
	private static volatile List<LanguageDocumentExample> DATASET;

	// cache of trained classifiers
	@GuardedBy("DF")
	private static volatile Map<Locale, Classifier<Double, Locale, LanguageDocumentExample>> DECISION_TREES;
	@GuardedBy("LC")
	private static volatile Map<Locale, Classifier<Double, Locale, LanguageDocumentExample>> LOGISITIC_CLASSIFIERS;

	// main ngram models
	private final Map<Pair<Locale, Integer>, NgramModel> languageNgramModels;
	protected final Integer[] ngramSet;

	protected final Path basePath;

	static {
		// EFIGS languages + portuguese
		LOCALES = new Locale[] { Locale.ENGLISH, Locale.FRENCH, Locale.ITALIAN, Locale.GERMAN, Locale.of("es"),
				Locale.of("pt") };

		LOCALE_MAP = Arrays.stream(LOCALES)
				.collect(Collectors.toUnmodifiableMap(Locale::toString, loc -> loc));
	}

	public NgramLanguageDetector(Path basePath) {

		// if you change this set you need to change set of enums for features
		// in NgramLanguageModelFeature
		this.ngramSet = Arrays.stream(NgramLanguageModelFeature.values())
				.filter(f -> !f.isNonStandard())
				.map(NgramLanguageModelFeature::getNGramSize)
				.toArray(Integer[]::new);
		this.basePath = basePath;

		// init all the models
		this.languageNgramModels = Collections.unmodifiableMap(populateLanguageModels());
	}

	/** Logs only the query length — never the user's text — to avoid leaking PII. */
	public final void logQuery(String q) {
		if (q != null && !q.isEmpty()) {
			log.info("Q length:[{}]", q.length());
		}
	}

	protected final NgramModel getNgramModelForText(String text, NgramModel model, boolean adjustValue) {

		if (text == null || text.isEmpty()) {
			return model;
		}
		return addTokensToModel(LanguageUtil.tokenize(text, 1), model, adjustValue);
	}

	/**
	 * Adds the n-grams of already-tokenized {@code words} to {@code model}. Split
	 * out from {@link #getNgramModelForText} so callers that need text models for
	 * several n-gram sizes can tokenize the input once and reuse the token list.
	 */
	private NgramModel addTokensToModel(List<String> words, NgramModel model, boolean adjustValue) {
		for (String word : words) {
			int wordLength = word.length();
			// the value of ngram is not simply its count but is adjusted
			// for longer words this way short language representative words
			// have larger effect, when building ngram models over large
			// text corpus we do not adjust the value
			// the value is only adjusted for user input text
			double ngramValue = (!adjustValue || wordLength <= WORD_LENGTH_BOUNDARY) ? 1.0
					: ((double) WORD_LENGTH_BOUNDARY) / ((double) wordLength);
			for (String nGram : model.getNgrams(word)) {
				// only adjust ngram values for text input
				// use original counts for building language model for text
				model.addNgram(nGram, ngramValue);
			}
		}
		return model;
	}

	public final Integer[] getNgramSet() {
		return Arrays.copyOf(this.ngramSet, this.ngramSet.length);
	}

	/*
	 * Get list of training example to train classifier
	 */
	protected final List<LanguageDocumentExample> getTrainingExamples(boolean addLinearWeightFeature)
			throws IOException {
		return getTrainingExamples(addLinearWeightFeature, -1, 0f);
	}

	/*
	 * Get first n of of training example to train classifier
	 */
	protected final List<LanguageDocumentExample> getTrainingExamples(boolean addLinearWeightFeature, int n, float ratio)
			throws IOException {

		Path trainingDir = basePath.resolve(BASE_MODEL_DIR).resolve(TRAINING_TEST_DIR);

		List<LanguageDocumentExample> examples = new ArrayList<>();
		outer: for (Locale positiveLocale : LOCALES) {
			log.info("Reading data set for: {}", positiveLocale);
			Path file = trainingDir.resolve(positiveLocale.toString() + "_training");

			if (!Files.exists(file)) {
				continue;
			}

			// need to read in UTF-8
			String s;
			try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				while ((s = br.readLine()) != null) {

					// sample if necessary
					if (!((ratio > 0f && rnd.nextFloat() < ratio) || ratio <= 0f)) {
						continue;
					}
					
					LanguageDocumentExample trainingExample = getExample(s, addLinearWeightFeature, positiveLocale);

					examples.add(trainingExample);
					int size = examples.size();

					if (size > 0 && size % 1000 == 0) {
						log.info("Loaded {} examples", size);
					}

					if (n > -1 && size >= n) {
						break outer;
					}
				}
			}
		}

		return examples;
	}

	/*
	 * get Language document example
	 */
	protected final LanguageDocumentExample getExample(String s, boolean addLinearWeightFeature, Locale positiveLocale)
			throws IOException {
		LanguageDocumentExample example = new LanguageDocumentExample(positiveLocale);
		for (int nGram : ngramSet) {
			// calculate all cosine similarities for each language
			Map<Locale, Double> rawSimilarities = getRawCosineSimilarities(s, nGram, true);
			example.addFeatureValue(NgramLanguageModelFeature.getEnumByValue(nGram), rawSimilarities);
		}
		if (addLinearWeightFeature) {
			// add special linear combination feature
			Map<Locale, Double> linearCombination = new HashMap<>();
			for (Entry<Locale, Double> entry : this.detectLanguageWithLinearWeights(s, false)) {
				linearCombination.put(entry.getKey(), entry.getValue());
			}

			example.addFeatureValue(NgramLanguageModelFeature.LINEAR_COMBINATION, linearCombination);
		}

		return example;
	}

	/*
	 * Training logistic classifier
	 */
	protected final Map<Locale, Classifier<Double, Locale, LanguageDocumentExample>> trainLogisiticClassifier()
			throws IOException {

		Map<Locale, Classifier<Double, Locale, LanguageDocumentExample>> retVal = new HashMap<>();

		// since training takes a long time we want to train using multiple
		// threads classifier itself can not be trained in multiple threads
		final int numThreads = 2;
		try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
			CompletionService<LogisticRegressionClassifier<Locale, LanguageDocumentExample>> completionService =
					new ExecutorCompletionService<>(executor);

			int numSubmitted = 0;
			for (Locale positiveLocale : LOCALES) {
				log.info("Creating logistic regression classifier for: {}", positiveLocale);
				// just need one datum to establish dimensions
				LanguageDocumentExample someExample = getTrainingExamples(true, 1, 0).get(0);
				LogisticRegressionClassifier<Locale, LanguageDocumentExample> localeClassifier =
						new LogisticRegressionClassifier<>(
								someExample.getFeatureValues(positiveLocale).size(), positiveLocale);

				// submit to read or train classifier
				completionService.submit(new LogisticClassifierTrainer(localeClassifier, positiveLocale));
				numSubmitted++;
				retVal.put(positiveLocale, localeClassifier);
			}

			for (int ind = 0; ind < numSubmitted; ind++) {
				try {
					LogisticRegressionClassifier<Locale, LanguageDocumentExample> localeClassifier = completionService
							.take()
							.get();
					retVal.put(localeClassifier.getPositiveLabel(), localeClassifier);
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
			}

			return retVal;
		}
	}

	protected class LogisticClassifierTrainer implements
			Callable<LogisticRegressionClassifier<Locale, LanguageDocumentExample>> {

		private final LogisticRegressionClassifier<Locale, LanguageDocumentExample> localeClassifier;

		private final Locale positiveLocale;
		
		public LogisticClassifierTrainer(
				LogisticRegressionClassifier<Locale, LanguageDocumentExample> localeClassifier, Locale positiveLocale) {
			this.localeClassifier = localeClassifier;
			this.positiveLocale = positiveLocale;
		}

		public LogisticRegressionClassifier<Locale, LanguageDocumentExample> call() throws IOException {

			try (DataInputStream input = getLogisticClassifierDataInput(positiveLocale)) {
				// try reading from cache
				if (input == null || !localeClassifier.read(input)) {
					localeClassifier.train(getCachedTrainingDataSet(true));
					// write new classifier
					try (DataOutputStream output = getLogisitcClassifierDataOutput(positiveLocale)) {
						localeClassifier.write(output);
					}
				}
			}
			return localeClassifier;
		}
	}

	protected List<LanguageDocumentExample> getCachedTrainingDataSet(boolean addLinearWeightFeature) throws IOException {
		// lazy init
		if (DATASET == null) {
			DS.lock();
			try {
				if (DATASET == null) {
					DATASET = getTrainingExamples(addLinearWeightFeature);
				}
			} finally {
				DS.unlock();
			}
		}
		return DATASET;
	}

	protected Path getLogisticClassifierPath(Locale locale) {
		return basePath.resolve(BASE_MODEL_DIR).resolve(LOGISTIC_CLASSFIER_DIR).resolve(locale.toString());
	}

	protected DataInputStream getLogisticClassifierDataInput(Locale locale) {

		Path location = getLogisticClassifierPath(locale);
		try {
			return new DataInputStream(Files.newInputStream(location));
		} catch (IOException e) {
			log.info("Could not load classifier from: {}", location);
			return null;
		}
	}

	protected DataOutputStream getLogisitcClassifierDataOutput(Locale locale) {

		// for now just write in memory
		return new DataOutputStream(new ByteArrayOutputStream());
	}

	/*
	 * Training decision tree
	 */
	protected final Map<Locale, Classifier<Double, Locale, LanguageDocumentExample>> trainDecisionTree(int numBags)
			throws IOException {

		// reduce data set for faster training
		List<LanguageDocumentExample> examples = getTrainingExamples(true, -1, getDatasetSampleRatio());

		Map<Locale, Classifier<Double, Locale, LanguageDocumentExample>> retVal = new HashMap<>();

		for (Locale positiveLocale : LOCALES) {
			log.info("Creating bagged decision tree classifier for: {}", positiveLocale);
			BaggedDecisionTreeClassifier<Double, Locale, LanguageDocumentExample> localeBag = new BaggedDecisionTreeClassifier<>(
					numBags, positiveLocale, NgramLanguageModelFeature.values());
			localeBag.train(examples);
			retVal.put(positiveLocale, localeBag);
		}

		return retVal;
	}
	
	protected float getDatasetSampleRatio(){
		return 1f;
	}
	
	/**
	 * 
	 * @param text
	 *            - the text that needs to classified
	 * @param classifiers
	 *            - the map of one vs. many classifier which will be used for
	 *            classification
	 * @return Locale of most likely language
	 * @throws IOException
	 */
	protected final Locale detectLanguageClassifier(String text,
			Map<Locale, ? extends Classifier<Double, Locale, LanguageDocumentExample>> classifiers,
			boolean addLinearWeightFeature) throws IOException {
		// populate example with feature values
		LanguageDocumentExample example = getExample(text, addLinearWeightFeature, null);

		// now pick highest classifier
		Locale predictedLocale = null;
		double highestConfidence = 0;

		List<Locale> sameConfidenceLocale = new ArrayList<>();
		for (Locale locale : LOCALES) {

			double confidenceLevel = classifiers.get(locale).getConfidenceLevel(example);
			if (confidenceLevel > highestConfidence) {
				highestConfidence = confidenceLevel;
				predictedLocale = locale;
				sameConfidenceLocale.clear();
				sameConfidenceLocale.add(locale);
			} else if (Double.compare(confidenceLevel, highestConfidence) == 0) {
				sameConfidenceLocale.add(locale);
				predictedLocale = null;
			}
		}

		// we have matching highestConfidences return random one
		if (predictedLocale == null) {
			return sameConfidenceLocale.get(rnd.nextInt(sameConfidenceLocale.size()));
		}
		return predictedLocale;
	}

	/**
	 * Will detect most likely language with with bagged decision tree
	 * classifier
	 */
	private final Locale detectLanguageWithDecisionTree(String text) throws IOException {

		// lazy init
		if (DECISION_TREES == null) {
			DF.lock();
			try {
				if (DECISION_TREES == null) {
					DECISION_TREES = Collections.unmodifiableMap(trainDecisionTree(DEFAULT_DECISION_TREE_BAGS));
				}
			} finally {
				DF.unlock();
			}
		}
		return detectLanguageClassifier(text, DECISION_TREES, true);
	}

	/**
	 * Will detect most likely language with logistic classifier
	 */
	private final Locale detectLanguageWithLogisiticClassifier(String text) throws IOException {

		// lazy init
		if (LOGISITIC_CLASSIFIERS == null) {
			LC.lock();
			try {
				if (LOGISITIC_CLASSIFIERS == null) {
					LOGISITIC_CLASSIFIERS = Collections.unmodifiableMap(trainLogisiticClassifier());
				}
			} finally {
				LC.unlock();
			}
		}
		return detectLanguageClassifier(text, LOGISITIC_CLASSIFIERS, true);
	}

	@Override
	public final Locale getMostLikelyLanguage(String text) throws IOException {
		return getMostLikelyLanguage(text, DEFAULT_CLASSIFIER);
	}

	@Override
	public final Locale getMostLikelyLanguage(String text, ClassificationAlgorithm algorithmToUse) throws IOException {
		Locale retVal = null;
		switch (algorithmToUse) {
		case BAGGED_DECISION_TREE:
			retVal = this.detectLanguageWithDecisionTree(text);
			break;
		case LINEAR_WEIGHTS:
			retVal = this.getLanguageWithLinerWeights(text);
			break;
		case LOGISTIC_CLASSIFIER:
			retVal = this.detectLanguageWithLogisiticClassifier(text);
			break;
		}
		return retVal;
	}

	/**
	 * @param text
	 *            - text for which we will detect language
	 * @return most likely language
	 */
	public final Locale getLanguageWithLinerWeights(String text) throws IOException {
		SortedSet<Entry<Locale, Double>> set = this.detectLanguageWithLinearWeights(text, true);
		if (set != null && set.size() > 0) {
			return set.first().getKey();
		} else {
			return null;
		}
	}

	/**
	 * returns ordered set of languages that are most similar to given text,
	 * using all nGram sizes specified for language detector
	 */
	@Override
	public final SortedSet<Entry<Locale, Double>> detectLanguageWithLinearWeights(String text, boolean ignoreLowScores)
			throws IOException {

		// tokenize once and reuse the token list across all n-gram sizes instead
		// of re-tokenizing the full input for each size
		String trimmed = text.trim();
		List<String> words = LanguageUtil.tokenize(trimmed, 1);

		List<Map<Locale, Double>> listOfRawCosineSimilaties = new ArrayList<>(ngramSet.length);

		// first get all the raw cosine similarities
		for (int nGramSize : ngramSet) {
			NgramModel textModel =
					trimmed.length() >= nGramSize ? addTokensToModel(words, new NgramModel(nGramSize), true) : null;
			listOfRawCosineSimilaties.add(rawCosineSimilarities(textModel, nGramSize, true));
		}

		Map<Locale, Double> retValue = new HashMap<>();

		int numOfModels = listOfRawCosineSimilaties.size();

		// for all raw cosine similarities create overall locale score
		// that combines positional information in each return list of results
		// TODO: maybe adding original scores will reflect matches better
		for (Map<Locale, Double> rawCosineSimilarity : listOfRawCosineSimilaties) {
			int counter = rawCosineSimilarity.size();
			double maxScore = 0;

			for (Entry<Locale, Double> entry : getValueSortedDescendingEntries(rawCosineSimilarity)) {

				Double value = entry.getValue();
				if (ignoreLowScores && value == 0) {
					continue;
				}
				if (counter == rawCosineSimilarity.size()) {
					maxScore = value;
				}

				// ignore the scores that are very low
				if (ignoreLowScores && value < MIN_SCORE) {
					continue;
				}

				// normalize it so that top score is 1.00
				Double currentValue = retValue.get(entry.getKey());
				if (currentValue == null) {
					currentValue = value > 0 ? (value / (maxScore * numOfModels)) : 0;
				} else {
					currentValue += value > 0 ? (value / (maxScore * numOfModels)) : 0;
				}
				retValue.put(entry.getKey(), currentValue);
				counter--;
			}
		}

		return getValueSortedDescendingEntries(retValue);
	}

	private SortedSet<Entry<Locale, Double>> getValueSortedDescendingEntries(Map<Locale, Double> map) {
		Comparator<Entry<Locale, Double>> byScoreDesc =
				Comparator.<Entry<Locale, Double>, Double>comparing(Entry<Locale, Double>::getValue).reversed();
		Comparator<Entry<Locale, Double>> byLanguage =
				Comparator.comparing(e -> e.getKey().getLanguage());
		SortedSet<Entry<Locale, Double>> retVal = new TreeSet<>(byScoreDesc.thenComparing(byLanguage));
		retVal.addAll(map.entrySet());
		return retVal;
	}

	/**
	 * return unsorted locales and their consine similarity to given text in
	 * nGram space
	 */
	public final Map<Locale, Double> getRawCosineSimilarities(String text, int nGramSize, boolean addNgramWeight)
			throws IOException {

		String trimmed = text.trim();

		NgramModel textModel = trimmed.length() >= nGramSize
				? this.getNgramModelForText(trimmed, new NgramModel(nGramSize), true) : null;

		return rawCosineSimilarities(textModel, nGramSize, addNgramWeight);
	}

	/**
	 * Cosine similarity of a prebuilt text model against every language model of
	 * the given n-gram size. Lets callers build the text model once and avoid
	 * re-tokenizing per size.
	 */
	private Map<Locale, Double> rawCosineSimilarities(NgramModel textModel, int nGramSize, boolean addNgramWeight) {

		Map<Locale, Double> retVal = new HashMap<>();

		for (Map.Entry<Pair<Locale, Integer>, NgramModel> model : this.languageNgramModels.entrySet()) {
			// skip models that have different ngram size
			if (model.getKey().second() != nGramSize) {
				continue;
			}
			// calculate cosine similarity
			double cosineSimilarity = textModel != null ? model.getValue().calculateCosineSimilarity(textModel) : 0.00;
			if (addNgramWeight) {
				cosineSimilarity *= nGramSize;
			}
			retVal.put(model.getKey().first(), cosineSimilarity);
		}

		return retVal;
	}

	private Map<Pair<Locale, Integer>, NgramModel> populateLanguageModels() {

		Map<Pair<Locale, Integer>, NgramModel> retVal = new HashMap<>(32);

		Path modelBase = basePath.resolve(BASE_MODEL_DIR).resolve(NGRAM_MODEL_DIR);

		for (Integer nGramSize : ngramSet) {

			// populate the models and cache them
			for (Locale locale : LOCALES) {
				Pair<Locale, Integer> key = new Pair<>(locale, nGramSize);

				Path modelPath = modelBase.resolve(locale.toString() + "_" + nGramSize);
				if (!Files.exists(modelPath)) {
					log.error("Could not load model from: {}", modelPath);
					continue;
				}
				try {
					retVal.put(key, readModel(modelPath, locale, nGramSize));
				} catch (IOException e) {
					throw new RuntimeException("Failed to read model with key: " + key, e);
				}
			}

		}

		return retVal;
	}

	private NgramModel readModel(Path modelPath, Locale locale, int nSize) throws IOException {

		NgramModel languageModel = new NgramModel(locale, nSize);

		try (Stream<String> lines = Files.lines(modelPath, StandardCharsets.UTF_8)) {
			lines.forEach(s -> {
				String[] parts = s.split(NgramModel.NGRAM_SEPARTOR);
				if (parts.length != 2) {
					throw new IllegalStateException("Malformed model line (expected 2 parts): " + s);
				}
				languageModel.addNormalizedNgram(parts[0], Double.parseDouble(parts[1]));
			});
		}

		return languageModel;
	}

	public static Locale[] getLocales() {
		return LOCALES;
	}

	public final Path getBasePath() {
		return basePath;
	}

	public static enum ClassificationAlgorithm {
		LINEAR_WEIGHTS, BAGGED_DECISION_TREE, LOGISTIC_CLASSIFIER;
	}

	public static enum BoundaryDetectionAlgorithm {
		ONE_WORD, TWO_WORD, THREE_WORD, BASE_BIGRAM, TWO_WORD_BIGRAM, THREE_WORD_BIGRAM, FOUR_WORD_BIGRAM, FIVE_WORD_BIGRAM, SIX_WORD_BIGRAM, FIVE_WORD_NESTED;
	}
}
