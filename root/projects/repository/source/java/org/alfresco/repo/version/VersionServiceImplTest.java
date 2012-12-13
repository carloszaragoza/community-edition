/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.repo.version;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alfresco.model.ApplicationModel;
import org.alfresco.model.ContentModel;
import org.alfresco.model.ForumModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.security.authentication.AuthenticationComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.security.authentication.AuthenticationUtil.RunAsWork;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.repo.version.common.VersionUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.coci.CheckOutCheckInService;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.version.Version;
import org.alfresco.service.cmr.version.VersionHistory;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.cmr.version.VersionServiceException;
import org.alfresco.service.cmr.version.VersionType;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ApplicationContextHelper;
import org.alfresco.util.GUID;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;

/**
 * versionService test class.
 * 
 * @author Roy Wetherall, janv
 */
public class VersionServiceImplTest extends BaseVersionStoreTest
{
    private static Log logger = LogFactory.getLog(VersionServiceImplTest.class);
    
    private static final String UPDATED_VALUE_1 = "updatedValue1";
    private static final String UPDATED_VALUE_2 = "updatedValue2";
    private static final String UPDATED_VALUE_3 = "updatedValue3";
    private static final String UPDATED_CONTENT_1 = "updatedContent1";
    private static final String UPDATED_CONTENT_2 = "updatedContent2";
    private static final String UPDATED_CONTENT_3 = "updatedContent3";
    
    private static final String PWD_A = "passA";
    private static final String USER_NAME_A = "userA";
    
    private VersionableAspect versionableAspect;
    private List<String> excludedOnUpdateProps;
    
    @Override
    protected void onSetUpInTransaction() throws Exception
    {
        super.onSetUpInTransaction();
        versionableAspect = (VersionableAspect) applicationContext.getBean("versionableAspect");
        excludedOnUpdateProps = versionableAspect.getExcludedOnUpdateProps();
    }

    @Override
    protected void onTearDownAfterTransaction() throws Exception
    {
        super.onTearDownAfterTransaction();
        versionableAspect.setExcludedOnUpdateProps(excludedOnUpdateProps);
        versionableAspect.afterDictionaryInit();
    }

    public void testSetup()
    {
        // NOOP
    }
    
    /**
     * Tests the creation of the initial version of a versionable node
     */
    public void testCreateIntialVersion()
    {
        NodeRef versionableNode = createNewVersionableNode();
        createVersion(versionableNode);
    }
    
    /**
     * Test creating a version history with many versions from the same workspace
     */
    public void testCreateManyVersionsSameWorkspace()
    {
        NodeRef versionableNode = createNewVersionableNode();
        createVersion(versionableNode);
        // TODO mess with some of the properties and stuff as you version
        createVersion(versionableNode);
        // TODO mess with some of the properties and stuff as you version
        createVersion(versionableNode);
        
        VersionHistory vh = this.versionService.getVersionHistory(versionableNode);
        assertNotNull(vh);
        assertEquals(3, vh.getAllVersions().size());
        
        // TODO check list of versions ... !
    }
    
    /**
     * Tests the creation of multiple versions of a versionable node with null version properties
     */
    public void testCreateManyVersionsWithNullVersionProperties()
    {
        this.versionProperties = null;
        
        NodeRef versionableNode = createNewVersionableNode();
        createVersion(versionableNode);
        createVersion(versionableNode);
        createVersion(versionableNode);
        
        VersionHistory vh = this.versionService.getVersionHistory(versionableNode);
        assertNotNull(vh);
        assertEquals(3, vh.getAllVersions().size());
    }
    
    /**
     * Test versioning a non versionable node ie: no version apsect
     */
    public void testCreateInitialVersionWhenNotVersionable()
    {
        NodeRef node = createNewNode(); // not marked as versionable
        createVersion(node);
    }
    
    // TODO test versioning numberious times with branchs implies by different workspaces
    
    /**
     * Test versioning the children of a verionable node
     */
    public void testVersioningChildren()
    {
        NodeRef versionableNode = createNewVersionableNode();
        
        // Snap shot data
        String expectedVersionLabel = peekNextVersionLabel(versionableNode, versionProperties);
        
        // Snap-shot the node created date-time
        long beforeVersionTime = ((Date)nodeService.getProperty(versionableNode, ContentModel.PROP_CREATED)).getTime();
        
        // Version the node and its children
        Collection<Version> versions = this.versionService.createVersion(
                versionableNode, 
                this.versionProperties,
                true);
        
        // Check the returned versions are correct
        CheckVersionCollection(expectedVersionLabel, beforeVersionTime, versions);
        
        // TODO check the version history is correct
    }	
    
    /**
     * Test versioning many nodes in one go
     */
    public void testVersioningManyNodes()
    {
        NodeRef versionableNode = createNewVersionableNode();
        
        // Snap shot data
        String expectedVersionLabel = peekNextVersionLabel(versionableNode, versionProperties);  
        
        // Snap-shot the node created date-time
        long beforeVersionTime = ((Date)nodeService.getProperty(versionableNode, ContentModel.PROP_CREATED)).getTime();
        
        // Version the list of nodes created
        Collection<Version> versions = this.versionService.createVersion(
                this.versionableNodes.values(),
                this.versionProperties);
        
        // Check the returned versions are correct
        CheckVersionCollection(expectedVersionLabel, beforeVersionTime, versions);
        
        // TODO check the version histories
    }
    
    /**
     * Helper method to check the validity of the list of newly created versions.
     * 
     * @param beforeVersionTime      the time before the versions where created
     * @param versions               the collection of version objects
     */
    private void CheckVersionCollection(String expectedVersionLabel, long beforeVersionTime, Collection<Version> versions)
    {
        for (Version version : versions)
        {
            // Get the frozen id from the version
            String frozenNodeId = null;
            
            // Switch VersionStore depending on configured impl
            if (versionService.getVersionStoreReference().getIdentifier().equals(Version2Model.STORE_ID))
            {
                // V2 version store (eg. workspace://version2Store)
                frozenNodeId = ((NodeRef)version.getVersionProperty(Version2Model.PROP_FROZEN_NODE_REF)).getId();
            } 
            else if (versionService.getVersionStoreReference().getIdentifier().equals(VersionModel.STORE_ID))
            {
                // Deprecated V1 version store (eg. workspace://lightWeightVersionStore)
                frozenNodeId = (String)version.getVersionProperty(VersionModel.PROP_FROZEN_NODE_ID);
            }
            
            assertNotNull("Unable to retrieve the frozen node id from the created version.", frozenNodeId);
            
            // Get the origional node ref (based on the forzen node)
            NodeRef originalNodeRef = this.versionableNodes.get(frozenNodeId);
            assertNotNull("The versionable node ref that relates to the frozen node id can not be found.", originalNodeRef);
            
            // Check the new version
            checkNewVersion(beforeVersionTime, expectedVersionLabel, version, originalNodeRef);
        }
    }
    
    private void CheckVersionHistory(VersionHistory vh, List<Version> expectedVersions)
    {
        if (vh == null)
        {
            assertNull(expectedVersions);
        }
        else
        {
            Iterator<Version> itr = expectedVersions.iterator();
            
            for (Version version : vh.getAllVersions())
            {
                Version expectedVersion = itr.next();
                
                assertEquals(version.getVersionLabel(), expectedVersion.getVersionLabel());
                assertEquals(version.getFrozenStateNodeRef(), expectedVersion.getFrozenStateNodeRef());
            }
            
            assertFalse(itr.hasNext());
        }
    }
    
    /**
     * Tests the version history
     */
    public void testNoVersionHistory()
    {
        NodeRef nodeRef = createNewVersionableNode();
        
        VersionHistory vh = this.versionService.getVersionHistory(nodeRef);
        assertNull(vh);
    }
    
    /**
     * Tests getVersionHistory when all the entries in the version history
     * are from the same workspace.
     */
    public void testGetVersionHistorySameWorkspace()
    {
        NodeRef versionableNode = createNewVersionableNode();
        
        Version version1 = addToVersionHistory(versionableNode, null);
        Version version2 = addToVersionHistory(versionableNode, version1);
        Version version3 = addToVersionHistory(versionableNode, version2);
        Version version4 = addToVersionHistory(versionableNode, version3);
        addToVersionHistory(versionableNode, version4);    
    }
    
    /**
     * Adds another version to the version history then checks that getVersionHistory is returning
     * the correct data.
     * 
     * @param versionableNode  the versionable node reference
     * @param parentVersion    the parent version
     */
    private Version addToVersionHistory(NodeRef versionableNode, Version parentVersion)
    {
        Version createdVersion = createVersion(versionableNode);
        
        VersionHistory vh = this.versionService.getVersionHistory(versionableNode);
        assertNotNull("The version history should not be null since we know we have versioned this node.", vh);
        
        if (parentVersion == null)
        {
            // Check the root is the newly created version
            Version root = vh.getRootVersion();
            assertNotNull(
                    "The root version should never be null, since every version history ust have a root version.", 
                    root);
            assertEquals(createdVersion.getVersionLabel(), root.getVersionLabel());
        }
        
        // Get the version from the version history
        Version version = vh.getVersion(createdVersion.getVersionLabel());
        assertNotNull(version);
        assertEquals(createdVersion.getVersionLabel(), version.getVersionLabel());
        
        // Check that the version is a leaf node of the version history (since it is newly created)
        Collection<Version> suc = vh.getSuccessors(version);
        assertNotNull(suc);
        assertEquals(0, suc.size());
        
        // Check that the predessor is the passed parent version (if root version should be null)
        Version pre = vh.getPredecessor(version);
        if (parentVersion == null)
        {
            assertNull(pre);
        }
        else
        {
            assertNotNull(pre);
            assertEquals(parentVersion.getVersionLabel(), pre.getVersionLabel());
        }
        
        if (parentVersion != null)
        {
            // Check that the successors of the parent are the created version
            Collection<Version> parentSuc = vh.getSuccessors(parentVersion);
            assertNotNull(parentSuc);
            assertEquals(1, parentSuc.size());
            Version tempVersion = (Version)parentSuc.toArray()[0];
            assertEquals(version.getVersionLabel(), tempVersion.getVersionLabel());
        }
        
        return createdVersion;
    }
    
