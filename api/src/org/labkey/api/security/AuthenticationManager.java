/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.security;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.*;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.RequestAuthenticationProvider;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.WriteableAppProps;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: Oct 10, 2007
 * Time: 6:53:03 PM
 */
public class AuthenticationManager
{
    // All registered authentication providers (DbLogin, LDAP, SSO, etc.)
    private static List<AuthenticationProvider> _allProviders = new ArrayList<AuthenticationProvider>();
    private static List<AuthenticationProvider> _activeProviders = null;
    // Map of user id to login provider.  This is needed to handle clean up on logout.
    private static Map<Integer, AuthenticationProvider> _userProviders = new HashMap<Integer, AuthenticationProvider>();
    private static LoginURLFactory _loginURLFactory = null;
    private static ActionURL _logoutURL = null;

    private static final Logger _log = Logger.getLogger(AuthenticationManager.class);
    private static Map<String, LinkFactory> _linkFactories = new HashMap<String, LinkFactory>();
    public static final String HEADER_LOGO_PREFIX = "auth_header_logo_";
    public static final String LOGIN_PAGE_LOGO_PREFIX = "auth_login_page_logo_";
    public enum Priority { High, Low }

    // TODO: Replace this with a generic domain-claiming mechanism
    public static String _ldapDomain = "";

    public static String getLdapDomain()
    {
        return _ldapDomain;
    }

    public static void setLdapDomain(String ldapDomain)
    {
        _ldapDomain = ldapDomain;
    }

    public interface LoginURLFactory
    {
        ActionURL getURL(ActionURL returnURL);
        ActionURL getURL(String returnURL);
    }


    public static void initialize()
    {
        // Load active providers and authentication logos.  Each active provider is initialized at load time. 
        loadProperties();
    }


    private static LoginURLFactory getLoginURLFactory()
    {
        if (null == _loginURLFactory)
            throw new IllegalArgumentException("Login URL factory has not been set");

        return _loginURLFactory;
    }


    public static ActionURL getLoginURL(ActionURL returnURL)
    {
        if (null == returnURL)
            returnURL = AppProps.getInstance().getHomePageActionURL();

        return getLoginURLFactory().getURL(returnURL);
    }

    /**
     * Additional login url method to handle non-action url strings
     */
    public static ActionURL getLoginURL(String returnURL)
    {
        return getLoginURLFactory().getURL(returnURL);
    }


    public static void setLoginURLFactory(LoginURLFactory loginURLFactory)
    {
        if (null != _loginURLFactory)
            throw new IllegalArgumentException("Login URL factory has already been set");

        _loginURLFactory = loginURLFactory;
    }


    public static ActionURL getLogoutURL()
    {
        if (null == _logoutURL)
            throw new IllegalArgumentException("Logout URL has not been set");

        return _logoutURL;
    }


    public static void setLogoutURL(ActionURL logoutURL)
    {
        if (null != _logoutURL)
            throw new IllegalArgumentException("Logout URL has already been set");

        _logoutURL = logoutURL;
    }


    public static LinkFactory getLinkFactory(String providerName)
    {
        return _linkFactories.get(providerName);
    }


    public static String getHeaderLogoHtml(ActionURL currentURL)
    {
        return getAuthLogoHtml(currentURL, HEADER_LOGO_PREFIX);
    }


    public static String getLoginPageLogoHtml(ActionURL currentURL)
    {
        return getAuthLogoHtml(currentURL, LOGIN_PAGE_LOGO_PREFIX);
    }


    private static String getAuthLogoHtml(ActionURL currentUrl, String prefix)
    {
        if (_linkFactories.size() == 0)
            return null;

        StringBuilder html = new StringBuilder();

        for (LinkFactory factory : _linkFactories.values())
        {
            String link = factory.getLink(currentUrl, prefix);

            if (null != link)
            {
                if (html.length() > 0)
                    html.append("&nbsp;");

                html.append(link);
            }
        }

        if (html.length() > 0)
            return html.toString();
        else
            return null;
    }


    public static void registerProvider(AuthenticationProvider authProvider, Priority priority)
    {
        if (Priority.High == priority)
            _allProviders.add(0, authProvider);
        else
            _allProviders.add(authProvider);
    }


    public static List<AuthenticationProvider> getActiveProviders()
    {
        assert (null != _activeProviders);

        return _activeProviders;
    }


    public static void enableProvider(String name)
    {
        AuthenticationProvider provider = getProvider(name);
        try
        {
            provider.activate();
        }
        catch (Exception e)
        {
            _log.error("Can't initialize provider " + provider.getName(), e);
        }
        _activeProviders.add(provider);

        saveActiveProviders();
    }


