package org.labkey.core.admin.importer;

import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterImpl;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.SubfolderWriter;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.SubfolderType;
import org.labkey.folder.xml.SubfoldersDocument;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

/**
 * User: cnathe
 * Date: 10/11/12
 */
public class SubfolderImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new SubfolderImporter();
    }

    @Override
    public int getPriority()
    {
        return 100;
    }

    public class SubfolderImporter implements FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDescription()
        {
            return "subfolders and workbooks";
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            VirtualFile subfoldersDir = ctx.getDir("subfolders");

            if (null != subfoldersDir)
            {
                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());

                // fail if the user does not have admin permissions to the parent container
                if (!ctx.getContainer().hasPermission(ctx.getUser(), AdminPermission.class))
                {
                    throw new UnauthorizedException("You must have admin permissions to import subfolders");
                }

                // get the subfolders.xml file so we know which subfolder directories to look for
                InputStream subfoldersIS = subfoldersDir.getInputStream(SubfolderWriter.SUBFOLDERS_FILENAME);
                if (subfoldersIS == null)
                {
                    ctx.getLogger().error("Could not find expected file: " + getFilePath(subfoldersDir, SubfolderWriter.SUBFOLDERS_FILENAME));
                    return;
                }

                // loop through each of the subfolder dirs listed in the subfolders.xml file
                SubfoldersDocument document = SubfoldersDocument.Factory.parse(subfoldersIS);
                for (SubfolderType subfolderNode : document.getSubfolders().getSubfolderArray())
                {
                    if (Arrays.asList(subfoldersDir.listDirs()).indexOf(subfolderNode.getName()) == -1)
                    {
                        ctx.getLogger().error("Could not find content directory for subfolder: " + getFilePath(subfoldersDir, subfolderNode.getName()));
                    }
                    else
                    {
                        VirtualFile subfolderDir = subfoldersDir.getDir(subfolderNode.getName());

                        // locate the folder.xml file for the given subfolder
                        InputStream folderXmlIS = subfolderDir.getInputStream("folder.xml");
                        if (folderXmlIS == null)
                        {
                            ctx.getLogger().error("Could not find expected folder.xml file: " + getFilePath(subfoldersDir, subfolderNode.getName()));
                            continue;
                        }
                        FolderDocument folderXml = FolderDocument.Factory.parse(folderXmlIS);

                        // create a new child container if one does not already exist
                        Container childContainer = ctx.getContainer().getChild(subfolderNode.getName());
                        if (childContainer == null)
                        {
                            String title = folderXml.getFolder().getTitle();
                            String description = folderXml.getFolder().getDescription();
                            Container.TYPE cType = folderXml.getFolder().isSetType() ? Container.TYPE.typeFromString(folderXml.getFolder().getType()) : Container.TYPE.normal;
                            childContainer = ContainerManager.createContainer(ctx.getContainer(), subfolderNode.getName(), title, description, cType, ctx.getUser());

                            // set the child container to inherit permissions from the parent by default
                            SecurityManager.setInheritPermissions(childContainer);
                            ctx.getLogger().info("New container created with inherited permissions for " + subfolderNode.getName());
                        }
                        else
                        {
                            if (!childContainer.hasPermission(ctx.getUser(), AdminPermission.class))
                            {
                                ctx.getLogger().error("You must have admin permissions to replace the subfolder: " + getFilePath(subfoldersDir, subfolderNode.getName()));
                                continue;
                            }
                        }

                        // import the subfolder with the folderDir as the root with a new import context
                        ctx.getLogger().info("Loading folder archive for " + subfolderNode.getName());
                        FolderImportContext folderCtx = new FolderImportContext(ctx.getUser(), childContainer, folderXml, ctx.getLogger(), subfolderDir);
                        FolderImporterImpl importer = new FolderImporterImpl(job);
                        importer.process(job, folderCtx, subfolderDir);
                        ctx.getLogger().info("Done importing folder archive for " + subfolderNode.getName());
                    }
                }

                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        private String getFilePath(VirtualFile dir, String name)
        {
            return dir.getLocation() + "\\" + name;
        }

        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return null;
        }

        @Override
        public boolean supportsVirtualFile()
        {
            return true;
        }
    }
}
