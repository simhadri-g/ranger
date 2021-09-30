/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ranger.patch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;
import org.apache.ranger.biz.SecurityZoneDBStore;
import org.apache.ranger.biz.ServiceDBStore;
import org.apache.ranger.common.RangerValidatorFactory;
import org.apache.ranger.db.RangerDaoManager;
import org.apache.ranger.db.XXAccessTypeDefDao;
import org.apache.ranger.db.XXAccessTypeDefGrantsDao;
import org.apache.ranger.entity.XXAccessTypeDef;
import org.apache.ranger.entity.XXAccessTypeDefGrants;
import org.apache.ranger.entity.XXService;
import org.apache.ranger.entity.XXServiceDef;
import org.apache.ranger.plugin.model.RangerPolicy;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItem;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItemAccess;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyResource;
import org.apache.ranger.plugin.model.RangerSecurityZone;
import org.apache.ranger.plugin.model.RangerSecurityZone.RangerSecurityZoneService;
import org.apache.ranger.plugin.model.RangerServiceDef;
import org.apache.ranger.plugin.model.validation.RangerServiceDefValidator;
import org.apache.ranger.plugin.model.validation.RangerValidator.Action;
import org.apache.ranger.plugin.store.EmbeddedServiceDefsUtil;
import org.apache.ranger.plugin.util.SearchFilter;
import org.apache.ranger.util.CLIUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PatchForSolrSvcDefAndPoliciesUpdate_J10055 extends BaseLoader {
    private static final Logger logger             = Logger.getLogger(PatchForSolrSvcDefAndPoliciesUpdate_J10055.class);
    private static final String ACCESS_TYPE_UPDATE = "update";
    private static final String ACCESS_TYPE_QUERY  = "query";
    private static final String ACCESS_TYPE_ADMIN  = "solr_admin";
    private static final String ACCESS_TYPE_OTHERS = "others";

    //TAG type solr:permissions
    private static final String ACCESS_TYPE_UPDATE_TAG = "solr:update";
    private static final String ACCESS_TYPE_QUERY_TAG  = "solr:query";
    private static final String ACCESS_TYPE_ADMIN_TAG  = "solr:solr_admin";
    private static final String ACCESS_TYPE_OTHERS_TAG = "solr:others";
    private enum NEW_RESOURCE { admin, config, schema }

    private static final String     SOLR_SVC_DEF_NAME      = EmbeddedServiceDefsUtil.EMBEDDED_SERVICEDEF_SOLR_NAME;
    private static RangerServiceDef embeddedSolrServiceDef = null;

    @Autowired
    private RangerDaoManager daoMgr;

    @Autowired
    ServiceDBStore svcDBStore;

    @Autowired
    private SecurityZoneDBStore secZoneDBStore;

    @Autowired
    private RangerValidatorFactory validatorFactory;

    public static void main(String[] args) {
        logger.info("main()");
        try {
            PatchForSolrSvcDefAndPoliciesUpdate_J10055 loader = (PatchForSolrSvcDefAndPoliciesUpdate_J10055) CLIUtil.getBean(PatchForSolrSvcDefAndPoliciesUpdate_J10055.class);
            loader.init();
            while (loader.isMoreToProcess()) {
                loader.load();
            }
            logger.info("Load complete. Exiting!!!");
            System.exit(0);
        } catch (Exception e) {
            logger.error("Error loading", e);
            System.exit(1);
        }
    }

    @Override
    public void init() throws Exception {
        // DO NOTHING
    }

    @Override
    public void printStats() {
        logger.info("PatchForSolrSvcDefAndPoliciesUpdate_J10055 logs ");
    }

    @Override
    public void execLoad() {
        logger.info("==> PatchForSolrSvcDefAndPoliciesUpdate_J10055.execLoad()");
        try {
            embeddedSolrServiceDef = EmbeddedServiceDefsUtil.instance().getEmbeddedServiceDef(SOLR_SVC_DEF_NAME);
            if(embeddedSolrServiceDef == null) {
                logger.error("The embedded Solr service-definition does not exist.");
                System.exit(1);
            }

            if (updateSolrSvcDef() != null) {
                final Long resTypeSvcDefId =  embeddedSolrServiceDef.getId();
                final Long tagSvcDefId = EmbeddedServiceDefsUtil.instance().getTagServiceDefId();
                updateExistingRangerResPolicy(resTypeSvcDefId);
                updateExistingRangerTagPolicies(tagSvcDefId);

                deleteOldAccessTypeRefs(resTypeSvcDefId);
                deleteOldAccessTypeRefs(tagSvcDefId);
            } else {
                logger.error("Error while updating " + SOLR_SVC_DEF_NAME + " service-def");
                throw new RuntimeException("Error while updating " + SOLR_SVC_DEF_NAME + " service-def");
            }
        } catch (Exception e) {
            logger.error("Error whille executing PatchForSolrSvcDefAndPoliciesUpdate_J10055.", e);
            System.exit(1);
        }
        logger.info("<== PatchForSolrSvcDefAndPoliciesUpdate_J10055.execLoad()");
    }

	private void updateExistingRangerResPolicy(Long svcDefId) throws Exception {
		logger.info("<== PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateExistingRangerResPolicy(...)");
		List<XXService> dbServices = daoMgr.getXXService().findByServiceDefId(svcDefId);
		if (CollectionUtils.isNotEmpty(dbServices)) {
			for (XXService dbService : dbServices) {
				SearchFilter filter = new SearchFilter();
				filter.setParam(SearchFilter.SERVICE_NAME, dbService.getName());
				filter.setParam(SearchFilter.FETCH_ZONE_UNZONE_POLICIES, "true");
				updateResPolicies(svcDBStore.getServicePolicies(dbService.getId(), filter));
				updateZoneResourceMapping(dbService);
			}
		}
		logger.info("<== PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateExistingRangerResPolicy(...)");
	}

    private void updateZoneResourceMapping(final XXService solrDBSvc) throws Exception {
         logger.info("==> PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateZoneResourceMapping(...)");
        // Update Zone Resource Mapping For Solr Services
        final String svcName = solrDBSvc.getName();
        SearchFilter filter  = new SearchFilter();
        filter.setParam(SearchFilter.SERVICE_NAME, svcName);
        List<RangerSecurityZone> secZoneList = this.secZoneDBStore.getSecurityZones(filter);
        for (RangerSecurityZone secZone : secZoneList) {
            RangerSecurityZoneService           secZoneSvc                  = secZone.getServices().get(svcName);// get secZoneSvc only for this svcName
            List<HashMap<String, List<String>>> solrZoneSvcResourcesMapList = secZoneSvc.getResources();

            final Set<HashMap<String, List<String>>> updatedResMapSet = new HashSet<HashMap<String, List<String>>>();
            for (HashMap<String, List<String>> existingResMap : solrZoneSvcResourcesMapList) {
                boolean isAllResource = false; // *
                for (Map.Entry<String, List<String>> resNameValueListMap : existingResMap.entrySet()) {

                    updatedResMapSet.add(existingResMap);
                    final List<String> resourceValueList = resNameValueListMap.getValue();

                    if (CollectionUtils.isNotEmpty(resourceValueList) && resourceValueList.indexOf("*") >= 0) {
                        updatedResMapSet.clear();
                        updatedResMapSet.add(existingResMap);
                        isAllResource = true;
                        break;
                    } else {
                        HashMap<String, List<String>> updatedResMap = new HashMap<String, List<String>>();
                        updatedResMap.put(NEW_RESOURCE.schema.name(), resourceValueList);
                        updatedResMapSet.add(updatedResMap);
                    }
                }

                if (isAllResource) {
                    final List<String> allResVal = Arrays.asList("*");
                    for (NEW_RESOURCE newRes : NEW_RESOURCE.values()) {
                        HashMap<String, List<String>> updatedResMap = new HashMap<String, List<String>>();
                        updatedResMap.put(newRes.name(), allResVal);
                        updatedResMapSet.add(updatedResMap);
                    }
                    secZoneSvc.setResources(new ArrayList<HashMap<String, List<String>>>(updatedResMapSet));
                    break;
                }
                secZoneSvc.setResources(new ArrayList<HashMap<String, List<String>>>(updatedResMapSet));
            }
            this.secZoneDBStore.updateSecurityZoneById(secZone);
            logger.info("<== PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateZoneResourceMapping(...)");
        }
    }

    private void updateExistingRangerTagPolicies(Long svcDefId) throws Exception {
        logger.info("==> PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateExistingRangerTagPolicies(" + svcDefId + ")");
        List<XXService> dbServices = daoMgr.getXXService().findByServiceDefId(svcDefId);
        if (CollectionUtils.isNotEmpty(dbServices)) {
            for (XXService dbService : dbServices) {
                SearchFilter filter = new SearchFilter();
                filter.setParam(SearchFilter.SERVICE_NAME, dbService.getName());
                updateTagPolicies(svcDBStore.getServicePolicies(dbService.getId(), filter));
            }
        }
        logger.info("<== PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateExistingRangerTagPolicies(" + svcDefId + ")");
}

    private void updateTagPolicies(List<RangerPolicy> tagServicePolicies) {
        logger.info("==> PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateTagPolicies(...)");
        if (CollectionUtils.isNotEmpty(tagServicePolicies)) {
            for (RangerPolicy exPolicy : tagServicePolicies) {
                try {
                    updateTagPolicyItemAccess(exPolicy.getPolicyItems());
                    updateTagPolicyItemAccess(exPolicy.getAllowExceptions());
                    updateTagPolicyItemAccess(exPolicy.getDenyPolicyItems());
                    updateTagPolicyItemAccess(exPolicy.getDenyExceptions());
                    this.svcDBStore.updatePolicy(exPolicy);
                } catch (Exception e) {
                    logger.error("Failed to apply the patch, Error - " + e.getCause());
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
        logger.info("<== PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateTagPolicies(...)");
    }

    private void updateResPolicies(List<RangerPolicy> policies) {
        logger.info("==> PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateResPolicies(...)");
        if (CollectionUtils.isNotEmpty(policies)) {
            for (RangerPolicy exPolicy : policies) {
                // Filter policy items which are eligible for admin,config and schema resources
                final List<RangerPolicy.RangerPolicyItem> filteredAllowPolciyItems  = filterPolicyItemsForAdminPermission(exPolicy.getPolicyItems());
                final List<RangerPolicy.RangerPolicyItem> filteredAllowExcpPolItems = filterPolicyItemsForAdminPermission(exPolicy.getAllowExceptions());
                final List<RangerPolicy.RangerPolicyItem> filteredDenyPolItems      = filterPolicyItemsForAdminPermission(exPolicy.getDenyPolicyItems());
                final List<RangerPolicy.RangerPolicyItem> filteredDenyExcpPolItems  = filterPolicyItemsForAdminPermission(exPolicy.getDenyExceptions());

                // check if there is a need to create additional policies with admin/config/schema resource(s)
                final boolean splitPolicy = (filteredAllowPolciyItems.size() > 0 || filteredAllowExcpPolItems.size() > 0 || filteredDenyPolItems.size() > 0 || filteredDenyExcpPolItems.size() > 0);
                if (splitPolicy) {
                    RangerPolicy newPolicyForNewResource = new RangerPolicy();
                    newPolicyForNewResource.setService(exPolicy.getService());
                    newPolicyForNewResource.setServiceType(exPolicy.getServiceType());
                    newPolicyForNewResource.setPolicyPriority(exPolicy.getPolicyPriority());

                    RangerPolicyResource newRes         = new RangerPolicyResource();
                    boolean              isAllResources = false;
                    // Only one entry expected
                    for (Map.Entry<String, RangerPolicyResource> entry : exPolicy.getResources().entrySet()) {
                        RangerPolicyResource exPolRes = entry.getValue();
                        newRes.setIsExcludes(exPolRes.getIsExcludes());
                        newRes.setIsRecursive(exPolRes.getIsRecursive());
                        newRes.setValues(exPolRes.getValues());
                        if (CollectionUtils.isNotEmpty(exPolRes.getValues()) && exPolRes.getValues().indexOf("*") >= 0) {
                            isAllResources = true;
                        }
                    }

                    newPolicyForNewResource.setPolicyItems(filteredAllowPolciyItems);
                    newPolicyForNewResource.setAllowExceptions(filteredAllowExcpPolItems);
                    newPolicyForNewResource.setDenyPolicyItems(filteredDenyPolItems);
                    newPolicyForNewResource.setDenyExceptions(filteredDenyExcpPolItems);
                    newPolicyForNewResource.setOptions(exPolicy.getOptions());
                    newPolicyForNewResource.setValiditySchedules(exPolicy.getValiditySchedules());
                    newPolicyForNewResource.setPolicyLabels(exPolicy.getPolicyLabels());
                    newPolicyForNewResource.setConditions(exPolicy.getConditions());
                    newPolicyForNewResource.setIsDenyAllElse(exPolicy.getIsDenyAllElse());
                    newPolicyForNewResource.setZoneName(exPolicy.getZoneName());

                    try {
                        if (isAllResources) {
                            newRes.setValue("*");
                            for (NEW_RESOURCE resType : NEW_RESOURCE.values()) {
                                createNewPolicy(resType.name(), newPolicyForNewResource, newRes, exPolicy.getName());
                            }
                        } else {
                            createNewPolicy(NEW_RESOURCE.schema.name(), newPolicyForNewResource, newRes, exPolicy.getName());
                        }

                    } catch (Exception e) {
                        logger.error("Failed to apply the patch, Error Msg - " + e.getCause());
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
                try {
                    // update policy items
                    updateResPolicyItemAccess(exPolicy.getPolicyItems());
                    updateResPolicyItemAccess(exPolicy.getAllowExceptions());
                    updateResPolicyItemAccess(exPolicy.getDenyPolicyItems());
                    updateResPolicyItemAccess(exPolicy.getDenyExceptions());
                    this.svcDBStore.updatePolicy(exPolicy);
                } catch (Exception e) {
                    logger.error("Failed to apply the patch, Error - " + e.getCause());
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
        logger.info("<== PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateResPolicies(...)");
    }

    private void createNewPolicy(final String resType, final RangerPolicy newPolicy, final RangerPolicyResource newRes, final String exPolicyName) throws Exception {
        logger.info("==> PatchForSolrSvcDefAndPoliciesUpdate_J10055.createNewPolicy(...)");
        final String newPolicyName = resType + " - '" + exPolicyName + "'";
        newPolicy.setName(newPolicyName);
        newPolicy.setDescription(newPolicyName);

        final Map<String, RangerPolicy.RangerPolicyResource> resForNewPol = new HashMap<String, RangerPolicy.RangerPolicyResource>();
        resForNewPol.put(resType, newRes);
        newPolicy.setResources(resForNewPol);
        newPolicy.setResourceSignature(null);
        newPolicy.setGuid(null);
        this.svcDBStore.createPolicy(newPolicy);
        logger.info("<== PatchForSolrSvcDefAndPoliciesUpdate_J10055.createNewPolicy(...)");
    }

    private void updateResPolicyItemAccess(List<RangerPolicyItem> policyItems) {
        logger.info("==> PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateResPolicyItemAccess(...)");
        Set<RangerPolicyItemAccess>         newRangerPolicyItemAccess = new HashSet<RangerPolicyItemAccess>();
        if (CollectionUtils.isNotEmpty(policyItems)) {
            for (RangerPolicyItem exPolicyItem : policyItems) {
                if (exPolicyItem != null) {
                    List<RangerPolicyItemAccess> exPolicyItemAccessList = exPolicyItem.getAccesses();
                    if (CollectionUtils.isNotEmpty(exPolicyItemAccessList)) {
                        newRangerPolicyItemAccess = new HashSet<RangerPolicyItemAccess>();
                        for (RangerPolicyItemAccess aPolicyItemAccess : exPolicyItemAccessList) {
                            if (aPolicyItemAccess != null) {
                                final String  accessType = aPolicyItemAccess.getType();
                                final Boolean isAllowed  = aPolicyItemAccess.getIsAllowed();
                                if (ACCESS_TYPE_ADMIN.equalsIgnoreCase(accessType)) {
                                    newRangerPolicyItemAccess.add(new RangerPolicyItemAccess(ACCESS_TYPE_QUERY, isAllowed));
                                    newRangerPolicyItemAccess.add(new RangerPolicyItemAccess(ACCESS_TYPE_UPDATE, isAllowed));
                                    break;
                                } else if (ACCESS_TYPE_UPDATE.equalsIgnoreCase(accessType)) {
                                    newRangerPolicyItemAccess.add(aPolicyItemAccess);
                                } else if (ACCESS_TYPE_QUERY.equalsIgnoreCase(accessType)) {
                                    newRangerPolicyItemAccess.add(aPolicyItemAccess);
                                } else if (ACCESS_TYPE_OTHERS.equalsIgnoreCase(accessType)) {
                                    newRangerPolicyItemAccess.add(new RangerPolicyItemAccess(ACCESS_TYPE_QUERY, isAllowed));
                                }
                            }
                        }
                        exPolicyItem.setAccesses(new ArrayList<RangerPolicy.RangerPolicyItemAccess>(newRangerPolicyItemAccess));
                    }
                }
            }
        }
        logger.info("<== PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateResPolicyItemAccess(...)");
    }

    private void updateTagPolicyItemAccess(List<RangerPolicyItem> policyItems) {
        logger.info("==> PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateTagPolicyItemAccess(...)");
        List<RangerPolicy.RangerPolicyItem> newPolicyItems            = new ArrayList<RangerPolicy.RangerPolicyItem>();
        Set<RangerPolicyItemAccess>         newRangerPolicyItemAccess = new HashSet<RangerPolicyItemAccess>();
        if (CollectionUtils.isNotEmpty(policyItems)) {
            for (RangerPolicyItem exPolicyItem : policyItems) {
                if (exPolicyItem != null) {
                    List<RangerPolicyItemAccess> exPolicyItemAccessList = exPolicyItem.getAccesses();
                    if (CollectionUtils.isNotEmpty(exPolicyItemAccessList)) {
                        newRangerPolicyItemAccess = new HashSet<RangerPolicyItemAccess>();
                        for (RangerPolicyItemAccess aPolicyItemAccess : exPolicyItemAccessList) {
                            if (aPolicyItemAccess != null) {
                                final String  accessType = aPolicyItemAccess.getType();
                                final Boolean isAllowed  = aPolicyItemAccess.getIsAllowed();
                                if (ACCESS_TYPE_ADMIN_TAG.equalsIgnoreCase(accessType)) {
                                    newRangerPolicyItemAccess.add(new RangerPolicyItemAccess(ACCESS_TYPE_QUERY_TAG, isAllowed));
                                    newRangerPolicyItemAccess.add(new RangerPolicyItemAccess(ACCESS_TYPE_UPDATE_TAG, isAllowed));
                                } else if (ACCESS_TYPE_UPDATE_TAG.equalsIgnoreCase(accessType)) {
                                    newRangerPolicyItemAccess.add(aPolicyItemAccess);
                                } else if (ACCESS_TYPE_QUERY_TAG.equalsIgnoreCase(accessType)) {
                                    newRangerPolicyItemAccess.add(aPolicyItemAccess);
                                } else if (ACCESS_TYPE_OTHERS_TAG.equalsIgnoreCase(accessType)) {
                                    newRangerPolicyItemAccess.add(new RangerPolicyItemAccess(ACCESS_TYPE_QUERY_TAG, isAllowed));
                                } else {
                                    newRangerPolicyItemAccess.add(aPolicyItemAccess);
                                }
                            }
                        }
                        exPolicyItem.setAccesses(new ArrayList<RangerPolicy.RangerPolicyItemAccess>(newRangerPolicyItemAccess));
                        newPolicyItems.add(exPolicyItem);
                    }
                }
            }
        }
        logger.info("<== PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateTagPolicyItemAccess(...)");
    }

    private List<RangerPolicy.RangerPolicyItem> filterPolicyItemsForAdminPermission(List<RangerPolicy.RangerPolicyItem> policyItems) {
        logger.info("==> PatchForSolrSvcDefAndPoliciesUpdate_J10055.filterPolicyItemsForAdminPermission(...)");
        // Add only those policy items who's access permission list contains 'solr_admin' permission
        List<RangerPolicy.RangerPolicyItem> filteredPolicyItems       = new ArrayList<RangerPolicy.RangerPolicyItem>();
        Set<RangerPolicyItemAccess>         newRangerPolicyItemAccess = new HashSet<RangerPolicyItemAccess>();
        policyItems.forEach(exPolicyItem -> exPolicyItem.getAccesses().forEach(polItemAcc -> {
            if (ACCESS_TYPE_ADMIN.equalsIgnoreCase(polItemAcc.getType())) {
                newRangerPolicyItemAccess.add(new RangerPolicyItemAccess(ACCESS_TYPE_QUERY, polItemAcc.getIsAllowed()));
                newRangerPolicyItemAccess.add(new RangerPolicyItemAccess(ACCESS_TYPE_UPDATE, polItemAcc.getIsAllowed()));
                RangerPolicyItem newPolicyItem = new RangerPolicyItem(new ArrayList<RangerPolicy.RangerPolicyItemAccess>(newRangerPolicyItemAccess), exPolicyItem.getUsers(), exPolicyItem.getGroups(),
                exPolicyItem.getRoles(), exPolicyItem.getConditions(), exPolicyItem.getDelegateAdmin());
                filteredPolicyItems.add(newPolicyItem);
            }
        }));
        logger.info("<== PatchForSolrSvcDefAndPoliciesUpdate_J10055.filterPolicyItemsForAdminPermission(...)");
        return filteredPolicyItems;
    }

    private RangerServiceDef updateSolrSvcDef() {
        logger.info("==> PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateSolrSvcDef()");
        RangerServiceDef                         ret                      = null;
        RangerServiceDef                         embeddedSolrServiceDef   = null;
        XXServiceDef                             xXServiceDefObj          = null;
        RangerServiceDef                         dbSolrServiceDef         = null;
        List<RangerServiceDef.RangerResourceDef> embeddedSolrResourceDefs = null;
        try {
            embeddedSolrServiceDef = EmbeddedServiceDefsUtil.instance().getEmbeddedServiceDef(SOLR_SVC_DEF_NAME);
            if (embeddedSolrServiceDef != null) {
                xXServiceDefObj = daoMgr.getXXServiceDef().findByName(SOLR_SVC_DEF_NAME);
                if (xXServiceDefObj == null) {
                    logger.info(xXServiceDefObj + ": service-def not found. No patching is needed");
                    System.out.println(0);
                }

                embeddedSolrResourceDefs = embeddedSolrServiceDef.getResources();                 // ResourcesType
                dbSolrServiceDef         = this.svcDBStore.getServiceDefByName(SOLR_SVC_DEF_NAME);
                dbSolrServiceDef.setResources(embeddedSolrResourceDefs);

                RangerServiceDefValidator validator = validatorFactory.getServiceDefValidator(this.svcDBStore);
                validator.validate(dbSolrServiceDef, Action.UPDATE);
                ret = this.svcDBStore.updateServiceDef(dbSolrServiceDef);
            }
        } catch (Exception e) {
            logger.error("Error while updating " + SOLR_SVC_DEF_NAME + " service-def", e);
        }
        logger.info("<== PatchForSolrSvcDefAndPoliciesUpdate_J10055.updateSolrSvcDef()");
        return ret;
    }

    private void deleteOldAccessTypeRefs(Long svcDefId) {
        logger.info("==> PatchForSolrSvcDefAndPoliciesUpdate_J10055.deleteOldAccessTypeRefs(" + svcDefId + ")");
        List<XXAccessTypeDef>    solrAccessDefTypes       = daoMgr.getXXAccessTypeDef().findByServiceDefId(svcDefId);
        XXAccessTypeDefDao       accessTypeDefDao         = daoMgr.getXXAccessTypeDef();
        XXAccessTypeDefGrantsDao xxAccessTypeDefGrantsDao = daoMgr.getXXAccessTypeDefGrants();
        for (XXAccessTypeDef xXAccessTypeDef : solrAccessDefTypes) {
            if (xXAccessTypeDef != null) {
                final String accessTypeName = xXAccessTypeDef.getName();
                final Long   id             = xXAccessTypeDef.getId();  // atd_id in x_access_type_def_grants tbl
                // remove solr_admin refs from implied grants refs tbl
                for (XXAccessTypeDefGrants xXAccessTypeDefGrants : xxAccessTypeDefGrantsDao.findByATDId(id)) {
                    if (xXAccessTypeDefGrants != null) {
                        xxAccessTypeDefGrantsDao.remove(xXAccessTypeDefGrants.getId());
                    }
                }
                // remove no longer supported accessTyeDef's (others,solr_admin, solr:others, solr:solr_admin)
                if (ACCESS_TYPE_ADMIN.equalsIgnoreCase(accessTypeName) || ACCESS_TYPE_OTHERS.equalsIgnoreCase(accessTypeName) || ACCESS_TYPE_OTHERS_TAG.equalsIgnoreCase(accessTypeName)
                || ACCESS_TYPE_ADMIN_TAG.equalsIgnoreCase(accessTypeName)) {
                    accessTypeDefDao.remove(xXAccessTypeDef.getId());
                }
            }
        }
        logger.info("<== PatchForSolrSvcDefAndPoliciesUpdate_J10055.deleteOldAccessTypeRefs(" + svcDefId + ")");
    }
}