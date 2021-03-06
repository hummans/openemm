/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.backend.dao;

import	java.sql.SQLException;
import	java.util.HashMap;
import	java.util.HashSet;
import	java.util.Map;
import	java.util.Set;
import	org.agnitas.backend.DBase;

public class ConfigDAO {
	private Map <String, Map <String, String>>	config;

	public ConfigDAO (DBase dbase, String user, String ... hostnames) throws SQLException {
		config = new HashMap <> ();
		
		try (DBase.With with = dbase.with ()) {
			Set <String>	seenByHost = new HashSet <> ();
			
			for (Map <String, Object> row : dbase.query (with.jdbc (), "SELECT class, name, value, hostname FROM config_tbl")) {
				String	configClass = dbase.asString (row.get ("class"));
				String	configName = dbase.asString (row.get ("name"));
				String	configValue = dbase.asString (row.get ("value"));
				String	configHostname = dbase.asString (row.get ("hostname"));
				
				if ((configClass != null) && (configName != null) && (configValue != null) && ((configHostname == null) || Tools.isin (configHostname, user, hostnames))) {
					String			key = configClass + ":" + configName;
					
					if ((configHostname != null) || (! seenByHost.contains (key))) {
						Map <String, String>	entry = config.get (configClass);
					
						if (entry == null) {
							entry = new HashMap <> ();
							config.put (configClass, entry);
						}
						entry.put (configName, configValue);
						if (configHostname != null) {
							seenByHost.add (key);
						}
					}
				}
			}
		}
	}
	
	public Map <String, String> getClassEntry (String className) {
		return config.get (className);
	}
}
