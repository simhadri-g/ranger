/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ranger.validation;

import org.apache.commons.lang.StringUtils;
import org.apache.ranger.common.MessageEnums;
import org.apache.ranger.common.RESTErrorUtil;
import org.apache.ranger.plugin.errors.ValidationErrorCode;
import org.apache.ranger.plugin.model.RangerGds;
import org.apache.ranger.plugin.model.RangerGds.RangerDataShareInDataset;
import org.apache.ranger.plugin.model.RangerGds.RangerDataShare;
import org.apache.ranger.plugin.model.RangerGds.RangerDatasetInProject;
import org.apache.ranger.plugin.model.RangerGds.RangerDataset;
import org.apache.ranger.plugin.model.RangerGds.RangerGdsACL;
import org.apache.ranger.plugin.model.RangerGds.RangerProject;
import org.apache.ranger.plugin.model.RangerGds.RangerSharedResource;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItemDataMaskInfo;
import org.apache.ranger.plugin.model.RangerPrincipal;
import org.apache.ranger.plugin.model.validation.ValidationFailureDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;


@Component
public class RangerGdsValidator {
    private static final Logger LOG = LoggerFactory.getLogger(RangerGdsValidator.class);

    private final RangerGdsValidationDataProvider dataProvider;

    @Autowired
    RESTErrorUtil restErrorUtil;


    public RangerGdsValidator(RangerGdsValidationDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    public void validateCreate(RangerDataset dataset) {
        LOG.debug("==> validateCreate(dataset={})", dataset);

        ValidationResult result   = new ValidationResult();
        Long             existing = dataProvider.getDatasetId(dataset.getName());

        if (existing != null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATASET_NAME_CONFLICT, "name", dataset.getName(), existing));
        }

