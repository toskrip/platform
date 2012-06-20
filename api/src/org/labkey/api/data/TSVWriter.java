/*
 * Copyright (c) 2004-2011 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.data;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.FileUtil;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class TSVWriter extends TextWriter
{
    private String _filenamePrefix = "tsv";

    protected char _chDelimiter = '\t';
    protected char _chQuote = '"';
    protected String _rowSeparator = "\n";

    protected List<String> _fileHeader = null;
    protected boolean _headerRowVisible = true;

    public static enum DELIM_ENUM {
        COMMA("comma", ',', "csv", "text/csv"),
        TAB("tab", '\t', "txt", "text/tab-separated-values")
        ;

        private DELIM_ENUM(final String text, char delim, String extension, String contentType) {
            this.text = text;
            this.delim = delim;
            this.extension = extension;
            this.contentType = contentType;
        }

        public String toString()
        {
            return this.text;
        }

        public String text;
        public char delim;
        public String extension;
        public String contentType;
    };

    public static enum QUOTE_ENUM {
        DOUBLE('"'),
        SINGLE('\''),
        NONE((char)Character.UNASSIGNED)
        ;

        private QUOTE_ENUM(final char quoteChar) {
            this.quoteChar = quoteChar;
        }

        public String toString()
        {
            return Character.toString(this.quoteChar);
        }

        public char quoteChar;
    };

    public TSVWriter()
    {
    }


    public String getFilenamePrefix()
    {
        return _filenamePrefix;
    }

    private static final Pattern badChars = Pattern.compile("[\\\\:/\\[\\]\\?\\*\\|]");

    public void setFilenamePrefix(String filenamePrefix)
    {
        _filenamePrefix = badChars.matcher(filenamePrefix).replaceAll("_");

        if (_filenamePrefix.length() > 30)
            _filenamePrefix = _filenamePrefix.substring(0, 30);
    }

    public void setDelimiterCharacter(char delimiter)
    {
        _chDelimiter = delimiter;
    }

    public void setQuoteCharacter(char quote)
    {
        _chQuote = quote;
    }

    public void setRowSeparator(String rowSeparator)
    {
        _rowSeparator = rowSeparator;
    }

    protected Pattern _escapedChars;
    protected Pattern _leadingWhitespace = Pattern.compile("^\\p{Space}.*");
    protected Pattern _trailingWhitespace = Pattern.compile(".*\\p{Space}$");

    /**
     * Quote the value if necessary.  The quoting rules are:
     * <ul>
     *   <li>Values containing leading or trailing whitespace, a newline, a carrage return, or the field separator will be quoted.
     *   <li>Values containing the quoting character will also be quoted with the quote character replaced by two quote characters.
     * </ul>
     * <p>
     * The quoting character is also known as a "text qualifier" in Excel and is usually
     * the double quote character but may be the single quote character.
     * <p>
     * Note: Excel will always quote a field if it includes comma even if it isn't the delimeter, but
     * this algorithm doesn't to avoid unnecessary quoting.
     * 
     * @param value The raw value.
     * @return The quoted value.
     */
    public String quoteValue(String value)
    {
        if (value == null)
            return "";
        
        String escaped = value;

        if (_escapedChars == null)
        {
            // NOTE: Excel always includes comma in the list of characters that will be quoted,
            // but we will only quote comma if it is the delimeter character.
            _escapedChars = Pattern.compile("[\r\n\\" + _chDelimiter + "\\" + _chQuote + "]");
        }

        if (_escapedChars.matcher(value).find() ||
                _leadingWhitespace.matcher(value).matches() ||
                _trailingWhitespace.matcher(value).matches())
        {
            StringBuffer sb = new StringBuffer(value.length() + 10);
            sb.append(_chQuote);
            int i = -1, lastMatch = 0;

            while (-1 != (i = value.indexOf(_chQuote, lastMatch)))
            {
                sb.append(value.substring(lastMatch, i));
                sb.append(_chQuote).append(_chQuote);
                lastMatch = i+1;
            }

            if (lastMatch < value.length())
                sb.append(value.substring(lastMatch));

            sb.append(_chQuote);
            escaped = sb.toString();
        }

        return escaped;
    }

    /**
     * Override to return a different content type
     * @return The content type
     */
    protected String getContentType()
    {
        return "text/tab-separated-values";
    }

    /**
     * Override to return a different filename
     * @return The filename
     */
    protected String getFilename()
    {
        return FileUtil.makeFileNameWithTimestamp(getFilenamePrefix(), "tsv");
    }

    public void setFileHeader(List<String> fileHeader)
    {
        _fileHeader = fileHeader;
    }

    public void setFileHeader(String... fileHeaders)
    {
        _fileHeader = Arrays.asList(fileHeaders);
    }

    public boolean isHeaderRowVisible()
    {
        return _headerRowVisible;
    }


    public void setHeaderRowVisible(boolean headerRowVisible)
    {
        _headerRowVisible = headerRowVisible;
    }

    @Override
    protected void write()
    {
        writeFileHeader();
        if (isHeaderRowVisible())
            writeColumnHeaders();
        writeBody();
        writeFileFooter();
    }

    public void writeFileHeader()
    {
        if (null == _fileHeader)
            return;

        for (String line : _fileHeader)
            _pw.println(line);
    }

    protected void writeFileFooter()
    {
    }

    protected void writeColumnHeaders()
    {
    }

    // CONSIDER: make abstract
    protected void writeBody()
    {
    }

    protected void writeLine(Iterable<String> values)
    {
        if (values == null)
            return;

        PrintWriter pw = getPrintWriter();
        Iterator<String> iter = values.iterator();
        while (iter.hasNext())
        {
            pw.write(quoteValue(iter.next()));
            if (iter.hasNext())
                pw.write(_chDelimiter);
        }

        pw.append(_rowSeparator);
    }

    public static class TestCase extends Assert
    {
        private static class FakeTSVWriter extends TSVWriter
        {
            protected void write()
            {
                // no-op
            }
        }

        @Test
        public void testBasic()
        {
            FakeTSVWriter w = new FakeTSVWriter();
            assertEquals("", w.quoteValue(""));
            assertEquals("a", w.quoteValue("a"));
            assertEquals("", w.quoteValue(null));
        }

        @Test
        public void testWhitespace()
        {
            FakeTSVWriter w = new FakeTSVWriter();
            assertEquals("a b", w.quoteValue("a b"));
            assertEquals("\"  ab\"", w.quoteValue("  ab"));
            assertEquals("\"ab  \"", w.quoteValue("ab  "));
            assertEquals("\"a\nb\"", w.quoteValue("a\nb"));
            assertEquals("\"a\n\rb\"", w.quoteValue("a\n\rb"));
        }

        @Test
        public void testDelimiterChar()
        {
            FakeTSVWriter w = new FakeTSVWriter();
            assertEquals("\"one\t two\t three\"", w.quoteValue("one\t two\t three"));
            //assertEquals("\"one, two, three\"", w.quoteValue("one, two, three")); // commas should always be quoted
            assertEquals("one; two; three", w.quoteValue("one; two; three"));

            w = new FakeTSVWriter();
            w.setDelimiterCharacter(';');
            assertEquals("one\ttwo\tthree", w.quoteValue("one\ttwo\tthree"));
            //assertEquals("\"one, two, three\"", w.quoteValue("one, two, three")); // commas should always be quoted
            assertEquals("\"one; two; three\"", w.quoteValue("one; two; three"));

            w = new FakeTSVWriter();
            w.setDelimiterCharacter('\\');
            assertEquals("one\ttwo\tthree", w.quoteValue("one\ttwo\tthree"));
            assertEquals("\"one\\ two\\ three\"", w.quoteValue("one\\ two\\ three"));

            w = new FakeTSVWriter();
            w.setDelimiterCharacter('(');
            assertEquals("\"one( two( three\"", w.quoteValue("one( two( three"));
        }

        @Test
        public void testQuoteChar()
        {
            FakeTSVWriter w = new FakeTSVWriter();
            assertEquals("hello", w.quoteValue("hello"));
            assertEquals("\"\"\"\"", w.quoteValue("\""));
            assertEquals("\"bob says, \"\"hello\"\"\"", w.quoteValue("bob says, \"hello\""));
            assertEquals("\"bob says; \"\"hello\"\"\"", w.quoteValue("bob says; \"hello\""));

            w = new FakeTSVWriter();
            w.setQuoteCharacter(':');
            assertEquals(":bob says:: \"hello\":", w.quoteValue("bob says: \"hello\""));
            //assertEquals(":bob says, \"hello\":", w.quoteValue("bob says, \"hello\"")); // commas should always be quoted
            assertEquals("bob says; \"hello\"", w.quoteValue("bob says; \"hello\""));
        }
    }
}
