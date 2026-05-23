package language.model;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NgramLanguageDetector with overrides for test
 * 
 * @author Andrey Gusev
 *
 */
public class NgramLanguageDetectorForTests extends NgramLanguageDetector {
	
	private static final Logger log = LoggerFactory.getLogger(NgramLanguageDetectorForTests.class);

	private NgramLanguageDetectorForTests(Path basePath) {
		super(basePath);
	}

	private static Path resolveTestBase() {
		String override = System.getProperty("lang.detect.test.data");
		if (override != null && !override.isEmpty()) {
			Path f = Path.of(override).toAbsolutePath();
			if (Files.isDirectory(f.resolve("languagemodels"))) {
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
			Path f = Path.of(c).toAbsolutePath().normalize();
			if (Files.isDirectory(f.resolve("languagemodels"))) {
				return f;
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

		Path location = getLogisticClassifierPath(locale);
		try {
			Files.createDirectories(location.getParent());
			return new DataOutputStream(Files.newOutputStream(location));
		} catch (IOException e) {
			log.error("Could not write classifier to: {}", location);
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
