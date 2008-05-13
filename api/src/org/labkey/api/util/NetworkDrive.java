/*
 * Copyright (c) 2004-2007 Fred Hutchinson Cancer Research Center
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

import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;

import org.apache.log4j.Logger;

public class NetworkDrive
{
    protected String path;
    protected String user;
    protected String password;

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public String getUser()
    {
        return user;
    }

    public void setUser(String user)
    {
        this.user = user;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String mount(char driveChar) throws InterruptedException, IOException
    {
        unmount(driveChar);
        StringBuilder sb = new StringBuilder();
        sb.append("net use ");
        sb.append(driveChar);
        sb.append(": ");
        sb.append(getPath());
        sb.append(" ");
        if (getPassword() != null && !"".equals(getPassword().trim()))
        {
            sb.append(getPassword());
        }
        if (getUser() != null && !"".equals(getUser().trim()))
        {
            sb.append(" /USER:");
            sb.append(getUser());
        }
        String connCommand = sb.toString();
        Process p = Runtime.getRuntime().exec(connCommand);
        p.waitFor();

        if (p.exitValue() != 0)
        {
            int count;
            char buffer[] = new char[4096];

            InputStreamReader reader = new InputStreamReader(p.getErrorStream(), "US-ASCII");
            StringBuffer errors = new StringBuffer();
            while ((count = reader.read(buffer, 0, buffer.length - 1)) != -1)
                errors.append(buffer, 0, count);

            return "Failed to map network drive for " + path + ":\n" +
                    connCommand + "\n" +
                    errors;
        }
        return null;
    }

    public void unmount(char driveChar)
        throws IOException, InterruptedException
    {
        // Make sure OS isn't holding another path mapped to this drive.
        String disconnCommand = "net use " + driveChar + ": " + "/d";
        Process p = Runtime.getRuntime().exec(disconnCommand);
        p.waitFor();
    }

    private static Logger _log = Logger.getLogger(NetworkDrive.class);

    public static boolean exists(File f)
    {
        if (f.exists())
            return true;
        ensureDrive(f.getPath());
        return f.exists();
    }

    public static void ensureDrive(String path)
    {
        if (path.length() != 1)
        {
            if (path.length() < 2 || path.charAt(1) != ':')
                return; // Not a path with a drive.
        }

        char driveChar = path.toLowerCase().charAt(0);

        File driveRoot = new File(driveChar + ":\\");
        if (driveRoot.exists())
            return; // Drive root already exists.

        try
        {
            NetworkDrive drive = getNetworkDrive(path);
            if (drive != null)
            {
                drive.mount(driveChar);
            }
        }
        catch (Exception e)
        {
            _log.error("Exception trying to map network drive for " + path, e);
        }
    }

    public static NetworkDrive getNetworkDrive(String path)
    {
        if (path.length() != 1)
        {
            if (path.length() < 2 || path.charAt(1) != ':')
                return null; // Not a path with a drive.
        }

        char driveChar = path.toLowerCase().charAt(0);

        AppProps props = AppProps.getInstance();
        if (props.getNetworkDriveLetter().equals(Character.toString(driveChar)))
        {
            NetworkDrive drive = new NetworkDrive();
            drive.setPath(props.getNetworkDrivePath());
            drive.setUser(props.getNetworkDriveUser());
            drive.setPassword(props.getNetworkDrivePassword());
            return drive;
        }
        
        return null;
    }

    public static String getDrive(String path)
    {
        if (null == path || path.length() < 2 || path.charAt(1) != ':')
            return null; // Not a path with a drive.

        return path.toLowerCase().substring(0, 2);
    }
}