    public static void disableProvider(String name) throws Exception
    {
        AuthenticationProvider provider = getProvider(name);
        provider.deactivate();
        _activeProviders.remove(provider);

        saveActiveProviders();
    }


    private static AuthenticationProvider getProvider(String name)
    {
        for (AuthenticationProvider provider : _allProviders)
            if (provider.getName().equals(name))
                return provider;

        return null;
    }


    private static final String AUTHENTICATION_SET = "Authentication";
    private static final String PROVIDERS_KEY = "Authentication";
    private static final String PROP_SEPARATOR = ":";

    public static void saveActiveProviders()
    {
        StringBuilder sb = new StringBuilder();
        String sep = "";

        for (AuthenticationProvider provider : _activeProviders)
        {
            sb.append(sep);
            sb.append(provider.getName());
            sep = PROP_SEPARATOR;
        }

        Map<String, String> props = PropertyManager.getWritableProperties(AUTHENTICATION_SET, true);
        props.put(PROVIDERS_KEY, sb.toString());
        PropertyManager.saveProperties(props);
        loadProperties();
    }


    private static final String AUTH_LOGO_URL_SET = "AuthenticationLogoUrls";

    private static void saveAuthLogoURL(String name, String url)
    {
        Map<String, String> props = PropertyManager.getWritableProperties(AUTH_LOGO_URL_SET, true);
        props.put(name, url);
        PropertyManager.saveProperties(props);
    }


    private static Map<String, String> getAuthLogoURLs()
    {
        return PropertyManager.getProperties(AUTH_LOGO_URL_SET, true);
    }


    private static void loadProperties()
    {
        Map<String, String> props = PropertyManager.getProperties(AUTHENTICATION_SET, true);
        String activeProviderProp = props.get(PROVIDERS_KEY);
        List<String> activeNames = Arrays.asList(null != activeProviderProp ? activeProviderProp.split(PROP_SEPARATOR) : new String[0]);
        List<AuthenticationProvider> activeProviders = new ArrayList<AuthenticationProvider>(_allProviders.size());

        // For now, auth providers are always handled in order of registration: LDAP, OpenSSO, DB.  TODO: Provide admin with mechanism for ordering

        // Add all permanent & active providers to the activeProviders list
        for (AuthenticationProvider provider : _allProviders)
            if (provider.isPermanent() || activeNames.contains(provider.getName()))
                addProvider(activeProviders, provider);

        props = getAuthLogoURLs();
        Map<String, LinkFactory> factories = new HashMap<String, LinkFactory>();

        for (String key : props.keySet())
            if (activeProviders.contains(getProvider(key)))
                factories.put(key, new LinkFactory(props.get(key), key));

        _activeProviders = activeProviders;
        _linkFactories = factories;
    }


    private static void addProvider(List<AuthenticationProvider> providers, AuthenticationProvider provider)
    {
        try
        {
            provider.activate();
            providers.add(provider);
        }
        catch (Exception e)
        {
            _log.error("Couldn't initialize provider", e);
        }
    }


    public static User authenticate(HttpServletRequest request, HttpServletResponse response, String id, String password) throws ValidEmail.InvalidEmailException
    {
        ValidEmail email = null;

        for (AuthenticationProvider authProvider : getActiveProviders())
        {
            if (authProvider instanceof LoginFormAuthenticationProvider)
            {
                if (areNotBlank(id, password))
                    email = ((LoginFormAuthenticationProvider)authProvider).authenticate(id, password);
            }
            else
            {
                if (areNotNull(request, response))
                {
                    try
                    {
                        email = ((RequestAuthenticationProvider)authProvider).authenticate(request, response);
                    }
                    catch (RedirectException e)
                    {
                        throw new RuntimeException(e);  // Some authentication provider has seen a hint and chosen to redirect
                    }
                }
            }

            if (email != null)
            {
                User user = SecurityManager.createUserIfNecessary(email);
                _userProviders.put(user.getUserId(), authProvider);
                AuditLogService.get().addEvent(user, null, UserManager.USER_AUDIT_EVENT, user.getUserId(),
                        "User: " + email + " logged in successfully.");
                return user;
            }
        }

        return null;
    }


    // Attempts to authenticate using only LoginFormAuthenticationProviders (e.g., DbLogin, LDAP).  This is for the case
    //  where you have an id & password in hand (from a post or get) and want to ignore SSO and other delegated
    //  authentication mechanisms that rely on cookies, browser redirects, etc.
    public static User authenticate(String id, String password) throws ValidEmail.InvalidEmailException
    {
        return authenticate(null, null, id, password);
    }


