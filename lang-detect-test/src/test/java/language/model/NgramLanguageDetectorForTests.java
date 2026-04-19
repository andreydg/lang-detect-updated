package language.model;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * NgramLanguageDetector with overrides for test
 * 
 * @author Andrey Gusev
 *
 */
public class NgramLanguageDetectorForTests extends NgramLanguageDetector {
	
	private static final Logger log = Logger.getLogger(NgramLanguageDetectorForTests.class.getName());

	private NgramLanguageDetectorForTests(File basePath) {
		super(basePath);
	}

	private static File resolveTestBase() {
		String override = System.getProperty("lang.detect.test.data");
		if (override != null && !override.isEmpty()) {
			File f = new File(override);
			if (new File(f, "languagemodels").isDirectory()) {
				return f;
			}
			throw new IllegalStateException(
					"lang.detect.test.data does not contain languagemodels/: " + f);
		}
		String[] candidates = {
			"lang-detect/war",
			"../lang-detect/war",
			"../../lang-detect/war",
			"../../../lang-detect/war"
		};
		for (String c : candidates) {
			File f = new File(c);
			if (new File(f, "languagemodels").isDirectory()) {
				try {
					return f.getCanonicalFile();
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			}
		}
		throw new IllegalStateException(
				"Could not find lang-detect/war with languagemodels. "
						+ "Set -Dlang.detect.test.data=/path/to/war");
	}
	
	protected static NgramLanguageDetector get() {
		return new NgramLanguageDetectorForTests(resolveTestBase());
	}
	
	
	/**
	 * Override to to write to file
	 */
	@Override
	protected final DataOutputStream getLogisitcClassifierDataOutput(Locale locale) {

		String location = getLogisticClassifierFileCache(locale);
		try {
			return new DataOutputStream(new FileOutputStream(location));
		} catch (FileNotFoundException e) {
			log.severe("Could not write classifier to: " + location);
			return null;
		}
	}
	
	/**
	 * Sample to speed up test
	 */
	@Override
	protected float getDatasetSampleRatio(){
		return 0.01f;
	}

}
