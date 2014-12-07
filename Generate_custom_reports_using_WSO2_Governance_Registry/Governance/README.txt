Governance Model
=======================

1. Follow instructions given in https://docs.wso2.com/display/Governance460/Reports to add the report to WSO2 Governance Registry.
   For example:

    Report Name     - GovernanceReport
    Template        - /_system/governance/reports/governance_template.jrxml
                       (Please note that you need to upload the report template to this location.
                        If you have uploaded it elsewhere, please use the correct path here)
    Report Type     - PDF
    Report Class    - org.wso2.carbon.registry.samples.reporting.GovernanceReportGenerator

2. Add the logo.png(found under resources) to the WSO2 Governance Registry. This should be uploaded to /_system/governance/repository/images/logo.png path.
3. Give read permissions to system/wso2.anonymous.role for /_system/governance/repository/images/logo.png
