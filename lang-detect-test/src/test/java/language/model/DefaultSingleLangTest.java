package language.model;

import org.junit.jupiter.api.Test;

import language.model.NgramLanguageDetector.ClassificationAlgorithm;

/**
 * Single language test with default linear weight combination
 *
 * @author Andrey Gusev
 */
class DefaultSingleLangTest extends BaseSingleLangTest {

	// basic check with linear weights
	@Test
	void testBasicPhraseLinear() throws Exception {
		_testBasicPhrase(ClassificationAlgorithm.LINEAR_WEIGHTS);
	}

	// check longer strings
	@Test
	void testLargeEnglishString() throws Exception {
		_testLargeEnglishString(ClassificationAlgorithm.LINEAR_WEIGHTS);
	}

}
