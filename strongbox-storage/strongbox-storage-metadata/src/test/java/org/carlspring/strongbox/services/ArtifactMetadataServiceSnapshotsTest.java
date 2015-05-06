package org.carlspring.strongbox.services;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.carlspring.maven.commons.util.ArtifactUtils;
import org.carlspring.strongbox.resource.ConfigurationResourceResolver;
import org.carlspring.strongbox.testing.TestCaseWithArtifactGeneration;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import static org.junit.Assert.assertNotNull;

/**
 * @author stodorov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/META-INF/spring/strongbox-*-context.xml", "classpath*:/META-INF/spring/strongbox-*-context.xml"})
public class ArtifactMetadataServiceSnapshotsTest
        extends TestCaseWithArtifactGeneration
{

    private static final File REPOSITORY_BASEDIR = new File(ConfigurationResourceResolver.getVaultDirectory() + "/storages/storage0/snapshots");

    public static final String[] CLASSIFIERS = { "javadoc", "sources", "source-release" };

    public static final String ARTIFACT_BASE_PATH_STRONGBOX_METADATA = "org/carlspring/strongbox/strongbox-metadata";

    private static Artifact artifact;

    private static Artifact pluginArtifact;

    private static Artifact mergeArtifact;

    private static Artifact artifactNoTimestamp;

    @Autowired
    private ArtifactMetadataService artifactMetadataService;

    @Autowired
    private BasicRepositoryService basicRepositoryService;

    private SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd.HHmmss");

    private Calendar calendar = Calendar.getInstance();

    private static boolean initialized;


    @Before
    public void setUp()
            throws NoSuchAlgorithmException, XmlPullParserException, IOException
    {
        if (!initialized)
        {
            //noinspection ResultOfMethodCallIgnored
            REPOSITORY_BASEDIR.mkdirs();

            // Create snapshot artifacts
            String ga = "org.carlspring.strongbox:strongbox-metadata";

            int i = 0;
            for (; i <= 2; i++)
            {
                calendar.add(Calendar.SECOND, 7);
                calendar.add(Calendar.MINUTE, 5);

                String timestamp = formatter.format(calendar.getTime());
                createTimestampedSnapshotArtifact(REPOSITORY_BASEDIR.getAbsolutePath(),
                                                  "org.carlspring.strongbox",
                                                  "strongbox-metadata",
                                                  "2.0-" + timestamp + "-" + (i + 1),
                                                  CLASSIFIERS);
            }

            calendar.add(Calendar.SECOND, 7);
            calendar.add(Calendar.MINUTE, 5);
            String timestamp = formatter.format(calendar.getTime());

            artifact = createSnapshot(REPOSITORY_BASEDIR.getAbsolutePath(), ga + ":2.0-" + timestamp + "-" + (i + 1) + ":jar",
                                      new String[] { "javadoc", "sources", "source-release" });

            changeCreationDate(artifact);

            // Create a snapshot without a timestamp
            artifactNoTimestamp = createSnapshot(REPOSITORY_BASEDIR.getAbsolutePath(), "org.carlspring.strongbox:strongbox-metadata-without-timestamp:2.0-SNAPSHOT:jar");

            // Create an artifact for metadata merging tests
            mergeArtifact = createTimestampedSnapshotArtifact(REPOSITORY_BASEDIR.getAbsolutePath(),
                                                              "org.carlspring.strongbox",
                                                              "strongbox-metadata-merge",
                                                              "2.0-" + formatter.format(calendar.getTime()) + "-1",
                                                              CLASSIFIERS);

            // Create plugin artifact
            pluginArtifact = createSnapshot(REPOSITORY_BASEDIR.getAbsolutePath(),
                                            "org.carlspring.strongbox.maven:strongbox-metadata-plugin:" +
                                            "1.1-" + formatter.format(calendar.getTime()) + "-1" + ":jar");

            generatePluginArtifact(REPOSITORY_BASEDIR.getAbsolutePath(),
                                   "org.carlspring.strongbox.maven:strongbox-metadata-plugin",
                                   "1.1-SNAPSHOT");

            initialized = true;
        }

        assertNotNull(basicRepositoryService);
    }

    @Test
    public void testSnapshotMetadataRebuild()
            throws IOException, XmlPullParserException, NoSuchAlgorithmException
    {
        artifactMetadataService.rebuildMetadata("storage0", "snapshots", ARTIFACT_BASE_PATH_STRONGBOX_METADATA);

        Metadata metadata = artifactMetadataService.getMetadata("storage0", "snapshots", artifact);

        assertNotNull(metadata);

        Versioning versioning = metadata.getVersioning();

        Assert.assertEquals("Incorrect artifactId!", artifact.getArtifactId(), metadata.getArtifactId());
        Assert.assertEquals("Incorrect groupId!", artifact.getGroupId(), metadata.getGroupId());
        //Assert.assertEquals("Incorrect latest release version!", artifact.getVersion(), versioning.getRelease());

        Assert.assertNotNull("No versioning information could be found in the metadata!",
                             versioning.getVersions().size());
        Assert.assertEquals("Incorrect number of versions stored in metadata!", 1, versioning.getVersions().size());
    }

    @Test
    public void testSnapshotPluginMetadataRebuild()
            throws IOException, XmlPullParserException, NoSuchAlgorithmException
    {
        artifactMetadataService.rebuildMetadata("storage0", "snapshots", ArtifactUtils.convertArtifactToPath(pluginArtifact));

        Metadata metadata = artifactMetadataService.getMetadata("storage0", "snapshots", pluginArtifact);

        assertNotNull(metadata);

        Versioning versioning = metadata.getVersioning();

        Assert.assertEquals("Incorrect artifactId!", pluginArtifact.getArtifactId(), metadata.getArtifactId());
        Assert.assertEquals("Incorrect groupId!", pluginArtifact.getGroupId(), metadata.getGroupId());
        Assert.assertNull("Incorrect latest release version!", versioning.getRelease());

        Assert.assertEquals("Incorrect number of versions stored in metadata!", 1, versioning.getVersions().size());
    }

    @Test
    public void testMetadataMerge()
            throws IOException, XmlPullParserException, NoSuchAlgorithmException
    {
        // Generate a proper maven-metadata.xml
        artifactMetadataService.rebuildMetadata("storage0", "snapshots", mergeArtifact);

        // Generate metadata to merge
        Metadata mergeMetadata = new Metadata();
        Versioning appendVersioning = new Versioning();

        appendVersioning.addVersion("1.0-SNAPSHOT");
        appendVersioning.addVersion("1.3-SNAPSHOT");

        Snapshot snapshot = new Snapshot();
        snapshot.setTimestamp(formatter.format(calendar.getTime()));
        snapshot.setBuildNumber(1);

        appendVersioning.setRelease(null);
        appendVersioning.setSnapshot(snapshot);
        appendVersioning.setLatest("1.3-SNAPSHOT");

        mergeMetadata.setVersioning(appendVersioning);

        // Merge
        artifactMetadataService.mergeMetadata("storage0", "snapshots", mergeArtifact, mergeMetadata);

        Metadata metadata = artifactMetadataService.getMetadata("storage0", "snapshots", mergeArtifact);

        assertNotNull(metadata);

        Assert.assertEquals("Incorrect latest release version!", "1.3-SNAPSHOT", metadata.getVersioning().getLatest());
        // Assert.assertEquals("Incorrect latest release version!", null, metadata.getVersioning().getRelease());
        Assert.assertEquals("Incorrect number of versions stored in metadata!",
                            3,
                            metadata.getVersioning().getVersions().size());
    }

}