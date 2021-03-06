/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.emm.core.company.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.agnitas.beans.AdminEntry;
import org.agnitas.beans.Company;
import org.agnitas.beans.factory.CompanyFactory;
import org.agnitas.beans.impl.PaginatedListImpl;
import org.agnitas.emm.core.commons.util.ConfigService;
import org.agnitas.emm.core.commons.util.ConfigValue;
import org.agnitas.emm.core.useractivitylog.UserAction;
import org.agnitas.emm.core.velocity.VelocityCheck;
import org.agnitas.service.UserActivityLogService;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;

import com.agnitas.beans.ComAdmin;
import com.agnitas.beans.ComCompany;
import com.agnitas.dao.ComCompanyDao;
import com.agnitas.emm.core.Permission;
import com.agnitas.emm.core.admin.form.AdminForm;
import com.agnitas.emm.core.admin.service.AdminSavingResult;
import com.agnitas.emm.core.admin.service.AdminService;
import com.agnitas.emm.core.company.bean.CompanyEntry;
import com.agnitas.emm.core.company.dto.CompanyAdminDto;
import com.agnitas.emm.core.company.dto.CompanyInfoDto;
import com.agnitas.emm.core.company.dto.CompanySettingsDto;
import com.agnitas.emm.core.company.enums.Business;
import com.agnitas.emm.core.company.enums.Language;
import com.agnitas.emm.core.company.enums.Sector;
import com.agnitas.emm.core.company.form.CompanyCreateForm;
import com.agnitas.emm.core.company.form.CompanyViewForm;
import com.agnitas.emm.core.company.service.ComCompanyService;
import com.agnitas.emm.core.recipient.service.RecipientProfileHistoryService;
import com.agnitas.emm.premium.web.PremiumFeature;
import com.agnitas.service.ExtendedConversionService;
import com.agnitas.web.mvc.Popups;

public class ComCompanyServiceImpl implements ComCompanyService {

    /**
     * The logger.
     */
    private static final transient Logger logger = Logger.getLogger(ComCompanyServiceImpl.class);

    private RecipientProfileHistoryService recipientProfileHistoryService;
    private UserActivityLogService userActivityLogService;
    private ExtendedConversionService conversionService;
    private CompanyFactory companyFactory;
    private ConfigService configService;
    private AdminService adminService;
    protected ComCompanyDao companyDao;

    @Override
    public boolean initTables(int companyID) {
        if (!companyDao.initTables(companyID)) {
            logger.error("Cannot create tables for company id: " + companyID);
            return false;
        }

        return true;
    }

    @Override
    public final List<ComCompany> listActiveCompanies() {
        return companyDao.getAllActiveCompaniesWithoutMasterCompany();
    }

    @Override
    public final ComCompany getCompany(int companyID) {
        return companyDao.getCompany(companyID);
    }

    @Override
    public CompanyInfoDto getLight(int companyID) {
        CompanyEntry companyLight = companyDao.getCompanyLight(companyID);
        return conversionService.convert(companyLight, CompanyInfoDto.class);
    }

    @Override
    public boolean addExecutiveAdmin(int companyID, int executiveAdminID) {
        return companyDao.addExecutiveAdmin(companyID, executiveAdminID);
    }

    @Override
    public int getPriorityCount(@VelocityCheck int companyId) {
        return companyDao.getPriorityCount(companyId);
    }

    @Override
    public void setPriorityCount(@VelocityCheck int companyId, int value) {
        companyDao.setPriorityCount(companyId, Math.max(0, value));
    }

    @Override
    public Set<Permission> getCompanyPermissions(int companyId) {
        return companyDao.getCompanyPermissions(companyId);
    }

    @Override
    public PaginatedListImpl<CompanyInfoDto> getCompanyList(int companyID, String sort, String direction, int page, int rownums) {
        PaginatedListImpl<CompanyEntry> companyList = companyDao.getCompanyList(companyID, sort, direction, page, rownums);
        return conversionService.convertPaginatedList(companyList, CompanyEntry.class, CompanyInfoDto.class);
    }

    @Override
    public CompanyViewForm getCompanyForm(int companyId) {
        ComCompany company = getCompany(companyId);
        return conversionService.convert(company, CompanyViewForm.class);
    }

