/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.util;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.beehive.netui.pageflow.Forward;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.util.MessageResources;
import org.apache.struts.validator.Resources;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ImageURL;
import org.labkey.api.settings.TemplateResourceHandler;
import org.labkey.api.view.*;
import org.labkey.api.admin.CoreUrls;
import org.labkey.common.util.Pair;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.util.WebUtils;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.tidy.Tidy;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;


public class PageFlowUtil
{
    private static Logger _log = Logger.getLogger(PageFlowUtil.class);
    private static final String _newline = System.getProperty("line.separator");

    private static final Object[] NO_ARGS = new Object[ 0 ];

    private static final Pattern urlPattern = Pattern.compile(".*((http|https|ftp|mailto)://\\S+).*");
    private static final Pattern urlPatternStart = Pattern.compile("((http|https|ftp|mailto)://\\S+).*");

    /**
     * Default parser class.
     */
    protected static final String DEFAULT_PARSER_NAME = "org.apache.xerces.parsers.SAXParser";

    String encodeURLs(String input)
    {
        Matcher m = urlPattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        int start;
        int end = 0;
        while (m.find())
        {
            start = m.start();
            String href = m.group(1);
            if (href.endsWith(".")) href = href.substring(0, href.length() - 1);
            sb.append(input.substring(end, start));
            sb.append("<a href=\"").append(href).append("\">").append(href).append("</a>");
            end = m.end();
        }
        sb.append(input.substring(end));
        return sb.toString();
    }


    static public final String NONPRINTING_ALTCHAR = "~";
    static final String nonPrinting;
    static
    {
        StringBuffer sb = new StringBuffer();
        for (char c = 1 ; c < ' ' ; c++)
        {
            if (" \t\r\n".indexOf('c') == -1)
                sb.append(c);
        }
        nonPrinting = sb.toString();
    }

    static public String filterXML(String s)
    {
        return filter(s,false,false);
    }

    static public String filter(String s, boolean encodeSpace, boolean encodeLinks)
    {
        if (null == s || 0 == s.length())
            return "";

        int len = s.length();
        StringBuilder sb = new StringBuilder(2 * len);
        boolean newline = false;

        for (int i=0 ; i < len; ++i)
        {
            char c = s.charAt(i);

            if (!Character.isWhitespace(c))
                newline = false;
            else if ('\r' == c || '\n' == c)
                newline = true;

            switch (c)
            {
                case '&':
                    sb.append("&amp;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#039;");    // works for xml and html
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '\n':
                    if (encodeSpace)
                        sb.append("<br>\n");
                    else
                        sb.append(c);
                    break;
                case '\r':
                    break;
                case '\t':
                    if (!encodeSpace)
                        sb.append(c);
                    else if (newline)
                        sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
                    else
                        sb.append("&nbsp; &nbsp; ");
                    break;
                case ' ':
                    if (encodeSpace && newline)
                        sb.append("&nbsp;");
                    else
                        sb.append(' ');
                    break;
                case 'f':
                case 'h':
                case 'm':
                    if (encodeLinks)
                    {
                        String sub = s.substring(i);
                        if (c == 'f' && sub.startsWith("ftp://") ||
                                c == 'h' && (sub.startsWith("http://") || sub.startsWith("https://")) ||
                                c == 'm' && sub.startsWith("mailto://"))
                        {
                            Matcher m = urlPatternStart.matcher(sub);
                            if (m.find())
                            {
                                String href = m.group(1);
                                if (href.endsWith(".")) href = href.substring(0, href.length() - 1);
                                sb.append("<a href=\"").append(href).append("\">").append(href).append("</a>");
                                i += href.length() - 1;
                                break;
                            }
                        }
                    }
                    sb.append(c);
                    break;
                default:
                    if (c >= ' ')
                        sb.append(c);
                    else
                    {
                        if (c == 0x08) // backspace (e.g. xtandem output)
                            break;
                        sb.append(NONPRINTING_ALTCHAR);
                    }
                    break;
            }
        }

        return sb.toString();
    }


    public static String filter(Object o)
    {
        return filter(o == null ? null : o.toString());
    }

    /**
     * HTML encode a string
     */
    public static String filter(String s)
    {
        return filter(s, false, false);
    }


    static public String filter(String s, boolean translateWhiteSpace)
    {
        return filter(s, translateWhiteSpace, false);
    }


    public static String encodeJavascriptStringLiteral(Object value)
    {
        if (value == null)
            return "null";
        String ret = PageFlowUtil.groovyString(value.toString());
        ret = StringUtils.replace(ret, "'", "\\'");
        return ret;
    }

    /**
     * put quotes around a JavaScript string, and HTML encode that.
     */
    public static String filterQuote(Object value)
    {
        if (value == null)
            return "null";
        String ret = PageFlowUtil.filter("\"" + PageFlowUtil.groovyString(value.toString()) + "\"");
        ret = ret.replace("&#039;", "\\&#039;");
        return ret;
    }

    static private String jsString(String s, boolean forceOuterSingleQuotes)
    {
        if (s == null)
            return "''";
        if (-1 != s.indexOf('\\'))
            s = s.replaceAll("\\\\", "\\\\\\\\");
        if (-1 != s.indexOf('\n'))
            s = s.replaceAll("\\n", "\\\\n");
        if (-1 != s.indexOf('\r'))
            s = s.replaceAll("\\r", "\\\\r");
        s = s.replace("<", "\\x3C");
        s = s.replace(">", "\\x3E");
        if (-1 == s.indexOf('\''))
            return "'" + s + "'";
        if (!forceOuterSingleQuotes && -1 == s.indexOf('"'))
            return "\"" + s + "\"";
        s = s.replaceAll("'", "\\\\'");
        return "'" + s + "'";
    }


    static public String jsString(String s)
    {
        return jsString(s, false);
    }


    //used to output strings from Java in Groovy script.
    static public String groovyString(String s)
    {
        //replace single backslash
        s = s.replaceAll("\\\\", "\\\\\\\\");
        //replace double quote
        s = s.replaceAll("\"", "\\\\\"");
        return s;
    }

    @SuppressWarnings({"unchecked"})
    static Pair<String, String>[] _emptyPairArray = new Pair[0];   // Can't delare generic array

    public static Pair<String, String>[] fromQueryString(String query)
    {
        return fromQueryString(query, "UTF-8");
    }

