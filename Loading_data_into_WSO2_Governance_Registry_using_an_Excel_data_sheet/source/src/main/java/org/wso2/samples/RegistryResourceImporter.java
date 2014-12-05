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

package org.wso2.samples;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.util.GovernanceArtifactConfiguration;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.utils.UUIDGenerator;
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient;

import javax.xml.namespace.QName;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class RegistryResourceImporter {

    // Carbon home - this should point to the folder where your WSO2 Governance Registry is unzipped
    public static final String CARBON_HOME = "/home/janaka/work/wso2/wso2greg-4.6.0";
    public static final String AXIS_2_CONFIGURATION = CARBON_HOME + File.separator + "repository" +
                                                      File.separator + "conf" + File.separator + "axis2" +
                                                      File.separator + "axis2_client.xml";
    // Server access URL
    public static final String SERVICE_URL = "https://localhost:9443/services/";
    // Default admin user name of the server
    public static final String USERNAME = "admin";
    // Default admin password
    public static final String PASSWORD = "admin";

    // Location of the Excel data
    public static final String DATA_DIR = "/home/janaka/work/wso2/source/src/main/resources";
    // Location of the properties files. The files contains the mapping between the Excel data cells and Governance
    // artifact fields
    public static final String PROPERTIES_BASE_PATH = "src/main/properties";
    // Property file for book publishers
    public static final String PUBLISHER_MAPPING_PROPERTIES = "publisher.mapping.properties";
    // Property file for books
    public static final String BOOK_MAPPING_PROPERTIES = "book.mapping.properties";
    // Publisher Governance artifact key
    public static final String PUBLISHER = "publisher";
    // Books Governance artifact key
    public static final String BOOK = "book";

    public static void main(String[] args) {
        // Set the system properties
        setSystemProperties();
        try {
            // Initialize the registry
            ConfigurationContext configContext = ConfigurationContextFactory.createConfigurationContextFromFileSystem
                    (AXIS_2_CONFIGURATION);
            Registry registry = new WSRegistryServiceClient(SERVICE_URL, USERNAME, PASSWORD, configContext);

            // Load the mapping properties
            Properties publisherProperties = getProperties(PROPERTIES_BASE_PATH + File.separator +
                                                                                 PUBLISHER_MAPPING_PROPERTIES);
            Properties bookProperties = getProperties(PROPERTIES_BASE_PATH + File.separator +
                                                                            BOOK_MAPPING_PROPERTIES);
            // Get the work books
            Workbook[] workbooks = getWorkbooks(new File(DATA_DIR));

            // Add the governance artifacts
            addAssets(registry, workbooks, PUBLISHER, publisherProperties);
            addAssets(registry, workbooks, BOOK, bookProperties);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    private static void setSystemProperties() {
        String trustStore = CARBON_HOME + File.separator + "repository" + File.separator +
                            "resources" + File.separator + "security" + File.separator + "wso2carbon.jks";
        System.setProperty("javax.net.ssl.trustStore", trustStore);
        System.setProperty("javax.net.ssl.trustStorePassword", "wso2carbon");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        System.setProperty("carbon.repo.write.mode", "true");
    }

    private static void addAssets(Registry registry, Workbook[] workbooks, String artifactType,
                                  Properties artifactMappings) throws Exception {
        for (Workbook workbook : workbooks) {
            Sheet sheet = workbook.getSheet(workbook.getSheetName(0));

            if (sheet == null || sheet.getLastRowNum() == -1) {
                throw new RuntimeException("The first sheet is empty");
            } else {
                System.out.println("Adding data in Sheet : " + sheet.getSheetName());
            }

            int limit = sheet.getLastRowNum();
            if (limit < 1) {
                throw new RuntimeException("Column headers were not specified in Asset Data Spreadsheet");
            } else {
                System.out.println("Total number of rows in the sheet : " + limit);
            }

            Row row = sheet.getRow(0);

            // We use a linked list to keep the order
            List<String> headersAttributeNames = new LinkedList<String>();
            String value;
            int count = 0;
            Set artifactAttributes = artifactMappings.keySet();

            while ((value = getCellValue(row.getCell(count++), null)) != null) {
                headersAttributeNames.add(getMappingName(artifactMappings, value));
            }

            Registry governanceRegistry = GovernanceUtils.getGovernanceUserRegistry(registry, USERNAME);
            addAssetValues(governanceRegistry, sheet, limit, artifactType, headersAttributeNames, artifactAttributes);
        }
    }

    private static void addAssetValues(Registry governanceRegistry, Sheet sheet, int limit, String assetType,
                                       List<String> headersAttributeNames, Set artifactAttributes)
            throws RegistryException {
        GovernanceArtifactConfiguration artifactConfiguration = GovernanceUtils.findGovernanceArtifactConfiguration
                (assetType, governanceRegistry);
        String nameAttribute = artifactConfiguration.getArtifactNameAttribute();
        String namespaceAttribute = artifactConfiguration.getArtifactNamespaceAttribute();

        GenericArtifactManager manager = new GenericArtifactManager(governanceRegistry, assetType);
        List<QName> addedQNames = new ArrayList<QName>();

        Map<String, String> attributeMap = new HashMap<String, String>();
        Row row;
        for (int i = 1; i <= limit; i++) {
            attributeMap.clear();
            row = sheet.getRow(i);

            if (row == null || row.getCell(0) == null || row.getCell(0).toString().trim().isEmpty()) {
                break;
            } else {
                System.out.println("Adding data in row : " + i);
            }

            // We use this code to get the cell values of the given attribute. We use a linked list to find the
            // column index of that attribute and add it to a map with the governance artifact field as the key
            for (Object attributeObject : artifactAttributes) {
                String attribute = attributeObject.toString();
                if (headersAttributeNames.contains(attribute)) {
                    if (BOOK.equals(assetType) && "overview_publisher".equals(attribute)) {
                        attributeMap.put(attribute, "/book-publishers/" + row.getCell(headersAttributeNames.indexOf
                                (attribute)).toString().trim());
                    } else {
                        attributeMap.put(attribute, row.getCell(headersAttributeNames.indexOf(attribute)).toString()
                                .trim());
                    }
                }
            }
            // Creating the artifact QName
            String namespaceURI = attributeMap.containsKey(namespaceAttribute) ? attributeMap.get(namespaceAttribute)
                                                                               : null;
            String localPart = attributeMap.containsKey(nameAttribute) ? attributeMap.get(nameAttribute) :
                               UUIDGenerator.generateUUID();
            localPart = removeInvalidCharacters(localPart);
            QName qName = new QName(namespaceURI, localPart);

            // Creating the governance artifact with the given fields.
            GenericArtifact artifact = manager.newGovernanceArtifact(qName);
            for (Map.Entry<String, String> e : attributeMap.entrySet()) {
                artifact.setAttribute(e.getKey(), e.getValue());
            }

            // Checking for duplicates
            if (!addedQNames.contains(qName)) {
                manager.addGenericArtifact(artifact);
                addedQNames.add(qName);
            }
        }
    }

    private static String removeInvalidCharacters(String localPart) {
        return localPart.replace("\'", "`").replace("\"", "`").replace("&", "and").replace(",", " ").replace("!", "");
    }

    private static Workbook[] getWorkbooks(File usersDir) throws Exception {
        List<Workbook> workbooks = new LinkedList<Workbook>();
        File[] files = usersDir.listFiles();
        if (files != null) {
            for (File file : files) {
                InputStream ins = null;
                try {
                    ins = new BufferedInputStream(new FileInputStream(file));
                    String extension = FilenameUtils.getExtension(file.getName());
                    if ("xlsx".equals(extension)) {
                        workbooks.add(new XSSFWorkbook(ins));
                    } else {
                        POIFSFileSystem fs = new POIFSFileSystem(ins);
                        workbooks.add(new HSSFWorkbook(fs));
                    }
                } finally {
                    if (ins != null) {
                        try {
                            ins.close();
                        } catch (IOException e) {
                            // We ignore exceptions here.
                        }
                    }
                }
            }
        }
        return workbooks.toArray(new Workbook[workbooks.size()]);
    }

    private static Properties getProperties(String fileName) throws Exception {
        Properties properties = new Properties();
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(fileName);
            properties.load(inputStream);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // We ignore any exceptions here.
                }
            }
        }
        return properties;
    }

    private static String getMappingName(Properties properties, String value) {
        for (Map.Entry<Object, Object> property : properties.entrySet()) {
            if (property.getValue().toString().equals(value)) {
                return property.getKey().toString();
            }
        }
        return null;
    }

    private static String getCellValue(Cell cell, String def) {
        return cell != null && cell.getCellType() != Cell.CELL_TYPE_BLANK ? cell.getStringCellValue() : def;
    }

}
