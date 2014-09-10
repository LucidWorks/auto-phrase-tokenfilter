package com.lucidworks.analysis;

import junit.framework.TestCase;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.analysis.util.WordlistLoader;
import org.apache.lucene.util.Version;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParserPlugin;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for the AutoPhrasingQParserPlugin
 * Note: The use of PowerMock with java 1.7.0_65 will blow up with a "Error exists in the bytecode" type message
 * More info at: https://code.google.com/p/powermock/issues/detail?id=504
 * Workaround is to add the -noverify vm option to the test run configuration
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({WordlistLoader.class, SolrCore.class})
public class TestAutoPhrasingQParserPlugin extends TestCase {

    private final boolean DefaultIgnoreCase = false;
    private final String DownstreamParser = "edismax";
    private final char DefaultReplaceWhitespaceWith = 'Z';

    public void testCreateParserNoChangeSingleTerm() throws Exception {
        String actual = "something";
        String expected = "something";
        invokeCreateParser(actual, expected);
    }

    public void testCreateParserNoChangeMultipleTerms() throws Exception {
        String actual = "two things";
        String expected = "two things";
        invokeCreateParser(actual, expected);
    }

    public void testCreateParserEmptyQuery() throws Exception {
        String actual = "";
        String expected = "";
        invokeCreateParser(actual, expected);
    }

    public void testCreateParserOnlySpace() throws Exception {
        String actual = " ";
        String expected = "";
        invokeCreateParser(actual, expected);
    }

    public void testCreateParserFieldAndValue() throws Exception {
        String actual = "Field:Value";
        String expected = "Field:Value";
        invokeCreateParser(actual, expected);
    }

    public void testCreateParserMultipleThings() throws Exception {
        String actual = "Field:Value something else";
        String expected = "Field:Value something else";
        invokeCreateParser(actual, expected);
    }

    public void testCreateParserSimpleReplace() throws Exception {
        String actual = "wheel chair";
        String expected = String.format("wheel%cchair", DefaultReplaceWhitespaceWith);
        invokeCreateParser(actual, expected);
    }

    public void testCreateParserDoNotIgnoreCase() throws Exception {
        String actual = "Wheel Chair";
        String expected = "Wheel Chair";
        invokeCreateParser(actual, expected);
    }

    public void testCreateParserIgnoreCase() throws Exception {
        String actual = "Wheel Chair";
        String expected = String.format("wheel%cchair", DefaultReplaceWhitespaceWith);
        invokeCreateParser(actual, expected, true, DefaultReplaceWhitespaceWith);
    }

    public void testCreateParserMultiplePhrases() throws Exception {
        String actual = "wheel chair hi there";
        String expected = String.format("wheel%cchair hi%cthere", DefaultReplaceWhitespaceWith, DefaultReplaceWhitespaceWith);
        invokeCreateParser(actual, expected);
    }

    private void invokeCreateParser(String query, String expectedModifiedQuery) throws IOException {
        invokeCreateParser(query, expectedModifiedQuery, DefaultIgnoreCase, DefaultReplaceWhitespaceWith);
    }

    private void invokeCreateParser(
            String query, String expectedModifiedQuery, boolean ignoreCase, char replaceWhitespaceWith) throws IOException {

        AutoPhrasingQParserPlugin parser = getParserAndInvokeInit(ignoreCase, replaceWhitespaceWith);
        assertNotNull(parser);

        invokeInform(parser);

        SolrParams params = SolrParams.toSolrParams(getParams());
        SolrParams localParams = SolrParams.toSolrParams(new NamedList());

        SolrQueryRequest mockQueryRequest = Mockito.mock(SolrQueryRequest.class);
        final SolrCore mockSolrCore = PowerMockito.mock(SolrCore.class);
        QParserPlugin mockQueryPlugin = Mockito.mock(QParserPlugin.class);

        Mockito.when(mockQueryRequest.getCore()).thenReturn(mockSolrCore);
        PowerMockito.when(mockSolrCore.getQueryPlugin(DownstreamParser)).thenReturn(mockQueryPlugin);
        Mockito.when(mockQueryPlugin.createParser(
                Matchers.eq(expectedModifiedQuery), Matchers.any(SolrParams.class),
                Matchers.any(SolrParams.class), Matchers.any(SolrQueryRequest.class))).thenReturn(null);

        parser.createParser(query, params, localParams, mockQueryRequest);

        Mockito.verify(mockQueryPlugin).createParser(
                Matchers.eq(expectedModifiedQuery), Matchers.any(SolrParams.class),
                Matchers.any(SolrParams.class), Matchers.any(SolrQueryRequest.class));
    }

    public void testInform() throws Exception {
        AutoPhrasingQParserPlugin parser = getParserAndInvokeInit();

        List<String> expectedPhrases = invokeInform(parser);

        CharArraySet actualSet = parser.getPhrases();
        CharArraySet expectedSet = StopFilter.makeStopSet(Version.LUCENE_48, expectedPhrases, DefaultIgnoreCase);

        assertEquals(expectedSet.size(), actualSet.size());
        for (Object anExpected : expectedSet) {
            assertTrue(actualSet.contains(anExpected));
        }
    }

    private List<String> invokeInform(AutoPhrasingQParserPlugin parser) throws IOException {
        ResourceLoader mockResourceLoader = Mockito.mock(ResourceLoader.class);
        PowerMockito.mockStatic(WordlistLoader.class);

        List<String> expectedPhrases = getPhrases();
        Mockito.when(WordlistLoader.getLines((InputStream) Matchers.anyObject(), (Charset) Matchers.anyObject()))
                .thenReturn(expectedPhrases);

        parser.inform(mockResourceLoader);

        return expectedPhrases;
    }

    private AutoPhrasingQParserPlugin getParserAndInvokeInit() {
        return getParserAndInvokeInit(DefaultIgnoreCase, DefaultReplaceWhitespaceWith);
    }

    private AutoPhrasingQParserPlugin getParserAndInvokeInit(boolean ignoreCase, char replaceWhitespaceWith) {
        AutoPhrasingQParserPlugin parser = new AutoPhrasingQParserPlugin();
        assertNotNull(parser);

        NamedList<java.io.Serializable> params = getParams(ignoreCase, replaceWhitespaceWith);
        parser.init(params);

        return parser;
    }

    private List<String> getPhrases() {
        List<String> phrases = new ArrayList<String>();
        phrases.add("hi there");
        phrases.add("wheel chair");
        return phrases;
    }

    private NamedList<Serializable> getParams() {
        return getParams(DefaultIgnoreCase, DefaultReplaceWhitespaceWith);
    }

    private NamedList<Serializable> getParams(boolean ignoreCase, char replaceWhitespaceWith) {

        NamedList<Serializable> params = new NamedList<Serializable>();
        params.add("defType", DownstreamParser);
        params.add("replaceWhitespaceWith", replaceWhitespaceWith);
        params.add("ignoreCase", ignoreCase);
        params.add("phrases", "phrases.txt");
        params.add("includeTokens", true);

        return params;
    }
}