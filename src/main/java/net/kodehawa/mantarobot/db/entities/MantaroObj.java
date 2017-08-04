package net.kodehawa.mantarobot.db.entities;

import lombok.Data;
import net.kodehawa.mantarobot.db.ManagedObject;
import org.apache.commons.lang3.tuple.Pair;

import java.beans.ConstructorProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.rethinkdb.RethinkDB.r;
import static net.kodehawa.mantarobot.data.MantaroData.conn;

@Data
public class MantaroObj implements ManagedObject {
	public static final String DB_TABLE = "mantaro";

	public static MantaroObj create() {
		return new MantaroObj(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new HashMap<>(), new HashMap<>());
	}

	public final String id = "mantaro";
	public List<String> blackListedGuilds = null;
	public List<String> blackListedUsers = null;
	public List<String> patreonUsers = null;
	private Map<String, Long> tempBans = null;
	private Map<Long, Pair<String, Long>> mutes = null;

	@ConstructorProperties({"blackListedGuilds", "blackListedUsers", "patreonUsers", "tempbans", "mutes"})
	public MantaroObj(List<String> blackListedGuilds, List<String> blackListedUsers, List<String> patreonUsers, Map<String, Long> tempBans, Map<Long, Pair<String, Long>> mutes) {
		this.blackListedGuilds = blackListedGuilds;
		this.blackListedUsers = blackListedUsers;
		this.patreonUsers = patreonUsers;
		this.tempBans = tempBans;
		this.mutes = mutes;
	}

	@Override
	public void delete() {
		r.table(DB_TABLE).get(getId()).delete().runNoReply(conn());
	}

	@Override
	public void save() {
		r.table(DB_TABLE).insert(this)
			.optArg("conflict", "replace")
			.runNoReply(conn());
	}
}
