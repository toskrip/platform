package org.labkey.query.olap.metadata;

import junit.framework.Assert;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.labkey.api.query.FieldKey;
import org.olap4j.impl.Named;
import org.olap4j.metadata.MetadataElement;

/**
* Created by matthew on 9/4/14.
*/
public abstract class MetadataElementBase implements MetadataElement, Named
{
//  NOTE storing String uniqueName can take a lot of memory, using FieldKey might be more efficient
    final UniqueName uniqueName;
    String name;                  // name is _usually_, but not always == uniqueName.getName()

    MetadataElementBase(MetadataElement mde, MetadataElementBase parent)
    {
        if (null == parent)
            this.uniqueName = UniqueName.parse(mde.getUniqueName());
        else
            this.uniqueName = new UniqueName(parent.uniqueName,mde.getName());
        // NOTE Properties don't always use the [ ] syntax
        assert StringUtils.equalsIgnoreCase(getUniqueName(),mde.getUniqueName())
                || null==uniqueName.getParent() && StringUtils.equalsIgnoreCase(getName(), mde.getName());
        name = uniqueName.getName().equals(mde.getName()) ? uniqueName.getName() : mde.getName();
    }


    MetadataElementBase(String name, MetadataElementBase parent)
    {
        if (null == parent)
            this.uniqueName = new UniqueName(null, name);
        else
            this.uniqueName = new UniqueName(parent.uniqueName,name);
    }


    @Override
    public String getName()
    {
        return null != name ? name : uniqueName.getName();
    }

    @Override
    final public String getUniqueName()
    {
        return uniqueName.toString();
    }

    @Override
    public String getCaption()
    {
        return getName();
    }

    @Override
    public String getDescription()
    {
        return getName();
    }

    @Override
    public boolean isVisible()
    {
        return true;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " " + getUniqueName();
    }


    public static class UniqueName extends FieldKey
    {
        UniqueName(UniqueName p, String name)
        {
            super(p,name);
        }

        static UniqueName parse(String s)
         {
             // Split is so unhelpful split(\\]\\.\\]) doesn't work
             String[] parts = StringUtils.split(s,'.');
             UniqueName u = null;
             String p = "";
             for (int i=0 ; i<parts.length ; i++)
             {
                 p += parts[i];
                 if (p.endsWith("]"))
                 {
                     u = new UniqueName(u, p.substring(1, p.length() - 1));
                     p = "";
                 }
                 else if (i < parts.length-1)
                     p += ".";
             }
             if (StringUtils.isNotEmpty(p))
             {
                 if (p.startsWith("[") && p.endsWith("]"))
                     p = p.substring(1,p.length()-1);
                 u = new UniqueName(u, p);
             }
             return u;
        }

        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            toStringBuilder(sb);
            return sb.toString();
        }

        private void toStringBuilder(StringBuilder sb)
        {
            if (null == getParent())
            {
                sb.append("[").append(getName()).append("]");
            }
            else
            {
                ((UniqueName)getParent()).toStringBuilder(sb);
                sb.append(".[").append(getName()).append("]");
            }
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testUniqueName()
        {
            assertEquals(UniqueName.parse("PropertyName"), new UniqueName(null,"PropertyName"));
            assertEquals(UniqueName.parse("PropertyName").getName(), "PropertyName");

            assertEquals(UniqueName.parse("[a]"), new UniqueName(null, "a"));
            assertEquals(UniqueName.parse("[a]").toString(), "[a]");

            assertEquals(UniqueName.parse("[a.b]"), new UniqueName(null, "a.b"));
            assertEquals(UniqueName.parse("[a.b]").toString(), "[a.b]");

            assertEquals(UniqueName.parse("[a].[b]"), new UniqueName(new UniqueName(null, "a"), "b"));
            assertEquals(UniqueName.parse("[a].[b]").toString(), "[a].[b]");

            assertEquals(UniqueName.parse("[a].[b].[c]"), new UniqueName(new UniqueName(new UniqueName(null, "a"), "b"), "c"));
            assertEquals(UniqueName.parse("[a].[b].[c]").toString(), "[a].[b].[c]");

            assertEquals(UniqueName.parse("[a.b].[]"), new UniqueName(new UniqueName(null, "a.b"), ""));
            assertEquals(UniqueName.parse("[a.b].[]").toString(), "[a.b].[]");
        }
    }
}
