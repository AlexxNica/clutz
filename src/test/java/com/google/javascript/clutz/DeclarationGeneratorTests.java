package com.google.javascript.clutz;

import static com.google.javascript.clutz.ProgramSubject.assertThatProgram;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.junit.runner.Describable;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@RunWith(AllTests.class)
public class DeclarationGeneratorTests {
  /** Comments in .d.ts and .js golden files starting with '//!!' are stripped. */
  static final Pattern GOLDEN_FILE_COMMENTS_REGEXP = Pattern.compile("(?m)^\\s*//!!.*\\n");

  public static final FilenameFilter JS = new FilenameFilter() {
    @Override
    public boolean accept(File dir, String name) {
      return name.endsWith(".js");
    }
  };

  public static final FilenameFilter JS_NO_EXTERNS = new FilenameFilter() {
    @Override
    public boolean accept(File dir, String name) {
      return name.endsWith(".js") && !name.endsWith(".externs.js");
    }
  };

  public static final FilenameFilter TS_SOURCES = new FilenameFilter() {
    @Override
    public boolean accept(File dir, String name) {
      return name.endsWith(".ts");
    }
  };

  public static TestSuite suite() throws IOException {
    TestSuite suite = new TestSuite(DeclarationGeneratorTests.class.getName());

    List<File> testFiles = getTestInputFiles(JS_NO_EXTERNS);
    for (final File input : testFiles) {
      File golden = getGoldenFile(input);
      final String goldenText = getTestFileText(golden);
      ProgramSubject subject = assertThatProgram(input);
      if (input.getName().contains("_with_platform")) {
        subject.withPlatform = true;
      }
      subject.extraExternFile = getExternFileNameOrNull(input.getName());
      suite.addTest(new DeclarationTest(input.getName(), goldenText, subject));
    }
    return suite;
  }

  static File getGoldenFile(final File input) {
    return new File(input.getPath().replaceAll("\\.js$", ".d.ts"));
  }

  static String getExternFileNameOrNull(String testFileName) {
    String possibleFileName = testFileName.replace(".js", ".externs.js");
    Path externFile = getPackagePath().resolve(possibleFileName);
    return externFile.toFile().exists() ? externFile.toString() : null;
  }

  static List<File> getTestInputFiles(FilenameFilter filter) {
    File[] testFiles = getPackagePath().toFile().listFiles(filter);
    return Arrays.asList(testFiles);
  }

  private static Path getPackagePath() {
    Path testDir = FileSystems.getDefault().getPath("src", "test", "java");
    String packageName = DeclarationGeneratorTests.class.getPackage().getName();
    return testDir.resolve(packageName.replace('.', File.separatorChar));
  }

  static String getTestFileText(final File input) throws IOException {
    String text = Files.asCharSource(input, Charsets.UTF_8).read();
    return GOLDEN_FILE_COMMENTS_REGEXP.matcher(text).replaceAll("");
  }

  private static final class DeclarationTest implements Test, Describable {
    private final String testName;
    private final ProgramSubject subject;
    private final String goldenText;

    private DeclarationTest(String testName, String goldenText, ProgramSubject subject) {
      this.testName = testName;
      this.goldenText = goldenText;
      this.subject = subject;
    }

    @Override
    public void run(TestResult result) {
      result.startTest(this);
      try {
        subject.generatesDeclarations(goldenText);
      } catch (Throwable t) {
        result.addError(this, t);
      } finally {
        result.endTest(this);
      }
    }

    @Override
    public int countTestCases() {
      return 1;
    }

    @Override
    public String toString() {
      return testName;
    }

    @Override
    public Description getDescription() {
      return Description.createTestDescription(DeclarationGeneratorTests.class, testName);
    }
  }
}