    public static void logout(User user, HttpServletRequest request)
    {
        AuthenticationProvider provider = _userProviders.get(user.getUserId());

        if (null != provider)
            provider.logout(request);

        AuditLogService.get().addEvent(user, null, UserManager.USER_AUDIT_EVENT, user.getUserId(),
                "User: " + user.getEmail() + " logged out.");
    }


    private static boolean areNotBlank(String id, String password)
    {
        return StringUtils.isNotBlank(id) && StringUtils.isNotBlank(password);
    }


    private static boolean areNotNull(HttpServletRequest request, HttpServletResponse response)
    {
        return null != request && null != response;
    }


    public static interface URLFactory
    {
        ActionURL getActionURL(AuthenticationProvider provider);
    }


    public static boolean isActive(String providerName)
    {
        AuthenticationProvider provider = getProvider(providerName);

        return null != provider && isActive(provider);
    }


    private static boolean isActive(AuthenticationProvider authProvider)
    {
        return getActiveProviders().contains(authProvider);
    }


    public static HttpView getConfigurationView(URLFactory enable, URLFactory disable)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<table>\n");
        sb.append("<tr><td colspan=\"4\">These are the installed authentication providers:<br><br></td></tr>\n");

        for (AuthenticationProvider authProvider : _allProviders)
        {
            sb.append("<tr><td>").append(PageFlowUtil.filter(authProvider.getName())).append("</td>");

            if (authProvider.isPermanent())
            {
                sb.append("<td>&nbsp;</td>");
            }
            else
            {
                if (isActive(authProvider))
                {
                    sb.append("<td>[<a href=\"");
                    sb.append(disable.getActionURL(authProvider).getEncodedLocalURIString());
                    sb.append("\">");
                    sb.append("disable");
                    sb.append("</a>]</td>");
                }
                else
                {
                    sb.append("<td>[<a href=\"");
                    sb.append(enable.getActionURL(authProvider).getEncodedLocalURIString());
                    sb.append("\">");
                    sb.append("enable");
                    sb.append("</a>]</td>");
                }
            }

            ActionURL url = authProvider.getConfigurationLink();

            if (null == url)
            {
                sb.append("<td>&nbsp;</td>");
            }
            else
            {
                sb.append("<td>[<a href=\"");
                sb.append(url.getEncodedLocalURIString());
                sb.append("\">");
                sb.append("configure");
                sb.append("</a>]</td>");
            }

            sb.append("<td>");
            sb.append(authProvider.getDescription());
            sb.append("</td>");            

            sb.append("</tr>\n");
        }

        sb.append("<tr><td colspan=\"4\">&nbsp;</td></tr>");
        sb.append("<tr><td colspan=\"4\">");
        sb.append(PageFlowUtil.buttonLink("Done", PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL()));
        sb.append("</td></tr></table>\n");