    public static Pair<String, String>[] fromQueryString(String query, String encoding)
    {
        if (null == query || 0 == query.length())
            return _emptyPairArray;

        if (null == encoding)
            encoding = "UTF-8";

        List<Pair> parameters = new ArrayList<Pair>();
        if (query.startsWith("?"))
            query = query.substring(1);
        String[] terms = query.split("&");

        try
        {
            for (String term : terms)
            {
                if (0 == term.length())
                    continue;
                // NOTE: faster to decode all at once, just can't allow keys to have '=' char
                term = URLDecoder.decode(term, encoding);
                int ind = term.indexOf('=');
                if (ind == -1)
                    parameters.add(new Pair<String,String>(term.trim(), ""));
                else
                    parameters.add(new Pair<String,String>(term.substring(0, ind).trim(), term.substring(ind + 1).trim()));
            }
        }
        catch (UnsupportedEncodingException x)
        {
            throw new IllegalArgumentException(encoding, x);
        }

        //noinspection unchecked
        return parameters.toArray(new Pair[parameters.size()]);
    }


    public static Map<String, String> mapFromQueryString(String queryString)
    {
        Pair<String, String>[] pairs = fromQueryString(queryString);
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (Pair<String, String> p : pairs)
            m.put(p.getKey(), p.getValue());

        return m;
    }


