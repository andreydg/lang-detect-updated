package language.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Feature vector layout of {@link LanguageDocumentExample}
 *
 * @author Andrey Gusev
 */
class LanguageDocumentExampleTest {

	// persisted logistic weights are positional, so values must come out in
	// LOCALES order however the per-locale maps were built
	@Test
	void testFeatureValuesFollowLocaleOrderNotMapOrder() {
		Locale[] locales = NgramLanguageDetector.getLocales();
		Map<Locale, Double> forward = new LinkedHashMap<>();
		Map<Locale, Double> reversed = new LinkedHashMap<>();
		for (int ind = 0; ind < locales.length; ind++) {
			forward.put(locales[ind], (double) ind);
			int rev = locales.length - 1 - ind;
			reversed.put(locales[rev], (double) rev);
		}

		LanguageDocumentExample a = new LanguageDocumentExample(Locale.ENGLISH);
		a.addFeatureValue(NgramLanguageModelFeature._1GRAM, forward);
		LanguageDocumentExample b = new LanguageDocumentExample(Locale.ENGLISH);
		b.addFeatureValue(NgramLanguageModelFeature._1GRAM, reversed);

		List<Double> expected = List.of(0.0, 1.0, 2.0, 3.0, 4.0, 5.0);
		assertEquals(expected, a.getFeatureValues(Locale.ENGLISH), "Values not in LOCALES order");
		assertEquals(expected, b.getFeatureValues(Locale.ENGLISH), "Values followed map order");
	}

	// every feature contributes one value per locale, 0.0 where a locale is missing
	@Test
	void testMissingLocalesAreZero() {
		LanguageDocumentExample example = new LanguageDocumentExample(Locale.ENGLISH);
		example.addFeatureValue(NgramLanguageModelFeature._2GRAM, Map.of());
		example.addFeatureValue(NgramLanguageModelFeature.LINEAR_COMBINATION, Map.of(Locale.FRENCH, 0.5));

		// features in enum order (LINEAR_COMBINATION first), locales as [en, fr, it, de, es, pt]
		assertEquals(List.of(0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
				example.getFeatureValues(Locale.ENGLISH), "Unexpected feature vector");
	}
}
