package language.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import language.classifier.tree.DecisionTreeEntity;
import language.classifier.tree.DecisionTreeExample;
import language.classifier.tree.DecisionTreeFeature;

/**
 * Training set example for language models
 * 
 * @author Andrey Gusev
 */
public class LanguageDocumentExample implements DecisionTreeExample<Double, Locale> {

	private final Locale exampleLocale;
	private final SortedMap<NgramLanguageModelFeature, Map<Locale, Double>> featureValues;

	private volatile List<Double> allFeatureValues;

	public LanguageDocumentExample(Locale exampleLocale) {
		this.exampleLocale = exampleLocale;
		this.featureValues = new TreeMap<>();

	}

	public void addFeatureValue(NgramLanguageModelFeature feature, Map<Locale, Double> value) {
		Objects.requireNonNull(feature, "feature must not be null");
		this.featureValues.put(feature, value);
	}

	/**
	 * 
	 * @return boolean whether this example cab classified as positive
	 */
	public boolean isPositive(Locale positiveLabel) {
		return positiveLabel.equals(exampleLocale);
	}

	/**
	 * 
	 * @param feat
	 *            - particular feature to be examined
	 * @param value
	 *            - Implementation of the comparable interface to ensure that
	 *            this example's feature can be compared with value
	 * 
	 * @return boolean to indicate whether this example has value specified for
	 *         the feature
	 */
	public boolean hasValueForFeature(DecisionTreeFeature<Double> feat, Comparable<Double> lowValue,
			Comparable<Double> highValue, Locale positiveLabel) {

		Map<Locale, Double> valuesForFeature = this.featureValues.get(feat);
		if (valuesForFeature == null) {
			return false;
		}

		Double valueForFeature = valuesForFeature.get(positiveLabel);
		if (valueForFeature == null) {
			valueForFeature = 0.0;
		}
		boolean value = DecisionTreeEntity.<Double> isValueWithinRange(valueForFeature, lowValue, highValue);
		return value;
	}

	/**
	 * All feature values, whatever {@code positiveLabel} is: for each feature (in
	 * {@link NgramLanguageModelFeature} order) one value per locale in
	 * {@link NgramLanguageDetector#getLocales()} order, 0.0 where missing.
	 * Persisted logistic-regression weights are positional, so this order must
	 * never depend on map iteration order (it used to follow {@code HashMap}
	 * order, which changed between JDK 7 and 8).
	 */
	public List<Double> getFeatureValues(Locale positiveLabel) {

		// we will actually use feature values not just for particular locale
		if (allFeatureValues == null) {
			synchronized (this) {

				if (allFeatureValues != null) {
					return allFeatureValues;
				}

				List<Double> tempList = new ArrayList<>();

				for (Map<Locale, Double> valueMap : this.featureValues.values()) {
					for (Locale locale : NgramLanguageDetector.getLocales()) {
						Double value = valueMap.get(locale);
						tempList.add(value == null ? 0.0 : value);
					}
				}
				allFeatureValues = Collections.<Double> unmodifiableList(tempList);
			}
		}

		return this.allFeatureValues;
	}

	@Override
	public String toString() {
		return this.exampleLocale.toString() + ", features: " + this.featureValues.toString();
	}

}