    /**
     * Test revert
     */
    @SuppressWarnings("unused")
    public void testRevert()
    {
       // Create a versionable node
       NodeRef versionableNode = createNewVersionableNode();

       // Store the node details for later
       Set<QName> origAspects = this.dbNodeService.getAspects(versionableNode);

       // Create the initial version
       Version version1 = createVersion(versionableNode);

       // Check the history is correct
       VersionHistory history = versionService.getVersionHistory(versionableNode);
       assertEquals(version1.getVersionLabel(), history.getHeadVersion().getVersionLabel());
       assertEquals(version1.getVersionedNodeRef(), history.getHeadVersion().getVersionedNodeRef());
       assertEquals(1, history.getAllVersions().size());
       Version[] versions = history.getAllVersions().toArray(new Version[1]);
       assertEquals("0.1", versions[0].getVersionLabel());
       assertEquals("0.1", nodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));

       // Change the property and content values
       this.dbNodeService.setProperty(versionableNode, PROP_1, UPDATED_VALUE_1);
       this.dbNodeService.setProperty(versionableNode, PROP_2, null);
       ContentWriter contentWriter = this.contentService.getWriter(versionableNode, ContentModel.PROP_CONTENT, true);
       assertNotNull(contentWriter);
       contentWriter.putContent(UPDATED_CONTENT_1);

       // Change the aspects on the node
       this.dbNodeService.addAspect(versionableNode, ApplicationModel.ASPECT_SIMPLE_WORKFLOW, null);

       // Store the node details for later
       Set<QName> origAspects2 = this.dbNodeService.getAspects(versionableNode);

       // Record this as a new version
       Version version2 = createVersion(versionableNode);
       
       // Check we're now seeing both versions in the history
       history = versionService.getVersionHistory(versionableNode);
       assertEquals(version2.getVersionLabel(), history.getHeadVersion().getVersionLabel());
       assertEquals(version2.getVersionedNodeRef(), history.getHeadVersion().getVersionedNodeRef());
       assertEquals(2, history.getAllVersions().size());
       