    @Override
    public List<AdminEntry> getAdmins(int companyId) {
        return ListUtils.emptyIfNull(adminService.listAdminsByCompanyID(companyId));
    }

    @Override
    public int save(ComAdmin admin, CompanyCreateForm form, Popups popups, String sessionId) throws Exception {
        ComCompany company = (ComCompany) companyFactory.newCompany();
        company.setCreatorID(admin.getCompanyID());
        company.setStatus(Company.STATUS_ACTIVE);
        company.setRdirDomain(configService.getValue(ConfigValue.DefaultRdirDomain));
        company.setMailloopDomain(configService.getValue(ConfigValue.DefaultMailloopDomain));

        setupInfo(company, form.getCompanyInfoDto());
        setupSettings(company, form.getCompanySettingsDto());
        // save new new company
        companyDao.saveCompany(company);

        // save config values
        saveConfigValues(admin, company.getId(), form.getCompanySettingsDto());

        // create new table for new admin
        initTableAndCopyTemplates(company, sessionId);

        // create new user for new company
        int executiveAdminId = createExecutiveAdmin(admin, form.getCompanyInfoDto(), form.getCompanyAdminDto(), company.getId(), popups);
        addExecutiveAdmin(company.getId(), executiveAdminId);
        return company.getId();
    }

    @Override
    public int update(ComAdmin admin, CompanyViewForm form) throws Exception {
        ComCompany company = companyDao.getCompany(form.getCompanyInfoDto().getId());
        UserAction companyChangesLog = getCompanyChangesLog(form, company);
        setupInfo(company, form.getCompanyInfoDto());
        setupSettings(company, form.getCompanySettingsDto());

        // saving company
        companyDao.saveCompany(company);

        // save config values
        saveConfigValues(admin, company.getId(), form.getCompanySettingsDto());

        // write UAL
        userActivityLogService.writeUserActivityLog(admin, companyChangesLog);
        return company.getId();
    }
    
    @Override
    public String getStatus(int companyID) {
    	ComCompany company = companyDao.getCompany(companyID);
		return company.getStatus();
    }

    @Override
    public boolean deleteCompany(int companyIdForRemove) {
        return false;
    }
    
