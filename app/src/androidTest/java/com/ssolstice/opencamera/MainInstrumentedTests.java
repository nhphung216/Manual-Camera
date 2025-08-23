package com.ssolstice.camera.manual;

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/** Tests that don't fit into another of the Test suites.
 */

@RunWith(Categories.class)
@Categories.IncludeCategory(MainTests.class)
@Suite.SuiteClasses({InstrumentedTest.class})
public class MainInstrumentedTests {}
