/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.emm.core.blacklist.service;

import java.util.List;
import java.util.Set;

import org.agnitas.beans.BlackListEntry;
import org.agnitas.beans.Mailinglist;
import org.agnitas.beans.impl.PaginatedListImpl;
import org.agnitas.emm.core.velocity.VelocityCheck;

import com.agnitas.emm.core.globalblacklist.beans.BlacklistDto;
import com.agnitas.emm.core.globalblacklist.beans.GlobalBlacklistDto;


public interface BlacklistService {

    boolean insertBlacklist(BlacklistModel model);

    boolean deleteBlacklist(BlacklistModel model);

    boolean checkBlacklist(BlacklistModel model);

    List<String> getEmailList(@VelocityCheck int companyID) throws Exception;

    List<BlackListEntry> getRecipientList(@VelocityCheck int companyID) throws Exception;

    List<Mailinglist> getMailinglistsWithBlacklistedBindings(BlacklistModel model);

    void updateBlacklistedBindings(BlacklistModel bm, List<Integer> mailinglists, int userStatus);

    boolean isAlreadyExist(@VelocityCheck int companyId, String email);

    boolean add(@VelocityCheck int companyId, int adminId, String email, String reason) throws Exception;

    void add(GlobalBlacklistDto globalBlacklistDto);

    boolean update(@VelocityCheck int companyId, String email, String reason);

    PaginatedListImpl<BlacklistDto> getAll(@VelocityCheck int companyId, String sort, String direction, int page, int rowNumber, String likePattern);

    List<BlacklistDto> getAll(@VelocityCheck int companyId) throws Exception;

    List<Mailinglist> getBindedMailingLists(@VelocityCheck int companyId, String email);

    boolean delete(@VelocityCheck int companyId, String email, Set<Integer> mailinglistIds);
    
    boolean blacklistCheck(String email, @VelocityCheck int companyId);
    
    boolean blacklistCheckCompanyOnly(String email, @VelocityCheck int companyId);
    
    Set<String> loadBlackList(@VelocityCheck int companyId) throws Exception;
}