        validatePrincipals(dataset.getAdmins(), "admins", result);
        validateAcl(dataset.getAcl(), "acl", result);

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateCreate(dataset={})", dataset);
    }

    public void validateUpdate(RangerDataset dataset, RangerDataset existing) {
        LOG.debug("==> validateUpdate(dataset={}, existing={})", dataset, existing);

        ValidationResult result = new ValidationResult();

        if (existing == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATASET_NAME_NOT_FOUND, "name", dataset.getName()));
        } else {
            if (!dataProvider.isAdminUser()) {
                validateAdmin(dataProvider.getCurrentUserLoginId(), "dataset", existing.getName(), existing.getAdmins(), result);
            }

            validatePrincipals(dataset.getAdmins(), "admins", result);
            validateAcl(dataset.getAcl(), "acl", result);
        }

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateUpdate(dataset={}, existing={})", dataset, existing);
    }

    public void validateDelete(long datasetId, RangerDataset existing) {
        LOG.debug("==> validateDelete(datasetId={}, existing={})", datasetId, existing);

        ValidationResult result = new ValidationResult();

        if (existing == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATASET_ID_NOT_FOUND, "id", datasetId));
        } else {
            if (!dataProvider.isAdminUser()) {
                validateAdmin(dataProvider.getCurrentUserLoginId(), "dataset", existing.getName(), existing.getAdmins(), result);
            }
        }

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateDelete(datasetId={}, existing={})", datasetId, existing);
    }

    public void validateCreate(RangerProject project) {
        LOG.debug("==> validateCreate(project={})", project);

        ValidationResult result   = new ValidationResult();
        Long             existing = dataProvider.getProjectId(project.getName());

        if (existing != null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_PROJECT_NAME_CONFLICT, "name", project.getName(), existing));
        }

        validatePrincipals(project.getAdmins(), "admins", result);
        validateAcl(project.getAcl(), "acl", result);

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateCreate(project={})", project);
    }

    public void validateUpdate(RangerProject project, RangerProject existing) {
        LOG.debug("==> validateUpdate(project={}, existing={})", project, existing);

        ValidationResult result = new ValidationResult();

        if (existing == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_PROJECT_NAME_NOT_FOUND, "name", project.getName()));
        } else {
            if (!dataProvider.isAdminUser()) {
                validateAdmin(dataProvider.getCurrentUserLoginId(), "project", existing.getName(), existing.getAdmins(), result);
            }

            validatePrincipals(project.getAdmins(), "admins", result);
            validateAcl(project.getAcl(), "acl", result);
        }

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateUpdate(project={}, existing={})", project, existing);
    }

    public void validateDelete(long projectId, RangerProject existing) {
        LOG.debug("==> validateDelete(projectId={}, existing={})", projectId, existing);

        ValidationResult result = new ValidationResult();

        if (existing == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_PROJECT_ID_NOT_FOUND, "id", projectId));
        } else {
            if (!dataProvider.isAdminUser()) {
                validateAdmin(dataProvider.getCurrentUserLoginId(), "project", existing.getName(), existing.getAdmins(), result);
            }
        }

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateDelete(projectId={}, existing={})", projectId, existing);
    }

    public void validateCreate(RangerDataShare dataShare) {
        LOG.debug("==> validateCreate(dataShare={})", dataShare);

        ValidationResult result   = new ValidationResult();
        Long             existing = dataProvider.getDataShareId(dataShare.getName());

        if (existing != null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATA_SHARE_NAME_CONFLICT, "name", dataShare.getName(), existing));
        }

        validateServiceZoneAdmin(dataShare.getService(), dataShare.getZone(), result);

        validatePrincipals(dataShare.getAdmins(), "admins", result);
        validateAccessTypes(dataShare.getService(), "defaultAccessTypes", dataShare.getDefaultAccessTypes(), result);
        validateMaskTypes(dataShare.getService(), "defaultMasks", dataShare.getDefaultMasks(), result);

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateCreate(dataShare={})", dataShare);
    }

    public void validateUpdate(RangerDataShare dataShare, RangerDataShare existing) {
        LOG.debug("==> validateUpdate(dataShare={}, existing={})", dataShare, existing);

        ValidationResult result = new ValidationResult();

        if (existing == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATA_SHARE_NAME_NOT_FOUND, "name", dataShare.getName()));
        } else {
            if (!dataProvider.isAdminUser()) {
                validateAdmin(dataProvider.getCurrentUserLoginId(), "datashare", existing.getName(), existing.getAdmins(), result);
            }

            validatePrincipals(dataShare.getAdmins(), "admins", result);
            validateAccessTypes(dataShare.getService(), "defaultAccessTypes", dataShare.getDefaultAccessTypes(), result);
            validateMaskTypes(dataShare.getService(), "defaultMasks", dataShare.getDefaultMasks(), result);
        }

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateUpdate(dataShare={}, existing={})", dataShare, existing);
    }

    public void validateDelete(long dataShareId, RangerDataShare existing) {
        LOG.debug("==> validateDelete(dataShareId={}, existing={})", dataShareId, existing);

        ValidationResult result = new ValidationResult();

        if (existing == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATA_SHARE_ID_NOT_FOUND, "id", dataShareId));
        } else {
            if (!dataProvider.isAdminUser()) {
                validateAdmin(dataProvider.getCurrentUserLoginId(), "datashare", existing.getName(), existing.getAdmins(), result);
            }
        }

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateDelete(dataShareId={}, existing={})", dataShareId, existing);
    }

    public void validateCreate(RangerSharedResource resource) {
        LOG.debug("==> validateCreate(resource={})", resource);

        ValidationResult result    = new ValidationResult();
        RangerDataShare  dataShare = dataProvider.getDataShare(resource.getDataShareId());

        if (dataShare == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATA_SHARE_ID_NOT_FOUND, "dataShareId", resource.getDataShareId()));
        } else {
            Long existing = dataProvider.getSharedResourceId(resource.getDataShareId(), resource.getName());

            if (existing != null) {
                result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_SHARED_RESOURCE_NAME_CONFLICT, "name", resource.getName(), dataShare.getName(), existing));
            } else {
                if (!dataProvider.isAdminUser() && !dataProvider.isServiceAdmin(dataShare.getService()) && !dataProvider.isZoneAdmin(dataShare.getZone())) {
                    validateAdmin(dataProvider.getCurrentUserLoginId(), "datashare", dataShare.getName(), dataShare.getAdmins(), result);
                }
            }
        }

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateCreate(resource={})", resource);
    }

    public void validateUpdate(RangerSharedResource resource, RangerSharedResource existing) {
        LOG.debug("==> validateUpdate(resource={}, existing={})", resource, existing);

        ValidationResult result = new ValidationResult();

        if (existing == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_SHARED_RESOURCE_ID_NOT_FOUND, "id", resource.getId()));
        } else {
            RangerDataShare dataShare = dataProvider.getDataShare(resource.getDataShareId());

            if (dataShare == null) {
                result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATA_SHARE_ID_NOT_FOUND, "dataShareId", resource.getDataShareId()));
            } else {
                if (!dataProvider.isAdminUser() && !dataProvider.isServiceAdmin(dataShare.getService()) && !dataProvider.isZoneAdmin(dataShare.getZone())) {
                    validateAdmin(dataProvider.getCurrentUserLoginId(), "datashare", dataShare.getName(), dataShare.getAdmins(), result);
                }
            }
        }

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateUpdate(resource={}, existing={})", resource, existing);
    }

    public void validateDelete(Long resourceId, RangerSharedResource existing) {
        LOG.debug("==> validateDelete(resourceId={}, existing={})", resourceId, existing);

        ValidationResult result = new ValidationResult();

        if (existing == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_SHARED_RESOURCE_ID_NOT_FOUND, "id", resourceId));
        } else {
            RangerDataShare dataShare = dataProvider.getDataShare(existing.getDataShareId());

            if (dataShare == null) {
                result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATA_SHARE_ID_NOT_FOUND, "dataShareId", existing.getDataShareId()));
            } else {
                if (!dataProvider.isAdminUser() && !dataProvider.isServiceAdmin(dataShare.getService()) && !dataProvider.isZoneAdmin(dataShare.getZone())) {
                    validateAdmin(dataProvider.getCurrentUserLoginId(), "datashare", dataShare.getName(), dataShare.getAdmins(), result);
                }
            }
        }

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateDelete(resourceId={}, existing={})", resourceId, existing);
    }

    public void validateCreate(RangerDataShareInDataset dshInDataset) {
        LOG.debug("==> validateCreate(dshInDataset={})", dshInDataset);

        ValidationResult result    = new ValidationResult();
        RangerDataShare  dataShare = dataProvider.getDataShare(dshInDataset.getDataShareId());
        RangerDataset    dataset   = dataProvider.getDataset(dshInDataset.getDatasetId());

        if (dataShare == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATA_SHARE_ID_NOT_FOUND, "dataShareId", dshInDataset.getDataShareId()));
        }

        if (dataset == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATASET_ID_NOT_FOUND, "datasetId", dshInDataset.getDatasetId()));
        }

        if (dataShare != null) {
            if (!dataProvider.isAdminUser() && !dataProvider.isServiceAdmin(dataShare.getService()) && !dataProvider.isZoneAdmin(dataShare.getZone())) {
                validateAdmin(dataProvider.getCurrentUserLoginId(), "datashare", dataShare.getName(), dataShare.getAdmins(), result);
            }
        }

        if (dshInDataset.getStatus() != RangerGds.GdsShareStatus.NONE && dshInDataset.getStatus() != RangerGds.GdsShareStatus.REQUESTED) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_ADD_DATA_SHARE_IN_DATASET_INVALID_STATUS, "status", dshInDataset.getStatus()));
        }

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateCreate(dshInDataset={})", dshInDataset);
    }

    public void validateUpdate(RangerDataShareInDataset dshInDataset, RangerDataShareInDataset existing) {
        LOG.debug("==> validateUpdate(dshInDataset={}, existing={})", dshInDataset, existing);

        ValidationResult result = new ValidationResult();

        if (existing == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATA_SHARE_IN_DATASET_ID_NOT_FOUND, "id", dshInDataset.getId()));
        } else {
            RangerDataShare dataShare = dataProvider.getDataShare(existing.getDataShareId());
            RangerDataset   dataset   = dataProvider.getDataset(existing.getDatasetId());

            if (dataShare == null) {
                result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATA_SHARE_ID_NOT_FOUND, "dataShareId", existing.getDataShareId()));
            }

            if (dataset == null) {
                result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATASET_ID_NOT_FOUND, "datasetId", existing.getDatasetId()));
            }

            if (!Objects.equals(dshInDataset.getDataShareId(), existing.getDataShareId())) {
                result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_UPDATE_IMMUTABLE_FIELD, "dataShareId"));

                dataShare = null;
            }

            if (!Objects.equals(dshInDataset.getDatasetId(), existing.getDatasetId())) {
                result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_UPDATE_IMMUTABLE_FIELD, "datasetId"));

                dataset = null;
            }

            if (dataShare != null && dataset != null) {
                boolean requireDataShareAdmin = false;
                boolean requireDatasetAdmin   = false;

                if (!Objects.equals(existing.getStatus(), dshInDataset.getStatus())) {
                    switch (existing.getStatus()) {
                        case NONE:
                            if (dshInDataset.getStatus() == RangerGds.GdsShareStatus.REQUESTED) {
                                requireDatasetAdmin = true;
                            } else if (dshInDataset.getStatus() == RangerGds.GdsShareStatus.GRANTED) {
                                requireDataShareAdmin = true;
                            } else if (dshInDataset.getStatus() == RangerGds.GdsShareStatus.ACCEPTED) {
                                result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_INVALID_STATUS_CHANGE, "status", existing.getStatus(), dshInDataset.getStatus()));
                            }
                            break;

                        case REQUESTED:
                            if (dshInDataset.getStatus() == RangerGds.GdsShareStatus.NONE) {
                                requireDatasetAdmin = true;
                            } else if (dshInDataset.getStatus() == RangerGds.GdsShareStatus.GRANTED) {
                                requireDataShareAdmin = true;
                            } else if (dshInDataset.getStatus() == RangerGds.GdsShareStatus.ACCEPTED) {
                                result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_INVALID_STATUS_CHANGE, "status", existing.getStatus(), dshInDataset.getStatus()));
                            }
                            break;

                        case GRANTED:
                            if (dshInDataset.getStatus() == RangerGds.GdsShareStatus.ACCEPTED) {
                                requireDatasetAdmin = true;
                            }
                            break;

                        case ACCEPTED:
                        default:
                            break;
                    }

                    if (requireDataShareAdmin) {
                        if (!dataProvider.isAdminUser() && !dataProvider.isServiceAdmin(dataShare.getService()) && !dataProvider.isZoneAdmin(dataShare.getZone())) {
                            validateAdmin(dataProvider.getCurrentUserLoginId(), "datashare", dataShare.getName(), dataShare.getAdmins(), result);
                        }
                    } else if (requireDatasetAdmin) {
                        if (!dataProvider.isAdminUser()) {
                            validateAdmin(dataProvider.getCurrentUserLoginId(), "dataset", dataset.getName(), dataset.getAdmins(), result);
                        }
                    } else { // must be either a dataset admin or a datashare admin
                        // TODO:
                    }
                }
            }
        }

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateUpdate(dshInDataset={}, existing={})", dshInDataset, existing);
    }

    public void validateDelete(Long dshInDatasetId, RangerDataShareInDataset existing) {
        LOG.debug("==> validateDelete(dshInDatasetId={}, existing={})", dshInDatasetId, existing);

        ValidationResult result = new ValidationResult();

        if (existing == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATA_SHARE_IN_DATASET_ID_NOT_FOUND, "id", dshInDatasetId));
        } else {
            RangerDataShare dataShare = dataProvider.getDataShare(existing.getDataShareId());
            RangerDataset   dataset   = dataProvider.getDataset(existing.getDatasetId());

            if (dataShare == null) {
                result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATA_SHARE_ID_NOT_FOUND, "dataShareId", existing.getDataShareId()));
            }

            if (dataset == null) {
                result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATASET_ID_NOT_FOUND, "datasetId", existing.getDatasetId()));
            }

            if (dataShare != null && dataset != null) {
                // TODO: must be either a dataset admin or datashare admin
            }
        }

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateDelete(dshInDatasetId={}, existing={})", dshInDatasetId, existing);
    }

    public void validateCreate(RangerDatasetInProject dsInProject) {
        LOG.debug("==> validateCreate(dsInProject={})", dsInProject);

        ValidationResult result = new ValidationResult();

        // TODO:

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateCreate(dsInProject={})", dsInProject);
    }

    public void validateUpdate(RangerDatasetInProject dsInProject, RangerDatasetInProject existing) {
        LOG.debug("==> validateUpdate(dsInProject={}, existing={})", dsInProject, existing);

        ValidationResult result = new ValidationResult();

        // TODO:

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateUpdate(dsInProject={}, existing={})", dsInProject, existing);
    }

    public void validateDelete(Long dsInProjectId, RangerDatasetInProject existing) {
        LOG.debug("==> validateDelete(dsInProjectId={}, existing={})", dsInProjectId, existing);

        ValidationResult result = new ValidationResult();

        // TODO:

        if (!result.isSuccess()) {
            result.throwRESTException();
        }

        LOG.debug("<== validateDelete(dsInProjectId={}, existing={})", dsInProjectId, existing);
    }


    private void validatePrincipals(List<RangerPrincipal> principals, String fieldName, ValidationResult result) {
        if (principals != null) {
            for (RangerPrincipal principal : principals) {
                switch (principal.getType()) {
                    case USER:
                        validateUser(principal.getName(), fieldName, result);
                    break;

                    case GROUP:
                        validateGroup(principal.getName(), fieldName, result);
                    break;

                    case ROLE:
                        validateRole(principal.getName(), fieldName, result);
                    break;
                }
            }
        }
    }

    private void validateAcl(RangerGdsACL acl, String fieldName, ValidationResult result) {
        if (acl != null) {
            if (acl.getUsers() != null) {
                for (String userName : acl.getUsers().keySet()) {
                    validateUser(userName, fieldName, result);
                }
            }

            if (acl.getGroups() != null) {
                for (String groupName : acl.getGroups().keySet()) {
                    validateGroup(groupName, fieldName, result);
                }
            }

            if (acl.getRoles() != null) {
                for (String roleName : acl.getRoles().keySet()) {
                    validateRole(roleName, fieldName, result);
                }
            }
        }
    }

    private void validateUser(String userName, String fieldName, ValidationResult result) {
        Long userId = dataProvider.getUserId(userName);

        if (userId == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_NON_EXISTING_USER, fieldName, userName));
        }
    }

    private void validateGroup(String groupName, String fieldName, ValidationResult result) {
        Long groupId = dataProvider.getGroupId(groupName);

        if (groupId == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_NON_EXISTING_GROUP, fieldName, groupName));
        }
    }

    private void validateRole(String roleName, String fieldName, ValidationResult result) {
        Long roleId = dataProvider.getRoleId(roleName);

        if (roleId == null) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_NON_EXISTING_ROLE, fieldName, roleName));
        }
    }

    private void validateAdmin(String userName, String objType, String objName, List<RangerPrincipal> admins, ValidationResult result) {
        boolean isAdmin = false;

        if (admins != null) {
            Set<String> userGroups = null;
            Set<String> userRoles  = null;

            for (RangerPrincipal admin : admins) {
                if (admin.getType() == RangerPrincipal.PrincipalType.USER) {
                    isAdmin = StringUtils.equals(userName, admin.getName());
                } else if (admin.getType() == RangerPrincipal.PrincipalType.GROUP) {
                    if (userGroups == null) {
                        userGroups = dataProvider.getGroupsForUser(userName);
                    }

                    isAdmin = userGroups.contains(admin.getName());
                } else if (admin.getType() == RangerPrincipal.PrincipalType.ROLE) {
                    if (userRoles == null) {
                        if (userGroups == null) {
                            userGroups = dataProvider.getGroupsForUser(userName);
                        }

                        userRoles = dataProvider.getRolesForUser(userName);
                    }

                    isAdmin = userRoles != null && userRoles.contains(admin.getName());
                }

                if (isAdmin) {
                    break;
                }
            }
        }

        if (!isAdmin) {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_NOT_ADMIN, null, userName, objType, objName));
        }
    }

    private void validateServiceZoneAdmin(String serviceName, String zoneName, ValidationResult result) {
        if (StringUtils.isNotBlank(serviceName)) {
            Long serviceId = dataProvider.getServiceId(serviceName);

            if (serviceId == null) {
                result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_NON_EXISTING_SERVICE, null, serviceName));
            } else {
                boolean isServiceAdmin = dataProvider.isAdminUser() || dataProvider.isServiceAdmin(serviceName);

                if (!isServiceAdmin) {
                    if (StringUtils.isNotBlank(zoneName)) {
                        Long zoneId = dataProvider.getZoneId(zoneName);

                        if (zoneId == null) {
                            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_NON_EXISTING_ZONE, null, zoneName));
                        } else {
                            boolean isZoneAdmin = dataProvider.isZoneAdmin(zoneName);

                            if (!isZoneAdmin) {
                                result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATA_SHARE_NOT_SERVICE_OR_ZONE_ADMIN, "serviceName", serviceName, zoneName));
                            }
                        }
                    } else {
                        result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_DATA_SHARE_NOT_SERVICE_ADMIN, "serviceName", serviceName));
                    }
                }
            }
        } else {
            result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_SERVICE_NAME_MISSING, null));
        }
    }

    private void validateAccessTypes(String serviceName, String fieldName, Set<String> accessTypes, ValidationResult result) {
        if (accessTypes != null && !accessTypes.isEmpty()) {
            Set<String> validAccessTypes = dataProvider.getAccessTypes(serviceName);

            for (String accessType : accessTypes) {
                if (!validAccessTypes.contains(accessType)) {
                    result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_INVALID_ACCESS_TYPE, fieldName, accessType));
                }
            }
        }
    }

    private void validateMaskTypes(String serviceName, String fieldName, Map<String, RangerPolicyItemDataMaskInfo> maskTypes, ValidationResult result) {
        if (maskTypes != null && !maskTypes.isEmpty()) {
            Set<String> validMaskTypes = dataProvider.getMaskTypes(serviceName);

            for (RangerPolicyItemDataMaskInfo maskInfo : maskTypes.values()) {
                if (!validMaskTypes.contains(maskInfo.getDataMaskType())) {
                    result.addValidationFailure(new ValidationFailureDetails(ValidationErrorCode.GDS_VALIDATION_ERR_INVALID_MASK_TYPE, fieldName, maskInfo.getDataMaskType()));
                }
            }
        }
    }

    public class ValidationResult {
        private final List<ValidationFailureDetails> validationFailures = new ArrayList<>();

        private ValidationResult() {
        }

        public boolean isSuccess() { return validationFailures.isEmpty(); }

        public void addValidationFailure(ValidationFailureDetails validationFailure) {
            validationFailures.add(validationFailure);
        }

        public List<ValidationFailureDetails> getValidationFailures() { return validationFailures; }

        public void throwRESTException() {
            throw restErrorUtil.createRESTException(validationFailures.toString(), MessageEnums.INVALID_INPUT_DATA);
        }
    }
}