       versions = history.getAllVersions().toArray(new Version[2]);
       assertEquals("0.2", versions[0].getVersionLabel());
       assertEquals("0.1", versions[1].getVersionLabel());
       assertEquals("0.2", nodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));

       
       // Change the property and content values
       this.dbNodeService.setProperty(versionableNode, PROP_1, UPDATED_VALUE_2);
       this.dbNodeService.setProperty(versionableNode, PROP_2, UPDATED_VALUE_3);
       this.dbNodeService.setProperty(versionableNode, PROP_3, null);
       ContentWriter contentWriter2 = this.contentService.getWriter(versionableNode, ContentModel.PROP_CONTENT, true);
       assertNotNull(contentWriter2);
       contentWriter2.putContent(UPDATED_CONTENT_2);

       String versionLabel = (String)this.dbNodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL);

       
       // Revert to the previous version, which will loose these changes
       this.versionService.revert(versionableNode);

       // Check that the version label is unchanged
       assertEquals(versionLabel, this.dbNodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));

       // Check that the properties have been reverted
       assertEquals(UPDATED_VALUE_1, this.dbNodeService.getProperty(versionableNode, PROP_1));
       assertNull(this.dbNodeService.getProperty(versionableNode, PROP_2));
       assertEquals(VALUE_3, this.dbNodeService.getProperty(versionableNode, PROP_3));

       // Check that the content has been reverted
       ContentReader contentReader1 = this.contentService.getReader(versionableNode, ContentModel.PROP_CONTENT);
       assertNotNull(contentReader1);
       assertEquals(UPDATED_CONTENT_1, contentReader1.getContentString());

       // Check that the aspects have been reverted correctly
       Set<QName> aspects1 = this.dbNodeService.getAspects(versionableNode);
       assertEquals(aspects1.size(), origAspects2.size());
       
       // Check that the history is back how it was
       history = versionService.getVersionHistory(versionableNode);
       assertEquals(version2.getVersionLabel(), history.getHeadVersion().getVersionLabel());
       assertEquals(version2.getVersionedNodeRef(), history.getHeadVersion().getVersionedNodeRef());
       assertEquals(2, history.getAllVersions().size());
       
       versions = history.getAllVersions().toArray(new Version[2]);
       assertEquals("0.2", versions[0].getVersionLabel());
       assertEquals("0.1", versions[1].getVersionLabel());
       assertEquals("0.2", nodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));
       assertEquals("0.2", history.getHeadVersion().getVersionLabel());

       
       // Revert to the first version
       this.versionService.revert(versionableNode, version1);

       // Check that the version label is correct
       assertEquals(versionLabel, this.dbNodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));

       // Check that the properties are correct
       assertEquals(VALUE_1, this.dbNodeService.getProperty(versionableNode, PROP_1));
       assertEquals(VALUE_2, this.dbNodeService.getProperty(versionableNode, PROP_2));
       assertEquals(VALUE_3, this.dbNodeService.getProperty(versionableNode, PROP_3));

       // Check that the content is correct
       ContentReader contentReader2 = this.contentService.getReader(versionableNode, ContentModel.PROP_CONTENT);
       assertNotNull(contentReader2);
       assertEquals(TEST_CONTENT, contentReader2.getContentString());

       // Check that the aspects have been reverted correctly
       Set<QName> aspects2 = this.dbNodeService.getAspects(versionableNode);
       assertEquals(aspects2.size(), origAspects.size());

       // Check that the version label is still the same
       assertEquals(versionLabel, this.dbNodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));
       
       
       // Check the history still has 2 versions
       // The head version remains as 0.2, but version on the node is 0.1
       history = versionService.getVersionHistory(versionableNode);
       assertEquals(version2.getVersionLabel(), history.getHeadVersion().getVersionLabel());
       assertEquals(version2.getVersionedNodeRef(), history.getHeadVersion().getVersionedNodeRef());
       assertEquals(2, history.getAllVersions().size());
       
       versions = history.getAllVersions().toArray(new Version[2]);
       assertEquals("0.2", versions[0].getVersionLabel());
       assertEquals("0.1", versions[1].getVersionLabel());
       
       // Head is 0.2, but the node is at 0.1
       assertEquals("0.2", history.getHeadVersion().getVersionLabel());
       
       // TODO Shouldn't the node now be at 0.1 not 0.2?
       //assertEquals("0.1", nodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));
    }
    
    
    /**
     * Test reverting a node that has comments, see ALF-13129
     */
    public void testRevertWithComments()
    {
        NodeRef versionableNode = createNewVersionableNode();

    	this.dbNodeService.setProperty(versionableNode, PROP_1, "I am before version");
    	Version version1 = createVersion(versionableNode);
    	this.dbNodeService.setProperty(versionableNode, PROP_1, "I am after version 1");
      
        VersionHistory vh = this.versionService.getVersionHistory(versionableNode);
        assertNotNull(vh);
        assertEquals(1, vh.getAllVersions().size());
        
    	// Create a new version
    	Version version2 = createVersion(versionableNode);
    	
    	//Test a revert with no comments
    	this.versionService.revert(versionableNode, version1);
        assertEquals("I am before version", this.dbNodeService.getProperty(versionableNode, PROP_1));
    	
        createComment(versionableNode, "my comment", "Do great work", false);
        assertTrue(nodeService.hasAspect(versionableNode, ForumModel.ASPECT_DISCUSSABLE));
        assertEquals(1, this.dbNodeService.getProperty(versionableNode,  ForumModel.PROP_COMMENT_COUNT));
    	
    	// Create a new version
    	this.dbNodeService.setProperty(versionableNode, PROP_1, "I am version 3");
    	Version version3 = createVersion(versionableNode);
    	this.dbNodeService.setProperty(versionableNode, PROP_1, "I am after version 3");
    	
        createComment(versionableNode, "v3", "Great version", false);
        assertEquals(2, this.dbNodeService.getProperty(versionableNode,  ForumModel.PROP_COMMENT_COUNT));
    	
    	//Revert to a version that has comments.
    	this.versionService.revert(versionableNode, version3);
        assertTrue(nodeService.hasAspect(versionableNode, ForumModel.ASPECT_DISCUSSABLE));
        assertEquals("I am version 3", this.dbNodeService.getProperty(versionableNode, PROP_1));

    }
    
    /**
     * This method was taken from the CommmentServiceImpl on the cloud branch
     * 
     * TODO: When this is merged to HEAD, please remove this method and use the one in CommmentServiceImpl
     */
    private NodeRef createComment(final NodeRef discussableNode, String title, String comment, boolean suppressRollups)
    {
    	if(comment == null)
    	{
    		throw new IllegalArgumentException("Must provide a non-null comment");
    	}

        // There is no CommentService, so we have to create the node structure by hand.
        // This is what happens within e.g. comment.put.json.js when comments are submitted via the REST API.
        if (!nodeService.hasAspect(discussableNode, ForumModel.ASPECT_DISCUSSABLE))
        {
            nodeService.addAspect(discussableNode, ForumModel.ASPECT_DISCUSSABLE, null);
        }
        if (!nodeService.hasAspect(discussableNode, ForumModel.ASPECT_COMMENTS_ROLLUP) && !suppressRollups)
        {
            nodeService.addAspect(discussableNode, ForumModel.ASPECT_COMMENTS_ROLLUP, null);
        }
        // Forum node is created automatically by DiscussableAspect behaviour.
        NodeRef forumNode = nodeService.getChildAssocs(discussableNode, ForumModel.ASSOC_DISCUSSION, QName.createQName(NamespaceService.FORUMS_MODEL_1_0_URI, "discussion")).get(0).getChildRef();
        
        final List<ChildAssociationRef> existingTopics = nodeService.getChildAssocs(forumNode, ContentModel.ASSOC_CONTAINS, QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "Comments"));
        NodeRef topicNode = null;
        if (existingTopics.isEmpty())
        {
            Map<QName, Serializable> props = new HashMap<QName, Serializable>(1, 1.0f);
            props.put(ContentModel.PROP_NAME, "Comments");
            topicNode = nodeService.createNode(forumNode, ContentModel.ASSOC_CONTAINS, QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "Comments"), ForumModel.TYPE_TOPIC, props).getChildRef();
        }
        else
        {
            topicNode = existingTopics.get(0).getChildRef();
        }

        NodeRef postNode = nodeService.createNode(topicNode, ContentModel.ASSOC_CONTAINS, ContentModel.ASSOC_CONTAINS, ForumModel.TYPE_POST).getChildRef();
        nodeService.setProperty(postNode, ContentModel.PROP_CONTENT, new ContentData(null, MimetypeMap.MIMETYPE_TEXT_PLAIN, 0L, null));
        nodeService.setProperty(postNode, ContentModel.PROP_TITLE, title);
        ContentWriter writer = contentService.getWriter(postNode, ContentModel.PROP_CONTENT, true);
        writer.setMimetype(MimetypeMap.MIMETYPE_HTML);
        writer.setEncoding("UTF-8");
        writer.putContent(comment);

        return postNode;
    }
    
    /**
     * Test reverting from Share
     */
    @SuppressWarnings("unused")
    public void testScriptNodeRevert()
    {
        CheckOutCheckInService checkOutCheckIn =
            (CheckOutCheckInService) applicationContext.getBean("checkOutCheckInService");
        
        // Create a versionable node
        NodeRef versionableNode = createNewVersionableNode();
        NodeRef checkedOut = checkOutCheckIn.checkout(versionableNode);
        
        Version versionC1 = createVersion(checkedOut);

        
        // Create a new, first proper version
        ContentWriter contentWriter = this.contentService.getWriter(checkedOut, ContentModel.PROP_CONTENT, true);
        assertNotNull(contentWriter);
        contentWriter.putContent(UPDATED_CONTENT_1);
        nodeService.setProperty(checkedOut, PROP_1, VALUE_1);
        checkOutCheckIn.checkin(checkedOut, null, contentWriter.getContentUrl(), false);
        Version version1 = createVersion(versionableNode);
        checkedOut = checkOutCheckIn.checkout(versionableNode);
        
        
        // Create another new version
        contentWriter = this.contentService.getWriter(checkedOut, ContentModel.PROP_CONTENT, true);
        assertNotNull(contentWriter);
        contentWriter.putContent(UPDATED_CONTENT_2);
        nodeService.setProperty(checkedOut, PROP_1, VALUE_2);
        checkOutCheckIn.checkin(checkedOut, null, contentWriter.getContentUrl(), false);
        Version version2 = createVersion(versionableNode);
        checkedOut = checkOutCheckIn.checkout(versionableNode);
        
        // Check we're now up to two versions
        // (The version created on the working copy doesn't count)
        VersionHistory history = versionService.getVersionHistory(versionableNode);
        assertEquals(version2.getVersionLabel(), history.getHeadVersion().getVersionLabel());
        assertEquals(version2.getVersionedNodeRef(), history.getHeadVersion().getVersionedNodeRef());
        assertEquals(2, history.getAllVersions().size());
        
        Version[] versions = history.getAllVersions().toArray(new Version[2]);
        assertEquals("0.2", versions[0].getVersionLabel());
        assertEquals("0.1", versions[1].getVersionLabel());
        
        
        // Add yet another version
        contentWriter = this.contentService.getWriter(checkedOut, ContentModel.PROP_CONTENT, true);
        assertNotNull(contentWriter);
        contentWriter.putContent(UPDATED_CONTENT_3);
        nodeService.setProperty(checkedOut, PROP_1, VALUE_3);
        checkOutCheckIn.checkin(checkedOut, null, contentWriter.getContentUrl(), false);
        Version version3 = createVersion(versionableNode);
        
        // Verify that the version labels are as we expect them to be
        history = versionService.getVersionHistory(versionableNode);
        assertEquals(version3.getVersionLabel(), history.getHeadVersion().getVersionLabel());
        assertEquals(version3.getVersionedNodeRef(), history.getHeadVersion().getVersionedNodeRef());
        assertEquals(3, history.getAllVersions().size());
        
        versions = history.getAllVersions().toArray(new Version[3]);
        assertEquals("0.3", versions[0].getVersionLabel());
        assertEquals("0.2", versions[1].getVersionLabel());
        assertEquals("0.1", versions[2].getVersionLabel());
        
        
        // Create a ScriptNode as used in Share
        ServiceRegistry services = applicationContext.getBean(ServiceRegistry.class); 
        ScriptNode scriptNode = new ScriptNode(versionableNode, services);
        assertEquals("0.3", nodeService.getProperty(scriptNode.getNodeRef(), ContentModel.PROP_VERSION_LABEL));
        assertEquals(VALUE_3, nodeService.getProperty(scriptNode.getNodeRef(), PROP_1));
        
        // Revert to version2
        // The content and properties will be the same as on Version 2, but we'll
        //  actually be given a new version number for it
        ScriptNode newNode = scriptNode.revert("History", false, version2.getVersionLabel());
        ContentReader contentReader = this.contentService.getReader(newNode.getNodeRef(), ContentModel.PROP_CONTENT);
        assertNotNull(contentReader);
        assertEquals(UPDATED_CONTENT_2, contentReader.getContentString());
        assertEquals(VALUE_2, nodeService.getProperty(newNode.getNodeRef(), PROP_1));
        // Will be a new version though - TODO Is this correct?
        assertEquals("0.4", nodeService.getProperty(newNode.getNodeRef(), ContentModel.PROP_VERSION_LABEL));
        
        // Revert to version1
        newNode = scriptNode.revert("History", false, version1.getVersionLabel());
        contentReader = this.contentService.getReader(newNode.getNodeRef(), ContentModel.PROP_CONTENT);
        assertNotNull(contentReader);
        assertEquals(UPDATED_CONTENT_1, contentReader.getContentString());
        assertEquals(VALUE_1, nodeService.getProperty(newNode.getNodeRef(), PROP_1));
        // Will be a new version though - TODO Is this correct?
        assertEquals("0.5", nodeService.getProperty(newNode.getNodeRef(), ContentModel.PROP_VERSION_LABEL));

        // All done
        setComplete();
        try
        {
            endTransaction();
        }
        catch(Throwable e)
        {
            fail("Transaction failed: " + e);
        }
    }
    
    /**
     * Test restore
     */
    public void testRestore()
    {
        // Try and restore a node without any version history
        try
        {
            this.versionService.restore(
                    new NodeRef(this.testStoreRef, "123"),
                    rootNodeRef, 
                    ContentModel.ASSOC_CHILDREN, 
                    QName.createQName("{test}MyVersionableNode"));
            fail("An exception should have been raised since this node has no version history.");
        }
        catch (VersionServiceException exception)
        {
            // We where expecting this exception
        }
        
        // Create a versionable node
        NodeRef versionableNode = createNewVersionableNode();
        
        // It isn't currently versionable
        assertEquals(null, versionService.getVersionHistory(versionableNode));
        
        // Store the node details for later
        Set<QName> origAspects = this.dbNodeService.getAspects(versionableNode);
        
        // Try and restore the node (won't be allowed as it already exists!)
        try
        {
            this.versionService.restore(
                    versionableNode,
                    rootNodeRef, 
                    ContentModel.ASSOC_CHILDREN, 
                    QName.createQName("{test}MyVersionableNode"));
            fail("An exception should have been raised since this node exists and you can't restore a node that exists.");
        }
        catch (VersionServiceException exception)
        {
            // We where expecting this exception
        }
        
        // Version it twice
        this.versionService.createVersion(versionableNode, null);
        this.versionService.createVersion(versionableNode, null);
        
        // Check we're now have a version history
        VersionHistory history = versionService.getVersionHistory(versionableNode);
        assertEquals("0.2", nodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));
        assertEquals("0.2", history.getHeadVersion().getVersionLabel());
        assertEquals(2, history.getAllVersions().size());
        
        
        // Delete the node
        this.dbNodeService.deleteNode(versionableNode);
        assertFalse(this.dbNodeService.exists(versionableNode));
        
        // You can still get the history of the node even though it's deleted
        history = versionService.getVersionHistory(versionableNode);
        assertEquals("0.2", history.getHeadVersion().getVersionLabel());
        assertEquals(2, history.getAllVersions().size());
        
        
        // Try and restore the node
        NodeRef restoredNode = this.versionService.restore(
                versionableNode, 
                this.rootNodeRef, 
                ContentModel.ASSOC_CHILDREN, 
                QName.createQName("{test}MyVersionableNode"));
        
        assertNotNull(restoredNode);
        assertTrue(this.dbNodeService.exists(restoredNode));
        
        // Check that the properties are correct
        assertEquals(VALUE_1, this.dbNodeService.getProperty(restoredNode, PROP_1));
        assertEquals(VALUE_2, this.dbNodeService.getProperty(restoredNode, PROP_2));
        assertEquals(VALUE_3, this.dbNodeService.getProperty(restoredNode, PROP_3));
        
        // Check that the content is correct
        ContentReader contentReader2 = this.contentService.getReader(restoredNode, ContentModel.PROP_CONTENT);
        assertNotNull(contentReader2);
        assertEquals(TEST_CONTENT, contentReader2.getContentString());
        
        // Check that the aspects have been reverted correctly
        Set<QName> aspects2 = this.dbNodeService.getAspects(restoredNode);
        assertEquals(aspects2.size(), origAspects.size());
        
        // Check the version is back to what it was
        history = versionService.getVersionHistory(restoredNode);
        assertEquals("0.2", history.getHeadVersion().getVersionLabel());
        assertEquals(2, history.getAllVersions().size());
        
        Version[] versions = history.getAllVersions().toArray(new Version[2]);
        assertEquals("0.2", versions[0].getVersionLabel());
        assertEquals("0.1", versions[1].getVersionLabel());
        
        // TODO Shouldn't these point to the restored node?
        //assertEquals(restoredNode, versions[0].getFrozenStateNodeRef());
        //assertEquals(restoredNode, versions[1].getFrozenStateNodeRef());
        
        // TODO Should we really be having reference to version store
        //  as the frozen state noderef?
        assertEquals(VersionService.VERSION_STORE_PROTOCOL, versions[0].getFrozenStateNodeRef().getStoreRef().getProtocol());
        assertEquals(VersionService.VERSION_STORE_PROTOCOL, versions[1].getFrozenStateNodeRef().getStoreRef().getProtocol());
        
        // The restored node won't have a version label set though
        // TODO Is this correct?
        assertEquals(null, nodeService.getProperty(restoredNode, ContentModel.PROP_VERSION_LABEL));
    }
    
    /**
     * Test deleteVersionHistory
     */
    public void testDeleteVersionHistory()
    {
    	// Create a versionable node
    	NodeRef versionableNode = createNewVersionableNode();
    	
    	// Check that there is no version history
    	VersionHistory versionHistory1 = this.versionService.getVersionHistory(versionableNode);
    	assertNull(versionHistory1);
    	
    	// Create a couple of versions
    	createVersion(versionableNode);
    	Version version1 = createVersion(versionableNode);
    	
    	// Check that the version label is correct on the versionable node
    	String versionLabel1 = (String)this.dbNodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL);
    	assertNotNull(versionLabel1);
    	assertEquals(version1.getVersionLabel(), versionLabel1);
    	
    	// Check that the version history has been created correctly
    	VersionHistory versionHistory2 = this.versionService.getVersionHistory(versionableNode);
    	assertNotNull(versionHistory2);
    	assertEquals(2, versionHistory2.getAllVersions().size());
    	
    	// Delete the version history
    	this.versionService.deleteVersionHistory(versionableNode);
    	
    	// Check that there is no version history available for the node
    	VersionHistory versionHistory3 = this.versionService.getVersionHistory(versionableNode);
    	assertNull(versionHistory3);
    	
    	// Check that the current version property on the versionable node is no longer set
    	String versionLabel2 = (String)this.dbNodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL);
    	assertNull(versionLabel2);
    	
    	// Create a couple of versions
    	createVersion(versionableNode);
    	Version version2 = createVersion(versionableNode);
    	
    	// Check that the version history is correct
    	VersionHistory versionHistory4 = this.versionService.getVersionHistory(versionableNode);
    	assertNotNull(versionHistory4);
    	assertEquals(2, versionHistory4.getAllVersions().size());
    	
    	// Check that the version label is correct on the versionable node    
    	String versionLabel3 = (String)this.dbNodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL);
    	assertNotNull(versionLabel3);
    	assertEquals(version2.getVersionLabel(), versionLabel3);
    	
    }
    
    /**
     * Test deleteVersion
     */
    public void testDeleteVersion()
    {
        // Use 1.0, 2.0 etc for the main part
        versionProperties.put(VersionModel.PROP_VERSION_TYPE, VersionType.MAJOR);
       
        // Create a versionable node
        NodeRef versionableNode = createNewVersionableNode();
        
        // Check that there is no version history
        VersionHistory versionHistory = this.versionService.getVersionHistory(versionableNode);
        CheckVersionHistory(versionHistory, null);
        
        // Check that the current version property on the versionable node is not set
        String versionLabel = (String)this.dbNodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL);
        assertNull(versionLabel);
        
        // Check that there is no current version
        Version version = this.versionService.getCurrentVersion(versionableNode);
        assertNull(version);
        
        // Create a couple of versions
        Version version1 = createVersion(versionableNode);
        Version version2 = createVersion(versionableNode);
        
        // Check that the version label is correct on the versionable node
        String versionLabel1 = (String)this.dbNodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL);
        assertEquals("first version label", "2.0", versionLabel1);
        assertEquals(version2.getVersionLabel(), versionLabel1);
        
        // Check the version history
        List<Version> expectedVersions = new ArrayList<Version>(2);
        expectedVersions.add(version2);    // 2.0
        expectedVersions.add(version1);    // 1.0
        versionHistory = this.versionService.getVersionHistory(versionableNode);
        assertEquals(2, versionHistory.getAllVersions().size());
        CheckVersionHistory(versionHistory, expectedVersions);
        
        // Check the versions on the history
        Version[] versions = versionHistory.getAllVersions().toArray(new Version[2]);
        assertEquals("2.0", versions[0].getVersionLabel()); 
        assertEquals("1.0", versions[1].getVersionLabel()); 
        
        
        // Check current version
        Version currentVersion = this.versionService.getCurrentVersion(versionableNode);
        assertEquals(currentVersion.getVersionLabel(), version2.getVersionLabel());
        assertEquals(currentVersion.getFrozenStateNodeRef(), version2.getFrozenStateNodeRef());
        
        // Create a couple more versions
        Version version3 = createVersion(versionableNode);  // 3.0
        Version version4 = createVersion(versionableNode);  // 4.0
        
        // Check that the version label is correct on the versionable node
        String versionLabel4 = (String)this.dbNodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL);
        assertEquals("4.0", versionLabel4);
        assertEquals(version4.getVersionLabel(), versionLabel4);
        
        // Check the version history
        expectedVersions = new ArrayList<Version>(4);
        expectedVersions.add(version4);
        expectedVersions.add(version3);
        expectedVersions.add(version2);
        expectedVersions.add(version1);
        versionHistory = this.versionService.getVersionHistory(versionableNode);
        assertEquals(4, versionHistory.getAllVersions().size());
        CheckVersionHistory(versionHistory, expectedVersions);
        
        // Check current version
        currentVersion = this.versionService.getCurrentVersion(versionableNode);
        assertEquals(currentVersion.getVersionLabel(), version4.getVersionLabel());
        assertEquals(currentVersion.getFrozenStateNodeRef(), version4.getFrozenStateNodeRef());
        assertEquals("4.0", currentVersion.getVersionLabel());

        // Delete version 3.0
        this.versionService.deleteVersion(versionableNode, version3);
        
        // Delete version 1.0
        this.versionService.deleteVersion(versionableNode, version1);
        
        // Check the version history
        expectedVersions = new ArrayList<Version>(2);
        expectedVersions.add(version4);
        expectedVersions.add(version2);
        versionHistory = this.versionService.getVersionHistory(versionableNode);
        assertEquals(2, versionHistory.getAllVersions().size());
        CheckVersionHistory(versionHistory, expectedVersions);
        
        // Check current version is unchanged
        currentVersion = this.versionService.getCurrentVersion(versionableNode);
        assertEquals(currentVersion.getVersionLabel(), version4.getVersionLabel());
        assertEquals(currentVersion.getFrozenStateNodeRef(), version4.getFrozenStateNodeRef());
        
        // Delete version 4
        this.versionService.deleteVersion(versionableNode, version4);
        
        // Check the version history size
        expectedVersions = new ArrayList<Version>(1);
        expectedVersions.add(version2);
        versionHistory = this.versionService.getVersionHistory(versionableNode);
        assertEquals(1, versionHistory.getAllVersions().size());
        CheckVersionHistory(versionHistory, expectedVersions);
        
        // Check current version has changed to version 2.0
        currentVersion = this.versionService.getCurrentVersion(versionableNode);
        assertEquals(currentVersion.getVersionLabel(), version2.getVersionLabel());
        assertEquals(currentVersion.getFrozenStateNodeRef(), version2.getFrozenStateNodeRef());
        
        
        // Create a new version. As 3.0 and 4.0 have been deleted, will be 3.0 again
        Version version3n = createVersion(versionableNode);
        
        // Check the version history size
        expectedVersions = new ArrayList<Version>(2);
        expectedVersions.add(version3n);
        expectedVersions.add(version2);
        versionHistory = this.versionService.getVersionHistory(versionableNode);
        assertEquals(2, versionHistory.getAllVersions().size());
        CheckVersionHistory(versionHistory, expectedVersions);
        
        // Check current version has changed to version 3.0
        currentVersion = this.versionService.getCurrentVersion(versionableNode);
        assertEquals(currentVersion.getVersionLabel(), version3n.getVersionLabel());
        assertEquals(currentVersion.getFrozenStateNodeRef(), version3n.getFrozenStateNodeRef());
        assertEquals("3.0", currentVersion.getVersionLabel());
        
        
        // Create versions 3.1 and 3.2
        versionProperties.put(VersionModel.PROP_VERSION_TYPE, VersionType.MINOR);        
        Version version31 = createVersion(versionableNode);
        Version version32 = createVersion(versionableNode);
        
        versionHistory = this.versionService.getVersionHistory(versionableNode);
        assertEquals(4, versionHistory.getAllVersions().size());
        expectedVersions = new ArrayList<Version>(2);
        expectedVersions.add(version32);
        expectedVersions.add(version31);
        expectedVersions.add(version3n);
        expectedVersions.add(version2);
        CheckVersionHistory(versionHistory, expectedVersions);
        
        // Check the current version is now 3.2
        currentVersion = this.versionService.getCurrentVersion(versionableNode);
        assertEquals(currentVersion.getVersionLabel(), version32.getVersionLabel());
        assertEquals(currentVersion.getFrozenStateNodeRef(), version32.getFrozenStateNodeRef());
        assertEquals("3.2", currentVersion.getVersionLabel());
        
        
        // Delete version 3.1
        versionService.deleteVersion(versionableNode, version31);
        
        versionHistory = this.versionService.getVersionHistory(versionableNode);
        assertEquals(3, versionHistory.getAllVersions().size());
        expectedVersions.remove(version31);
        CheckVersionHistory(versionHistory, expectedVersions);
        
        // Current version is still 3.2
        currentVersion = this.versionService.getCurrentVersion(versionableNode);
        assertEquals(currentVersion.getVersionLabel(), version32.getVersionLabel());
        assertEquals(currentVersion.getFrozenStateNodeRef(), version32.getFrozenStateNodeRef());

        
        // Delete version 3.2
        versionService.deleteVersion(versionableNode, version32);
        
        versionHistory = this.versionService.getVersionHistory(versionableNode);
        assertEquals(2, versionHistory.getAllVersions().size());
        expectedVersions.remove(version32);
        CheckVersionHistory(versionHistory, expectedVersions);
        
        // Check current version is back to 3.0
        currentVersion = this.versionService.getCurrentVersion(versionableNode);
        assertEquals(currentVersion.getVersionLabel(), version3n.getVersionLabel());
        assertEquals(currentVersion.getFrozenStateNodeRef(), version3n.getFrozenStateNodeRef());
        assertEquals("3.0", currentVersion.getVersionLabel());
        
        
        // Delete version 2.0
        this.versionService.deleteVersion(versionableNode, version2);
        currentVersion = this.versionService.getCurrentVersion(versionableNode);
        assertEquals(currentVersion.getVersionLabel(), version3n.getVersionLabel());
        assertEquals(currentVersion.getFrozenStateNodeRef(), version3n.getFrozenStateNodeRef());
        
        // Delete version 3.0 (our last version)
        this.versionService.deleteVersion(versionableNode, version3n);
        
        // Check the version history is empty
        versionHistory = this.versionService.getVersionHistory(versionableNode);
        CheckVersionHistory(versionHistory, null);
        
        // Check that the current version property on the versionable node is no longer set
        versionLabel = (String)this.dbNodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL);
        assertNull(versionLabel);
        
        // Check that there is no current version
        version = this.versionService.getCurrentVersion(versionableNode);
        assertNull(version);
    }
    
    public void testAutoVersionOnInitialVersionOn()
    {
        // Create a versionable node
        final NodeRef versionableNode = createNewVersionableNode();
        
        setComplete();
        endTransaction();

        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                // Check that the initial version has not been created
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());
                
                // Add some content 
                ContentWriter contentWriter = contentService.getWriter(versionableNode, ContentModel.PROP_CONTENT, true);
                assertNotNull(contentWriter);
                contentWriter.putContent(UPDATED_CONTENT_1);
                
                return null;
            }
        });
        
        // Now lets have a look and make sure we have the correct number of entries in the version history
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(2, versionHistory.getAllVersions().size());
                
                return null;
            }
        
        });
    }
    
    public void testAutoVersionOff()
    {
        // Create a versionable node
        final NodeRef versionableNode = createNewVersionableNode();
        
        this.dbNodeService.setProperty(versionableNode, ContentModel.PROP_AUTO_VERSION, false);
        
        setComplete();
        endTransaction();
        
        // The initial version should have been created now
        
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                // Add some content 
                ContentWriter contentWriter = contentService.getWriter(versionableNode, ContentModel.PROP_CONTENT, true);
                assertNotNull(contentWriter);
                contentWriter.putContent(UPDATED_CONTENT_1);
                
                return null;
            }
        });
        
        // Now lets have a look and make sure we have the correct number of entries in the version history
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());
                
                return null;
            }
        
        });
    }
    
    public void testInitialVersionOff()
    {
        // Create node (this node has some content)
        HashMap<QName, Serializable> props = new HashMap<QName, Serializable>();
        props.put(ContentModel.PROP_INITIAL_VERSION, false);
        HashMap<QName, Serializable> props2 = new HashMap<QName, Serializable>();
        props2.put(ContentModel.PROP_NAME, "test.txt");
        final NodeRef nodeRef = this.dbNodeService.createNode(
                rootNodeRef, 
                ContentModel.ASSOC_CHILDREN, 
                QName.createQName("{test}MyVersionableNode2"),
                TEST_TYPE_QNAME,
                props2).getChildRef();
        this.dbNodeService.addAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE, props);
        
        setComplete();
        endTransaction();

        // The initial version should NOT have been created
        
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(nodeRef);
                assertNull(versionHistory);
                
                return null;
            }
        });
       
    }
    
    public void testAddVersionableAspectWithNoVersionType()
    {
        // No version-type specified when adding the aspect
        NodeRef nodeRef = createNodeWithVersionType(null);
        setComplete();
        endTransaction();
        assertCorrectVersionLabel(nodeRef, "1.0");
    }

    public void testAddVersionableAspectWithMinorVersionType()
    {
        // MINOR version-type specified when adding the aspect
        NodeRef nodeRef = createNodeWithVersionType(VersionType.MINOR);
        setComplete();
        endTransaction();
        assertCorrectVersionLabel(nodeRef, "0.1");
    }
    
    public void testAddVersionableAspectWithMajorVersionType()
    {
        // MAJOR version-type specified when adding the aspect
        NodeRef nodeRef = createNodeWithVersionType(VersionType.MAJOR);
        setComplete();
        endTransaction();
        assertCorrectVersionLabel(nodeRef, "1.0");
    }
    
    private void assertCorrectVersionLabel(final NodeRef nodeRef, final String versionLabel)
    {
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                // Check that the version history has been created
                VersionHistory versionHistory = versionService.getVersionHistory(nodeRef);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());
                Version version = versionService.getCurrentVersion(nodeRef);
                assertEquals("Wrong version label", versionLabel, version.getVersionLabel());
                
                return null;
            }
        });
    }
    
    private NodeRef createNodeWithVersionType(VersionType versionType)
    {
        HashMap<QName, Serializable> props = new HashMap<QName, Serializable>();
        props.put(ContentModel.PROP_NAME, "test.txt");
        
        final NodeRef nodeRef = dbNodeService.createNode(
                rootNodeRef, 
                ContentModel.ASSOC_CHILDREN, 
                QName.createQName("{test}MyVersionableNode"),
                TEST_TYPE_QNAME,
                props).getChildRef();
        
        HashMap<QName, Serializable> aspectProps = new HashMap<QName, Serializable>();
        if (versionType != null)
        {
            aspectProps.put(ContentModel.PROP_VERSION_TYPE, versionType);
        }
        dbNodeService.addAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE, aspectProps);
        
        return nodeRef;
    }

    public void testAddRemoveVersionableAspect()
    {
    	HashMap<QName, Serializable> props2 = new HashMap<QName, Serializable>();
        props2.put(ContentModel.PROP_NAME, "test.txt");
        final NodeRef nodeRef = this.dbNodeService.createNode(
                rootNodeRef, 
                ContentModel.ASSOC_CHILDREN, 
                QName.createQName("{test}MyVersionableNode2"),
                TEST_TYPE_QNAME,
                props2).getChildRef();
        this.dbNodeService.addAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE, null);
        
        setComplete();
        endTransaction();
        
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
            	// Check that the version history has been created
                VersionHistory versionHistory = versionService.getVersionHistory(nodeRef);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());
                
                // Remove the versionable aspect 
                dbNodeService.removeAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE);
                
                return null;
            }
        });
        
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
            	// Check that the version history has been removed
                VersionHistory versionHistory = versionService.getVersionHistory(nodeRef);
                assertNull(versionHistory);
                
                // Re-add the versionable aspect
                dbNodeService.addAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE, null);
                
                return null;
            }
        });
        
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
            	// Check that the version history has been created 
                VersionHistory versionHistory = versionService.getVersionHistory(nodeRef);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());                
                
                return null;
            }
        });
    }
    
    public void testAutoRemovalOfVersionHistory()
    {
    	StoreRef spacesStoreRef = new StoreRef(StoreRef.PROTOCOL_WORKSPACE, "SpacesStore");
    	NodeRef root = this.dbNodeService.getRootNode(spacesStoreRef);
    	
    	HashMap<QName, Serializable> props2 = new HashMap<QName, Serializable>();
        props2.put(ContentModel.PROP_NAME, "test-" + GUID.generate() + ".txt");
        final NodeRef nodeRef = this.dbNodeService.createNode(
                root, 
                ContentModel.ASSOC_CHILDREN, 
                QName.createQName("{test}MyVersionableNode2"),
                ContentModel.TYPE_CONTENT,
                props2).getChildRef();
        this.dbNodeService.addAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE, null);
        
        setComplete();
        endTransaction();
        
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
            	VersionHistory versionHistory = versionService.getVersionHistory(nodeRef);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());
                
            	// Delete the node
                dbNodeService.deleteNode(nodeRef);
                
                return null;
            }
        });
        
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
            	// Get the archived noderef
            	NodeRef archivedNodeRef = nodeArchiveService.getArchivedNode(nodeRef);
            	
            	// The archived noderef should still have a link to the version history
            	VersionHistory versionHistory = versionService.getVersionHistory(archivedNodeRef);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size()); 
                
                // Delete the node for good
                dbNodeService.deleteNode(archivedNodeRef);
                
                return null;
            }
        });
        
        txnHelper.doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
            	// Get the archived noderef
            	NodeRef archivedNodeRef = nodeArchiveService.getArchivedNode(nodeRef);
            	
            	// Check that the version histories have been deleted
            	VersionHistory versionHistory12 = versionService.getVersionHistory(nodeRef);
                assertNull(versionHistory12);
            	VersionHistory versionHistory23 = versionService.getVersionHistory(archivedNodeRef);
                assertNull(versionHistory23);
                
                return null;
            }
        });
    }
    
    public void testAutoVersionOnUpdatePropsOnly()
    {
        // test auto-version props on
        final NodeRef versionableNode = createNewVersionableNode();
        this.dbNodeService.setProperty(versionableNode, ContentModel.PROP_AUTO_VERSION_PROPS, true);
        
        setComplete();
        endTransaction();
        
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());
                
                nodeService.setProperty(versionableNode, ContentModel.PROP_AUTHOR, "ano author 1");
                
                return null;
            }
        });
        
        // Now lets have a look and make sure we have the correct number of entries in the version history
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(2, versionHistory.getAllVersions().size());
                
                return null;
            }
        
        });
        
        //Checking whether VersionModel.PROP_VERSION_TYPE set to MINOR type after update node properties
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                nodeService.setProperty(versionableNode, ContentModel.PROP_DESCRIPTION, "test description");
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                VersionType vType = (VersionType) versionHistory.getHeadVersion().getVersionProperty(VersionModel.PROP_VERSION_TYPE);
                assertNotNull("Is not setted the version type", vType);
                assertEquals(vType, VersionType.MINOR);
                return null;
            }
        
        });
        
        
        // test auto-version props off
        
        startNewTransaction();
        
        final NodeRef versionableNode2 = createNewVersionableNode();
        this.dbNodeService.setProperty(versionableNode2, ContentModel.PROP_AUTO_VERSION_PROPS, false);
        
        setComplete();
        endTransaction();
        
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode2);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());
                
                nodeService.setProperty(versionableNode2, ContentModel.PROP_AUTHOR, "ano author 2");
                
                return null;
            }
        });
        
        // Now lets have a look and make sure we have the correct number of entries in the version history
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode2);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());
                
                return null;
            }
        
        });
    }
    
    public void testAutoVersionOnUpdatePropsOnlyWithExcludes()
    {
        // test auto-version props on - without any excludes
        final NodeRef versionableNode = createNewVersionableNode();
        this.dbNodeService.setProperty(versionableNode, ContentModel.PROP_AUTO_VERSION_PROPS, true);
        
        setComplete();
        endTransaction();
        
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());
                
                nodeService.setProperty(versionableNode, ContentModel.PROP_AUTHOR, "ano author 1");
                nodeService.setProperty(versionableNode, ContentModel.PROP_DESCRIPTION, "description 1");
                
                return null;
            }
        });
        
        // Now lets have a look and make sure we have the correct number of entries in the version history
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(2, versionHistory.getAllVersions().size());
                
                // Check version labels, should be 0.2 and 0.1 as property changes
                //  are minor updates, and we had no initial label set
                Version[] versions = versionHistory.getAllVersions().toArray(new Version[2]);
                assertEquals("1.1", versions[0].getVersionLabel());
                assertEquals("1.0", versions[1].getVersionLabel());
                
                return null;
            }
        });
        
        List<String> excludedOnUpdateProps = new ArrayList<String>(1);
        NamespaceService namespaceService = (NamespaceService) applicationContext.getBean("namespaceService");
        excludedOnUpdateProps.add(ContentModel.PROP_AUTHOR.toPrefixString(namespaceService));
        versionableAspect.setExcludedOnUpdateProps(excludedOnUpdateProps);
        versionableAspect.afterDictionaryInit();
        
        // test auto-version props on - with an excluded prop change
        
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(2, versionHistory.getAllVersions().size());
                
                nodeService.setProperty(versionableNode, ContentModel.PROP_AUTHOR, "ano author 2");
                nodeService.setProperty(versionableNode, ContentModel.PROP_DESCRIPTION, "description 2");
                
                return null;
            }
        });
        
        // Now lets have a look and make sure we have the correct number of entries in the version history
        // (The property changes were excluded so there should have been no changes)
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(2, versionHistory.getAllVersions().size());
                
                return null;
            }
        
        });
        
        //Checking whether VersionModel.PROP_VERSION_TYPE set to MINOR type after update node properties
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                VersionType vType = (VersionType) versionHistory.getHeadVersion().getVersionProperty(VersionModel.PROP_VERSION_TYPE);
                assertNotNull("Is not setted the version type", vType);
                assertEquals(vType, VersionType.MINOR);
                return null;
            }
        
        });
        
        // test auto-version props on - with a non-excluded prop change
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(2, versionHistory.getAllVersions().size());
                
                nodeService.setProperty(versionableNode, ContentModel.PROP_DESCRIPTION, "description 3");
                
                return null;
            }
        });
        
        // Now lets have a look and make sure we have the correct number of entries in the version history
        // (We should have gained one more)
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(3, versionHistory.getAllVersions().size());
                
                // Check the versions, 
                Version[] versions = versionHistory.getAllVersions().toArray(new Version[3]);
                assertEquals("1.2", versions[0].getVersionLabel());
                assertEquals("1.1", versions[1].getVersionLabel());
                assertEquals("1.0", versions[2].getVersionLabel());
                
                return null;
            }
        });
        
        // Delete version 0.2, auto changes won't affect this
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
               VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
               Version[] versions = versionHistory.getAllVersions().toArray(new Version[3]);
               
               Version v = versions[1];
               assertEquals("1.1", v.getVersionLabel());
               versionService.deleteVersion(versionableNode, v);
               return null;
            }
        });
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(2, versionHistory.getAllVersions().size());
                
                // Check the versions, will now have a gap 
                Version[] versions = versionHistory.getAllVersions().toArray(new Version[2]);
                assertEquals("1.2", versions[0].getVersionLabel());
                assertEquals("1.0", versions[1].getVersionLabel());
                return null;
            }
        });
        
        // Delete the head version, will revert back to 0.1
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
               Version v = versionService.getCurrentVersion(versionableNode);
               assertEquals("1.2", v.getVersionLabel());
               versionService.deleteVersion(versionableNode, v);
               return null;
            }
        });
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());
                
                // Check the version 
                Version[] versions = versionHistory.getAllVersions().toArray(new Version[1]);
                assertEquals("1.0", versions[0].getVersionLabel());
                return null;
            }
        });
    }
    
    public void testAutoVersionWithPropsOnRevert()
    {
       // test auto-version props on - without any excludes
       final NodeRef versionableNode = createNewVersionableNode();
       nodeService.setProperty(versionableNode, ContentModel.PROP_AUTO_VERSION_PROPS, true);
       nodeService.setProperty(versionableNode, ContentModel.PROP_DESCRIPTION, "description 0");
       nodeService.setProperty(versionableNode, PROP_1, VALUE_1);
       
       // Force it to be 2.0
       Map<String,Serializable> vprops = new HashMap<String, Serializable>();
       vprops.put(VersionModel.PROP_VERSION_TYPE, VersionType.MAJOR);
       versionService.createVersion(versionableNode, vprops);
       versionService.createVersion(versionableNode, vprops);
       
       // Check it's 2.0
       assertEquals("2.0", nodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));
       
       // Zap 1.0
       versionService.deleteVersion(versionableNode,
            versionService.getVersionHistory(versionableNode).getVersion("1.0"));
       
       // Ready to test
       setComplete();
       endTransaction();
       
       // Check the first version is now 2.0
       transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
       {
          public Object execute() throws Exception
          {
             VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
             assertNotNull(versionHistory);
             assertEquals(1, versionHistory.getAllVersions().size());

             assertEquals("2.0", versionHistory.getHeadVersion().getVersionLabel());
             assertEquals("2.0", nodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));
             return null;
          }
       });
       
       // Create a few more versions
       transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
       {
          public Object execute() throws Exception
          {
             nodeService.setProperty(versionableNode, ContentModel.PROP_AUTHOR, "ano author 2");
             nodeService.setProperty(versionableNode, ContentModel.PROP_DESCRIPTION, "description 2");
             nodeService.setProperty(versionableNode, PROP_1, VALUE_2);
             return null;
          }
       });
       transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
       {
          public Object execute() throws Exception
          {
             nodeService.setProperty(versionableNode, ContentModel.PROP_AUTHOR, "ano author 3");
             nodeService.setProperty(versionableNode, ContentModel.PROP_DESCRIPTION, "description 3");
             nodeService.setProperty(versionableNode, PROP_1, VALUE_3);
             return null;
          }
       });
       
       // Check the history is correct
       transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
       {
          public Object execute() throws Exception
          {
             VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
             assertNotNull(versionHistory);
             assertEquals(3, versionHistory.getAllVersions().size());

             assertEquals("2.2", versionHistory.getHeadVersion().getVersionLabel());
             assertEquals("2.2", nodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));
             
             // Check the version 
             Version[] versions = versionHistory.getAllVersions().toArray(new Version[3]);
             assertEquals("2.2", versions[0].getVersionLabel());
             assertEquals("2.1", versions[1].getVersionLabel());
             assertEquals("2.0", versions[2].getVersionLabel());
             return null;
          }
       });
       
       // Delete the middle version
       transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
       {
          public Object execute() throws Exception
          {
             VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
             Version v21 = versionHistory.getVersion("2.1");
             versionService.deleteVersion(versionableNode, v21);
             return null;
          }
       });
       // Check the history now
       transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
       {
          public Object execute() throws Exception
          {
             VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
             assertNotNull(versionHistory);
             assertEquals(2, versionHistory.getAllVersions().size());

             assertEquals("2.2", versionHistory.getHeadVersion().getVersionLabel());
             assertEquals("2.2", nodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));
             
             // Check the version 
             Version[] versions = versionHistory.getAllVersions().toArray(new Version[2]);
             assertEquals("2.2", versions[0].getVersionLabel());
             assertEquals("2.0", versions[1].getVersionLabel());
             return null;
          }
       });
       
       // Revert to V2.0
       transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
       {
          public Object execute() throws Exception
          {
             VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
             Version v20 = versionHistory.getVersion("2.0");
             versionService.revert(versionableNode, v20);
             return null;
          }
       });
       
       // Check things went back as expected
       transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
       {
          public Object execute() throws Exception
          {
             // Still has two in the version history
             VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
             assertNotNull(versionHistory);
             assertEquals(2, versionHistory.getAllVersions().size());

             Version[] versions = versionHistory.getAllVersions().toArray(new Version[2]);
             assertEquals("2.2", versions[0].getVersionLabel());
             assertEquals("2.0", versions[1].getVersionLabel());
             
             // Head version is still 2.2
             assertEquals("2.2", versionHistory.getHeadVersion().getVersionLabel());
             
             // But the node is back at 2.0
             // TODO Shouldn't the node be at 2.0 now, not 2.2?
             //assertEquals("2.0", nodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));
             assertEquals("2.2", nodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL));
             
             // And the properties show it has gone back
             assertEquals(VALUE_1, nodeService.getProperty(versionableNode, PROP_1));
             assertEquals("description 0", nodeService.getProperty(versionableNode, ContentModel.PROP_DESCRIPTION));
             return null;
          }
       });
    }
    
    public void testALF5618()
    {
        final NodeRef versionableNode = createNewVersionableNode();
        this.dbNodeService.setProperty(versionableNode, ContentModel.PROP_AUTO_VERSION_PROPS, true);
        
        setComplete();
        endTransaction();
        
        final String lockToken = "opaquelocktoken:" + versionableNode.getId() + ":admin";
        
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());                
                return null;
            }
        });
        
        // Now lets have a look and make sure we have the correct number of entries in the version history
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());
                return null;
            }
        
        });
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());
                return null;
            }
        });

        // Now lets have a look and make sure we have the correct number of entries in the version history
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                VersionHistory versionHistory = versionService.getVersionHistory(versionableNode);
                assertNotNull(versionHistory);
                assertEquals(1, versionHistory.getAllVersions().size());

                return null;
            }

        });
    }
    
    public void testAR807() 
    {
    	QName prop = QName.createQName("http://www.alfresco.org/test/versionstorebasetest/1.0", "intProp");
    	
        ChildAssociationRef childAssociation = 
        	nodeService.createNode(this.rootNodeRef, 
                    				 ContentModel.ASSOC_CHILDREN, 
                    				 QName.createQName("http://www.alfresco.org/test/versionstorebasetest/1.0", "integerTest"), 
                    				 TEST_TYPE_QNAME);
        NodeRef newNode = childAssociation.getChildRef();
        nodeService.setProperty(newNode, prop, 1);

        Object editionCode = nodeService.getProperty(newNode, prop);
        assertEquals(editionCode.getClass(), Integer.class);

        Map<String, Serializable> versionProps = new HashMap<String, Serializable>(1);
        versionProps.put(VersionModel.PROP_VERSION_TYPE, VersionType.MAJOR);
        Version version = versionService.createVersion(newNode, versionProps);

        NodeRef versionNodeRef = version.getFrozenStateNodeRef();
        assertNotNull(versionNodeRef);
        
        Object editionCodeArchive = nodeService.getProperty(versionNodeRef, prop);
        assertEquals(editionCodeArchive.getClass(), Integer.class);
    }
    
    /**
     * Check read permission for the frozen node
     */
    public void testHasPermission()
    {
        AuthenticationUtil.setFullyAuthenticatedUser(AuthenticationUtil.getAdminUserName());
        
        if(!authenticationDAO.userExists(USER_NAME_A))
        {
            authenticationService.createAuthentication(USER_NAME_A, PWD_A.toCharArray());
        }
        
        permissionService.setPermission(rootNodeRef, PermissionService.ALL_AUTHORITIES, PermissionService.READ, true);
        permissionService.setInheritParentPermissions(rootNodeRef, true);
        
        // Create a new versionable node
        NodeRef versionableNode = createNewVersionableNode();
        
        // Create a new version
        Version version = createVersion(versionableNode, versionProperties);
        NodeRef versionNodeRef = version.getFrozenStateNodeRef();
        
        assertEquals(AccessStatus.ALLOWED, permissionService.hasPermission(versionNodeRef, PermissionService.READ));
        
        AuthenticationUtil.setFullyAuthenticatedUser(USER_NAME_A);
        
        assertEquals(AccessStatus.ALLOWED, permissionService.hasPermission(versionNodeRef, PermissionService.READ));
        
        AuthenticationUtil.setFullyAuthenticatedUser(AuthenticationUtil.getAdminUserName());
        
        permissionService.setInheritParentPermissions(versionableNode, false);
        
        assertEquals(AccessStatus.ALLOWED, permissionService.hasPermission(versionNodeRef, PermissionService.READ));
        
        AuthenticationUtil.setFullyAuthenticatedUser(USER_NAME_A);
        
        assertEquals(AccessStatus.DENIED, permissionService.hasPermission(versionNodeRef, PermissionService.READ));
    }
    
    public void testALF_3962()
    {
        NodeRef versionableNode = createNode(true, QName.createQName("http://www.alfresco.org/model/action/1.0", "action"));
        
        // create some versions of content without version label policy
        createVersion(versionableNode);
        createVersion(versionableNode);
        createVersion(versionableNode);
        
        // create some more versions and force them to have same frozen modified date
        Version ver = createVersion(versionableNode);
        Date frozenModifiedDate = ver.getFrozenModifiedDate();
        
        ver = createVersion(versionableNode);
        NodeRef versionNodeRef = VersionUtil.convertNodeRef(ver.getFrozenStateNodeRef());
        this.dbNodeService.setProperty(versionNodeRef, Version2Model.PROP_QNAME_FROZEN_MODIFIED, frozenModifiedDate);
        
        ver = createVersion(versionableNode);
        versionNodeRef = VersionUtil.convertNodeRef(ver.getFrozenStateNodeRef());
        this.dbNodeService.setProperty(versionNodeRef, Version2Model.PROP_QNAME_FROZEN_MODIFIED, frozenModifiedDate);
        
        // corrupt versions
        Collection<Version> versions = versionService.getVersionHistory(versionableNode).getAllVersions();
        
        List<Version> oldVersions = new ArrayList<Version>(versions.size());

        for (Version version : versions)
        {
            // update version with corrupted label
            versionNodeRef = VersionUtil.convertNodeRef(version.getFrozenStateNodeRef());
            this.dbNodeService.setProperty(versionNodeRef, Version2Model.PROP_QNAME_VERSION_LABEL, "0");
            
            // cache results
            oldVersions.add(version);
        }
        this.nodeService.setProperty(versionableNode, ContentModel.PROP_VERSION_LABEL, "0");

        // should correct version labels
        versionService.createVersion(versionableNode, this.versionProperties);

        versions = versionService.getVersionHistory(versionableNode).getAllVersions();
        List<Version> newVersions = new ArrayList<Version>(versions.size());

        for (Version version : versions)
        {
            assertFalse(version.getVersionLabel().equals("0"));
            newVersions.add(version);
        }

        // check live node
        assertFalse(this.nodeService.getProperty(versionableNode, ContentModel.PROP_VERSION_LABEL).toString().equals("0"));

        //check order        
        for (int i = 0; i < oldVersions.size(); i++)
        {
            Version oldVersion = oldVersions.get(i);
            Version newVersion = newVersions.get(i + 1);

            assertEquals(oldVersion.getFrozenModifiedDate(), newVersion.getFrozenModifiedDate());

            assertEquals(oldVersion.getVersionLabel(), newVersion.getVersionLabel());
            String nodeDbidKey = ContentModel.PROP_NODE_DBID.getLocalName();
            assertEquals(oldVersion.getVersionProperty(nodeDbidKey), newVersion.getVersionProperty(nodeDbidKey));
            String nodeUuidKey = ContentModel.PROP_NODE_UUID.getLocalName();
            assertEquals(oldVersion.getVersionProperty(nodeUuidKey), newVersion.getVersionProperty(nodeUuidKey));
        }
    }
    
    /**
     * Ensure that versioning actions don't alter the auditable
     *  aspect properties on the original nodes
     */
    public void testVersioningAndAuditable() throws Exception {
        AuthenticationUtil.setFullyAuthenticatedUser(AuthenticationUtil.getAdminUserName());
        if(!authenticationDAO.userExists(USER_NAME_A))
        {
            authenticationService.createAuthentication(USER_NAME_A, PWD_A.toCharArray());
        }
        
        // Create a node as the "A" user
        NodeRef nodeA = AuthenticationUtil.runAs(new RunAsWork<NodeRef>()
           {
             @Override
             public NodeRef doWork() throws Exception
             {
                 return transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<NodeRef>()
                   {
                      public NodeRef execute() throws Exception
                      {
                         AuthenticationUtil.setFullyAuthenticatedUser(USER_NAME_A);
                         NodeRef a = nodeService.createNode(
                                 rootNodeRef, 
                                 ContentModel.ASSOC_CONTAINS, 
                                 QName.createQName("{test}NodeForA"),
                                 ContentModel.TYPE_CONTENT
                         ).getChildRef();
                         nodeService.addAspect(a, ContentModel.ASPECT_AUDITABLE, null);
                         return a;
                      }
                   }
                 );
             }
           }, USER_NAME_A
        );
        
        // Check that it's owned by A
        assertEquals(USER_NAME_A, nodeService.getProperty(nodeA, ContentModel.PROP_CREATOR));
        assertEquals(USER_NAME_A, nodeService.getProperty(nodeA, ContentModel.PROP_MODIFIER));
        assertEquals(false, nodeService.hasAspect(nodeA, ContentModel.ASPECT_VERSIONABLE));
        
        // Now enable it for versioning, as Admin
        AuthenticationUtil.setFullyAuthenticatedUser(AuthenticationUtil.getAdminUserName());
        versionService.createVersion(nodeA, null);
        
        // Ensure it's still owned by A
        assertEquals(USER_NAME_A, nodeService.getProperty(nodeA, ContentModel.PROP_CREATOR));
        assertEquals(USER_NAME_A, nodeService.getProperty(nodeA, ContentModel.PROP_MODIFIER));
        assertEquals(true, nodeService.hasAspect(nodeA, ContentModel.ASPECT_VERSIONABLE));
    }
    
    public void testEnsureVersioningEnabled() throws Exception 
    {
        AuthenticationUtil.setFullyAuthenticatedUser(AuthenticationUtil.getAdminUserName());
        if(!authenticationDAO.userExists(USER_NAME_A))
        {
            authenticationService.createAuthentication(USER_NAME_A, PWD_A.toCharArray());
        }
        
        // Create 3 nodes in the 3 different states
        AuthenticationUtil.setFullyAuthenticatedUser(USER_NAME_A);
        NodeRef none = nodeService.createNode(
                rootNodeRef, ContentModel.ASSOC_CONTAINS,
                QName.createQName("{test}None"), 
                ContentModel.TYPE_CONTENT
        ).getChildRef();
        nodeService.addAspect(none, ContentModel.ASPECT_AUDITABLE, null);
        
        NodeRef aspect = nodeService.createNode(
                rootNodeRef, ContentModel.ASSOC_CONTAINS,
                QName.createQName("{test}None"), 
                ContentModel.TYPE_CONTENT
        ).getChildRef();
        nodeService.addAspect(aspect, ContentModel.ASPECT_AUDITABLE, null);
        nodeService.addAspect(aspect, ContentModel.ASPECT_VERSIONABLE, null);
        nodeService.setProperty(aspect, ContentModel.PROP_AUTO_VERSION, Boolean.FALSE); 
        nodeService.setProperty(aspect, ContentModel.PROP_AUTO_VERSION_PROPS, Boolean.TRUE); 
        
        NodeRef versioned = nodeService.createNode(
                rootNodeRef, ContentModel.ASSOC_CONTAINS,
                QName.createQName("{test}None"), 
                ContentModel.TYPE_CONTENT
        ).getChildRef();
        nodeService.addAspect(versioned, ContentModel.ASPECT_AUDITABLE, null);
        nodeService.addAspect(versioned, ContentModel.ASPECT_VERSIONABLE, null);
        nodeService.setProperty(versioned, ContentModel.PROP_AUTO_VERSION, Boolean.TRUE); 
        nodeService.setProperty(versioned, ContentModel.PROP_AUTO_VERSION_PROPS, Boolean.FALSE); 
        versionService.createVersion(versioned, null);

        
        // Check their state
        assertEquals(false, nodeService.hasAspect(none, ContentModel.ASPECT_VERSIONABLE));
        assertEquals(true, nodeService.hasAspect(aspect, ContentModel.ASPECT_VERSIONABLE));
        assertEquals(true, nodeService.hasAspect(versioned, ContentModel.ASPECT_VERSIONABLE));
        assertNull(versionService.getVersionHistory(none));
        assertNull(versionService.getVersionHistory(aspect));
        assertNotNull(versionService.getVersionHistory(versioned));
        
        assertEquals(USER_NAME_A, nodeService.getProperty(none, ContentModel.PROP_CREATOR));
        assertEquals(USER_NAME_A, nodeService.getProperty(none, ContentModel.PROP_MODIFIER));
        assertEquals(USER_NAME_A, nodeService.getProperty(aspect, ContentModel.PROP_CREATOR));
        assertEquals(USER_NAME_A, nodeService.getProperty(aspect, ContentModel.PROP_MODIFIER));
        assertEquals(USER_NAME_A, nodeService.getProperty(versioned, ContentModel.PROP_CREATOR));
        assertEquals(USER_NAME_A, nodeService.getProperty(versioned, ContentModel.PROP_MODIFIER));
        
        
        // If we turn on the aspect, what with?
        Map<QName, Serializable> props = new HashMap<QName, Serializable>();
        props.put(ContentModel.PROP_TITLE, "This shouldn't be set by the method");
        props.put(ContentModel.PROP_AUTO_VERSION, Boolean.TRUE);
        
        
        // Now call ensureVersioningEnabled for each
        AuthenticationUtil.setFullyAuthenticatedUser(AuthenticationUtil.getAdminUserName());
        versionService.ensureVersioningEnabled(none, props);
        versionService.ensureVersioningEnabled(aspect, props);
        versionService.ensureVersioningEnabled(versioned, props);
        
        // And finally check their state:
        
        // None will have the aspect applied, along with both properties
        assertEquals(true, nodeService.hasAspect(none, ContentModel.ASPECT_VERSIONABLE));
        assertEquals(Boolean.TRUE, nodeService.getProperty(none, ContentModel.PROP_AUTO_VERSION));
        assertEquals(Boolean.TRUE, nodeService.getProperty(none, ContentModel.PROP_AUTO_VERSION_PROPS));
        assertEquals(null, nodeService.getProperty(none, ContentModel.PROP_TITLE));
        
        // Aspect won't have altered it's props
        assertEquals(true, nodeService.hasAspect(aspect, ContentModel.ASPECT_VERSIONABLE));
        assertEquals(Boolean.FALSE, nodeService.getProperty(aspect, ContentModel.PROP_AUTO_VERSION));
        assertEquals(Boolean.TRUE,  nodeService.getProperty(aspect, ContentModel.PROP_AUTO_VERSION_PROPS));
        assertEquals(null, nodeService.getProperty(aspect, ContentModel.PROP_TITLE));
        
        // Versioned won't have altered it's props
        assertEquals(true, nodeService.hasAspect(versioned, ContentModel.ASPECT_VERSIONABLE));
        assertEquals(Boolean.TRUE,  nodeService.getProperty(versioned, ContentModel.PROP_AUTO_VERSION));
        assertEquals(Boolean.FALSE, nodeService.getProperty(versioned, ContentModel.PROP_AUTO_VERSION_PROPS));
        assertEquals(null, nodeService.getProperty(versioned, ContentModel.PROP_TITLE));

        // Alll will have a version history now
        assertNotNull(versionService.getVersionHistory(none));
        assertNotNull(versionService.getVersionHistory(aspect));
        assertNotNull(versionService.getVersionHistory(versioned));
        
        // The auditable properties won't have changed
        assertEquals(USER_NAME_A, nodeService.getProperty(none, ContentModel.PROP_CREATOR));
        assertEquals(USER_NAME_A, nodeService.getProperty(none, ContentModel.PROP_MODIFIER));
        assertEquals(USER_NAME_A, nodeService.getProperty(aspect, ContentModel.PROP_CREATOR));
        assertEquals(USER_NAME_A, nodeService.getProperty(aspect, ContentModel.PROP_MODIFIER));
        assertEquals(USER_NAME_A, nodeService.getProperty(versioned, ContentModel.PROP_CREATOR));
        assertEquals(USER_NAME_A, nodeService.getProperty(versioned, ContentModel.PROP_MODIFIER));
    }

    /*
     * It should be possible to create a version for a locked node, see ALF-16540
     */
    public void testVersionLockedNode()
    {
        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
        {
            public Object execute() throws Exception
            {
                AuthenticationUtil.setFullyAuthenticatedUser(AuthenticationUtil.getAdminUserName());
                
                // create versionable node and ensure it has the necessary aspect
                NodeRef versionableNode = createNewVersionableNode();
                assertEquals(true, nodeService.hasAspect(versionableNode, ContentModel.ASPECT_VERSIONABLE));
                
                // add lockable aspect and write lock the node
                dbNodeService.addAspect(versionableNode, ContentModel.ASPECT_LOCKABLE, new HashMap<QName, Serializable>());
                assertEquals(true, nodeService.hasAspect(versionableNode, ContentModel.ASPECT_LOCKABLE));
                
                checkOutCheckInService.checkout(versionableNode);
                
                // try to create a version
                createVersion(versionableNode);
                VersionHistory vh = versionService.getVersionHistory(versionableNode);
                assertEquals(1, vh.getAllVersions().size());
                return null;
            }
        });
    }

    public static void main(String ... args)
    {
        try
        {
            doMain(args);
            System.exit(1);
        }
        catch (Throwable e)
        {
            logger.error(e);
            System.exit(1);
        }
    }
    private static void doMain(String ... args)
    {
        if (args.length != 1)
        {
            System.out.println("Usage: VersionServiceImplTest fileCount");
            System.exit(1);
        }
        int fileCount = Integer.parseInt(args[0]);
        
        ApplicationContext ctx = ApplicationContextHelper.getApplicationContext();
        final ServiceRegistry serviceRegistry = (ServiceRegistry) ctx.getBean(ServiceRegistry.SERVICE_REGISTRY);
        final FileFolderService fileFolderService = serviceRegistry.getFileFolderService();
        final NodeService nodeService = serviceRegistry.getNodeService();
        final VersionService versionService = serviceRegistry.getVersionService();
        final AuthenticationComponent authenticationComponent = (AuthenticationComponent) ctx.getBean("authenticationComponent");
        
        authenticationComponent.setSystemUserAsCurrentUser();
        
        // TEMP - for migration testing - force V1 store (override repository property)  
        final Version2ServiceImpl version2ServiceImpl = (Version2ServiceImpl)ctx.getBean("versionService");
        version2ServiceImpl.setOnlyUseDeprecatedV1(true);
        
        System.out.println("Using: " + versionService.getVersionStoreReference());
        
        // Create a new store
        StoreRef storeRef = new StoreRef("test", "VersionServiceImplTest-main-"+System.currentTimeMillis());
        if (!nodeService.exists(storeRef))
        {
            nodeService.createStore(storeRef.getProtocol(), storeRef.getIdentifier());
        }
        NodeRef rootNodeRef = nodeService.getRootNode(storeRef);
        // Create a folder
        NodeRef folderNodeRef = nodeService.createNode(
                rootNodeRef,
                ContentModel.ASSOC_CHILDREN,
                QName.createQName("test", "versionMain"),
                ContentModel.TYPE_FOLDER).getChildRef();
        // Now load the folder with the prescribed number of documents
        int count = 0;
        long start = System.currentTimeMillis();
        long lastReport = start;
        for (int i = 0; i < fileCount; i++)
        {
            fileFolderService.create(folderNodeRef, "file-" + i, ContentModel.TYPE_CONTENT);
            count++;
            // Report every 10s
            long now = System.currentTimeMillis();
            if (now - lastReport > 10000L)
            {
                long delta = (now - start);
                double average = (double) delta / (double) count;
                System.out.println(
                        "File Creation: \n" +
                        "   Count:        " + count + " of " + fileCount + "\n" +
                        "   Average (ms): " + average);
                lastReport = now;
            }
        }
        // Get all the children again
        List<FileInfo> files = fileFolderService.listFiles(folderNodeRef);
        // Version each one
        count = 0;
        start = System.currentTimeMillis();
        lastReport = start;
        for (FileInfo fileInfo : files)
        {
            NodeRef nodeRef = fileInfo.getNodeRef();
            versionService.createVersion(nodeRef, null);
            count++;
            // Report every 10s
            long now = System.currentTimeMillis();
            if (now - lastReport > 10000L)
            {
                long delta = (now - start);
                double average = (double) delta / (double) count;
                System.out.println(
                        "Version: \n" +
                        "   Count:        " + count + " of " + fileCount + "\n" +
                        "   Average (ms): " + average);
                lastReport = now;
            }
        }
        
        System.out.println("Finished: " + fileCount);
    }
}
