package language.model;

import org.junit.jupiter.api.Test;

import language.model.NgramLanguageDetector.ClassificationAlgorithm;

/**
 * Single language test with logistic classifier
 *
 * @author Andrey Gusev
 */
class LogisticRegressionSingleLangTest extends BaseSingleLangTest {

	// basic check with logistic classifier
	@Test
	void testBasicPhrase() throws Exception {
		_testBasicPhrase(ClassificationAlgorithm.LOGISTIC_CLASSIFIER);
	}

	// check longer strings
	@Test
	void testLargeEnglishString() throws Exception {
		_testLargeEnglishString(ClassificationAlgorithm.LOGISTIC_CLASSIFIER);
	}

}