    public static String toQueryString(Collection<? extends Map.Entry<?,?>> c)
    {
        if (null == c || c.isEmpty())
            return null;
        String strAnd = "";
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<?,?> entry : c)
        {
            sb.append(strAnd);
            Object key = entry.getKey();
            if (null == key)
                continue;
            Object v = entry.getValue();
            String value = v == null ? "" : String.valueOf(v);
            sb.append(encode(String.valueOf(key)));
            sb.append('=');
            sb.append(encode(value));
            strAnd = "&";
        }
        return sb.toString();
    }


    public static String toQueryString(PropertyValues pvs)
    {
        if (null == pvs || pvs.isEmpty())
            return null;
        String strAnd = "";
        StringBuffer sb = new StringBuffer();
        for (PropertyValue entry : pvs.getPropertyValues())
        {
            Object key = entry.getName();
            if (null == key)
                continue;
            String encKey = encode(String.valueOf(key));
            Object v = entry.getValue();
            if (v == null || v instanceof String || !v.getClass().isArray())
            {
                sb.append(strAnd);
                sb.append(encKey);
                sb.append('=');
                sb.append(encode(v==null?"":String.valueOf(v)));
                strAnd = "&";
            }
            else
            {
                Object[] a = (Object[])v;
                for (Object o : a)
                {
                    sb.append(strAnd);
                    sb.append(encKey);
                    sb.append('=');
                    sb.append(encode(o==null?"":String.valueOf(o)));
                    strAnd = "&";
                }
            }
        }
        return sb.toString();
    }


    public static <T> Map<T, T> map(T... args)
    {
        HashMap<T, T> m = new HashMap<T, T>();
        for (int i = 0; i < args.length; i += 2)
            m.put(args[i], args[i + 1]);
        return m;
    }


    public static <T> Set<T> set(T... args)
    {
        HashSet<T> s = new HashSet<T>();
        s.addAll(Arrays.asList(args));
        return s;
    }


    public static ArrayList pairs(Object... args)
    {
        ArrayList<Pair> list = new ArrayList<Pair>();
        for (int i = 0; i < args.length; i += 2)
            list.add(new Pair<Object,Object>(args[i], args[i + 1]));
        return list;
    }


    private static final Pattern pattern = Pattern.compile("\\+");


    /**
     * URL Encode string.
     * NOTE! this should be used on parts of a url, not an entire url
     */
    public static String encode(String s)
    {
        if (null == s)
            return "";
        try
        {
            return pattern.matcher(URLEncoder.encode(s, "UTF-8")).replaceAll("%20");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }


    public static String decode(String s)
    {
        try
        {
            return URLDecoder.decode(s, "UTF-8");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }

    /**
     * Encode path URL parts, preserving path separators.
     * @param path The raw path to encode.
     * @return An encoded version of the path parameter.
     */
    public static String encodePath(String path)
    {
        String[] parts = path.split("/");
        String ret = "";
        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0)
                ret += "/";
            ret += encode(parts[i]);
        }
        return ret;
    }

    public static URI redirectToURI(HttpServletRequest request, String uri)
    {
        if (null == uri)
            uri = request.getContextPath() + "/";

        // Try redirecting to the URI stashed in the session
        try
        {
            return new URI(uri);
        }
        catch (Exception x)
        {
            // That didn't work, just redirect home
            try
            {
                return new URI(request.getContextPath());
            }
            catch (Exception y)
            {
                return null;
            }
        }
    }


    // Cookie helper function -- loops through Cookie array and returns matching value (or defaultValue if not found)
    public static String getCookieValue(Cookie[] cookies, String cookieName, String defaultValue)
    {
        if (null != cookies)
            for (Cookie cookie : cookies)
            {
                if (cookieName.equals(cookie.getName()))
                    return (cookie.getValue());
            }
        return (defaultValue);
    }


    /**
     * boolean controlling whether or now we compress {@link ObjectOutputStream}s when we render them in HTML forms.
     * 
      */
    static private final boolean COMPRESS_OBJECT_STREAMS = true;
    static public String encodeObject(Object o) throws IOException
    {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStream osCompressed;
        if (COMPRESS_OBJECT_STREAMS)
        {
            osCompressed = new DeflaterOutputStream(byteArrayOutputStream);
        }
        else
        {
            osCompressed = byteArrayOutputStream;
        }
        ObjectOutputStream oos = new ObjectOutputStream(osCompressed);
        oos.writeObject(o);
        oos.close();
        osCompressed.close();
        return new String(Base64.encodeBase64(byteArrayOutputStream.toByteArray(), true));
    }


    public static Object decodeObject(String s) throws IOException
    {
        s = StringUtils.trimToNull(s);
        if (null == s)
            return null;
        
        try
        {
            byte[] buf = Base64.decodeBase64(s.getBytes());
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buf);
            InputStream isCompressed;

            if (COMPRESS_OBJECT_STREAMS)
            {
                isCompressed = new InflaterInputStream(byteArrayInputStream);
            }
            else
            {
                isCompressed = byteArrayInputStream;
            }
            ObjectInputStream ois = new ObjectInputStream(isCompressed);
            Object obj = ois.readObject();
            return obj;
        }
        catch (ClassNotFoundException x)
        {
            throw new IOException(x.getMessage());
        }
    }

    
    public static byte[] gzip(String s)
    {
        try
        {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            GZIPOutputStream zip = new GZIPOutputStream(buf);
            zip.write(s.getBytes("UTF-8"));
            zip.close();
            return buf.toByteArray();
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static ActionErrors getActionErrors(HttpServletRequest request)
    {
        return getActionErrors(request, false);
    }
    
    public static ActionErrors getActionErrors(HttpServletRequest request, boolean create)
    {
        ActionErrors errors = (ActionErrors) request.getAttribute(org.apache.struts.Globals.ERROR_KEY);
        if (create && null == errors)
        {
            errors = new ActionErrors();
            request.setAttribute(org.apache.struts.Globals.ERROR_KEY, errors);
        }
        return errors;
    }

    public static int[] toInts(Collection<String> strings)
    {
        return toInts(strings.toArray(new String[strings.size()]));
    }

    public static int[] toInts(String[] strings)
    {
        int[] result = new int[strings.length];
        for (int i = 0; i < strings.length; i++)
        {
            result[i] = Integer.parseInt(strings[i]);
        }
        return result;
    }


    // this is stupid hack to handle non-struts controllers
    public static abstract class MessageFormatter
    {
        public abstract String get(String key);
        String format(String key, String... args)
        {
            return MessageFormat.format(get(key), args);
        }
    }


    public static String getStrutsError(HttpServletRequest request, String key)
    {
        ActionErrors errors = getActionErrors(request);
        if (null == errors)
            return "";

        String header = "";
        String prefix = "";
        String suffix = "";
        String footer = "";

        MessageFormatter formatter = null;
        
        final MessageResources resources = Resources.getMessageResources(request);
        if (null != resources)
        {
            if (resources.isPresent("errors.header"))
                header = resources.getMessage("errors.header");
            prefix = resources.getMessage("errors.prefix", NO_ARGS);
            suffix = resources.getMessage("errors.suffix", NO_ARGS);
            if (resources.isPresent("errors.footer"))
                footer = resources.getMessage("errors.footer");
            formatter = new MessageFormatter()
            {
                public String get(String key)
                {
                    return resources.getMessage(key);
                }
            };
        }
        else
        {
            // HACK make this works with non-Struts controllers
            final Properties props = new Properties();
            props.put("errors.prefix","<font class=\"labkey-error\">");
            props.put("errors.suffix","</font><br>");
            props.put("Error","{0}");
            props.put("ConversionError","Could not convert {0} to correct type.");
            props.put("NullError","Field {0} cannot be null.");
            props.put("UniqueViolationError","The value of the {0} field conflicts with another value in the database. Please enter a different value.");

            try
            {
                InputStream is = PageFlowUtil.class.getClassLoader().getResourceAsStream("/messages/Validation.properties");
                if (null != is)
                    props.load(is);

                header = StringUtils.trimToEmpty((String)props.get("errors.header"));
                prefix = StringUtils.trimToEmpty((String)props.get("errors.prefix"));
                suffix = StringUtils.trimToEmpty((String)props.get("errors.suffix"));
                footer = StringUtils.trimToEmpty((String)props.get("errors.footer"));
                formatter = new MessageFormatter()
                {
                    public String get(String key)
                    {
                        return (String)props.get(key);
                    }
                };
            }
            catch (IOException x)
            {
                _log.error("unexpected error", x);
            }
        }
        if (formatter == null)
            throw new IllegalStateException("JPF does not have an error resource - its annotation should be something like @Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = \"messages.Validation\")})");

        Iterator iter = key == null ? errors.get() : errors.get(key);
        if (!iter.hasNext())
            return "";

        StringBuffer sb = new StringBuffer();
        sb.append(header);
        
        while (iter.hasNext())
        {
            ActionMessage message = (ActionMessage) iter.next();
            String msg;

            try
            {
                if (null != resources)
                {
                    if (message.getValues() == null)
                        msg = resources.getMessage(message.getKey());
                    else
                        msg = resources.getMessage(message.getKey(), message.getValues());
                }
                else
                {
                    // UNDONE: make non-struts controller work
                    if (message.getValues() == null)
                        msg = message.getKey();
                    else
                        msg = String.valueOf(message.getValues()[0]);
                }
            }
            catch (Exception x)
            {
                _log.error(x);
                msg = message.getKey();
                Object[] v = message.getValues();
                if (null != v && 0 < v.length)
                    msg += ": " + String.valueOf(v[0]);
            }

            if (null != prefix)
                sb.append(prefix);
            sb.append(filter(msg));
            if (null != suffix)
                sb.append(suffix);
        }
        sb.append(footer);
        return sb.toString();
    }


    private static String _tempPath = null;

    // Under Catalina, it seems to pick \tomcat\temp
    // On the web server under Tomcat, it seems to pick c:\Documents and Settings\ITOMCAT_EDI\Local Settings\Temp
    public static String getTempDirectory()
    {
        if (null == _tempPath)
        {
            try
            {
                File temp = File.createTempFile("deleteme", null);
                _tempPath = temp.getParent() + File.separator;
                temp.delete();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        return _tempPath;
    }


    private static MimeMap _mimeMap = new MimeMap();

    public static String getContentTypeFor(String filename)
    {
        String contentType = _mimeMap.getContentTypeFor(filename);
        if (null == contentType)
        {
            contentType = "application/octet-stream";
        }
        return contentType;
    }

    private static void prepareResponseForFile(HttpServletResponse response, Map<String, String> responseHeaders, String filename, long countOfBytes, boolean asAttachment)
    {
        String contentType = getContentTypeFor(filename);
        response.reset();
        response.setContentType(contentType);
        if (countOfBytes < Integer.MAX_VALUE)
            response.setContentLength((int) countOfBytes);
        if (asAttachment)
        {
            response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        }
        for (Map.Entry<String, String> entry : responseHeaders.entrySet())
            response.setHeader(entry.getKey(), entry.getValue());
    }

    public static void streamFile(HttpServletResponse response, File file, boolean asAttachment) throws IOException
    {
        streamFile(response, Collections.<String, String>emptyMap(), file, asAttachment);
    }

    public static void streamFile(HttpServletResponse response, String fileName, boolean asAttachment) throws IOException
    {
        streamFile(response, Collections.<String, String>emptyMap(), new File(fileName), asAttachment);
    }


    // Read the file and stream it to the browser
    public static void streamFile(HttpServletResponse response, Map<String, String> responseHeaders, File file, boolean asAttachment) throws IOException
    {
        // TODO: setHeader(modified)
        long length = file.length();

        FileInputStream s = null;

        try
        {
            // TODO: use FileUtils.copyData()
            s = new FileInputStream(file);

            prepareResponseForFile(response, responseHeaders, file.getName(), length, asAttachment);
            ServletOutputStream out = response.getOutputStream();
            FileUtil.copyData(s, out);
        }
        finally
        {
            IOUtils.closeQuietly(s);
        }
    }

    public static void streamFileBytes(HttpServletResponse response, String filename, byte[] bytes, boolean asAttachment) throws IOException
    {
        prepareResponseForFile(response, Collections.<String, String>emptyMap(), filename, bytes.length, asAttachment);
        response.getOutputStream().write(bytes);
    }

    // Fetch the contents of a text file, and return it in a String.
    public static String getFileContentsAsString(File aFile)
    {
        StringBuilder contents = new StringBuilder();
        BufferedReader input = null;

        try
        {
            input = new BufferedReader(new FileReader(aFile));
            String line;
            while ((line = input.readLine()) != null)
            {
                contents.append(line);
                contents.append(_newline);
            }
        }
        catch (FileNotFoundException e)
        {
            _log.error(e);
            contents.append("File not found");
            contents.append(_newline);
        }
        catch (IOException e)
        {
            _log.error(e);
        }
        finally
        {
            IOUtils.closeQuietly(input);
        }
        return contents.toString();
    }


    public static class Content
    {
        public Content(String s)
        {
            this(s, null, System.currentTimeMillis());
        }

        public Content(String s, byte[] e, long m)
        {
            content = s;
            encoded = e;
            modified = m;
        }

        public Object dependencies;
        public String content;
        public byte[] encoded;
        public long modified;
    }


    public static Content getViewContent(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        final StringWriter writer = new StringWriter();
        HttpServletResponse sresponse = new HttpServletResponseWrapper(response)
            {
                public PrintWriter getWriter()
                {
                    return new PrintWriter(writer);
                }
            };
        mv.getView().render(mv.getModel(), request, sresponse);
        String sheet = writer.toString();
        Content c = new Content(sheet);
        return c;
    }



    // Fetch the contents of an input stream, and return in a String.
    public static String getStreamContentsAsString(InputStream is)
    {
        StringBuilder contents = new StringBuilder();
        BufferedReader input = null;

        try
        {
            input = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = input.readLine()) != null)
            {
                contents.append(line);
                contents.append(_newline);
            }
        }
        catch (IOException e)
        {
            _log.error("getStreamContentsAsString", e);
        }
        finally
        {
            IOUtils.closeQuietly(input);
        }
        return contents.toString();
    }


    // Fetch the contents of an input stream, and return it in a list.
    public static List<String> getStreamContentsAsList(InputStream is) throws IOException
    {
        List<String> contents = new ArrayList<String>();
        BufferedReader input = new BufferedReader(new InputStreamReader(is));

        try
        {
            String line;
            while ((line = input.readLine()) != null)
                contents.add(line);
        }
        finally
        {
            IOUtils.closeQuietly(input);
        }

        return contents;
    }


    public static boolean empty(String str)
    {
        return null == str || str.trim().length() == 0;
    }


    static Pattern patternPhone = Pattern.compile("((1[\\D]?)?\\(?(\\d\\d\\d)\\)?[\\D]*)?(\\d\\d\\d)[\\D]?(\\d\\d\\d\\d)");

    public static String formatPhoneNo(String s)
    {
        s = StringUtils.trimToNull(s);
        if (null == s)
            return "";
        Matcher m = patternPhone.matcher(s);
        if (!m.find())
            return s;
        //for (int i=0 ; i<=m.groupCount() ; i++) System.err.println(i + " " + m.group(i));
        StringBuffer sb = new StringBuffer(20);
        m.appendReplacement(sb, "");
        String area = m.group(3);
        String exch = m.group(4);
        String num = m.group(5);
        if (null != area && 0 < area.length())
            sb.append("(").append(area).append(") ");
        sb.append(exch).append("-").append(num);
        m.appendTail(sb);
        return sb.toString();
    }


    // pass through for convenience
    public static String submitSrc()
    {
        return ButtonServlet.submitSrc();
    }

    public static String cancelSrc()
    {
        return ButtonServlet.cancelSrc();
    }

    public static String buttonSrc(String name)
    {
        return ButtonServlet.buttonSrc(name);
    }

    // styles should not need encoding
    public static String buttonSrc(String name, String style)
    {
        return ButtonServlet.buttonSrc(name, style);
    }

    public static String buttonImg(String name)
    {
        return "<img border=0 alt=\"" + filter(name) + "\" src=\"" + ButtonServlet.buttonSrc(name) + "\">";
    }

    public static String buttonImg(String name, String style)
    {
        return "<img border=0 id=\"nav"+filter(name)+"\" alt=\"" + filter(name) + "\" src=\"" + ButtonServlet.buttonSrc(name, style) + "\">";
    }

    /*
     * Renders a button wrapped in an &lt;a> tag.
     * Consider: is there any way to name this method in such a way as to
     * make the order of parameters unambiguous?
     */
    public static String buttonLink(String text, String href)
    {
        return buttonLink(text, href, null);
    }

    /*
     * Renders a button wrapped in an &lt;a> tag.
     * Consider: is there any way to name this method in such a way as to
     * make the order of parameters unambiguous?
     */
    public static String buttonLink(String text, String href, String onClickScript)
    {
        return "<a href=\"" + filter(href) + "\"" +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + buttonImg(text) + "</a>";
    }

    public static String buttonLink(String text, ActionURL href)
    {
        return buttonLink(text, href, null);
    }

    public static String buttonLink(String text, ActionURL href, String onClickScript)
    {
        return "<a href=\"" + filter(href) + "\"" +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + buttonImg(text) + "</a>";
    }
    
    public static String buttonLink(String text, String style, ActionURL href, String onClickScript)
    {
        style = StringUtils.defaultString(style, "default");
        return "<a href=\"" + filter(href) + "\"" +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + buttonImg(text,style) + "</a>";
    }

    public static String textLink(String text, String href, String id)
    {
        return textLink(text, href, null, id);
    }

    public static String textLink(String text, String href)
    {
        return textLink(text, href, null, null);
    }

    public static String textLink(String text, String href, String onClickScript, String id)
    {
        return "[<a href=\"" + filter(href) + "\"" +
                (id != null ? " id=\"" + id + "\"" : "") +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + text + "</a>]";
    }

    public static String textLink(String text, ActionURL url, String onClickScript, String id)
    {
        return "[<a href=\"" + filter(url) + "\"" +
                (id != null ? " id=\"" + id + "\"" : "") +
                (onClickScript != null ? " onClick=\"" + onClickScript + "\"" : "") +
                ">" + text + "</a>]";
    }

    public static String textLink(String text, ActionURL url)
    {
        return textLink(text, url.getLocalURIString(), null, null);
    }

    public static String textLink(String text, ActionURL url, String id)
    {
        return textLink(text, url.getLocalURIString(), null, id);
    }

    public static String helpPopup(String title, String helpText)
    {
        return helpPopup(title, helpText, false);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText)
    {
        return helpPopup(title, helpText, htmlHelpText, 0);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, int width)
    {
        String questionMarkHtml = "<span class=\"labkey-help-pop-up\"><sup>?</sup></span>";
        return helpPopup(title, helpText, htmlHelpText, questionMarkHtml, width);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml)
    {
        return helpPopup(title, helpText, htmlHelpText, linkHtml, 0);
    }

    public static String helpPopup(String title, String helpText, boolean htmlHelpText, String linkHtml, int width)
    {
        if (title == null && !htmlHelpText)
        {
            // use simple tooltip
            StringBuilder link = new StringBuilder();
            link.append("<a href=\"#\" tabindex=\"-1\" onClick=\"return false\" title=\"");
            link.append(filter(helpText));
            link.append("\">").append(linkHtml).append("</a>");
            return link.toString();
        }
        else
        {
            StringBuilder link = new StringBuilder();
            link.append("<a href=\"#\" tabindex=\"-1\" onClick=\"return false\" onMouseOut=\"return hideHelpDivDelay();\" onMouseOver=\"return showHelpDiv(this, ");
            link.append(filter(jsString(filter(title)), true)).append(", ");

            // The value of the javascript string literal is used to set the innerHTML of an element.  For this reason, if
            // it is text, we escape it to make it HTML.  Then, we have to escape it to turn it into a javascript string.
            // Finally, since this is script inside of an attribute, it must be HTML escaped again.
            link.append(filter(jsString(htmlHelpText ? helpText : filter(helpText, true))));
            if (width != 0)
                link.append(", ").append(filter(jsString(filter(String.valueOf(width) + "px"))));
            link.append(");\">").append(linkHtml).append("</a>");
            return link.toString();
        }
    }


    /**
     * helper for script validation
     */
    public static String convertHtmlToXml(String html, Collection<String> errors)
    {
        return tidy(html, true, errors);
    }


    static Pattern scriptPattern = Pattern.compile("(<script.*?>)(.*?)(</script>)", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);        

    public static Document convertHtmlToDocument(final String html, final Collection<String> errors)
    {
        Tidy tidy = new Tidy();
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8");
        tidy.setOutputEncoding("UTF-8");
        tidy.setDropEmptyParas(false); // radeox wikis use <p/> -- don't remove them
        tidy.setTrimEmptyElements(false); // keeps tidy from converting <p/> to <br><br>

        // TIDY does not property parse the contents of script tags!
        // see bug 5007
        // CONSIDER: fix jtidy see ParserImpl$ParseScript
        Map<String,String> scriptMap = new HashMap<String,String>();
        StringBuffer stripped = new StringBuffer(html.length());
        Matcher scriptMatcher = scriptPattern.matcher(html);
        int unique = html.hashCode();
        int count = 0;

        while (scriptMatcher.find())
        {
            count++;
            String key = "{{{" + unique + ":::" + count + "}}}";
            String match = scriptMatcher.group(2);
            scriptMap.put(key,match);
            scriptMatcher.appendReplacement(stripped, "$1" + key + "$3");
        }
        scriptMatcher.appendTail(stripped);

        StringWriter err = new StringWriter();
        try
        {
            // parse wants to use streams
            tidy.setErrout(new PrintWriter(err));
            Document doc = tidy.parseDOM(new ByteArrayInputStream(stripped.toString().getBytes("UTF-8")), null);

            // fix up scripts
            if (null != doc && null != doc.getDocumentElement())
            {
                NodeList nl = doc.getDocumentElement().getElementsByTagName("script");
                for (int i=0 ; i<nl.getLength() ; i++)
                {
                    Node script = nl.item(i);
                    NodeList childNodes = script.getChildNodes();
                    if (childNodes.getLength() != 1)
                        continue;
                    Node child = childNodes.item(0);
                    if (!(child instanceof CharacterData))
                        continue;
                    String contents = ((CharacterData)child).getData();
                    String replace = scriptMap.get(contents);
                    if (null == replace)
                        continue;
                    doc.createTextNode(replace);
                    script.removeChild(childNodes.item(0));
                    script.appendChild(doc.createTextNode(replace));
                }
            }

            tidy.getErrout().close();
            for (String error : err.toString().split("\n"))
            {
                if (error.contains("Error:"))
                    errors.add(error.trim());
            }
            return doc;
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }


    public static String convertNodeToHtml(Node node)
    {
        try
        {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.METHOD, "html");
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            t.transform(new DOMSource(node), new StreamResult(out));
            out.close();

            String nodeHtml = new String(out.toByteArray(), "UTF-8").trim();
            return nodeHtml;
        }
        catch (TransformerException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    public static String tidy(final String html, boolean asXML, final Collection<String> errors)
    {
        Tidy tidy = new Tidy();
        if (asXML)
            tidy.setXHTML(true);
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8"); // utf8
        tidy.setOutputEncoding("UTF-8"); // utf8
        tidy.setDropEmptyParas(false); // allow <p/> in html wiki pages

        StringWriter err = new StringWriter();

        try
        {
            // parse want's to use streams
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            tidy.setErrout(new PrintWriter(err));
            tidy.parse(new ByteArrayInputStream(html.getBytes("UTF-8")), out);
            tidy.getErrout().close();
            for (String error : err.toString().split("\n"))
            {
                if (error.contains("Error:"))
                    errors.add(error.trim());
            }
            return new String(out.toByteArray(), "UTF-8");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static String tidyXML(final String xml, final Collection<String> errors)
    {
        Tidy tidy = new Tidy();
        tidy.setXmlOut(true);
        tidy.setXmlTags(true);
        tidy.setShowWarnings(false);
        tidy.setIndentContent(false);
        tidy.setSmartIndent(false);
        tidy.setInputEncoding("UTF-8"); // utf8
        tidy.setOutputEncoding("UTF-8"); // utf8

        StringWriter err = new StringWriter();

        try
        {
            // parse want's to use streams
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            tidy.setErrout(new PrintWriter(err));
            tidy.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")), out);
            tidy.getErrout().close();
            for (String error : err.toString().split("\n"))
            {
                if (error.contains("Error:"))
                    errors.add(error.trim());
            }
            return new String(out.toByteArray(), "UTF-8");
        }
        catch (UnsupportedEncodingException x)
        {
            throw new RuntimeException(x);
        }
    }


    private static void parserSetFeature(XMLReader parser, String feature, boolean b)
    {
        try
        {
            parser.setFeature(feature, b);
        }
        catch (SAXNotSupportedException e)
        {
            _log.error("parserSetFeature", e);
        }
        catch (SAXNotRecognizedException e)
        {
            _log.error("parserSetFeature", e);
        }
    }

    public static String getStandardIncludes(Container c)
    {
        StringBuilder sb = getFaviconIncludes(c);
        sb.append(getStylesheetIncludes(c, false));
        return sb.toString();
    }

    public static StringBuilder getFaviconIncludes(Container c)
    {
        StringBuilder sb = new StringBuilder();

        ImageURL faviconURL = TemplateResourceHandler.FAVICON.getURL(c);

        sb.append("    <link rel=\"shortcut icon\" href=\"");
        sb.append(PageFlowUtil.filter(faviconURL));
        sb.append("\" />\n");

        sb.append("    <link rel=\"icon\" href=\"");
        sb.append(PageFlowUtil.filter(faviconURL));
        sb.append("\" />\n");

        return sb;
    }

    public static StringBuilder getStylesheetIncludes(Container c, boolean email)
    {
        StringBuilder sb = new StringBuilder();

        ImageURL stylesheetURL = new ImageURL("stylesheet.css", ContainerManager.getRoot());

        sb.append("    <link href=\"");
        sb.append(PageFlowUtil.filter(stylesheetURL));
        sb.append("\" type=\"text/css\" rel=\"stylesheet\"/>\n");

        if (!email)
        {
            ImageURL printStyleURL = new ImageURL("printStyle.css", ContainerManager.getRoot());
            sb.append("    <link href=\"");
            sb.append(PageFlowUtil.filter(printStyleURL));
            sb.append("\" type=\"text/css\" rel=\"stylesheet\" media=\"print\"/>\n");
        }

        CoreUrls coreUrls = urlProvider(CoreUrls.class);

        sb.append("    <link href=\"");
        sb.append(PageFlowUtil.filter(coreUrls.getThemeStylesheetURL()));
        sb.append("\" type=\"text/css\" rel=\"stylesheet\"/>\n");

        ActionURL rootCustomStylesheetURL = coreUrls.getCustomStylesheetURL();

        if (null != rootCustomStylesheetURL)
        {
            sb.append("    <link href=\"");
            sb.append(PageFlowUtil.filter(rootCustomStylesheetURL));
            sb.append("\" type=\"text/css\" rel=\"stylesheet\"/>\n");
        }

        if (!c.isRoot())
        {
            ActionURL containerThemeStylesheetURL = coreUrls.getThemeStylesheetURL(c);

            if (null != containerThemeStylesheetURL)
            {
                sb.append("    <link href=\"");
                sb.append(PageFlowUtil.filter(containerThemeStylesheetURL));
                sb.append("\" type=\"text/css\" rel=\"stylesheet\"/>\n");
            }

            ActionURL containerCustomStylesheetURL = coreUrls.getCustomStylesheetURL(c);

            if (null != containerCustomStylesheetURL)
            {
                sb.append("    <link href=\"");
                sb.append(PageFlowUtil.filter(containerCustomStylesheetURL));
                sb.append("\" type=\"text/css\" rel=\"stylesheet\"/>\n");
            }
        }

        return sb;
    }


    /** use this version if you don't care which errors are html parsing errors and which are safety warnings */
    public static String validateHtml(String html, Collection<String> errors, boolean scriptAsErrors)
    {
        return validateHtml(html, errors, scriptAsErrors ? null : errors);
    }


    /** validate an html fragment */
    public static String validateHtml(String html, Collection<String> errors, Collection<String> scriptWarnings)
    {
        if (errors.size() > 0 || (null != scriptWarnings && scriptWarnings.size() > 0))
            throw new IllegalArgumentException("empty errors collection expected");

        if (StringUtils.trimToEmpty(html).length() == 0)
            return "";

        // UNDONE: use convertHtmlToDocument() instead of tidy() to avoid double parsing
        String xml = tidy(html, true, errors);
        if (errors.size() > 0)
            return null;

        if (null != scriptWarnings)
        {
            try
            {
                XMLReader parser = XMLReaderFactory.createXMLReader(DEFAULT_PARSER_NAME);
                parserSetFeature(parser, "http://xml.org/sax/features/namespaces", false);
                parserSetFeature(parser, "http://xml.org/sax/features/namespace-prefixes", false);
                parserSetFeature(parser, "http://xml.org/sax/features/validation", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/dynamic", false);
                parserSetFeature(parser, "http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                parserSetFeature(parser, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/schema", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/schema-full-checking", false);
                parserSetFeature(parser, "http://apache.org/xml/features/validation/dynamic", false);
                parserSetFeature(parser, "http://apache.org/xml/features/continue-after-fatal-error", false);

                parser.setContentHandler(new ValidateHandler(scriptWarnings));
                parser.parse(new InputSource(new StringReader(xml)));
            }
            catch (UnsupportedEncodingException e)
            {
                _log.error(e.getMessage(), e);
                errors.add(e.getMessage());
            }
            catch (IOException e)
            {
                _log.error(e.getMessage(), e);
                errors.add(e.getMessage());
            }
            catch (SAXException e)
            {
                _log.error(e.getMessage(), e);
                errors.add(e.getMessage());
            }
        }

        if (errors.size() > 0 || (null != scriptWarnings && scriptWarnings.size() > 0))
            return null;
        
        // let's return html not xhtml
        String tidy = tidy(html, false, errors);
        //FIX: 4528: old code searched for "<body>" but the body element can have attributes
        //and Word includes some when saving as HTML (even Filtered HTML).
        int endOpenBodyIndex = tidy.indexOf("<body");
        int beginCloseBodyIndex = tidy.indexOf("</body>");
        assert endOpenBodyIndex != -1 && beginCloseBodyIndex != -1: "Tidied HTML did not include a body element!";
        endOpenBodyIndex = tidy.indexOf('>', endOpenBodyIndex);
        assert endOpenBodyIndex != -1 : "Could not find closing > of open body element!";

        tidy = tidy.substring(endOpenBodyIndex + 1, beginCloseBodyIndex).trim();
        return tidy;
    }



    static Integer serverHash = null;
    
    public static String jsInitObject()
    {
        String contextPath = AppProps.getInstance().getContextPath();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("contextPath:'").append(contextPath).append("'");
        sb.append(",");
        sb.append("imagePath:'").append(contextPath).append("/_images'");
        sb.append(",");
        sb.append("devMode:").append(AppProps.getInstance().isDevMode()?"true":"false");
        sb.append(",");
        if (null == serverHash)
            serverHash = 0x7fffffff & AppProps.getInstance().getServerSessionGUID().hashCode();
        sb.append("hash:'").append(serverHash).append("'");
        sb.append(",");

        //TODO: these should be passed in by callers
        ViewContext context = HttpView.currentView().getViewContext();
        Container container = context.getContainer();
        User user = HttpView.currentView().getViewContext().getUser();
        sb.append("user:{id:").append(user.getUserId());
        sb.append(",displayName:").append(PageFlowUtil.jsString(user.getDisplayName(context)));
        sb.append(",email:").append(PageFlowUtil.jsString(user.getEmail()));
        sb.append(",canInsert:").append(container.hasPermission(user, ACL.PERM_INSERT) ? "true" : "false");
        sb.append(",canUpdate:").append(container.hasPermission(user, ACL.PERM_UPDATE) ? "true" : "false");
        sb.append(",canUpdateOwn:").append(container.hasPermission(user, ACL.PERM_UPDATEOWN) ? "true" : "false");
        sb.append(",canDelete:").append(container.hasPermission(user, ACL.PERM_DELETE) ? "true" : "false");
        sb.append(",canDeleteOwn:").append(container.hasPermission(user, ACL.PERM_DELETEOWN) ? "true" : "false");
        sb.append(",isAdmin:").append(container.hasPermission(user, ACL.PERM_ADMIN) ? "true" : "false");
        sb.append("}"); //end user object

        sb.append("}"); //end config
        return sb.toString();
    }


    private static class ValidateHandler extends org.xml.sax.helpers.DefaultHandler
    {
        static HashSet<String> _illegalElements = new HashSet<String>();

        static
        {
            _illegalElements.add("link");
            _illegalElements.add("style");
            _illegalElements.add("script");
            _illegalElements.add("object");
            _illegalElements.add("applet");
            _illegalElements.add("form");
            _illegalElements.add("input");
            _illegalElements.add("button");
            _illegalElements.add("frame");
            _illegalElements.add("frameset");
            _illegalElements.add("iframe");
            _illegalElements.add("embed");
            _illegalElements.add("plaintext");
        }

        static HashSet<String> _illegalAttributes = new HashSet<String>();

        Collection<String> _errors;
        HashSet<String> _reported = new HashSet<String>();


        ValidateHandler(Collection<String> errors)
        {
            _errors = errors;
        }


        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
        {
            String e = qName.toLowerCase();
            if ((e.startsWith("?") || _illegalElements.contains(e)) && !_reported.contains(e))
            {
                _reported.add(e);
                _errors.add("Illegal element <" + qName + ">. For permissions to use this element, contact your system administrator.");
            }

            for (int i = 0; i < attributes.getLength(); i++)
            {
                String a = attributes.getQName(i).toLowerCase();
                String value = attributes.getValue(i).toLowerCase();

                if ((a.startsWith("on") || a.startsWith("behavior")) && !_reported.contains(a))
                {
                    _reported.add(a);
                    _errors.add("Illegal attribute '" + attributes.getQName(i) + "' on element <" + qName + ">.");
                }
                if ("href".equals(a))
                {
                    if (value.indexOf("script") != -1 && value.indexOf("script") < value.indexOf(":") && !_reported.contains("href"))
                    {
                        _reported.add("href");
                        _errors.add("Script is not allowed in 'href' attribute on element <" + qName + ">.");
                    }
                }
                if ("style".equals(a))
                {
                    if ((value.indexOf("behavior") != -1 || value.indexOf("url") != -1 || value.indexOf("expression") != -1) && !_reported.contains("style"))
                    {
                        _reported.add("style");
                        _errors.add("Style attribute cannot contain behaviors, expresssions, or urls. Error on element <" + qName + ">.");
                    }
                }
            }
        }

        @Override
        public void warning(SAXParseException e) throws SAXException
        {
        }

        @Override
        public void error(SAXParseException e) throws SAXException
        {
            _errors.add(e.getMessage());
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException
        {
            _errors.add(e.getMessage());
        }
    }


    //
    // TestCase
    //


    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void testPhone()
        {
            assertEquals(formatPhoneNo("5551212"), "555-1212");
            assertEquals(formatPhoneNo("2065551212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("12065551212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("206.555.1212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("1-206) 555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("1-206) 555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("1(206) 555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("1 (206)555.1212"), "(206) 555-1212");
            assertEquals(formatPhoneNo("(206)-555.1212  "), "(206) 555-1212");
            assertEquals(formatPhoneNo("work (206)555.1212"), "work (206) 555-1212");
            assertEquals(formatPhoneNo("206.555.1212 x0001"), "(206) 555-1212 x0001");
        }


        public void testFilter()
        {
            assertEquals(filter("this is a test"), "this is a test");
            assertEquals(filter("<this is a test"), "&lt;this is a test");
            assertEquals(filter("this is a test<"), "this is a test&lt;");
            assertEquals(filter("'t'&his is a test\""), "'t'&amp;his is a test&quot;");
            assertEquals(filter("<>\"&"), "&lt;&gt;&quot;&amp;");
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }


    public static Forward sendAjaxCompletions(HttpServletResponse response, List<AjaxCompletion> completions) throws IOException
    {
        response.setContentType("text/xml");
        response.setHeader("Cache-Control", "no-cache");
        Writer writer = response.getWriter();
        writer.write("<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>\n");
        writer.write("<completions>");
        for (AjaxCompletion completion : completions)
        {
            writer.write("<completion>\n");
            writer.write("    <display>" + filter(completion.getKey()) + "</display>");
            writer.write("    <insert>" + filter(completion.getValue()) + "</insert>");
            writer.write("</completion>\n");
        }
        writer.write("</completions>");
        return null;
    }


    // Compares two objects even if they're null.
    public static boolean nullSafeEquals(Object o1, Object o2)
    {
        if (null == o1)
            return null == o2;

        return o1.equals(o2);
    }



    //
    //  From PFUtil
    //

    @Deprecated
    @SuppressWarnings("deprecation")
    static public ActionURL urlFor(Enum action, Container container)
    {
        String pageFlowName = ModuleLoader.getInstance().getPageFlowForPackage(action.getClass().getPackage());
        return new ActionURL(pageFlowName, action.toString(), container.getPath());
    }

    /**
     * Returns a specified <code>UrlProvider</code> interface implementation, for use
     * in writing URLs implemented in other modules.
     *
     * @param inter interface extending UrlProvider
     * @return an implementation of the interface.
     */
    static public <P extends UrlProvider> P urlProvider(Class<P> inter)
    {
        return ModuleLoader.getInstance().getUrlProvider(inter);
    }

    static public String helpTopic(Class<? extends Controller> action)
    {
        return SpringActionController.getPageFlowName(action) + "/" + SpringActionController.getActionName(action);
    }

    static private String h(Object o)
    {
        return PageFlowUtil.filter(o);
    }

    static public <T> String strSelect(String selectName, Map<T,String> map, T current)
    {
        return strSelect(selectName, map.keySet(), map.values(), current);
    }

    static public String strSelect(String selectName, Collection<?> values, Collection<String> labels, Object current)
    {
        if (values.size() != labels.size())
            throw new IllegalArgumentException();
        StringBuilder ret = new StringBuilder();
        ret.append("<select name=\"");
        ret.append(h(selectName));
        ret.append("\">");
        boolean found = false;
        Iterator itValue;
        Iterator<String> itLabel;
        for (itValue  = values.iterator(), itLabel = labels.iterator();
             itValue.hasNext() && itLabel.hasNext();)
        {
            Object value = itValue.next();
            String label = itLabel.next();
            boolean selected = !found && ObjectUtils.equals(current, value);
            ret.append("\n<option value=\"");
            ret.append(h(value));
            ret.append("\"");
            if (selected)
            {
                ret.append(" SELECTED");
                found = true;
            }
            ret.append(">");
            ret.append(h(label));
            ret.append("</option>");
        }
        ret.append("</select>");
        return ret.toString();
    }

    static public void close(Closeable closeable)
    {
        if (closeable == null)
            return;
        try
        {
            closeable.close();
        }
        catch (IOException e)
        {
            _log.error("Error in close", e);
        }
    }

    static public String getResourceAsString(Class clazz, String resource)
    {
        InputStream is = null;
        try
        {
            is = clazz.getResourceAsStream(resource);
            if (is == null)
                return null;
            return PageFlowUtil.getStreamContentsAsString(is);
        }
        finally
        {
            close(is);
        }
    }

    static public String _gif()
    {
        return _gif(1, 1);
    }

    static public String _gif(int height, int width)
    {
        StringBuilder ret = new StringBuilder();
        ret.append("<img src=\"");
        ret.append(AppProps.getInstance().getContextPath());
        ret.append("/_.gif\" height=\"");
        ret.append(height);
        ret.append("\" width=\"");
        ret.append(width);
        ret.append("\">");
        return ret.toString();
    }

    static public String strCheckbox(String name, boolean checked)
    {
        return strCheckbox(name, null, checked);
    }
    
    static public String strCheckbox(String name, String value, boolean checked)
    {
        StringBuilder out = new StringBuilder();
        String htmlName = h(name);
        
        out.append("<input type=\"checkbox\" name=\"");
        out.append(htmlName);
        out.append("\"");
        if (null != value)
        {
            out.append(" value=\"");
            out.append(h(value));
            out.append("\"");
        }
        if (checked)
        {
            out.append(" checked");
        }
        out.append(">");
        out.append("<input type=\"hidden\" name=\"");
        out.append(SpringActionController.FIELD_MARKER);
        out.append(htmlName);
        out.append("\">");
        return out.toString();
    }


    /** CONSOLIDATE ALL .lastFilter handling **/

    public static void saveLastFilter()
    {
        ViewContext context = HttpView.getRootContext();
        saveLastFilter(context, context.getActionURL(), "");
    }


    // scope is not fully supported
    public static void saveLastFilter(ViewContext context, ActionURL url, String scope)
    {
        boolean lastFilter = ColumnInfo.booleanFromString(url.getParameter(scope + DataRegion.LAST_FILTER_PARAM));
        if (lastFilter)
            return;
        ActionURL clone = url.clone();
        clone.deleteParameter(scope + DataRegion.LAST_FILTER_PARAM);
        context.getRequest().getSession().setAttribute(url.getPath() + "#" + scope + DataRegion.LAST_FILTER_PARAM, clone);
    }

    public static ActionURL getLastFilter(ViewContext context, ActionURL url)
    {
        ActionURL ret = (ActionURL) context.getSession().getAttribute(url.getPath() + "#" + DataRegion.LAST_FILTER_PARAM);
        return ret != null ? ret.clone() : url.clone();
    }

    public static ActionURL addLastFilterParameter(ActionURL url)
    {
        return url.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
    }

    
    public static ActionURL addLastFilterParameter(ActionURL url, String scope)
    {
        return url.addParameter(scope + DataRegion.LAST_FILTER_PARAM, "true");
    }

    public static String getSessionId(HttpServletRequest request)
    {
        return WebUtils.getSessionId(request);
    }
}
