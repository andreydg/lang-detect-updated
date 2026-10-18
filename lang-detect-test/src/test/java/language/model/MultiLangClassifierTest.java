package language.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.junit.jupiter.api.Test;

import language.classifier.BaggedDecisionTreeClassifier;
import language.model.NgramLanguageDetector.ClassificationAlgorithm;
import language.model.multiling.LanguageBoundaryDetector;
import language.model.multiling.NestedSlidingWindowBigramBoundaryDetector;
import language.util.Pair;

/**
 * Multi-language strings with a classifier instead of linear weights
 *
 * @author Andrey Gusev
 */
class MultiLangClassifierTest {

	// the nested detector's boundary search must see the classifier's own answer
	// for a digits-only fragment; English is only the fallback for no answer
	@Test
	void testNestedDetectorKeepsClassifierPredictions() throws Exception {

		resetClassifierState();
		LanguageBoundaryDetector boundaryDetector = new NestedSlidingWindowBigramBoundaryDetector(
				ClassificationAlgorithm.BAGGED_DECISION_TREE, NgramLanguageDetectorForTests.get(), 5);

		String text = "itself i say i 1 2 3 4 was seated";
		assertEquals(List.of(new Pair<>(text, Locale.ENGLISH)), boundaryDetector.tagStringWithLanguages(text),
				"Should be one English phrase");
	}

	/**
	 * The trained trees and the random number generators behind training and tie
	 * breaking are static, so they depend on what ran earlier in this JVM. Start
	 * from the seeds a fresh JVM has and retrain.
	 */
	private static void resetClassifierState() throws ReflectiveOperationException {
		Field trees = NgramLanguageDetector.class.getDeclaredField("DECISION_TREES");
		trees.setAccessible(true);
		trees.set(null, null);
		reseed(NgramLanguageDetector.class);
		reseed(BaggedDecisionTreeClassifier.class);
	}

	private static void reseed(Class<?> owner) throws ReflectiveOperationException {
		Field field = owner.getDeclaredField("rnd");
		field.setAccessible(true);
		((Random) field.get(null)).setSeed(1);
	}
}
