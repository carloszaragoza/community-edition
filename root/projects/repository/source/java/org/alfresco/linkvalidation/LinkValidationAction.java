/*
 * Copyright (C) 2005-2007 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing
 */

package org.alfresco.linkvalidation;

import java.util.List;

import org.alfresco.config.JNDIConstants;
import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.repo.avm.AVMNodeConverter;
import org.alfresco.repo.domain.PropertyValue;
import org.alfresco.sandbox.SandboxConstants;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.avm.AVMService;
import org.alfresco.service.cmr.avmsync.AVMSyncException;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.util.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Performs a link validation check.
 * 
 * @author gavinc
 */
public class LinkValidationAction extends ActionExecuterAbstractBase
{
    public static final String NAME = "avm-link-validation";

    public static final String PARAM_COMPARE_TO_STAGING = "compare-to-staging";
    public static final String PARAM_MONITOR = "monitor";

    private int noThreads = 5;
    private int readTimeout = 30000;
    private int connectionTimeout = 10000;
    private LinkValidationService linkValidationService;
    private AVMService avmService;

    private static Log logger = LogFactory.getLog(LinkValidationAction.class);
   
    /**
     * Sets the LinkValidationService instance to use
     * 
     * @param service The LinkValidationService instance
     */
    public void setLinkValidationService(LinkValidationService service)
    {
        this.linkValidationService = service;
    }
    
    /**
     * Sets the AVMService instance to use
     * 
     * @param service The AVMService instance
     */
    public void setAvmService(AVMService service)
    {
        this.avmService = service;
    }
    
    /**
     * Sets the number of millseconds to wait for a connection
     * 
     * @param connectionTimeout Number of milliseconds to wait for connection
     */
    public void setConnectionTimeout(int connectionTimeout)
    {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Sets the number of milliseconds to wait for a response
     * 
     * @param readTimeout Number of milliseconds to wait for a response
     */
    public void setReadTimeout(int readTimeout)
    {
        this.readTimeout = readTimeout;
    }
    
    /**
     * Sets the number of threads to use to gather broken links
     * 
     * @param threads Number of threads to use to gather broken links
     */
    public void setNoThreads(int threads)
    {
        this.noThreads = threads;
    }

    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> paramList)
    {
        paramList.add(new ParameterDefinitionImpl(PARAM_COMPARE_TO_STAGING, DataTypeDefinition.BOOLEAN, 
                 false, getParamDisplayLabel(PARAM_COMPARE_TO_STAGING)));
        paramList.add(new ParameterDefinitionImpl(PARAM_MONITOR, DataTypeDefinition.ANY, false, 
                 getParamDisplayLabel(PARAM_MONITOR)));
    }
   
    @Override
    protected void executeImpl(Action action, NodeRef actionedUponNodeRef)
    {
        // get the webapp path to check the links for (is represented by the actioned upon node)
        Pair<Integer, String> avmVersionPath = AVMNodeConverter.ToAVMVersionPath(actionedUponNodeRef);
        String webappPath = avmVersionPath.getSecond();
        
        // get store name and path parts.
        String [] webappParts = webappPath.split(":");
        if (webappParts.length != 2)
        {
            throw new AVMSyncException("Malformed source path: " + webappPath);
        }

        // extract the store name
        String storeName = actionedUponNodeRef.getStoreRef().getIdentifier();
        
        // extract the webapp name
        String webappName = webappPath.substring(webappPath.lastIndexOf("/")+1);
        
        // get the compare to staging flag
        String destWebappPath = null;
        Boolean compareToStaging = (Boolean)action.getParameterValue(PARAM_COMPARE_TO_STAGING);
        if (compareToStaging != null)
        {
           if (compareToStaging.booleanValue())
           {
              // get the corresponding path in the staging area for the given source
              PropertyValue val = this.avmService.getStoreProperty(storeName, SandboxConstants.PROP_WEBSITE_NAME);
              if (val != null)
              {
                 String stagingStoreName = val.getStringValue();
                 destWebappPath = stagingStoreName + ":/" + JNDIConstants.DIR_DEFAULT_WWW + "/" +
                                  JNDIConstants.DIR_DEFAULT_APPBASE + "/" + webappName;
              }
           }
        }
        
        // get the monitor object
        HrefValidationProgress monitor = (HrefValidationProgress)action.getParameterValue(PARAM_MONITOR);
        
        if (logger.isDebugEnabled())
        {
            if (destWebappPath == null)
            {
                logger.debug("Performing link validation check for webapp '" + webappPath + "'");
            }
            else
            {
               logger.debug("Performing link validation check for webapp '" + webappPath + "', comparing against '" +
                            destWebappPath + "'");
            }
        }
        
        LinkValidationReport report = null;
        try
        {
            // determine which API to call depending on whether there is a destination webapp present 
            if (destWebappPath != null)
            {
                // get the object to represent the broken files
                HrefDifference hdiff = this.linkValidationService.getHrefDifference(webappPath, destWebappPath, 
                         this.connectionTimeout, this.readTimeout, this.noThreads, monitor);
                
                // get the broken files created due to deletions and new/modified files
                HrefManifest brokenByDelete = this.linkValidationService.getHrefManifestBrokenByDelete(hdiff);
                HrefManifest brokenByNewOrMod = this.linkValidationService.getHrefManifestBrokenByNewOrMod(hdiff);
                
                // create the report object using the 2 sets of results
                report = new LinkValidationReport(storeName, webappName, monitor, brokenByDelete, brokenByNewOrMod);
            }
            else
            {
                // firstly call updateHrefInfo to scan the whole store for broken links
                // NOTE: currently this is NOT done incrementally
                this.linkValidationService.updateHrefInfo(webappPath, false, this.connectionTimeout, 
                         this.readTimeout, this.noThreads, monitor);
            
                // retrieve the manifest of all the broken links and files for the webapp
                List<HrefManifestEntry> manifests = this.linkValidationService.getBrokenHrefManifestEntries(webappPath);
                
                // create the report object using the link check results
                report = new LinkValidationReport(storeName, webappName, monitor, manifests);
            }
        }
        catch (Throwable err)
        {
            if (report != null)
            {
                report.setError(err);
            }
            else
            {
               report = new LinkValidationReport(storeName, webappName, err);
            }
            
            logger.error("Link Validation Error: ", err);
        }
        
        // store the report as a store property on the store we ran the link check on
        this.avmService.deleteStoreProperty(storeName, SandboxConstants.PROP_LINK_VALIDATION_REPORT);
        this.avmService.setStoreProperty(storeName, SandboxConstants.PROP_LINK_VALIDATION_REPORT, 
                 new PropertyValue(DataTypeDefinition.ANY, report));
   }
}