        return new HtmlView(sb.toString());
    }


    public abstract static class PickAuthLogoAction extends FormViewAction<AuthLogoForm>
    {
        abstract protected String getProviderName();
        abstract protected ActionURL getReturnURL();

        public void validateCommand(AuthLogoForm target, Errors errors)
        {
        }

        public ModelAndView getView(AuthLogoForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<AuthLogoBean>("/org/labkey/core/login/pickAuthLogo.jsp", new AuthLogoBean(getProviderName(), getReturnURL(), reshow));
        }

        public boolean handlePost(AuthLogoForm form, BindException errors) throws Exception
        {
            Map<String, MultipartFile> fileMap = getFileMap();

            boolean changedLogos = deleteLogos(form);
            changedLogos |= handleLogo(fileMap, HEADER_LOGO_PREFIX);
            changedLogos |= handleLogo(fileMap, LOGIN_PAGE_LOGO_PREFIX);

            // If user changed one or both logos then...
            if (changedLogos)
            {
                // Clear the image cache so the web server sends the new logo
                AttachmentCache.clearAuthLogoCache();
                // Bump the look & feel revision to force browsers to retrieve new logo
                WriteableAppProps.incrementLookAndFeelRevisionAndSave();
            }

            saveAuthLogoURL(getProviderName(), form.getUrl());
            loadProperties();

            return false;  // Always reshow the page so user can view updates.  After post, second button will change to "Done".
        }

        // Returns true if a new logo is saved
        private boolean handleLogo(Map<String, MultipartFile> fileMap, String prefix) throws IOException, SQLException
        {
            MultipartFile file = fileMap.get(prefix + "file");

            if (null == file || file.isEmpty())
                return false;

            AttachmentFile aFile = new SpringAttachmentFile(file);
            aFile.setFilename(prefix + getProviderName());
            AttachmentService.get().add(getViewContext().getUser(), ContainerManager.RootContainer.get(), Arrays.asList(aFile));

            return true;
        }

        // Returns true is a logo is deleted
        public boolean deleteLogos(AuthLogoForm form) throws SQLException
        {
            String[] deletedLogos = form.getDeletedLogos();

            if (null == deletedLogos)
                return false;

            for (String logoName : deletedLogos)
                AttachmentService.get().delete(getViewContext().getUser(), ContainerManager.RootContainer.get(), logoName);

            return true;
        }

        public ActionURL getSuccessURL(AuthLogoForm form)
        {
            return null;  // Should never get here
        }
    }


    public static class AuthLogoBean
    {
        public String name;
        public ActionURL returnURL;
        public String url;
        public String headerLogo;
        public String loginPageLogo;
        public boolean reshow;

        private AuthLogoBean(String name, ActionURL returnURL, boolean reshow)
        {
            this.name = name;
            this.returnURL = returnURL;
            this.reshow = reshow;
            url = getAuthLogoURLs().get(name);
            headerLogo = getAuthLogoHtml(name, HEADER_LOGO_PREFIX);
            loginPageLogo = getAuthLogoHtml(name, LOGIN_PAGE_LOGO_PREFIX);
        }

        public String getAuthLogoHtml(String name, String prefix)
        {
            LinkFactory factory = new LinkFactory("", name);
            String filePicker = "<input name=\"" + prefix + "file\" type=\"file\" size=\"60\">";
            String logo = factory.getImg(prefix);

            if (null == logo)
            {
                return "<td>" + filePicker + "</td>";
            }
            else
            {
                StringBuilder html = new StringBuilder();
                html.append("<td><div id=\"").append(prefix).append("id\">");
                html.append(logo);
                String innerHtml = filePicker + "<input type=\"hidden\" name=\"deletedLogos\" value=\"" + prefix + name + "\">";
                html.append("&nbsp;[<a href=\"javascript:{}\" onClick=\"document.getElementById('").append(prefix).append("id').innerHTML = '").append(innerHtml.replaceAll("\"", "&quot;")).append("'\">delete</a>]");
                html.append("</div></td>\n");

                return html.toString();
            }
        }
    }


    public static class AuthLogoForm
    {
        private String _url;
        private String[] _deletedLogos;

        public String getUrl()
        {
            return _url;
        }

        public void setUrl(String url)
        {
            _url = url;
        }

        public String[] getDeletedLogos()
        {
            return _deletedLogos;
        }

        public void setDeletedLogos(String[] deletedLogos)
        {
            _deletedLogos = deletedLogos;
        }
    }


    public static class LinkFactory
    {
        private final String NO_LOGO = "NO_LOGO";
        private Matcher _redirectURLMatcher;
        private String _name;

        // Need to check the attachments service to see if logo exists... use map to check this once and cache result
        private Map<String, String> _imgMap = new HashMap<String, String>();

        private LinkFactory(String redirectUrl, String name)
        {
            _name = name;
            _redirectURLMatcher = Pattern.compile("%returnURL%", Pattern.CASE_INSENSITIVE).matcher(redirectUrl);
        }

        private String getLink(ActionURL returnURL, String prefix)
        {
            String img = getImg(prefix);

            if (null == img)
                return null;
            else
                return "<a href=\"" + PageFlowUtil.filter(getURL(returnURL)) + "\">" + img + "</a>";
        }

        public String getURL(ActionURL returnURL)
        {
            ActionURL loginUrl = getLoginURL(returnURL);
            return _redirectURLMatcher.replaceFirst(PageFlowUtil.encode(loginUrl.getURIString()));
        }

        private String getImg(String prefix)
        {
            String img = _imgMap.get(prefix);

            if (null == img)
            {
                img = NO_LOGO;

                try
                {
                    Attachment logo = AttachmentService.get().getAttachment(ContainerManager.RootContainer.get(), prefix + _name);

                    if (null != logo)
                        img = "<img src=\"" + AppProps.getInstance().getContextPath() + "/" + prefix + _name + ".image?revision=" + AppProps.getInstance().getLookAndFeelRevision() + "\" alt=\"Sign in using " + _name + "\">";
                }
                catch(SQLException e)
                {
                    // TODO: log to mothership
                }

                _imgMap.put(prefix, img);
            }

            return (NO_LOGO.equals(img) ? null : img);
        }
    }
}
