package language.model;

import org.junit.jupiter.api.Test;

import language.model.NgramLanguageDetector.ClassificationAlgorithm;

class BaggedDTWithTrainingSingleLangTest extends BaseSingleLangTest {

	// basic check with bagged decision tree classifier
	@Test
	void testBasicPhrase() throws Exception {
		_testBasicPhrase(ClassificationAlgorithm.BAGGED_DECISION_TREE);
	}

	// check longer strings
	@Test
	void testLargeEnglishString() throws Exception {
		_testLargeEnglishString(ClassificationAlgorithm.BAGGED_DECISION_TREE);
	}

}