    @Override
    public boolean deactivateCompany(int companyIdForDeactivation) {
        ComCompany company = companyDao.getCompany(companyIdForDeactivation);
        if (Objects.nonNull(company)) {
            companyDao.deleteCompany(company);
            if (logger.isInfoEnabled()) {
                logger.info("Company: " + companyIdForDeactivation + " deactivated");
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isCompanyNameUnique(int companyId, String shortname) {
        String actualShortname = companyDao.getShortName(companyId);
        if(StringUtils.isNotEmpty(actualShortname) && StringUtils.equals(actualShortname, shortname)) {
            return true;
        }

        return StringUtils.isNotEmpty(shortname) && companyDao.isCompanyNameUnique(shortname);
    }

    @Override
    public boolean isCreatorId(@VelocityCheck int companyId, int creatorId) {
        return companyId > 0 && companyDao.isCreatorId(companyId, creatorId);
    }

    private void setupInfo(ComCompany company, CompanyInfoDto companyInfoDto) {
        company.setShortname(companyInfoDto.getName());
        company.setDescription(companyInfoDto.getDescription());
    }

    private void setupSettings(ComCompany company, CompanySettingsDto settingsDto) {
        company.setSalutationExtended(BooleanUtils.toInteger(settingsDto.isHasExtendedSalutation()));
        company.setStatAdmin(settingsDto.getExecutiveAdministrator());
        company.setContactTech(settingsDto.getTechnicalContacts());

        /* update export notify admin field if HasDataExportNotify checkbox true,
                otherwise set export notify admin value 0 */
        if (settingsDto.isHasDataExportNotify()) {
            company.setExportNotifyAdmin(settingsDto.getExecutiveAdministrator());
        } else {
            company.setExportNotifyAdmin(0);
        }
        company.setSecretKey(RandomStringUtils.randomAscii(32));
        company.setSector(settingsDto.getSector());
        company.setBusiness(settingsDto.getBusiness());
    }

    private void saveConfigValues(ComAdmin admin, int companyId, CompanySettingsDto settings) {
        if (admin.permissionAllowed(Permission.COMPANY_FORCE_SENDING)) {
            configService.writeOrDeleteIfDefaultBooleanValue(ConfigValue.HostAuthentication,
                    companyId,
                    settings.isHasTwoFactorAuthentication());
        }

        if (admin.permissionAllowed(Permission.COMPANY_FORCE_SENDING)) {
            configService.writeOrDeleteIfDefaultBooleanValue(ConfigValue.ForceSending,
                    companyId,
                    settings.isHasForceSending());
        }

        if (!settings.getLanguage().equalsIgnoreCase(Language.NONE.toString())) {
            String locale = settings.getLanguage();
            configService.writeOrDeleteIfDefaultValue(ConfigValue.LocaleLanguage, companyId, locale);

            String localeCountry = locale.substring(locale.indexOf('_') + 1);
            configService.writeOrDeleteIfDefaultValue(ConfigValue.LocaleCountry, companyId, localeCountry);
        } else {
            configService.writeOrDeleteIfDefaultValue(ConfigValue.LocaleLanguage, companyId, null);
            configService.writeOrDeleteIfDefaultValue(ConfigValue.LocaleCountry, companyId, null);
        }

        if (!StringUtils.equalsIgnoreCase(settings.getTimeZone(), "0")) {
            configService.writeOrDeleteIfDefaultValue(ConfigValue.LocaleTimezone, companyId, settings.getTimeZone());
        } else {
            configService.writeOrDeleteIfDefaultValue(ConfigValue.LocaleTimezone, companyId, null);
        }

        if (settings.isHasRecipientsCleanup() != configService.getBooleanValue(ConfigValue.CleanRecipientsWithoutBinding, companyId)) {
            configService.writeOrDeleteIfDefaultValue(ConfigValue.CleanRecipientsWithoutBinding, companyId, String.valueOf(settings.isHasRecipientsCleanup()));
        }

        if (settings.isHasRecipientsAnonymisation() != configService.getBooleanValue(ConfigValue.CleanRecipientsData, companyId)) {
        	configService.writeOrDeleteIfDefaultValue(ConfigValue.CleanRecipientsData, companyId, String.valueOf(settings.isHasRecipientsAnonymisation()));
        }

        if (settings.isHasTrackingVeto() != configService.getBooleanValue(ConfigValue.AnonymizeTrackingVetoRecipients, companyId)) {
            configService.writeOrDeleteIfDefaultValue(ConfigValue.AnonymizeTrackingVetoRecipients,
                    companyId,
                    String.valueOf(settings.isHasTrackingVeto()));
        }

        configService.writeOrDeleteIfDefaultBooleanValue(ConfigValue.SupervisorRequiresLoginPermission,
                companyId,
                settings.isHasActivatedAccessAuthorization());
        
        if (settings.getMaxAdminMails() != 0) {
        	configService.writeOrDeleteIfDefaultValue(ConfigValue.MaxAdminMails, companyId, Integer.toString(settings.getMaxAdminMails()));
        }
        
    	int expireMaximum = configService.getIntegerValue(ConfigValue.ExpireStatisticsMax);
        if (settings.getStatisticsExpireDays() > 0) {
        	configService.writeOrDeleteIfDefaultValue(ConfigValue.ExpireStatistics, companyId, Integer.toString(Math.min(expireMaximum, settings.getStatisticsExpireDays())));
        }
        
        configService.writeOrDeleteIfDefaultValue(ConfigValue.ExpireRecipient, companyId, Integer.toString(settings.getRecipientExpireDays()));
    }

    private int initTableAndCopyTemplates(ComCompany company, String sessionId) throws Exception {
        int companyId = company.getId();

        if (initTables(companyId)) {
            logger.info("Company: " + companyId + " created");

            if ("Inhouse".equalsIgnoreCase(configService.getValue(ConfigValue.System_License_Type))) {
                if (StringUtils.isBlank(configService.getValue(ConfigValue.RecipientProfileFieldHistory, companyId))) {
                    // Setup RecipientProfileFieldHistory for inhouse instances only
                    recipientProfileHistoryService.enableProfileFieldHistory(companyId);
                }

                // Check Automation Package
                if (company.getMailtracking() <= 0) {
                    company.setMailtracking(1);
                    companyDao.saveCompany(company);
                }
                companyDao.changeFeatureRights(PremiumFeature.AUTOMATION, companyId, true);

                checkRetargeting(company);
            }

            generateMissingTemplateThumbnails(companyId, sessionId);

            return companyId;
        }

        logger.info("Cannot successfully create new company: " + companyId);
        return 0;
    }

    void generateMissingTemplateThumbnails(int companyId, String sessionId) {
        // do nothing
    }

    void checkRetargeting(ComCompany company) {
    	// do nothing
    }

    private int createExecutiveAdmin(ComAdmin admin, CompanyInfoDto companyInfo, CompanyAdminDto companyAdmin, int newCompanyID, Popups ppopups) {
        AdminForm adminForm = new AdminForm();
        adminForm.setAdminID(0);
        adminForm.setCompanyID(newCompanyID);
        adminForm.setCompanyName(companyInfo.getName());
        adminForm.setEmail(companyAdmin.getEmail());
        adminForm.setStatEmail(companyAdmin.getStatisticEmail());
        adminForm.setTitle(companyAdmin.getTitle());
        adminForm.setGender(companyAdmin.getSalutation());
        adminForm.setUsername(companyAdmin.getUsername());
        adminForm.setFirstname(companyAdmin.getFirstName());
        adminForm.setFullname(companyAdmin.getLastName());
        adminForm.setPassword(companyAdmin.getPassword());
        adminForm.setOneTimePassword(companyAdmin.getHasDisposablePassword());
        adminForm.setGroupID(adminService.adminGroupExists(admin.getCompanyID(), "Administrator"));

        if (companyAdmin.getLanguage() != null) {
            int aPos = companyAdmin.getLanguage().indexOf('_');
            String lang = companyAdmin.getLanguage().substring(0, aPos);
            String country = companyAdmin.getLanguage().substring(aPos + 1);
            adminForm.setAdminLocale(new Locale(lang, country));
        }

        adminForm.setAdminTimezone(companyAdmin.getTimeZone());

        AdminSavingResult result = adminService.saveAdmin(adminForm, admin);

        if (result.isSuccess()) {
            ComAdmin savedAdmin = result.getResult();
            return savedAdmin.getAdminID();
        } else {
            ppopups.alert(result.getError());
            return 0;
        }
    }

    private UserAction getCompanyChangesLog(CompanyViewForm newCompany, ComCompany oldCompany) {
        StringBuilder description = new StringBuilder();
        description.append("ID(").append(newCompany.getCompanyInfoDto().getId()).append(")");

        String oldCompanyShortname = oldCompany.getShortname();

        if (!oldCompanyShortname.equals(newCompany.getCompanyInfoDto().getName())) {
            description.append(" Short Name(")
                    .append(oldCompanyShortname)
                    .append(" changed to ")
                    .append(newCompany.getCompanyInfoDto().getName())
                    .append(")");
        }

        String oldDescription = StringUtils.trimToEmpty(oldCompany.getDescription());
        String newDescription = StringUtils.trimToEmpty(newCompany.getCompanyInfoDto().getDescription());

        if (!oldDescription.equals(newDescription)) {
            description.append(" Description(")
                    .append(oldDescription)
                    .append(" changed to ")
                    .append(newCompany.getCompanyInfoDto().getDescription())
                    .append(")");
        }

        int oldExpireStat = configService.getIntegerValue(ConfigValue.ExpireStatistics, oldCompany.getId());

        if (oldExpireStat != newCompany.getCompanySettingsDto().getStatisticsExpireDays()) {
            description.append(" Expire Stat(")
                    .append(oldExpireStat)
                    .append(" changed to ")
                    .append(newCompany.getCompanySettingsDto().getStatisticsExpireDays())
                    .append(")");
        }

        int oldExpireRecipient = configService.getIntegerValue(ConfigValue.ExpireRecipient, oldCompany.getId());

        if (oldExpireRecipient != newCompany.getCompanySettingsDto().getRecipientExpireDays()) {
            description.append(" Expire Recipient(")
                    .append(oldExpireRecipient)
                    .append(" changed to ")
                    .append(newCompany.getCompanySettingsDto().getRecipientExpireDays())
                    .append(")");
        }
 
        int oldSectorIndex = oldCompany.getSector();
        int newSectorIndex = newCompany.getCompanySettingsDto().getSector();

        if (oldSectorIndex != newSectorIndex) {
            description.append(" Sector(")
                    .append(Sector.getById(oldSectorIndex))
                    .append(" changed to ")
                    .append(Sector.getById(newSectorIndex))
                    .append(")");
        }

        int oldBusinessIndex = oldCompany.getBusiness();

        if (oldBusinessIndex != newCompany.getCompanySettingsDto().getBusiness()) {
            description.append(" Business(")
                    .append(Business.getById(oldBusinessIndex))
                    .append(" changed to ")
                    .append(Business.getById(newCompany.getCompanySettingsDto().getBusiness()))
                    .append(")");
        }

        boolean oldCleanupSettings = configService.getBooleanValue(ConfigValue.CleanRecipientsWithoutBinding, newCompany.getCompanyInfoDto().getId());
        if (oldCleanupSettings != newCompany.getCompanySettingsDto().isHasRecipientsCleanup()) {
            description.append(" Recipients Cleanup (")
                    .append(oldCleanupSettings)
                    .append(" changed to ")
                    .append(newCompany.getCompanySettingsDto().isHasRecipientsCleanup())
                    .append(")");
        }

        boolean oldRecipientAnonymiseSettings = configService.getBooleanValue(ConfigValue.CleanRecipientsData, newCompany.getCompanyInfoDto().getId());
        if (oldRecipientAnonymiseSettings != newCompany.getCompanySettingsDto().isHasRecipientsAnonymisation()) {
        	description.append(" Recipients Anonymisation (")
            .append(oldRecipientAnonymiseSettings)
            .append(" changed to ")
            .append(newCompany.getCompanySettingsDto().isHasRecipientsAnonymisation())
            .append(")");
        }

        boolean oldAnonymizeSettings = configService.getBooleanValue(ConfigValue.AnonymizeTrackingVetoRecipients, newCompany.getCompanyInfoDto().getId());
        if (oldAnonymizeSettings != newCompany.getCompanySettingsDto().isHasTrackingVeto()) {
            description.append(" Recipients AnonymizeTrackingVetoStatistics (")
                    .append(oldAnonymizeSettings)
                    .append(" changed to ")
                    .append(newCompany.getCompanySettingsDto().isHasTrackingVeto())
                    .append(")");
        }

        boolean oldAccessAuthorisation = configService.getBooleanValue(ConfigValue.SupervisorRequiresLoginPermission, newCompany.getCompanyInfoDto().getId());
        if (oldAccessAuthorisation != newCompany.getCompanySettingsDto().isHasActivatedAccessAuthorization()) {
            description.append("Access authorisation settings were ")
                    .append(oldAccessAuthorisation)
                    .append(" changed to ")
                    .append(newCompany.getCompanySettingsDto().isHasActivatedAccessAuthorization());
        }

        return new UserAction("edit company", description.toString());
    }
    
    @Override
    public boolean createFrequencyFields(@VelocityCheck int companyID) {
    	return companyDao.createFrequencyFields(companyID);
    }

    @Required
    public void setRecipientProfileHistoryService(RecipientProfileHistoryService recipientProfileHistoryService) {
        this.recipientProfileHistoryService = recipientProfileHistoryService;
    }

    @Required
    public void setUserActivityLogService(UserActivityLogService userActivityLogService) {
        this.userActivityLogService = userActivityLogService;
    }

    @Required
    public void setConversionService(ExtendedConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Required
    public void setCompanyFactory(CompanyFactory companyFactory) {
        this.companyFactory = companyFactory;
    }

    @Required
    public void setConfigService(ConfigService configService) {
        this.configService = configService;
    }

    @Required
    public void setAdminService(AdminService adminService) {
        this.adminService = adminService;
    }

	@Required
    public void setCompanyDao(ComCompanyDao companyDao) {
        this.companyDao = companyDao;
    }

}
