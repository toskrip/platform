package org.labkey.test.tests.mothership;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyB;
import org.labkey.test.pages.mothership.ShowInstallationDetailPage;
import org.labkey.test.util.mothership.MothershipHelper;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.labkey.test.util.mothership.MothershipHelper.MOTHERSHIP_PROJECT;

/**
 * User: tgaluhn
 * Date: 10/28/2016
 */
@Category({DailyB.class})
public class MothershipReportTest extends BaseWebDriverTest
{
    private MothershipHelper _mothershipHelper;

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return MOTHERSHIP_PROJECT;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("mothership");
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        // Don't delete the _mothership project
    }

    @Before
    public void preTest() throws Exception
    {
        _mothershipHelper = new MothershipHelper(getDriver());
    }

    @Test
    public void testTopLevelItems() throws Exception
    {
        // TODO: Test others

        _mothershipHelper.createUsageReport(MothershipHelper.ReportLevel.MEDIUM, true);
        ShowInstallationDetailPage installDetail = ShowInstallationDetailPage.beginAt(this);
        assertEquals("Incorrect distribution name", "localBuild", installDetail.getDistributionName());
        assertNotNull("Usage reporting level is empty", StringUtils.trimToNull(installDetail.getInstallationValue("Usage Reporting Level")));
        assertNotNull("Exception reporting level is empty", StringUtils.trimToNull(installDetail.getInstallationValue("Exception Reporting Level")));
    }

    @Test
    public void testJsonMetrics() throws Exception
    {
        _mothershipHelper.createUsageReport(MothershipHelper.ReportLevel.MEDIUM, true);
        assertTextPresent("jsonMetrics",
                "modules",
                "CoreController", // in the module page hit counts
                "folderTypeCounts",
                "Collaboration", // a folder type guaranteed to exist, different from any module name
                "targetedMSRuns",
                "activeDayCount" // a LOW level metric, should also exist at MEDIUM
        );

        // TODO: Verify jsonMetrics persisted?
    }


    // TODO: Test output of each reporting level

    // TODO: test the View sample report buttons from Customize Site page?
}
