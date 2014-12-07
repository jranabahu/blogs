/*
*  Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.carbon.registry.samples.reporting;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.session.CurrentSession;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.registry.reporting.AbstractReportGenerator;
import org.wso2.carbon.registry.reporting.annotation.Property;
import org.wso2.carbon.registry.server.service.RegistryAdmin;
import org.wso2.carbon.reporting.api.ReportingException;
import org.wso2.carbon.reporting.util.JasperPrintProvider;
import org.wso2.carbon.reporting.util.ReportParamMap;
import org.wso2.carbon.reporting.util.ReportStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

public class GovernanceReportGenerator extends AbstractReportGenerator {
    private Log log = LogFactory.getLog(GovernanceReportGenerator.class);
    private String description;
    private String reportTitle;
    private String reportLogo;

    String logoURL;

    @Property(mandatory = false)
    public void setDescription(String description) {
        this.description = description;
    }

    @Property(mandatory = false)
    public void setReportTitle(String reportTitle) {
        this.reportTitle = reportTitle;
    }

    @Property(mandatory = false)
    public void setReportLogo(String reportLogo) {
        this.reportLogo = reportLogo;
    }

    public ByteArrayOutputStream execute(String template, String type) throws IOException {
        Registry governanceRegistry;

        try {
            Registry registry = getRegistry();
            List<ArtifactDescription> artifactDescriptions = new ArrayList<ArtifactDescription>();
            List<MetaData> serviceDataList = new ArrayList<MetaData>();

            String templateContent = new String((byte[]) registry.get(template).getContent(),"UTF-8");

            populateLogoURL();

            ReportParamMap map = new ReportParamMap();
            map.setParamKey("logo");
            map.setParamValue(logoURL);
            map.setParamType("String");

            List<ReportParamMap> reportList = new ArrayList<ReportParamMap>();

            governanceRegistry = GovernanceUtils.getGovernanceUserRegistry(registry, CurrentSession.getUser());
            loadServiceData(governanceRegistry, artifactDescriptions);
            loadPolicyData(governanceRegistry, artifactDescriptions);

            MetaData data = new MetaData();
            setReportTitle(data);
            setReportDescription(data);
            data.setArtifactData(new JRBeanCollectionDataSource(artifactDescriptions));

            serviceDataList.add(data);

            reportList.add(map);

            JRDataSource dataSource = new JRBeanCollectionDataSource(serviceDataList);
            JasperPrint print = new JasperPrintProvider().createJasperPrint(dataSource, templateContent, reportList
                    .toArray(new ReportParamMap[reportList.size()]));
            return new ReportStream().getReportStream(print, type.toLowerCase());

        } catch (RegistryException e) {
            log.error("Error while getting the Governance Registry", e);
        } catch (JRException e) {
            log.error("Error occurred while creating the jasper print ", e);
        } catch (ReportingException e) {
            log.error("Error while generating the report", e);
        }

        return new ByteArrayOutputStream(0);
    }

    private void populateLogoURL() {
        RegistryAdmin admin = new RegistryAdmin();
        try {
            if (reportLogo != null) {
                logoURL = admin.getHTTPPermalink(reportLogo);
            } else {
                logoURL = admin.getHTTPPermalink("/_system/governance/repository/images/logo.png");
            }
        } catch (Exception e) {
            log.error(e);
            logoURL = admin.getHTTPPermalink("/_system/governance/repository/images/logo.png");
        }
    }

    private void loadServiceData(Registry governanceRegistry, List<ArtifactDescription> artifactDescriptions)
            throws RegistryException {
        GenericArtifactManager manager = new GenericArtifactManager(governanceRegistry, "service");
        GenericArtifact[] genericArtifacts = manager.getAllGenericArtifacts();

        List<ReportBean> beanList = new LinkedList<ReportBean>();
        for (GenericArtifact artifact : genericArtifacts) {
            ReportBean bean = new ReportBean();
            String[] attributeKeys = artifact.getAttributeKeys();
            for (String key : attributeKeys) {
                String value = artifact.getAttribute(key);
                if (key.equals("overview_name")) {
                    bean.setOverview_name(convertValue(value));
                } else if (key.equals("overview_version")) {
                    bean.setOverview_version(convertValue(value));
                } else if (key.equals("overview_description")) {
                    bean.setOverview_description(convertValue(value));
                }
            }

            loadArtifactAssociations(governanceRegistry, artifact, bean);
            bean.setLifecycle_state(getLifecycleState(artifact));

            beanList.add(bean);
        }

        ArtifactDescription artifact = new ArtifactDescription();
        artifact.setArtifactType("Services");
        artifact.setMetaData(new JRBeanCollectionDataSource(beanList));

        artifactDescriptions.add(artifact);
    }

    private void loadPolicyData(Registry governanceRegistry, List<ArtifactDescription> artifactDescriptions)
            throws RegistryException {
        GenericArtifactManager manager = new GenericArtifactManager(governanceRegistry, "policy");
        GenericArtifact[] genericArtifacts = manager.getAllGenericArtifacts();

        List<ReportBean> beanList = new LinkedList<ReportBean>();
        for (GenericArtifact artifact : genericArtifacts) {
            ReportBean bean = new ReportBean();

            Resource policyResource = governanceRegistry.get(artifact.getPath());
            bean.setOverview_name(RegistryUtils.getResourceName(policyResource.getPath()));
            bean.setOverview_description(policyResource.getDescription());

            // We derive the version from path
            String parentPath = policyResource.getParentPath();
            String version = RegistryUtils.getResourceName(parentPath);

            if (version.matches("^\\d+[.]\\d+[.]\\d+(-[a-zA-Z0-9]+)?$")) {
                bean.setOverview_version(version);
            } else {
                bean.setOverview_version(" - ");
            }

            loadArtifactAssociations(governanceRegistry, artifact, bean);
            bean.setLifecycle_state(getLifecycleState(artifact));

            beanList.add(bean);
        }

        ArtifactDescription artifact = new ArtifactDescription();
        artifact.setArtifactType("Policies");
        artifact.setMetaData(new JRBeanCollectionDataSource(beanList));

        artifactDescriptions.add(artifact);
    }

    private void loadArtifactAssociations(Registry governanceRegistry, GenericArtifact artifact, ReportBean bean)
            throws RegistryException {
        org.wso2.carbon.registry.core.Association[] associations = governanceRegistry.getAllAssociations(artifact.getPath());
        List<Association> metaDataAssociations = new ArrayList<Association>();

        Map<String, List<String>> associationMap = new HashMap<String, List<String>>();
        for (org.wso2.carbon.registry.core.Association association : associations) {
            if (!association.getSourcePath().equals(artifact.getPath())) {
                continue;
            }
            if (associationMap.containsKey(association.getAssociationType())) {
                associationMap.get(association.getAssociationType()).
                        add(RegistryUtils.getResourceName(association.getDestinationPath()));
            } else {
                List<String> associatedNames = new ArrayList<String>();
                associatedNames.add(RegistryUtils.getResourceName(association.getDestinationPath()));
                associationMap.put(association.getAssociationType(), associatedNames);
            }

        }

        for (Map.Entry<String, List<String>> entry : associationMap.entrySet()) {
            Association association = new Association();
            association.setAssociationName(entry.getKey());
            association.setAssociatedResourceNames(new JRBeanCollectionDataSource(entry.getValue()));
            metaDataAssociations.add(association);
        }

        bean.setRelationships(new JRBeanCollectionDataSource(metaDataAssociations));
    }

    private String getLifecycleState(GenericArtifact artifact) throws GovernanceException {
        return artifact.getLifecycleState() == null ? " - " : artifact.getLifecycleState();
    }

    private void setReportDescription(MetaData data) {
        if (description == null) {
            data.setDescription("This report gives a overview of all the services and policies in the Registry. " +
                                "This information includes the name, version, description, relationships" +
                                "(dependencies and associations) and the lifecycle information of each service and " +
                                "Policy.");
        } else {
            data.setDescription(description);
        }
    }

    private void setReportTitle(MetaData data) {
        if (reportTitle != null) {
            data.setTitle(reportTitle);
        } else {
            data.setTitle("Governance Report");
        }
    }

    private String convertValue(String originalValue) {
        return originalValue == null ? "" : originalValue;
    }

    @SuppressWarnings("unused")
    public static class MetaData {
        private String title;
        private String description;
        private JRBeanCollectionDataSource artifactData;


        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public JRBeanCollectionDataSource getArtifactData() {
            return artifactData;
        }

        public void setArtifactData(JRBeanCollectionDataSource artifactData) {
            this.artifactData = artifactData;
        }
    }

    public static class ArtifactDescription {
        private String artifactType;
        private JRBeanCollectionDataSource metaData;

        public String getArtifactType() {
            return artifactType;
        }

        public void setArtifactType(String artifactType) {
            this.artifactType = artifactType;
        }

        public JRBeanCollectionDataSource getMetaData() {
            return metaData;
        }

        public void setMetaData(JRBeanCollectionDataSource metaData) {
            this.metaData = metaData;
        }
    }

    public static class ReportBean {

        private String overview_name;
        private String overview_version;
        private String overview_description;
        private String lifecycle_state;
        private JRBeanCollectionDataSource relationships;

        public String getOverview_name() {
            return overview_name;
        }

        public void setOverview_name(String overview_name) {
            this.overview_name = overview_name;
        }

        public String getLifecycle_state() {
            return lifecycle_state;
        }

        public void setLifecycle_state(String lifecycle_state) {
            this.lifecycle_state = lifecycle_state;
        }

        public String getOverview_version() {
            return overview_version;
        }

        public void setOverview_version(String overview_version) {
            this.overview_version = overview_version;
        }

        public String getOverview_description() {
            return overview_description;
        }

        public void setOverview_description(String overview_description) {
            this.overview_description = overview_description;
        }

        public JRBeanCollectionDataSource getRelationships() {
            return relationships;
        }

        public void setRelationships(JRBeanCollectionDataSource relationships) {
            this.relationships = relationships;
        }
    }

    public static class Association {

        private String associationName;
        private JRBeanCollectionDataSource associatedResourceNames;

        public String getAssociationName() {
            return associationName;
        }

        public void setAssociationName(String associationName) {
            this.associationName = associationName;
        }

        public JRBeanCollectionDataSource getAssociatedResourceNames() {
            return associatedResourceNames;
        }

        public void setAssociatedResourceNames(JRBeanCollectionDataSource associatedResourceNames) {
            this.associatedResourceNames = associatedResourceNames;
        }
    }
}
