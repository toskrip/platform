/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.wiki;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.webdav.*;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.settings.AppProps;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;

import javax.naming.OperationNotSupportedException;
import java.io.*;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Oct 21, 2008
 * Time: 9:52:40 AM
 */

class WikiWebdavProvider implements WebdavService.Provider
{
    final String WIKI_NAME = "@wiki";
    
    // currently addChildren is called only for web folders
    public Set<String> addChildren(@NotNull WebdavResolver.Resource target)
    {
        if (!(target instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) target;
        Container c = folder.getContainer();
        return hasWiki(c) ? PageFlowUtil.set(WIKI_NAME) : null;
    }


    public WebdavResolver.Resource resolve(@NotNull WebdavResolver.Resource parent, @NotNull String name)
    {
        if (!WIKI_NAME.equalsIgnoreCase(name))
            return null;
        if (!(parent instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) parent;
        Container c = folder.getContainer();
        return WIKI_NAME.equals(name) ? new WikiProviderResource(c) : null;
    }
    

    boolean hasWiki(Container c)
    {
        for (Module m : c.getActiveModules())
            if (m instanceof WikiModule)
                return true;
        return false;
    }


    class WikiProviderResource extends AbstractCollectionResource
    {
        Container _c;
        
        WikiProviderResource(Container c)
        {
            super(c.getPath(), WIKI_NAME);
            _c = c;
            _acl = c.getAcl();
        }

        @Override
        public String getName()
        {
            return WIKI_NAME;
        }

        public WebdavResolver.Resource find(String name)
        {
            return new WikiFolder(this, name);
        }

        @NotNull
        public List<String> listNames()
        {
            try
            {
                return WikiManager.getWikiNameList(_c);
            }
            catch(SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        public boolean exists()
        {
            return true;
        }
        
        public boolean isFile()
        {
            return false;
        }

        public long getCreation()
        {
            return 0;
        }

        public long getLastModified()
        {
            return 0;
        }

        public String getExecuteHref(ViewContext context)
        {
            return null;
        }
    }
    

    public static class WikiFolder extends AbstractCollectionResource
    {
        Container _c;
        Wiki _wiki;
        
        WikiFolder(WikiProviderResource folder, String name)
        {
            super(folder.getPath(), name);
            _c = folder._c;
            _acl = _c.getAcl();
            _wiki = WikiManager.getWiki(_c, name);
        }

        public boolean canDelete(User user)
        {
            return false;   // NYI
        }

        public boolean canRename(User user)
        {
            return false;   // NYI
        }

        public boolean exists()
        {
            return null != _wiki;
        }

        @NotNull
        public List<String> listNames()
        {
            if (!exists())
                return Collections.emptyList();
            return Arrays.asList(getDocumentName(_wiki));
        }

        public WebdavResolver.Resource find(String name)
        {
            String docName = getDocumentName(_wiki);
            if (docName.equals(name))
            {
                return new WikiPageResource(this, _wiki, docName);
            }
            return null;
        }

        public long getCreation()
        {
            return 0;
        }

        public long getLastModified()
        {
            return 0;
        }

        public String getExecuteHref(ViewContext context)
        {
            return new ActionURL(WikiController.PageAction.class, _c).addParameter("name",_wiki.getName()).toString();
        }
    }


    static String getDocumentName(Wiki wiki)
    {
        WikiVersion v = WikiManager.getLatestVersion(wiki);
        WikiRendererType r = WikiRendererType.HTML;
        try
        {
            r = WikiRendererType.valueOf(v.getRendererType());
        }
        catch (IllegalArgumentException x)
        {
        }
        switch (r)
        {
            default:
            case HTML: return wiki.getName() + ".html";
            case RADEOX: return wiki.getName() +  ".wiki";
            case TEXT_WITH_LINKS: return wiki.getName() + ".txt";
        }
    }

    public static class WikiPageResource extends AbstractDocumentResource
    {
        WikiFolder _folder = null;
        Wiki _wiki = null;
        WikiVersion _version = null;

        WikiPageResource(WikiFolder folder, Wiki wiki, String docName)
        {
            super(folder.getPath(), docName);
            _folder = folder;
            _acl = _folder._c.getAcl();
            _wiki = wiki;
        }

        WikiVersion getWikiVersion()
        {
            if (_wiki != null && _version == null)
                _version = WikiManager.getLatestVersion(_wiki);
            return _version;
        }

        public boolean exists()
        {
            WikiVersion version = getWikiVersion();
            return null != version && !StringUtils.isEmpty(version.getBody());
        }

        public boolean isCollection()
        {
            return false;
        }

        public boolean isVirtual()
        {
            return true;
        }

        public boolean canRename()
        {
            return false; // NYI
        }

        public InputStream getInputStream(User user) throws IOException
        {
            WikiVersion v = getWikiVersion();
            byte[] buf = v.getBody().getBytes("UTF-8");
            return new ByteArrayInputStream(buf);
        }

        public long copyFrom(User user, InputStream in) throws IOException
        {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            FileUtil.copyData(in,buf);
            long len = buf.size();
            WikiVersion version = getWikiVersion();
            version.setBody(buf.toString("UTF-8"));
            try
            {
                WikiManager.updateWiki(user, _wiki, version);
                WikiManager.getLatestVersion(_wiki, true);
                return len;
            }
            catch (SQLException x)
            {
                throw new IOException("error writing to wiki");
            }
        }


        @NotNull
        public List<WebdavResolver.History> getHistory()
        {
            WikiVersion[] versions = WikiManager.getAllVersions(_wiki);
            List<WebdavResolver.History> list = new ArrayList<WebdavResolver.History>();
            for (WikiVersion v : versions)
                list.add(new WikiHistory(_wiki, v));
            return list;
        }


        public static class WikiHistory implements WebdavResolver.History
        {
            Wiki w;
            WikiVersion v;

            WikiHistory(Wiki w, WikiVersion v)
            {
                this.w = w;
                this.v = v;    
            }

            public User getUser()
            {
                return UserManager.getUser(v.getCreatedBy());
            }

            public Date getDate()
            {
                return v.getCreated();
            }

            public String getMessage()
            {
                return "version " + v.getVersion();
            }

            public String getHref()
            {
                ActionURL url = new ActionURL(WikiController.VersionAction.class, ContainerManager.getForId(w.getContainerId()));
                url.addParameter("name", w.getName());
                url.addParameter("version", String.valueOf(v.getVersion()));
                return url.toString();
            }
        }

        // You can't actually delete this file, however, some clients do delete instead of overwrite,
        // so pretend we deleted it.
        public boolean delete(User user) throws IOException
        {
            copyFrom(user, new ByteArrayInputStream(new byte[0]));
            return true;
        }

        public WebdavResolver.Resource parent()
        {
            return _folder;
        }

        public long getCreation()
        {
            return _wiki.getCreated().getTime();
        }

        public long getLastModified()
        {
            WikiVersion v = getWikiVersion();
            return v.getCreated().getTime();
        }

        public String getContentType()
        {
            WikiVersion v = getWikiVersion();
            if ("HTML".equals(v.getRendererType()))
                return "text/html";
            return "text/plain";
        }

        public long getContentLength()
        {
            WikiVersion v = getWikiVersion();
            String txt = v.getBody();
            try
            {
                byte[] buf = txt.getBytes("UTF-8");
                return buf.length;
            }
            catch (UnsupportedEncodingException e)
            {
                return 0;
            }
        }

		@Override
        public String getExecuteHref(ViewContext context)
        {
            return new ActionURL(WikiController.PageAction.class, _folder._c).addParameter("name",_wiki.getName()).toString();
        }

        @Override
        public String getIconHref()
        {
            WikiVersion v = getWikiVersion();
            if (WikiRendererType.RADEOX.toString().equals(v.getRendererType()))
                return AppProps.getInstance().getContextPath() + "/_icons/wiki.png";
            return super.getIconHref();
        }
    }


    public static class AttachmentResource extends AbstractDocumentResource
    {
        WikiFolder _folder = null;
        AttachmentParent _parent = null;
        String _name = null;

        AttachmentResource(WikiFolder folder, AttachmentParent parent, String name)
        {
            super(folder.getPath(), name);
            _folder = folder;
            _acl = _folder._c.getAcl();
            _name = name;
            _parent = parent;
        }

        public boolean exists()
        {
            Attachment r = AttachmentService.get().getAttachment(_parent, _name);
            return null != r;
        }

        public boolean isCollection()
        {
            return false;
        }

        public boolean isVirtual()
        {
            return true;
        }

        public boolean canRename()
        {
            return false; // NYI
        }

        public boolean canDelete()
        {
            return true;
        }

        public InputStream getInputStream(User user) throws IOException
        {
            return AttachmentService.get().getInputStream(_parent, _name);
        }

        //        OutputStream getOutputStream(User user) throws IOException;
        public long copyFrom(User user, InputStream in) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        public WebdavResolver.Resource parent()
        {
            return _folder;
        }

        public long getCreation()
        {
            return 0;
        }

        public long getLastModified()
        {
            return 0;
        }

        public long getContentLength()
        {
            // UNDONE how expensive is this
            InputStream is = null;
            try
            {
                is = getInputStream(null);
                if (null != is)
                {
                    if (is instanceof FileInputStream)
                        return ((FileInputStream) is).getChannel().size();
                    else if (is instanceof FilterInputStream)
                        return is.available();
                }
            }
            catch (IOException x)
            {
            }
            finally
            {
                IOUtils.closeQuietly(is);    
            }
            return 0;
        }

		@Override
        public int getPermissions(User user)
        {
            return super.getPermissions(user) & ACL.PERM_READ;
        }

		@Override
        public File getFile()
        {
            assert false;
            return null;
        }

        @NotNull
        public List<WebdavResolver.History> getHistory()
        {
            //noinspection unchecked
            return Collections.EMPTY_LIST;
        }
    }
}
